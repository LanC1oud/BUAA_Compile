package utils;

import error.ErrorTable;
import frontend.Lexer;
import frontend.Parser;
import frontend.ParserWatcher;
import frontend.Traverser;
import frontend.ast.CompileUnit;
import frontend.symbol.SymbolTable;
import frontend.token.TokenStream;

import java.io.FileWriter;
import java.io.IOException;

public class Controller {
    public static final ErrorTable errors = new ErrorTable();
    /** 语义分析产出的符号表；由 {@link frontend.Traverser} 填充。 */
    public static final SymbolTable symbols = new SymbolTable();
    
    public static void run() throws IOException {
        
        // stage 1: Lexer
        String source = Configure.source;
        Lexer lexer = new Lexer(source);
        TokenStream tokens = lexer.lex().emit();
        if (Configure.debug.displayTokens) {
            System.err.println(tokens.toDebugString());
        }
        if (HomeworkConfig.hw == HomeworkConfig.hw.Lexer) {
            try (FileWriter writer = new FileWriter(errors.noError() ?
                    Configure.target : Configure.error)) {
                writer.write((errors.noError() ? tokens : errors).toString());
            }
            Controller.exit();
        }

        // stage 2: Parser
        if (HomeworkConfig.hw == HomeworkConfig.Hw.Syntax) {
            Configure.debug.displayTokensWithAst = true;
        }
        ParserWatcher watcher = new ParserWatcher();
        CompileUnit ast = new Parser(tokens, watcher).parse().emit();
        if (HomeworkConfig.hw == HomeworkConfig.Hw.Syntax) {
            try (FileWriter writer = new FileWriter(errors.noError() ?
                    Configure.target : Configure.error)) {
                writer.write((errors.noError() ? watcher : errors).toString());
            }
            Controller.exit();
        }

        // stage 3: Semantic analysis
        // 遍历 AST，填充 Controller.symbols 并报出错误 b ~ n
        new Traverser(ast).spawn();
        if (Configure.debug.displaySymbols) {
            System.err.println(symbols);
        }
        if (HomeworkConfig.hw == HomeworkConfig.Hw.Semantic) {
            try (FileWriter writer = new FileWriter(errors.noError() ?
                    Configure.target : Configure.error)) {
                writer.write((errors.noError() ? symbols.toString() : errors.toString()) + "\n");
            }
            Controller.exit();
        }
    }
    
    private static void exit() {
        if (Configure.debug.displayErrors) {
            System.err.println("==> Errors: ");
            System.err.println(errors.toDebugString());
        }
        
        System.exit(0);
    }
}
