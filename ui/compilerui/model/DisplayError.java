package compilerui.model;

import error.ErrorEntry;
import error.ErrorType;

/**
 * 一条错误的可读展示形式。
 *
 * <p><b>这只是界面显示用的派生数据，不影响 error.txt。</b>
 * {@code error.txt} 的内容由 {@link error.ErrorTable#toString()} 决定，
 * 那个方法一个字符都没动；这里是在其之上重新拼一份更好读的文本。
 *
 * @param line  行号
 * @param entry 原始错误条目（含 {@link ErrorType} 与列号）
 */
public record DisplayError(int line, ErrorEntry entry) {

    /** 错误码，如 {@code i}。 */
    public String code() {
        return entry.getType().toString();
    }

    /** 错误类型英文名，即枚举常量名，如 {@code ExpectedSemicolon}。 */
    public String typeName() {
        return entry.getType().name();
    }

    /**
     * 要求的主格式：{@code line行号：错误码——错误类型英文名}。
     * 例如 {@code line5：i——ExpectedSemicolon}。
     */
    public String headline() {
        return String.format("line%d：%s——%s", line, code(), typeName());
    }

    /** 附在后面的中文说明，纯粹为了好读。 */
    public String chineseDescription() {
        return switch (entry.getType()) {
            case InvalidToken -> "非法 token";
            case DuplicatedDeclaration -> "重复声明";
            case UndefinedReference -> "引用未定义";
            case MismatchedParameterCount -> "形实参个数不匹配";
            case MismatchedParameterType -> "形实参类型不匹配";
            case MismatchedReturnType -> "返回类型不匹配";
            case MissingReturnStatement -> "缺少 return 语句";
            case AssignToConstant -> "给常量赋值";
            case ExpectedSemicolon -> "缺少分号";
            case ExpectedRParen -> "缺少右括号";
            case ExpectedRBracket -> "缺少右方括号";
            case MismatchedFormatArgument -> "printf 格式串与实参不匹配";
            case InvalidLoopControl -> "break/continue 不在循环内";
            case DuplicatedCaseLabel -> "case 标签重复";
        };
    }

    /**
     * 一行完整展示，例如：
     * {@code line5：i——ExpectedSemicolon（缺少分号，第 12 列）}
     */
    public String detail() {
        return String.format("%s（%s，第 %d 列）", headline(), chineseDescription(), entry.getColumn());
    }
}
