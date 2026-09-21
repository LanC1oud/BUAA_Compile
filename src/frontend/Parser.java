package frontend;

import error.ErrorTable;
import error.ErrorType;
import frontend.ast.CompileUnit;
import frontend.ast.block.*;
import frontend.ast.declaration.*;
import frontend.ast.expression.*;
import frontend.ast.expression.Number;
import frontend.ast.function.*;
import frontend.ast.statement.*;
import frontend.token.Token;
import frontend.token.TokenStream;
import frontend.token.TokenType;
import utils.BackTrace;
import utils.Controller;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class Parser {

    private final ErrorTable errors = Controller.errors;
    private final CompileUnit ast;

    private final TokenStream __tokens;
    private final ParserWatcher __watcher;

    /** Used for speculative parsing; rewinds tokens, emitted lines and errors together. */
    private final BackTrace trace;

    public Parser(TokenStream tokens, ParserWatcher watcher) {
        this.__tokens = tokens;
        this.__watcher = watcher;
        this.ast = new CompileUnit();
        this.trace = new BackTrace(errors, __tokens, __watcher);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Consume the next token, which must have the expected type. A missing ';', ')' or ']'
     * is recoverable: the matching error is recorded and the offending token is left in the
     * stream so that the caller can keep analysing the rest of the program.
     */
    private Token consume(TokenType type) {
        Token token = null;
        try {
            token = __tokens.consume(type);
            __watcher.add(token.toString());
        } catch (IllegalStateException e) {
            if (type.equals(TokenType.Semicolon)) {
                errors.add(ErrorType.ExpectedSemicolon,
                        Objects.requireNonNull(__tokens.peek(-1)).location());
            } else if (type.equals(TokenType.RParen)) {
                errors.add(ErrorType.ExpectedRParen,
                        Objects.requireNonNull(__tokens.peek(-1)).location());
            } else if (type.equals(TokenType.RBracket)) {
                errors.add(ErrorType.ExpectedRBracket,
                        Objects.requireNonNull(__tokens.peek(-1)).location());
            } else {
                throw e;
            }
        }
        return token;
    }

    private boolean checkConsume(TokenType... types) {
        Token token = __tokens.checkConsume(types);
        if (token != null) {
            __watcher.add(token.toString());
            return true;
        }
        return false;
    }

    private Token next() {
        Token token = __tokens.next();
        __watcher.add(String.valueOf(token));
        return token;
    }

    private Token consumeAny(TokenType... types) {
        for (TokenType type : types) {
            if (among(type)) {
                return consume(type);
            }
        }
        throw new IllegalStateException("Expecting one of " + Arrays.toString(types)
                + " but got " + __tokens.peek());
    }

    private<T> T watch(T inst) {
        __watcher.add(inst.toString());
        return inst;
    }

    private boolean among(TokenType... types) {
        return among(0, types);
    }

    private boolean among(int next, TokenType... types) {
        Token tk = __tokens.peek(next);
        if (tk == null) {
            return false;
        }
        return tk.among(types);
    }

    private boolean eof() {
        return __tokens.eof();
    }

    // ------------------------------------------------------------- compile unit

    public Parser parse() {
        // CompUnit -> {Decl} {FuncDef} MainFuncDef
        while (!eof()) {
            if (among(TokenType.Const, TokenType.Static)) {
                ast.addDeclaration(parseDecl());
            } else if (among(TokenType.Void)) {
                ast.addFunction(parseFuncDef());
            } else if (among(TokenType.Int, TokenType.Char)) {
                if (among(1, TokenType.Main)) {
                    ast.setMainFunc(parseMainFuncDef());
                } else if (among(1, TokenType.Ident) && among(2, TokenType.LParen)) {
                    ast.addFunction(parseFuncDef());
                } else {
                    ast.addDeclaration(parseDecl());
                }
            } else {
                // Unreachable for well-formed SysY sources; consuming keeps progress.
                next();
            }
        }
        watch(ast);
        return this;
    }

    public CompileUnit emit() {
        return ast;
    }

    // ------------------------------------------------------------- declarations

    /** Decl -> ConstDecl | VarDecl ; note that {@code <Decl>} is not emitted. */
    private Decl parseDecl() {
        if (among(TokenType.Const)) {
            return parseConstDecl();
        }
        return parseVarDecl();
    }

    /** BType -> 'int' | 'char' ; note that {@code <BType>} is not emitted. */
    private Token parseBType() {
        return consumeAny(TokenType.Int, TokenType.Char);
    }

    /** ConstDecl -> 'const' BType ConstDef { ',' ConstDef } ';' */
    private ConstDecl parseConstDecl() {
        Token constToken = consume(TokenType.Const);
        Token btype = parseBType();
        List<ConstDef> defs = new LinkedList<>();
        defs.add(parseConstDef());
        while (among(TokenType.Comma)) {
            consume(TokenType.Comma);
            defs.add(parseConstDef());
        }
        consume(TokenType.Semicolon);
        return watch(new ConstDecl(constToken, btype, defs));
    }

    /** ConstDef -> Ident [ '[' ConstExp ']' ] '=' ConstInitVal */
    private ConstDef parseConstDef() {
        Token ident = consume(TokenType.Ident);
        ConstExp length = null;
        if (among(TokenType.LBracket)) {
            consume(TokenType.LBracket);
            length = parseConstExp();
            consume(TokenType.RBracket);
        }
        consume(TokenType.Assign);
        return watch(new ConstDef(ident, length, parseConstInitVal()));
    }

    /** ConstInitVal -> ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}' | StringConst */
    private ConstInitVal parseConstInitVal() {
        if (among(TokenType.LBrace)) {
            consume(TokenType.LBrace);
            List<ConstExp> elements = new LinkedList<>();
            if (!among(TokenType.RBrace)) {
                elements.add(parseConstExp());
                while (among(TokenType.Comma)) {
                    consume(TokenType.Comma);
                    elements.add(parseConstExp());
                }
            }
            consume(TokenType.RBrace);
            return watch(ConstInitVal.list(elements));
        }
        if (among(TokenType.StringConst)) {
            return watch(ConstInitVal.string(consume(TokenType.StringConst)));
        }
        return watch(ConstInitVal.exp(parseConstExp()));
    }

    /** VarDecl -> [ 'static' ] BType VarDef { ',' VarDef } ';' */
    private VarDecl parseVarDecl() {
        Token staticToken = among(TokenType.Static) ? consume(TokenType.Static) : null;
        Token btype = parseBType();
        List<VarDef> defs = new LinkedList<>();
        defs.add(parseVarDef());
        while (among(TokenType.Comma)) {
            consume(TokenType.Comma);
            defs.add(parseVarDef());
        }
        consume(TokenType.Semicolon);
        return watch(new VarDecl(staticToken, btype, defs));
    }

    /** VarDef -> Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal */
    private VarDef parseVarDef() {
        Token ident = consume(TokenType.Ident);
        ConstExp length = null;
        if (among(TokenType.LBracket)) {
            consume(TokenType.LBracket);
            length = parseConstExp();
            consume(TokenType.RBracket);
        }
        InitVal initVal = null;
        if (among(TokenType.Assign)) {
            consume(TokenType.Assign);
            initVal = parseInitVal();
        }
        return watch(new VarDef(ident, length, initVal));
    }

    /** InitVal -> Exp | '{' [ Exp { ',' Exp } ] '}' | StringConst */
    private InitVal parseInitVal() {
        if (among(TokenType.LBrace)) {
            consume(TokenType.LBrace);
            List<Exp> elements = new LinkedList<>();
            if (!among(TokenType.RBrace)) {
                elements.add(parseExp());
                while (among(TokenType.Comma)) {
                    consume(TokenType.Comma);
                    elements.add(parseExp());
                }
            }
            consume(TokenType.RBrace);
            return watch(InitVal.list(elements));
        }
        if (among(TokenType.StringConst)) {
            return watch(InitVal.string(consume(TokenType.StringConst)));
        }
        return watch(InitVal.exp(parseExp()));
    }

    // ---------------------------------------------------------------- functions

    /** FuncType -> 'void' | 'int' | 'char' */
    private FuncType parseFuncType() {
        return watch(new FuncType(consumeAny(TokenType.Void, TokenType.Int, TokenType.Char)));
    }

    /** FuncDef -> FuncType Ident '(' [FuncFParams] ')' Block */
    private FuncDef parseFuncDef() {
        FuncType funcType = parseFuncType();
        Token ident = consume(TokenType.Ident);
        consume(TokenType.LParen);
        FuncFormalParams params = among(TokenType.RParen) ? null : parseFuncFParams();
        consume(TokenType.RParen);
        return watch(new FuncDef(funcType, ident, params, parseBlock()));
    }

    /** MainFuncDef -> 'int' 'main' '(' ')' Block */
    private MainFuncDef parseMainFuncDef() {
        consume(TokenType.Int);
        Token identifier = consume(TokenType.Main);
        consume(TokenType.LParen);
        consume(TokenType.RParen);
        return watch(new MainFuncDef(identifier, parseBlock()));
    }

    /** FuncFParams -> FuncFParam { ',' FuncFParam } */
    private FuncFormalParams parseFuncFParams() {
        List<FuncFormalParam> params = new LinkedList<>();
        params.add(parseFuncFParam());
        while (among(TokenType.Comma)) {
            consume(TokenType.Comma);
            params.add(parseFuncFParam());
        }
        return watch(new FuncFormalParams(params));
    }

    /** FuncFParam -> BType Ident ['[' ']'] */
    private FuncFormalParam parseFuncFParam() {
        Token btype = parseBType();
        Token ident = consume(TokenType.Ident);
        boolean array = false;
        if (among(TokenType.LBracket)) {
            consume(TokenType.LBracket);
            consume(TokenType.RBracket);
            array = true;
        }
        return watch(new FuncFormalParam(btype, ident, array));
    }

    // ------------------------------------------------------------------- blocks

    /** Block -> '{' { BlockItem } '}' */
    private Block parseBlock() {
        Block block = new Block();
        consume(TokenType.LBrace);
        while (!among(TokenType.RBrace) && !eof()) {
            block.addBlock(parseBlockItem());
        }
        block.setEndToken(consume(TokenType.RBrace));
        return watch(block);
    }

    /** BlockItem -> Decl | Stmt ; note that {@code <BlockItem>} is not emitted. */
    private BlockItem parseBlockItem() {
        if (among(TokenType.Const, TokenType.Static, TokenType.Int, TokenType.Char)) {
            return new BlockItem(parseDecl());
        }
        return new BlockItem(parseStmt());
    }

    // --------------------------------------------------------------- statements

    /** Stmt -> ... (see the branches below) */
    private Stmt parseStmt() {
        if (among(TokenType.LBrace)) {
            return watch(new BlockStmt(parseBlock()));
        }
        if (among(TokenType.If)) {
            return parseIfStmt();
        }
        if (among(TokenType.While)) {
            return parseWhileStmt();
        }
        if (among(TokenType.Switch)) {
            return parseSwitchStmt();
        }
        if (among(TokenType.Break)) {
            Token token = consume(TokenType.Break);
            consume(TokenType.Semicolon);
            return watch(new BreakStmt(token));
        }
        if (among(TokenType.Continue)) {
            Token token = consume(TokenType.Continue);
            consume(TokenType.Semicolon);
            return watch(new ContinueStmt(token));
        }
        if (among(TokenType.Return)) {
            return parseReturnStmt();
        }
        if (among(TokenType.Printf)) {
            return parsePrintfStmt();
        }
        if (among(TokenType.Semicolon)) {
            consume(TokenType.Semicolon);
            return watch(new ExpStmt(null));
        }
        if (isAssignStmt()) {
            LVal lval = parseLVal();
            consume(TokenType.Assign);
            Exp exp = parseExp();
            consume(TokenType.Semicolon);
            return watch(new AssignStmt(lval, exp));
        }
        Exp exp = among(TokenType.Semicolon) ? null : parseExp();
        consume(TokenType.Semicolon);
        return watch(new ExpStmt(exp));
    }

    /**
     * Distinguishes {@code Stmt -> LVal '=' Exp ';'} from {@code Stmt -> [Exp] ';'}.
     * The LVal is parsed speculatively and every side effect is rolled back.
     */
    private boolean isAssignStmt() {
        if (!among(TokenType.Ident)) {
            return false;
        }
        trace.save();
        boolean assign = false;
        try {
            parseLVal();
            assign = among(TokenType.Assign);
        } catch (RuntimeException e) {
            assign = false;
        } finally {
            trace.restore();
        }
        return assign;
    }

    /** Stmt -> 'if' '(' Cond ')' Stmt [ 'else' Stmt ] */
    private Stmt parseIfStmt() {
        consume(TokenType.If);
        consume(TokenType.LParen);
        Cond cond = parseCond();
        consume(TokenType.RParen);
        Stmt thenStmt = parseStmt();
        Stmt elseStmt = null;
        if (among(TokenType.Else)) {
            consume(TokenType.Else);
            elseStmt = parseStmt();
        }
        return watch(new IfStmt(cond, thenStmt, elseStmt));
    }

    /** Stmt -> 'while' '(' Cond ')' Stmt */
    private Stmt parseWhileStmt() {
        consume(TokenType.While);
        consume(TokenType.LParen);
        Cond cond = parseCond();
        consume(TokenType.RParen);
        return watch(new WhileStmt(cond, parseStmt()));
    }

    /** Stmt -> 'switch' '(' Exp ')' '{' { CaseStmt } '}' */
    private Stmt parseSwitchStmt() {
        consume(TokenType.Switch);
        consume(TokenType.LParen);
        Exp exp = parseExp();
        consume(TokenType.RParen);
        consume(TokenType.LBrace);
        List<CaseStmt> cases = new LinkedList<>();
        while (among(TokenType.Case, TokenType.Default)) {
            cases.add(parseCaseStmt());
        }
        consume(TokenType.RBrace);
        return watch(new SwitchStmt(exp, cases));
    }

    /** CaseStmt -> 'case' Number ':' { Stmt } | 'default' ':' { Stmt } */
    private CaseStmt parseCaseStmt() {
        Token keyword;
        Number number = null;
        if (among(TokenType.Case)) {
            keyword = consume(TokenType.Case);
            number = parseNumber();
        } else {
            keyword = consume(TokenType.Default);
        }
        consume(TokenType.Colon);
        List<Stmt> stmts = new LinkedList<>();
        while (!among(TokenType.Case, TokenType.Default, TokenType.RBrace) && !eof()) {
            stmts.add(parseStmt());
        }
        return watch(new CaseStmt(keyword, number, stmts));
    }

    /** Stmt -> 'return' [Exp] ';' */
    private Stmt parseReturnStmt() {
        Token returnToken = consume(TokenType.Return);
        Exp exp = among(TokenType.Semicolon) ? null : parseExp();
        consume(TokenType.Semicolon);
        return watch(new ReturnStmt(returnToken, exp));
    }

    /** Stmt -> 'printf' '(' StringConst { ',' Exp } ')' ';' */
    private Stmt parsePrintfStmt() {
        Token printfToken = consume(TokenType.Printf);
        consume(TokenType.LParen);
        Token format = consume(TokenType.StringConst);
        List<Exp> args = new LinkedList<>();
        while (among(TokenType.Comma)) {
            consume(TokenType.Comma);
            args.add(parseExp());
        }
        consume(TokenType.RParen);
        consume(TokenType.Semicolon);
        return watch(new PrintfStmt(printfToken, format, args));
    }

    // -------------------------------------------------------------- expressions

    /** Exp -> AddExp */
    private Exp parseExp() {
        return watch(new Exp(parseAddExp()));
    }

    /** ConstExp -> AddExp */
    private ConstExp parseConstExp() {
        return watch(new ConstExp(parseAddExp()));
    }

    /** Cond -> LOrExp */
    private Cond parseCond() {
        return watch(new Cond(parseLOrExp()));
    }

    /** LOrExp -> LAndExp | LOrExp '||' LAndExp */
    private LOrExp parseLOrExp() {
        LOrExp exp = watch(new LOrExp(parseLAndExp()));
        while (among(TokenType.Or)) {
            Token op = consume(TokenType.Or);
            exp = watch(new LOrExp(exp, op, parseLAndExp()));
        }
        return exp;
    }

    /** LAndExp -> EqExp | LAndExp '&&' EqExp */
    private LAndExp parseLAndExp() {
        LAndExp exp = watch(new LAndExp(parseEqExp()));
        while (among(TokenType.And)) {
            Token op = consume(TokenType.And);
            exp = watch(new LAndExp(exp, op, parseEqExp()));
        }
        return exp;
    }

    /** EqExp -> RelExp | EqExp ('==' | '!=') RelExp */
    private EqExp parseEqExp() {
        EqExp exp = watch(new EqExp(parseRelExp()));
        while (among(TokenType.Eq, TokenType.Neq)) {
            Token op = consumeAny(TokenType.Eq, TokenType.Neq);
            exp = watch(new EqExp(exp, op, parseRelExp()));
        }
        return exp;
    }

    /** RelExp -> AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp */
    private RelExp parseRelExp() {
        RelExp exp = watch(new RelExp(parseAddExp()));
        while (among(TokenType.Lt, TokenType.Gt, TokenType.Leq, TokenType.Geq)) {
            Token op = consumeAny(TokenType.Lt, TokenType.Gt, TokenType.Leq, TokenType.Geq);
            exp = watch(new RelExp(exp, op, parseAddExp()));
        }
        return exp;
    }

    /** AddExp -> MulExp | AddExp ('+' | '-') MulExp */
    private AddExp parseAddExp() {
        AddExp exp = watch(new AddExp(parseMulExp()));
        while (among(TokenType.Plus, TokenType.Minus)) {
            Token op = consumeAny(TokenType.Plus, TokenType.Minus);
            exp = watch(new AddExp(exp, op, parseMulExp()));
        }
        return exp;
    }

    /** MulExp -> UnaryExp | MulExp ('*' | '/' | '%') UnaryExp */
    private MulExp parseMulExp() {
        MulExp exp = watch(new MulExp(parseUnaryExp()));
        while (among(TokenType.Mult, TokenType.Div, TokenType.Mod)) {
            Token op = consumeAny(TokenType.Mult, TokenType.Div, TokenType.Mod);
            exp = watch(new MulExp(exp, op, parseUnaryExp()));
        }
        return exp;
    }

    /**
     * UnaryExp -> PrimaryExp
     * | Ident '(' [FuncRParams] ')'
     * | UnaryOp UnaryExp
     * | '(' BType ')' UnaryExp
     */
    private UnaryExp parseUnaryExp() {
        if (among(TokenType.Plus, TokenType.Minus, TokenType.Not)) {
            UnaryOp op = watch(new UnaryOp(consumeAny(TokenType.Plus, TokenType.Minus, TokenType.Not)));
            return watch(UnaryExp.unary(op, parseUnaryExp()));
        }
        // '(' BType is only ever the start of an explicit cast: a keyword can never
        // begin an Exp, so a missing ')' here is still treated as a cast (error j,
        // reported at the BType token, as required by the spec).
        if (among(TokenType.LParen) && among(1, TokenType.Int, TokenType.Char)) {
            consume(TokenType.LParen);
            Token castType = parseBType();
            consume(TokenType.RParen);
            return watch(UnaryExp.cast(castType, parseUnaryExp()));
        }
        if (among(TokenType.Ident) && among(1, TokenType.LParen)) {
            Token ident = consume(TokenType.Ident);
            consume(TokenType.LParen);
            FuncRParams params = among(TokenType.RParen) ? null : parseFuncRParams();
            consume(TokenType.RParen);
            return watch(UnaryExp.call(ident, params));
        }
        return watch(UnaryExp.primary(parsePrimaryExp()));
    }

    /** FuncRParams -> Exp { ',' Exp } */
    private FuncRParams parseFuncRParams() {
        List<Exp> params = new LinkedList<>();
        params.add(parseExp());
        while (among(TokenType.Comma)) {
            consume(TokenType.Comma);
            params.add(parseExp());
        }
        return watch(new FuncRParams(params));
    }

    /** PrimaryExp -> '(' Exp ')' | LVal | Number */
    private PrimaryExp parsePrimaryExp() {
        if (among(TokenType.LParen)) {
            consume(TokenType.LParen);
            Exp exp = parseExp();
            consume(TokenType.RParen);
            return watch(PrimaryExp.paren(exp));
        }
        if (among(TokenType.Ident)) {
            return watch(PrimaryExp.lval(parseLVal()));
        }
        return watch(PrimaryExp.number(parseNumber()));
    }

    /** LVal -> Ident ['[' Exp ']'] */
    private LVal parseLVal() {
        Token ident = consume(TokenType.Ident);
        Exp index = null;
        if (among(TokenType.LBracket)) {
            consume(TokenType.LBracket);
            index = parseExp();
            consume(TokenType.RBracket);
        }
        return watch(new LVal(ident, index));
    }

    /** Number -> IntConst | CharConst */
    private Number parseNumber() {
        return watch(new Number(consumeAny(TokenType.IntConst, TokenType.CharConst)));
    }
}
