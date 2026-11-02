import utils.Controller;
import utils.Configure;
import utils.HomeworkConfig;

import java.io.IOException;

public class Compiler {
    public static void main(String[] args) throws IOException {
        for (int i=0; i<args.length; i++) {
            if (i+1>=args.length) throw new IllegalArgumentException("Missing option value: " + args[i]);
            switch (args[i]) {
                case "--stage" -> HomeworkConfig.hw = HomeworkConfig.Hw.valueOf(args[++i]);
                case "--source" -> Configure.source = args[++i];
                case "--output" -> Configure.target = args[++i];
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        boolean customOutput = java.util.Arrays.asList(args).contains("--output");
        if (!customOutput) Configure.target = HomeworkConfig.getTarget();
        Controller.run();
    }
}
