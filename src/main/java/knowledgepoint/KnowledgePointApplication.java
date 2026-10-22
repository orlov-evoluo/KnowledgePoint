package knowledgepoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class KnowledgePointApplication {

  public static void main(String[] args) throws IOException {
    String dataDir = System.getenv("DATA_DIR");
    if (dataDir == null || dataDir.isBlank()) {
      dataDir = ".data";
    }
    Files.createDirectories(Path.of(dataDir));
    SpringApplication.run(KnowledgePointApplication.class, args);
  }
}
