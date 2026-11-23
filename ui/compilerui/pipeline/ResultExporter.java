package compilerui.pipeline;

import compilerui.model.CompileResult;
import compilerui.model.Stage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** 单次编译导出；MIPS 同时保留对应 IR，失败时清理旧目标代码。 */
public final class ResultExporter {
    private ResultExporter() { }

    public record Export(Path target, boolean errors, boolean includesIr) { }

    public static Export export(Map<Stage, CompileResult> results, Stage stage, Path directory,
                                String fileName, boolean trailingNewline,
                                String rawErrors, String crash) throws IOException {
        if (results.isEmpty()) throw new IOException("请先编译生成结果。");
        CompileResult chosen = results.get(stage);
        boolean failed = chosen == null || !chosen.noError();
        if (chosen == null && (!stage.isCodeGeneration()
                || (rawErrors == null || rawErrors.isEmpty()) && crash == null))
            throw new IOException("尚未运行到「" + stage.displayName() + "」阶段，请先编译。");
        Path target = directory.resolve(fileName == null || fileName.isBlank()
                ? stage.defaultFileName() : fileName.trim());
        boolean includeIr = stage == Stage.MIPS && !failed;
        Path irTarget = directory.resolve(Stage.LLVM.defaultFileName());
        if (includeIr && target.toAbsolutePath().normalize().equals(irTarget.toAbsolutePath().normalize()))
            throw new IOException("MIPS 文件名不能使用 llvm_ir.txt，请使用 mips.txt 或其他名称。");
        CompileResult ir = results.get(Stage.LLVM);
        if (includeIr && (ir == null || !ir.noError()))
            throw new IOException("缺少对应 LLVM IR，请重新编译到 MIPS 阶段。");
        Files.createDirectories(directory);
        if (stage.isCodeGeneration() && failed) {
            Files.deleteIfExists(directory.resolve(Stage.LLVM.defaultFileName()));
            Files.deleteIfExists(directory.resolve(Stage.MIPS.defaultFileName()));
            Files.deleteIfExists(target);
            target = directory.resolve("error.txt");
        }
        String text = failed ? rawErrors : chosen.outputText();
        if (failed && (text == null || text.isEmpty()))
            text = "# Code generation aborted: " + (crash == null ? "未生成所选阶段的结果" : crash);
        Files.writeString(target, text + (trailingNewline ? "\n" : ""), StandardCharsets.UTF_8);
        if (includeIr)
            Files.writeString(irTarget, ir.payload() + (trailingNewline ? "\n" : ""), StandardCharsets.UTF_8);
        if (!failed && !target.toAbsolutePath().normalize().equals(directory.resolve("error.txt").toAbsolutePath().normalize()))
            Files.deleteIfExists(directory.resolve("error.txt"));
        return new Export(target, failed, includeIr);
    }
}
