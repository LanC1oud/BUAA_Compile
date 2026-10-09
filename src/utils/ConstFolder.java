package utils;

import frontend.ast.ASTNode;

/**
 * 常量折叠的安全入口。
 *
 * <p>AST 上的 {@code calculateConst()} 链遇到"不是编译期常量"的情况会抛异常
 * （非常量变量、函数调用、除零、符号未解析……）。语义分析需要在很多地方试着折叠，
 * 但<b>绝大多数失败都不是错误</b>——比如局部变量的初值本来就允许是非常量表达式。
 *
 * <p>所以这里把异常统一转成 {@code null}，让调用方用"能不能折出来"来决定后续动作，
 * 而不是用异常控制流程。
 */
public final class ConstFolder {

    private ConstFolder() {
    }

    /**
     * 尝试在编译期求出表达式的值。
     *
     * @param node 表达式结点，可以为 {@code null}
     * @return 折叠结果；不是编译期常量时返回 {@code null}
     */
    public static Integer fold(ASTNode node) {
        if (node == null) {
            return null;
        }
        try {
            return node.calculateConst();
        } catch (RuntimeException e) {
            // 非常量、除零、符号未解析……一律视为"折不出来"
            return null;
        }
    }
}
