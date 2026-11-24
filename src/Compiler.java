import utils.Controller;
import utils.Configure;
import utils.HomeworkConfig;

import java.io.IOException;

public class Compiler {
    public static void main(String[] args) throws IOException {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--stage" -> {
                    if (++i == args.length) throw new IllegalArgumentException("Missing --stage value");
                    HomeworkConfig.hw = switch (args[i].toLowerCase(java.util.Locale.ROOT)) {
                        case "lexer" -> HomeworkConfig.Hw.Lexer;
                        case "syntax" -> HomeworkConfig.Hw.Syntax;
                        case "semantic" -> HomeworkConfig.Hw.Semantic;
                        case "llvm" -> HomeworkConfig.Hw.Llvm;
                        case "mips" -> HomeworkConfig.Hw.Mips;
                        default -> throw new IllegalArgumentException("Unknown stage: " + args[i]);
                    };
                }
                case "--optimize" -> Configure.optimize = true;
                case "--no-optimize" -> Configure.optimize = false;
                case "--delay-slots" -> Configure.enableDelaySlotRemoval = false;
                case "--no-delay-slots" -> Configure.enableDelaySlotRemoval = true;
                case "--ir-before" -> {
                    if (++i == args.length) throw new IllegalArgumentException("Missing --ir-before path");
                    Configure.irBeforeOptimization = args[i];
                }
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        Configure.target = HomeworkConfig.getTarget();
        Controller.run();
    }
}
