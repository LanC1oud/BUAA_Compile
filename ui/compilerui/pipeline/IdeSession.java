package compilerui.pipeline;

import compilerui.model.Stage;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;

/** 一次实验 IDE 运行。前端编译单独启动 JVM，隔离 Pipeline 的静态状态。 */
public final class IdeSession {
    private volatile boolean stopped;
    private Process active;
    private volatile Process program;

    /** 子 JVM 入口。将编译诊断写进终端，将成功生成的 IR 交给链接器。 */
    public static void main(String[] args) throws IOException {
        Pipeline pipeline = new Pipeline();
        pipeline.compile(args[0], Stage.LLVM);
        for (var error : pipeline.displayErrors()) {
            System.err.println(error.detail());
        }
        if (pipeline.crashMessage() != null) {
            System.err.println(pipeline.crashMessage());
        }
        var result = pipeline.resultOf(Stage.LLVM);
        if (result == null || !result.noError()) {
            System.exit(1);
        }
        Files.writeString(Path.of(args[1]), result.payload());
    }

    public record Result(String status, boolean programExited) {}

    public Result run(String source, Consumer<String> output, Runnable ready)
            throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("sysy-ide-");
        try {
            Path file = Files.writeString(dir.resolve("main.sy"), source);
            Path ir = dir.resolve("main.ll");
            String launcher = Path.of(System.getProperty("java.home"), "bin",
                    java.io.File.separatorChar == '\\' ? "java.exe" : "java").toString();
            // 保留启动目录，使 IDE / 脚本传入的相对 classpath 都能解析。
            var frontend = new ProcessBuilder(launcher, "-Dfile.encoding=UTF-8", "-cp",
                    System.getProperty("java.class.path"), IdeSession.class.getName(),
                    file.toString(), ir.toString());
            if (execute(frontend, output, null) != 0) return new Result("SysY 编译失败", false);

            // 管道默认会缓存 stdout。仅为交互版本关闭缓冲，让无换行提示也即时可见。
            Path console = Files.writeString(dir.resolve("console.c"), """
                    #include <stdio.h>
                    __attribute__((constructor)) static void init_console(void) {
                        setvbuf(stdout, NULL, _IONBF, 0);
                    }
                    """);
            Path exe = dir.resolve("main.exe");
            String clang = System.getenv("CLANG");
            if (clang == null || clang.isBlank()) clang = "clang";
            var link = new ProcessBuilder(clang, "-Wno-override-module", "-funsigned-char",
                    ir.toString(), Path.of("runtime/libsysy.c").toAbsolutePath().toString(),
                    console.toString(), "-o", exe.toString()).directory(dir.toFile());
            if (execute(link, output, null) != 0) return new Result("Clang 编译或链接失败", false);

            int exit = execute(new ProcessBuilder(exe.toString()).directory(dir.toFile()), output, ready);
            return new Result("程序退出码 " + exit, true);
        } finally {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private int execute(ProcessBuilder command, Consumer<String> output, Runnable ready)
            throws IOException, InterruptedException {
        Process process;
        synchronized (this) {
            if (stopped) throw new InterruptedException();
            process = command.redirectErrorStream(true).start();
            active = process;
            if (ready != null) program = process;
        }
        try (var reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            if (ready != null) ready.run();
            char[] buffer = new char[4096];
            int total = 0;
            int count;
            while ((count = reader.read(buffer)) != -1) {
                total += count;
                if (total > 256 * 1024) throw new IOException("终端输出达到 256 Ki 字符上限，运行已结束");
                output.accept(new String(buffer, 0, count));
            }
            return process.waitFor();
        } finally {
            if (process.isAlive()) process.destroyForcibly().waitFor();
            synchronized (this) {
                program = null;
                active = null;
            }
        }
    }

    public void sendLine(String text) throws IOException {
        Process target = program;
        if (target == null || !target.isAlive()) throw new IOException("程序已结束");
        var stdin = target.getOutputStream();
        stdin.write((text + "\n").getBytes(StandardCharsets.UTF_8));
        stdin.flush();
    }

    public synchronized void stop() {
        stopped = true;
        if (active != null) {
            try {
                active.descendants().forEach(ProcessHandle::destroyForcibly);
            } catch (RuntimeException ignored) {
                // 部分平台限制枚举进程。
            }
            active.destroyForcibly();
        }
    }
}
