package compilerui.model;

import utils.Configure;

/**
 * 界面暴露的全部编译设置。
 *
 * <p>四个 {@code display*} 开关直接对应 {@code utils.Configure.debug} 的四个字段。
 * 界面把它们拆成细粒度开关，而不是原来那一个笼统的「调试输出」。
 *
 * @param stage                编译到哪一步
 * @param outputDir            导出目录（可为相对路径，相对项目根）
 * @param fileName             导出文件名
 * @param trailingNewline      导出时末尾是否补换行（命令行 Controller 不补）
 * @param displayTokens        把 token 流打到日志
 * @param displaySymbols       把符号表打到日志
 * @param displayTokensWithAst 把 token + AST 一起打到日志（ParserWatcher 的输出开关）
 * @param displayErrors        把错误表打到日志
 */
public record CompilerSettings(Stage stage,
                               String outputDir,
                               String fileName,
                               boolean trailingNewline,
                               boolean displayTokens,
                               boolean displaySymbols,
                               boolean displayTokensWithAst,
                               boolean displayErrors) {

    public static CompilerSettings defaults() {
        // displayTokensWithAst 默认打开：语法阶段的正式产物就是「token + AST」文本
        // （ParserWatcher 靠这个开关决定记不记录）。关掉的话 parser.txt 会是空的，
        // 而且「近似 AST」页也没东西可看。
        return new CompilerSettings(
                Stage.SEMANTIC,
                ".",
                Stage.SEMANTIC.defaultFileName(),
                false,
                false, false, true, true);
    }

    /**
     * 把调试开关写进 {@code utils.Configure.debug}。
     *
     * <p>由 {@code Pipeline} 在每次编译开始时调用——静态字段是全局的，
     * 与其让调用方自己记得设，不如把这一步收进接口。
     */
    public void applyDebugFlags() {
        Configure.debug.displayTokens = displayTokens;
        Configure.debug.displaySymbols = displaySymbols;
        Configure.debug.displayTokensWithAst = displayTokensWithAst;
        Configure.debug.displayErrors = displayErrors;
    }

    /** 换阶段时同步默认文件名。 */
    public CompilerSettings withStage(Stage newStage) {
        return new CompilerSettings(newStage, outputDir, newStage.defaultFileName(),
                trailingNewline, displayTokens, displaySymbols, displayTokensWithAst, displayErrors);
    }

    public CompilerSettings withFileName(String newFileName) {
        return new CompilerSettings(stage, outputDir, newFileName, trailingNewline,
                displayTokens, displaySymbols, displayTokensWithAst, displayErrors);
    }

    public CompilerSettings withOutputDir(String newOutputDir) {
        return new CompilerSettings(stage, newOutputDir, fileName, trailingNewline,
                displayTokens, displaySymbols, displayTokensWithAst, displayErrors);
    }

    public CompilerSettings withTrailingNewline(boolean value) {
        return new CompilerSettings(stage, outputDir, fileName, value,
                displayTokens, displaySymbols, displayTokensWithAst, displayErrors);
    }

    public CompilerSettings withDebug(boolean tokens, boolean symbols, boolean tokensWithAst, boolean errors) {
        return new CompilerSettings(stage, outputDir, fileName, trailingNewline,
                tokens, symbols, tokensWithAst, errors);
    }
}
