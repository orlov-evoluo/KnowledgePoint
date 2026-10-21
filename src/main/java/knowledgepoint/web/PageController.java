package knowledgepoint.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
public class PageController {

  private final Path page;

  public PageController(@Value("${knowledgepoint.page}") String page) {
    this.page = Path.of(page);
  }

  @GetMapping("/")
  public ResponseEntity<String> index() throws IOException {
    if (!Files.isRegularFile(page)) {
      return ResponseEntity.status(500)
          .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
          .body("page missing");
    }
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
        .body(Files.readString(page));
  }
}
