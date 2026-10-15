package compilerui.pipeline;

import compilerui.model.CompileResult;
import compilerui.model.CompilerSettings;
import compilerui.model.Stage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 批量编译：把一个文件夹里的所有 {@code .txt} 样例逐个编译，各自输出到独立子目录。
 *
 * <p><b>这是前端外壳专用流程</b>，与命令行 {@code Compiler.main()} 完全无关：
 * 后者照旧只读项目根的 {@code testfile.txt}、把产物写在项目根。
 *
 * <h2>输入目录约定</h2>
 * 样例既可以放在所选文件夹<b>本身</b>，也可以放在它的<b>下一级子目录</b>里
 * （即 {@code <所选文件夹>/<样例子目录>/xxx.txt}）。两种都会扫到；
 * 再深的层级默认不递归，避免误吞无关文件。
 *
 * <h2>输出目录结构</h2>
 * <pre>
 * &lt;输出文件夹&gt;/
 * ├── result of "a"/
 * │   ├── lexer.txt
 * │   ├── parser.txt
 * │   ├── symbol.txt
 * │   └── error.txt      （仅当该样例有错误 / 解析中断时才生成）
 * └── result of "b"/
 *     └── ...
 * </pre>
 */
public final class BatchRunner {

    /** 扫描时跳过的目录名。 */
    private static final Set<String> SKIPPED_DIRS = Set.of(
            ".git", ".idea", "build", "out", "node_modules", "target", ".gradle");

    /** 每个样例的输出子目录前缀，对应需求里的 {@code result of "名字"}。 */
    public static final String RESULT_DIR_PREFIX = "result of ";

    /**
     * 一个待编译的样例。
     *
     * @param path   源文件路径
     * @param name   样例名（不含 {@code .txt} 后缀），用于输出目录命名
     * @param source 文件内容
     * @param group  所在子目录名；直接在所选文件夹里时为空串
     */
    public record Sample(Path path, String name, String source, String group) {

        /** 列表里展示用的名字。 */
        public String display() {
            return group.isEmpty() ? name : group + "/" + name;
        }
    }

    /**
     * 一个样例的编译结果。
     *
     * @param sample    样例
     * @param results   各阶段结果（按阶段顺序）
     * @param outputDir 写出的目录；失败时为 null
     * @param failure   整个流程抛异常时的消息，正常为 null
     */
    public record SampleOutcome(Sample sample,
                                List<CompileResult> results,
                                Path outputDir,
                                String failure) {

        public SampleOutcome {
            results = List.copyOf(results);
        }

        /** 最后跑到的阶段。 */
        public Stage lastStage() {
            return results.isEmpty() ? null : results.get(results.size() - 1).stage();
        }

        /** 该样例是否零错误通过（解析中断也算未通过）。 */
        public boolean passed() {
            return failure == null && !results.isEmpty() && results.get(results.size() - 1).noError();
        }

        /** 是否需要在结果目录里写 {@code error.txt}。 */
        public boolean hasErrorFile() {
            return failure != null || !passed();
        }

        public int finalErrorCount() {
            if (failure != null || results.isEmpty()) {
                return -1;
            }
            return results.get(results.size() - 1).errorCount();
        }
    }

    /** 进度回调，供界面实时刷新。 */
    public interface ProgressListener {
        void onSampleFinished(int index, int total, SampleOutcome outcome);
    }

    private BatchRunner() { }

    // ==================================================================
    // 扫描
    // ==================================================================

    /**
     * 扫描样例文件夹里的所有 {@code .txt}。
     *
     * <p>深度 2 的含义：所选文件夹本身 + 它的下一级子目录，正好覆盖
     * "样例直接放在该文件夹里"与"样例放在下一级子目录里"两种布局。
     *
     * @param root 样例文件夹
     */
    public static List<Sample> scanSamples(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        Path base = root.toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        // Files.walk 的深度按"层数"计：2 层 = 本级文件 + 下一级目录里的文件。
        try (Stream<Path> stream = Files.walk(base, 2)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !isInsideSkippedDir(base, path))
                    .filter(BatchRunner::isTxt)
                    .forEach(files::add);
        }
        files.sort(Comparator.comparing(Path::toString));

