package compilerui.pipeline;

import compilerui.model.CompileResult;
import compilerui.model.CompilerSettings;
import compilerui.model.DisplayError;
import compilerui.model.Stage;
import compilerui.model.StageStats;
import error.ErrorEntry;
import error.ErrorTable;
import frontend.Lexer;
import frontend.Parser;
import frontend.ParserWatcher;
import frontend.Traverser;
import frontend.ast.CompileUnit;
import frontend.symbol.SymbolTable;
import frontend.token.TokenStream;
import utils.Configure;
import utils.Controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 编译器前端管线：{@code utils.Controller} 的"库版本"。
 *
 * <p>Controller 面向命令行评测——读静态配置、每个阶段结束就 {@code System.exit(0)}，
 * 因此没法被界面反复调用。这个类把同样的三个阶段抽成可重复调用、返回结果对象、
 * 不退出 JVM 的接口。
 *
 * <p><b>本类不是线程安全的。</b> 它要复用 {@code Controller.errors} /
 * {@code Controller.symbols} 这两个全局静态实例，所以同一 JVM 内必须串行调用
 * {@link #compile}。
 *
 * <p><b>对 {@code src/} 的依赖</b>：那两个字段是 {@code static final}，无法替换引用
 * （Java 21 下 {@code Field.set} 与 {@code VarHandle} 都拒绝写 final 字段），
 * 只能原地清空，所以 {@link ErrorTable#clear()} 与 {@link SymbolTable#clear()} 是为此新增的。
 * 每次编译开始先清空，结束后把 {@code Configure.source} 还原，除此之外不残留跨调用状态。
 */
public final class Pipeline {

    private final Map<Stage, CompileResult> results = new EnumMap<>(Stage.class);
    private CompilerSettings settings = CompilerSettings.defaults();
    private List<DisplayError> displayErrors = List.of();
    /** 编译器中途抛出的异常消息（解析器碰到严重非法输入时会直接抛），正常为 null。 */
    private String crash = null;

    /** 每次编译前设定调试开关与阶段选择。 */
    public void setSettings(CompilerSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** 指定阶段的结果；没跑过该阶段时返回 null。 */
    public CompileResult resultOf(Stage stage) {
        return results.get(stage);
    }

    /**
     * 拿"最后一次编译"的结构化错误列表（行号 + 错误类型）。
     *
     * <p>界面靠它拼可读提示；{@link ErrorTable#toString()} 那条路径完全没碰，
     * 所以 {@code error.txt} 的格式不受影响。
     */
    public List<DisplayError> displayErrors() {
        return displayErrors;
    }

    /**
     * @return 上次编译中途崩掉时的异常消息；正常跑完（哪怕报了错误）返回 null。
     *
     * <p>编译器对某些严重非法输入会直接抛 {@code IllegalStateException} 而不是走错误恢复
     * （例如仓库里的 {@code testfile.invalid-sysy.bak}）。界面得能区分"正常报错"和
     * "崩溃"，否则用户只会看到一坨堆栈。
     */
    public String crashMessage() {
        return crash;
    }

    /**
     * 从词法开始依次跑到 {@code until} 阶段（含），结果放进 {@link #resultOf}。
     *
     * @param sourceFile 已落盘的 SysY 源文件路径
     * @param until      停止阶段（含）
     */
    public void compile(String sourceFile, Stage until) throws IOException {
        Objects.requireNonNull(until, "until");
        results.clear();
        displayErrors = List.of();
        crash = null;

        String originalSource = Configure.source;
        try {
            // 每一次编译都用干净的错误表 / 符号表。
            ErrorTable errors = Controller.errors;
            SymbolTable symbols = Controller.symbols;
            errors.clear();
            symbols.clear();

            Configure.source = sourceFile;
            settings.applyDebugFlags();

            try {
                runStages(sourceFile, until, errors, symbols);
            } catch (RuntimeException e) {
                // 解析器遇严重非法输入会直接抛异常而不是走错误恢复。
                // 记下来让界面能说人话，而不是丢一坨堆栈给用户。
                crash = e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage());
            }
            // 崩之前的阶段结果、以及当时已记录的错误，仍然要交出去。
            snapshotErrors(errors);
        } finally {
            Configure.source = originalSource;
        }
    }

    /** 真正的阶段链。异常由 {@link #compile} 统一兜住。 */
    private void runStages(String sourceFile, Stage until, ErrorTable errors, SymbolTable symbols)
            throws IOException {
        // ---- stage 1: 词法分析 ----
        long start = System.nanoTime();
        StringBuilder log = new StringBuilder();
        Captured<TokenStream> lexed = capture(() -> {
            Lexer lexer = new Lexer(sourceFile);
            return lexer.lex().emit();
        });
        long lexerMillis = millisSince(start);
        log.append(lexed.log());
        TokenStream tokens = lexed.value();
        String tokenText = tokens.toString();
        // 趁 Parser 还没推进 TokenStream 游标，先把词法结果定格下来。
        // token 数 = 产物行数减掉末尾那行 EOF。
        record(Stage.LEXER, tokenText, errors, log,
                new StageStats(Stage.LEXER, lexerMillis, 0,
                        Math.max(0, tokenText.lines().count() - 1), "token"));
        if (until == Stage.LEXER) {
            return;
        }

        // ---- stage 2: 语法分析 ----
        start = System.nanoTime();
        log = new StringBuilder();
        ParserWatcher watcher = new ParserWatcher();
        CompileUnit[] astHolder = new CompileUnit[1];
        Captured<CompileUnit> parsed = capture(() -> {
            astHolder[0] = new Parser(tokens, watcher).parse().emit();
            return astHolder[0];
        });
        long syntaxMillis = millisSince(start);
        log.append(parsed.log());
        String astText = watcher.toString();
        // AST 文本里形如 <NodeName> 的行就是语法树节点。
        record(Stage.SYNTAX, astText, errors, log,
                new StageStats(Stage.SYNTAX, syntaxMillis, 0,
                        astText.lines().filter(line -> line.startsWith("<")).count(), "节点"));
        if (until == Stage.SYNTAX) {
            return;
        }

        // ---- stage 3: 语义分析 ----
        start = System.nanoTime();
        log = new StringBuilder();
        CompileUnit ast = astHolder[0];
        Captured<Void> traversed = capture(() -> {
            new Traverser(ast).spawn();
            return null;
        });
        long semanticMillis = millisSince(start);
        log.append(traversed.log());
        String symbolText = symbols.toString();
        record(Stage.SEMANTIC, symbolText, errors, log,
                new StageStats(Stage.SEMANTIC, semanticMillis, 0,
                        symbolText.lines().count(), "符号"));
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private void snapshotErrors(ErrorTable errors) {
        List<DisplayError> collected = new ArrayList<>();
        for (ErrorEntry entry : errors.entries()) {
            collected.add(new DisplayError(entry.getLine(), entry));
        }
        displayErrors = List.copyOf(collected);
    }

    private void record(Stage stage, String payload, ErrorTable errors, StringBuilder log, StageStats stats) {
        boolean ok = errors.noError();
        String text = ok ? payload : errors.toString();
        results.put(stage, new CompileResult(
                stage,
                ok,
                ok ? payload : null,
                ok ? null : text,
                errors.size(),
                log.length() == 0 ? "" : log.toString(),
                new StageStats(stage, stats.millis(), text.lines().count(),
                        stats.metric(), stats.metricName())));
    }

    // ------------------------------------------------------------------
    // 输出捕获
    // ------------------------------------------------------------------

    private record Captured<T>(T value, String log) { }

    private interface Callable<T> {
        T call() throws IOException;
    }

    /**
     * 执行 action，并把期间写入 stdout/stderr 的内容收集成日志。
     * 用类对象做锁，避免多线程同时改写 System.out。
     */
    private static synchronized <T> Captured<T> capture(Callable<T> action) throws IOException {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(stream);
            System.setErr(stream);
            T value = action.call();
            stream.flush();
            return new Captured<>(value, buffer.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
