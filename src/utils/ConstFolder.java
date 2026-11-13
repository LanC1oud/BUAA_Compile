package utils;

import frontend.ast.ASTNode;

public final class ConstFolder {

    private ConstFolder() {
    }

    // 尝试计算编译期常量，失败时返回 null
    public static Integer fold(ASTNode node) {
        if (node == null) {
            return null;
        }
        try {
            return node.calculateConst();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