        List<Sample> samples = new ArrayList<>(files.size());
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String name = fileName.substring(0, fileName.length() - ".txt".length());
            String group = base.relativize(file.getParent()).toString();
            String source = Files.readString(file, StandardCharsets.UTF_8);
            samples.add(new Sample(file, name, source, group));
        }
        return samples;
    }

    private static boolean isTxt(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".txt");
    }

    private static boolean isInsideSkippedDir(Path base, Path path) {
        for (Path part : base.relativize(path.toAbsolutePath().normalize())) {
            if (SKIPPED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    // ==================================================================
    // 编译 + 落盘
    // ==================================================================

    /**
     * 逐个编译样例并写出结果。
     *
     * <p>刻意<b>串行</b>：{@link Pipeline} 复用 {@code Controller} 的全局静态状态，
     * 并发会互相污染。
     *
     * @param samples   待编译样例
     * @param outputDir 输出文件夹
     * @param settings  编译设置（阶段固定跑到语义）
     * @param listener  进度回调，可为 null
     */
    public static List<SampleOutcome> run(List<Sample> samples,
                                          Path outputDir,
                                          CompilerSettings settings,
                                          ProgressListener listener) {
        List<SampleOutcome> outcomes = new ArrayList<>(samples.size());
        int index = 0;
        for (Sample sample : samples) {
            index++;
            SampleOutcome outcome = compileOne(sample, outputDir, settings);
            outcomes.add(outcome);
            if (listener != null) {
                listener.onSampleFinished(index, samples.size(), outcome);
            }
        }
        return outcomes;
    }

    private static SampleOutcome compileOne(Sample sample, Path outputDir, CompilerSettings settings) {
        Pipeline pipeline = new Pipeline();
        pipeline.setSettings(settings);
        try {
            // 三个阶段一次跑完，产物后面按阶段分别落盘。
            pipeline.compileContent(sample.source(), sample.path().toString(), Stage.SEMANTIC);
        } catch (IOException e) {
            return new SampleOutcome(sample, List.of(), null,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        List<CompileResult> results = new ArrayList<>();
        for (Stage stage : Stage.values()) {
            CompileResult result = pipeline.resultOf(stage);
            if (result != null) {
                results.add(result);
            }
        }
        String crash = pipeline.crashMessage();
        if (results.isEmpty() && crash == null) {
            return new SampleOutcome(sample, List.of(), null, "没有任何阶段产出结果");
        }

        Path dir = outputDir.resolve(resultDirName(sample));
        try {
            Files.createDirectories(dir);
            for (CompileResult result : results) {
                writeProduct(dir, result);
            }
            // 用错误表的原始文本（与命令行 error.txt 同格式），
            // 而不是从阶段结果里推——解析中断时阶段结果可能压根没提交。
            String errorText = errorFileText(pipeline.rawErrors(), results, crash);
            if (errorText != null) {
                Files.writeString(dir.resolve("error.txt"), errorText, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return new SampleOutcome(sample, results, null, "写出失败: " + e.getMessage());
        }
        return new SampleOutcome(sample, results, dir, crash);
    }

    /** 单个阶段的产物文件名，与命令行保持一致。 */
    private static void writeProduct(Path dir, CompileResult result) throws IOException {
        Files.writeString(dir.resolve(result.stage().defaultFileName()),
                result.outputText(), StandardCharsets.UTF_8);
    }

    /**
     * 组装 {@code error.txt} 的内容。
     *
     * <p>写的是各阶段已记录到的错误（取最后一阶段的错误表，它包含前面累积的错误），
     * 格式与命令行 {@code error.txt} 完全一致（{@code 行号 错误码}）。
     * 全部通过时返回 {@code null}，表示不生成这个文件。
     */
    private static String errorFileText(String rawErrors, List<CompileResult> results, String crash) {
        boolean clean = (rawErrors == null || rawErrors.isEmpty()) && crash == null;
        if (clean) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        if (rawErrors != null && !rawErrors.isEmpty()) {
            text.append(rawErrors);
        }
        if (crash != null) {
            if (text.length() > 0) {
                text.append('\n');
            }
            // 解析中断不是编译错误，单独标注，便于与真实错误码区分。
            text.append("# 解析中断: ").append(crash);
        }
        // 极端情况：既没错误文本也没中断消息，但确实没通过，留一行说明免得是空文件。
        if (text.length() == 0) {
            CompileResult last = results.isEmpty() ? null : results.get(results.size() - 1);
            if (last != null && !last.noError()) {
                text.append("# 该样例未通过，但错误表为空");
            } else if (last == null && !results.isEmpty()) {
                text.append("# 该样例未产出任何阶段结果");
            }
        }
        return text.length() == 0 ? null : text.toString();
    }

    /** 输出子目录名：{@code result of "样例名"}，并做文件名安全化。 */
    public static String resultDirName(Sample sample) {
        // 带上 group 消歧：不同子目录下可能有同名样例。
        String base = sample.group().isEmpty() ? sample.name() : sample.group() + "_" + sample.name();
        return RESULT_DIR_PREFIX + "\"" + sanitize(base) + "\"";
    }

    /** 把路径分隔符等换成下划线，避免生成非法目录名。 */
    private static String sanitize(String name) {
        return name.replace('/', '_').replace('\\', '_').replace(':', '_')
                .replace('*', '_').replace('?', '_').replace('"', '_')
                .replace('<', '_').replace('>', '_').replace('|', '_');
    }

    /** 默认样例文件夹：与 src 同级的 {@code testfile}。 */
    public static Path defaultSampleDir() {
        return Paths.get("").toAbsolutePath().normalize().resolve("testfile");
    }

    /** 默认输出文件夹：与 src 同级的 {@code output}。 */
    public static Path defaultOutputDir() {
        return Paths.get("").toAbsolutePath().normalize().resolve("output");
    }

    /** 统计通过数。 */
    public static long passedCount(List<SampleOutcome> outcomes) {
        return outcomes.stream().filter(SampleOutcome::passed).count();
    }
}
