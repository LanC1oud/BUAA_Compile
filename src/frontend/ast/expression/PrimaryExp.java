package frontend.ast.expression;

import frontend.ast.ASTNode;

/**
 * PrimaryExp -> '(' Exp ')' | LVal | Number
 */
public class PrimaryExp extends ASTNode {
    public enum Kind {
        Paren, LVal, Number
    }

    private final Kind kind;
    private final Exp exp;
    private final LVal lval;
    private final Number number;

    private PrimaryExp(Kind kind, Exp exp, LVal lval, Number number) {
        this.kind = kind;
        this.exp = exp;
        this.lval = lval;
        this.number = number;
    }

    public static PrimaryExp paren(Exp exp) {
        return new PrimaryExp(Kind.Paren, exp, null, null);
    }

    public static PrimaryExp lval(LVal lval) {
        return new PrimaryExp(Kind.LVal, null, lval, null);
    }

    public static PrimaryExp number(Number number) {
        return new PrimaryExp(Kind.Number, null, null, number);
    }

    public Kind getKind() {
        return kind;
    }

    public Exp getExp() {
        return exp;
    }

    public LVal getLVal() {
        return lval;
    }

    public Number getNumber() {
        return number;
    }

    /**
     * 按当前分支在编译期求值。
     *
     * @return 表达式的值
     * @throws UnsupportedOperationException 引用到的变量不是编译期常量
     */
    @Override
    public int calculateConst() {
        return switch (kind) {
            case Paren -> exp.calculateConst();
            case LVal -> lval.calculateConst();
            case Number -> number.calculateConst();
        };
    }

    @Override
    protected String getName() {
        return "<PrimaryExp>";
    }
}
