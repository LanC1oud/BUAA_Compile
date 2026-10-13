package utils;

import frontend.ast.ASTNode;

public final class ConstFolder {

    private ConstFolder() {
    }

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