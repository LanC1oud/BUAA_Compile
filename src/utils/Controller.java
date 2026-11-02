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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import llvm.IrGenerator;

public class Controller {
    public static final ErrorTable errors = new ErrorTable();
    public static final SymbolTable symbols = new SymbolTable();
    
    public static void run() throws IOException {
        errors.clear();
        symbols.clear();
        Files.deleteIfExists(Path.of(Configure.target));
        Files.deleteIfExists(Path.of(Configure.error));
        if (HomeworkConfig.hw == HomeworkConfig.Hw.Llvm) Files.deleteIfExists(Path.of("llvm_ir.txt"));
        
        // stage 1: Lexer
        String source = Configure.source;
        Lexer lexer = new Lexer(source);
        TokenStream tokens = lexer.lex().emit();
        if (Configure.debug.displayTokens) {
            System.err.println(tokens.toDebugString());
        }
        if (HomeworkConfig.hw == HomeworkConfig.Hw.Lexer) {
            try (FileWriter writer = new FileWriter(errors.noError() ?
                    Configure.target : Configure.error, StandardCharsets.UTF_8)) {
                writer.write((errors.noError() ? tokens : errors).toString());
            }
            Controller.exit();
            return;
        }

        // stage 2: Parser
        if (HomeworkConfig.hw == HomeworkConfig.Hw.Syntax) {
            Configure.debug.displayTokensWithAst = true;
        }
        ParserWatcher watcher = new ParserWatcher();
        CompileUnit ast;
        try {
            ast = new Parser(tokens, watcher).parse().emit();
        } catch (RuntimeException e) {
            if (Configure.debug.displayErrors) {
                System.err.println("==> Parsing aborted: " + e);
            }
            try (FileWriter writer = new FileWriter(Configure.error, StandardCharsets.UTF_8)) {
                writer.write("" + errors);
            }
            Controller.exit();
            if (errors.noError()) throw e;
            return;
        }
        if (HomeworkConfig.hw == HomeworkConfig.Hw.Syntax) {
            try (FileWriter writer = new FileWriter(errors.noError() ?
                    Configure.target : Configure.error, StandardCharsets.UTF_8)) {
                writer.write((errors.noError() ? watcher : errors).toString());
            }
            Controller.exit();
            return;
        }

        // stage 3: Semantic analysis
        Traverser traverser = new Traverser(ast);
        traverser.spawn();
        if (Configure.debug.displaySymbols) {
            System.err.println(symbols);
        }
        if (HomeworkConfig.hw == HomeworkConfig.Hw.Semantic) {
            try (FileWriter writer = new FileWriter(errors.noError() ?
                    Configure.target : Configure.error, StandardCharsets.UTF_8)) {
                writer.write((errors.noError() ? symbols.toString() : errors.toString()) + "\n");
            }
            Controller.exit();
            return;
        }
        if (!errors.noError()) {
            Files.writeString(Path.of(Configure.error), errors.toString(), StandardCharsets.UTF_8);
            return;
        }
        IrGenerator generator = new IrGenerator(traverser.getSemanticInfo(), Configure.optimize ? Configure.irPasses : java.util.List.of());
        String ir = generator.generate(ast).toString();
        if (Configure.irBeforeOptimization != null) Files.writeString(Path.of(Configure.irBeforeOptimization), generator.getUnoptimizedIr(), StandardCharsets.UTF_8);
        Files.writeString(Path.of(Configure.target), ir, StandardCharsets.UTF_8);
    }
    
    private static void exit() {
        if (Configure.debug.displayErrors) {
            System.err.println("==> Errors: ");
            System.err.println(errors.toDebugString());
        }
        
    }
}
