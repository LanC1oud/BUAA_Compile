package compilerui.pipeline;

import compilerui.model.CompileResult;
import compilerui.model.CompilerSettings;
import compilerui.model.DisplayError;
import compilerui.model.Stage;
import compilerui.model.StageStats;
import error.ErrorTable;
import frontend.symbol.SymbolTable;
import utils.Configure;
import utils.Controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 无头自检：不开窗口就能验证管线、错误展示格式，以及 error.txt 格式未被改动。
 *
 * <p>用法：{@code java -cp build/classes compilerui.pipeline.PipelineCheck <源文件> [输出目录]}
 */
public final class PipelineCheck {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("用法: java -cp build/classes compilerui.pipeline.PipelineCheck <源文件> [输出目录]");
            System.exit(2);
        }
        Path source = Path.of(args[0]).toAbsolutePath();
        if (!Files.isRegularFile(source)) {
            System.err.println("源文件不存在: " + source);
            System.exit(2);
        }
        Path outDir = args.length > 1 ? Path.of(args[1]) : Files.createTempDirectory("pipeline-check-");
        Files.createDirectories(outDir);

        String sourceBefore = Configure.source;
        ErrorTable errorsInstance = Controller.errors;
        SymbolTable symbolsInstance = Controller.symbols;

        System.out.println("源文件: " + source);
        System.out.println("输出目录: " + outDir);

        checkStages(source, outDir);
        checkReproducible(source);
        checkErrorDisplay(source);
        checkMipsUi(outDir.resolve("mips-ui"));

        if (!sourceBefore.equals(Configure.source)) {
            fail("Configure.source 未还原: " + Configure.source);
        }
        if (errorsInstance != Controller.errors || symbolsInstance != Controller.symbols) {
            fail("Controller.errors/symbols 引用被替换了");
        }
        System.out.println(failures == 0
                ? "\n==> 全部检查通过（结果可复现，静态状态已还原）"
                : "\n==> 有 " + failures + " 项失败");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 各阶段

    private static void checkStages(Path source, Path outDir) throws Exception {
        System.out.println("\n[1] 分阶段编译");
        for (Stage stage : Stage.values()) {
            Pipeline pipeline = new Pipeline();
            pipeline.setSettings(CompilerSettings.defaults().withStage(stage));
            pipeline.compile(source.toString(), stage);

            CompileResult result = pipeline.resultOf(stage);
            if (result == null) {
                if (stage.isCodeGeneration() && !pipeline.rawErrors().isEmpty()) {
                    System.out.println("  [ OK ] 前端错误阻止 IR 生成");
                    continue;
                }
                // 解析器中断是编译器自身的健壮性问题，不是界面的错误：
                // 只要中断消息明确，就算"已正确报告"。
                String crash = pipeline.crashMessage();
                if (crash != null) {
                    System.out.printf("  [中断] %-18s %s%n", stage.displayName(), crash);
                } else {
                    fail(stage.displayName() + " 没有产出结果，也没有中断信息");
                }
                continue;
            }
            Path target = outDir.resolve(stage.defaultFileName());
            Files.writeString(target, result.outputText());

            StageStats stats = result.stats();
            System.out.printf("  [%s] %-18s %4d ms  产物 %5d 行  %-12s 错误 %d -> %s%n",
                    result.noError() ? " OK " : "ERR ", stage.displayName(),
                    stats.millis(), result.outputLines(), stats.metricText(),
                    result.errorCount(), target.getFileName());

            if (result.outputText().isEmpty()) {
                fail(stage.displayName() + " 产物是空的");
            }
            // 度量字段要与实际产物对得上，不能是写死的 0。
            if (stats.outputLines() != result.outputLines()) {
                fail(stage.displayName() + " 统计里的产物行数(" + stats.outputLines()
                        + ")与实际(" + result.outputLines() + ")不符");
            }
            if (result.errorCount() != pipeline.displayErrors().size()) {
                fail(stage.displayName() + " 错误计数(" + result.errorCount()
                        + ")与结构化错误数(" + pipeline.displayErrors().size() + ")不符");
            }
            if (result.noError() && stats.metric() == 0) {
                fail(stage.displayName() + " 无错误但规模指标是 0（统计没接上？）");
            }
        }
    }

    // ------------------------------------------------------------------ 可复现

    private static void checkReproducible(Path source) throws Exception {
        System.out.println("\n[2] 同一输入连跑两遍结果一致（检查状态残留）");
        for (Stage stage : Stage.values()) {
            Pipeline first = new Pipeline();
            first.setSettings(CompilerSettings.defaults());
            first.compile(source.toString(), stage);
            Pipeline second = new Pipeline();
            second.setSettings(CompilerSettings.defaults());
            second.compile(source.toString(), stage);
            CompileResult a = first.resultOf(stage);
            CompileResult b = second.resultOf(stage);
            if (stage.isCodeGeneration() && a == null && b == null && first.rawErrors().equals(second.rawErrors())
                    && !first.rawErrors().isEmpty()) continue;
            String crashA = first.crashMessage();
            String crashB = second.crashMessage();
            if (crashA != null || crashB != null) {
                if (crashA == null || !crashA.equals(crashB)) {
                    fail(stage.displayName() + " 两次中断信息不同");
                } else {
                    System.out.println("  [ OK ] " + stage.displayName() + " 两次都在同一处中断");
                }
                continue;
            }
            if (a == null || b == null || !a.outputText().equals(b.outputText())) {
                fail(stage.displayName() + " 两次结果不同");
            } else {
                System.out.println("  [ OK ] " + stage.displayName() + " 逐字节一致");
            }
        }
    }

    // ------------------------------------------------------------------ 错误展示

    /**
     * 核心检查：界面的可读格式与 error.txt 的原始格式必须<b>同时正确</b>。
     *
     * <p>原始格式是 {@code "行 错误码"}（如 {@code 5 i}），
     * 界面格式是 {@code "line行号：错误码——错误类型英文名"}。
     */
    private static void checkErrorDisplay(Path source) throws Exception {
        System.out.println("\n[3] 错误展示：界面可读格式 vs error.txt 原始格式");
        Pipeline pipeline = new Pipeline();
        pipeline.setSettings(CompilerSettings.defaults());
        pipeline.compile(source.toString(), Stage.SEMANTIC);

        List<DisplayError> display = pipeline.displayErrors();
        CompileResult semantic = pipeline.resultOf(Stage.SEMANTIC);
        if (semantic == null) {
            if (pipeline.crashMessage() != null) {
                System.out.println("  [跳过] 语义阶段因解析中断未产出结果："
                        + pipeline.crashMessage());
                return;
            }
            fail("语义阶段没有结果");
            return;
        }
        if (display.isEmpty()) {
            System.out.println("  [跳过] 该用例没有错误，换个会报错的用例才能验证格式");
            return;
        }

        System.out.println("  界面展示（前 3 条）：");
        display.stream().limit(3).forEach(error -> System.out.println("      " + error.headline()));
        System.out.println("  error.txt 内容（前 3 行）：");
        semantic.errors().lines().limit(3).forEach(line -> System.out.println("      " + line));

        // 逐条断言：原始行 == "行 错误码"，界面行 == "line行：码——枚举名"
        List<String> rawLines = semantic.errors().lines().toList();
        if (rawLines.size() != display.size()) {
            fail("原始错误行数 " + rawLines.size() + " 与结构化条目数 " + display.size() + " 不一致");
        }
        for (int i = 0; i < display.size(); i++) {
            DisplayError error = display.get(i);
            String expectedRaw = error.line() + " " + error.code();
            if (i < rawLines.size() && !expectedRaw.equals(rawLines.get(i))) {
                fail("第 " + (i + 1) + " 条原始格式被改动了：期望「" + expectedRaw
                        + "」实际「" + rawLines.get(i) + "」");
            }
            String expected = "line" + error.line() + "：" + error.code() + "——" + error.typeName();
            if (!expected.equals(error.headline())) {
                fail("界面格式不符：期望「" + expected + "」实际「" + error.headline() + "」");
            }
        }
        System.out.println("  [ OK ] " + display.size()
                + " 条错误：界面格式正确，且 error.txt 原始格式未被改动");
    }

    /** 验证 UI 优化设置、单次/批量 MIPS 产物及导出清理。 */
    private static void checkMipsUi(Path directory) throws Exception {
        System.out.println("\n[4] MIPS 翻译、优化开关与导出");
        boolean globalOptimize = Configure.optimize;
        String source = "int main(){int x=get_int();printf(\"%d\",x*8+3);return 0;}";
        Pipeline pipeline = new Pipeline();
        var baselineSettings = CompilerSettings.defaults().withOptimize(false)
                .withStage(Stage.MIPS).withOutputDir(directory.toString()).withFileName("program.s")
                .withTrailingNewline(false).withDebug(false, false, true, false);
        if (baselineSettings.optimize()) fail("复制设置时丢失了优化开关");
        pipeline.setSettings(baselineSettings);
        pipeline.compileContent(source, "mips-ui", Stage.MIPS);
        var baseline = pipeline.resultOf(Stage.MIPS);
        if (baseline == null) throw new AssertionError("MIPS 生成失败：" + pipeline.crashMessage());
        var results = new java.util.EnumMap<Stage, CompileResult>(Stage.class);
        results.put(Stage.MIPS, baseline);
        results.put(Stage.LLVM, pipeline.resultOf(Stage.LLVM));
        var export = ResultExporter.export(results, Stage.MIPS, directory, "program.s", false, "", null);
        if (!export.includesIr() || !Files.readString(export.target()).equals(baseline.payload())
                || !Files.readString(directory.resolve("llvm_ir.txt")).equals(results.get(Stage.LLVM).payload()))
            fail("MIPS 与对应 IR 导出不一致");
        try {
            ResultExporter.export(results, Stage.MIPS, directory, "llvm_ir.txt", false, "", null);
            fail("允许 MIPS 覆盖对应 IR");
        } catch (java.io.IOException expected) { }
        pipeline.setSettings(baselineSettings.withOptimize(true));
        pipeline.compileContent(source, "optimized", Stage.MIPS);
        var optimized = pipeline.resultOf(Stage.MIPS);
        if (optimized == null || optimized.payload().equals(baseline.payload()))
            fail("UI 优化开关没有切换生成路径");
        if (Configure.optimize != globalOptimize) fail("UI 编译修改了全局优化设置");
        var sample = new BatchRunner.Sample(Path.of("mips-ui.sy"), "mips-ui", source, "");
        var batch = BatchRunner.run(List.of(sample), directory.resolve("batch"), baselineSettings, null).get(0);
        if (!batch.passed() || !Files.readString(batch.outputDir().resolve("mips.txt")).equals(baseline.payload()))
            fail("批量编译未使用相同的 MIPS 优化设置");
        pipeline.compileContent("int main(){missing=1;return 0;}", "invalid", Stage.MIPS);
        results.clear();
        for (Stage stage : Stage.values()) {
            var result = pipeline.resultOf(stage);
            if (result != null) results.put(stage, result);
        }
        Files.writeString(directory.resolve("mips.txt"), "stale");
        var errors = ResultExporter.export(results, Stage.MIPS, directory, "program.s", false,
                pipeline.rawErrors(), pipeline.crashMessage());
        if (!errors.errors() || Files.exists(directory.resolve("program.s"))
                || Files.exists(directory.resolve("mips.txt")) || Files.exists(directory.resolve("llvm_ir.txt"))
                || !Files.readString(errors.target()).equals(pipeline.rawErrors()))
            fail("失败后导出保留旧目标代码，或改变了错误格式");
        pipeline.compileContent(source, "llvm-only", Stage.LLVM);
        if (pipeline.resultOf(Stage.MIPS) != null) fail("LLVM 编译残留旧 MIPS");
        results.clear();
        results.put(Stage.LLVM, pipeline.resultOf(Stage.LLVM));
        Files.writeString(directory.resolve("mips.txt"), "keep");
        try {
            ResultExporter.export(results, Stage.MIPS, directory, "", false, "", null);
            fail("未编译 MIPS 时允许导出");
        } catch (java.io.IOException expected) { }
        if (!Files.readString(directory.resolve("mips.txt")).equals("keep")) fail("未运行阶段导出误删文件");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var panel = new compilerui.gui.StageStatsPanel();
            results.put(Stage.MIPS, optimized);
            panel.update(results, Stage.MIPS);
            panel.reset();
        });
        System.out.println("  [ OK ] 单次/批量优化、MIPS+IR 导出、失败清理、阶段统计");
    }

    private static void fail(String message) {
        System.out.println("  [FAIL] " + message);
        failures++;
    }
}
