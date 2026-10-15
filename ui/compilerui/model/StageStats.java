package compilerui.model;

/**
 * 一次编译里某个阶段的度量数据。
 *
 * @param stage       阶段
 * @param millis      该阶段耗时（毫秒）
 * @param outputLines 产物行数（有错误时是错误信息的行数）
 * @param metric      阶段特有的规模指标（token 数 / AST 节点数 / 符号数）
 * @param metricName  {@link #metric} 的含义
 */
public record StageStats(Stage stage,
                         long millis,
                         long outputLines,
                         long metric,
                         String metricName) {

    public String metricText() {
        return metricName == null ? "—" : metric + " " + metricName;
    }
}
