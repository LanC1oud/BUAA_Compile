import frontend.Lexer;
import frontend.Parser;
import frontend.ParserWatcher;
import frontend.Traverser;
import utils.Configure;
import utils.Controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Actual-side adapter only. Expected results are produced independently by Python. */
public final class CompilerProbe {
    private static void write(Path dir, String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        Path batch = Path.of(args[0]);
        int crashes = 0;
        try (var children = Files.list(batch)) {
            for (Path sample : children.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("case_"))
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                Path actual = sample.resolve("actual");
                Files.createDirectories(actual);
                // Clear previous products so repeated checks cannot read stale successes.
                for (String name : new String[]{"lexer.txt", "parser.txt", "symbol.txt",
                        "lexer.errors.txt", "parser.errors.txt", "semantic.errors.txt", "crash.txt", "status.txt"}) {
                    Files.deleteIfExists(actual.resolve(name));
                }
                Controller.errors.clear();
                Controller.symbols.clear();
                Configure.debug.displayTokensWithAst = true;
                String stage = "lexer";
                try {
                    String source = Files.readString(sample.resolve("testfile.txt"), StandardCharsets.UTF_8);
                    var tokens = new Lexer(source, sample.toString()).lex().emit();
                    write(actual, "lexer.txt", tokens.toString());
                    write(actual, "lexer.errors.txt", Controller.errors.toString());
                    stage = "parser";
                    ParserWatcher watcher = new ParserWatcher();
                    var ast = new Parser(tokens, watcher).parse().emit();
                    write(actual, "parser.txt", watcher.toString());
                    write(actual, "parser.errors.txt", Controller.errors.toString());
                    stage = "semantic";
                    new Traverser(ast).spawn();
                    write(actual, "semantic.errors.txt", Controller.errors.toString());
                    if (Controller.errors.noError()) {
                        write(actual, "symbol.txt", Controller.symbols.toString());
                    }
                    write(actual, "status.txt", "complete");
                } catch (Exception | StackOverflowError e) {
                    crashes++;
                    write(actual, "crash.txt", e.toString());
                    write(actual, "status.txt", stage + "\n" + e.getClass().getName());
                    if (stage.equals("parser")) {
                        write(actual, "parser.errors.txt", Controller.errors.toString());
                    }
                }
            }
        }
        System.out.println("CompilerProbe: " + crashes + " crashes");
        // Per-case failures are data for Python's checks, including intentional
        // parser rejection fixtures. An adapter/infrastructure failure still exits nonzero.
    }
}
