package utils;

import error.ErrorTable;
import frontend.Lexer;
import frontend.Parser;
import frontend.ParserWatcher;
import frontend.ast.CompileUnit;
import frontend.token.TokenStream;

import java.io.FileWriter;
import java.io.IOException;

public class Controller {
    public static final ErrorTable errors = new ErrorTable();
    
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
    }
    
    private static void exit() {
        if (Configure.debug.displayErrors) {
            System.err.println("==> Errors: ");
            System.err.println(errors.toDebugString());
        }
        
        System.exit(0);
    }
}
