public enum TokenType {
    // 标识符与常量
    IDENFR,      // Ident
    INTCON,      // IntConst
    CHARCON,     // CharConst
    STRCON,      // StringConst

    // 关键字
    CONTINUETK,  // continue
    DEFAULTTK,   // default
    PRINTFTK,    // printf
    RETURNTK,    // return
    STATICTK,    // static
    BREAKTK,     // break
    CONSTTK,     // const
    WHILETK,     // while
    CASETK,      // case
    CHARTK,      // char
    ELSETK,      // else
    MAINTK,      // main
    VOIDTK,      // void
    INTTK,       // int
    IFTK,        // if
    SWITCHTK,    // switch

    // 运算符与界符
    GEQ,         // >=
    OR,          // ||
    NOT,         // !
    MOD,         // %
    LPARENT,     // (
    RPARENT,     // )
    MULT,        // *
    PLUS,        // +
    COMMA,       // ,
    DIV,         // /
    COLON,       // :
    SEMICN,      // ;
    LSS,         // <
    ASSIGN,      // =
    GRE,         // >
    LBRACK,      // [
    RBRACK,      // ]
    LBRACE,      // {
    RBRACE,      // }
    MINU,        // -
    AND,         // &&
    NEQ,         // !=
    LEQ,         // <=
    EQL          // ==
}