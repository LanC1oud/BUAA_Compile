package compilerui.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 在独立临时目录中链接课程运行时并执行本机程序。 */
public final class IrRunner {
    private static final int OUTPUT_LIMIT = 256 * 1024;

    public record Result(String status, String output, String diagnostics, boolean success) {}

    private record Step(String stopped, int exit, String out, String err) {}

    public static Result run(String ir, String input) throws IOException, InterruptedException {
        Path runtime = Path.of("runtime/libsysy.c").toAbsolutePath();
        if (!Files.isRegularFile(runtime)) {
            throw new IOException("找不到 " + runtime + "，请从项目根目录启动 UI。");
        }
        Path dir = Files.createTempDirectory("sysy-ir-");
        try {
            Files.writeString(dir.resolve("program.ll"), ir);
            Path stdin = Files.writeString(dir.resolve("stdin.txt"), input);
            Path exe = dir.resolve("program.exe");
            String clang = System.getenv("CLANG");
            if (clang == null || clang.isBlank()) {
                clang = "clang";
            }
            Step build = execute(dir, List.of(clang, "-Wno-override-module", "-funsigned-char",
                    "program.ll", runtime.toString(), "-o", exe.toString()), stdin, 30);
            String log = build.out + build.err;
            if (build.stopped != null || build.exit != 0) {
                return new Result(build.stopped == null ? "编译或链接失败" : "编译" + build.stopped,
                        "", log, false);
            }
            Step run = execute(dir, List.of(exe.toString()), stdin, 5);
            String status = run.stopped == null ? "程序退出码 " + run.exit : "运行" + run.stopped;
            return new Result(status, run.out, log + run.err, run.stopped == null && run.exit == 0);
        } finally {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Step execute(Path dir, List<String> command, Path stdin, int seconds)
            throws IOException, InterruptedException {
        Path out = dir.resolve("stdout.txt");
        Path err = dir.resolve("stderr.txt");
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile())
                .redirectInput(stdin.toFile()).redirectOutput(out.toFile()).redirectError(err.toFile());
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            throw new IOException("无法启动 " + command.get(0) + "。" +
                    (command.size() > 1 ? "请安装 Clang 和本机链接工具，或用 CLANG 指定 clang 路径。" : "")
                    + "\n" + ex.getMessage(), ex);
        }
        String stopped = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        try {
            while (!process.waitFor(50, TimeUnit.MILLISECONDS)) {
                if (Files.size(out) > OUTPUT_LIMIT || Files.size(err) > OUTPUT_LIMIT) {
                    stopped = "输出超过 256 KiB";
                    break;
                }
                if (System.nanoTime() >= deadline) {
                    stopped = "超时（" + seconds + " 秒）";
                    break;
                }
            }
        } finally {
            if (process.isAlive()) {
                try {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                } catch (RuntimeException ignored) {
                    // 部分系统限制枚举子进程，仍需结束直接启动的进程。
                }
                process.destroyForcibly().waitFor();
            }
        }
        if (stopped == null && (Files.size(out) > OUTPUT_LIMIT || Files.size(err) > OUTPUT_LIMIT)) {
            stopped = "输出超过 256 KiB";
        }
        return new Step(stopped, process.exitValue(), readOutput(out), readOutput(err));
    }

    private static String readOutput(Path file) throws IOException {
        try (var stream = Files.newInputStream(file)) {
            return new String(stream.readNBytes(OUTPUT_LIMIT), StandardCharsets.UTF_8);
        }
    }
}
