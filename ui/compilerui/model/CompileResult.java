package compilerui.model;

/**
 * 编译到某个阶段后的结果快照。
 *
 * @param stage      这是哪一个阶段的结果
 * @param noError    该阶段结束时是否没有错误
 * @param payload    该阶段的正文产物（token 流 / AST 文本 / 符号表），无错误时非 null
 * @param errors     错误表文本，<b>与 error.txt 的内容逐字相同</b>，有错误时非 null
 * @param errorCount 该阶段结束时的累计错误数
 * @param log        该阶段的调试/日志文本
 * @param stats      该阶段的度量数据
 */
public record CompileResult(Stage stage,
                            boolean noError,
                            String payload,
                            String errors,
                            int errorCount,
                            String log,
                            StageStats stats) {

    /** 导出时应写入的文本：有错误写错误，否则写正文。 */
    public String outputText() {
        return noError ? (payload == null ? "" : payload) : (errors == null ? "" : errors);
    }

    public long outputLines() {
        return outputText().lines().count();
    }
}
