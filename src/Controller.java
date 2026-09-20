import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Controller {
    public static final ErrorTable errors = new ErrorTable();
    public static void run(String[] args) throws IOException {
        String source = new String(
                Files.readAllBytes(Paths.get(args[0])),
                java.nio.charset.StandardCharsets.UTF_8
        );
    }
}
