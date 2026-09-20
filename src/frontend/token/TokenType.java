package frontend.token;

public enum TokenType {
    Ident("IDENFR"),
    IntConst("INTCON"),
    CharConst("CHARCON"),
    StringConst("STRCON"),
    Continue("CONTINUETK"),
    Default("DEFAULTTK"),
    Printf("PRINTFTK"),
    Return("RETURNTK"),
    Static("STATICTK"),
    Break("BREAKTK"),
    Const("CONSTTK"),
    While("WHILETK"),
    Case("CASETK"),
    Char("CHARTK"),
    Else("ELSETK"),
    Main("MAINTK"),
    Void("VOIDTK"),
    Int("INTTK"),
    If("IFTK"),
    Neq("NEQ"),
    Leq("LEQ"),
    Eq("EQL"),
    Geq("GEQ"),
    Or("OR"),
    Not("NOT"),
    Mod("MOD"),
    LParen("LPARENT"),
    RParen("RPARENT"),
    Mult("MULT"),
    Plus("PLUS"),
    Comma("COMMA"),
    Div("DIV"),
    Colon("COLON"),
    Semicolon("SEMICN"),
    Lt("LSS"),
    Assign("ASSIGN"),
    Gt("GRE"),
    LBracket("LBRACK"),
    RBracket("RBRACK"),
    LBrace("LBRACE"),
    RBrace("RBRACE"),
    Minus("MINU"),
    Switch("SWITCHTK"),
    And("AND"),
    EOF("EOF");
    
    private final String word;
    
    TokenType(String word) { this.word = word; }
    
    @Override
    public String toString() { return word; }
}