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
    return html(page);
  }

  @GetMapping("/analytics")
  public ResponseEntity<String> analytics() throws IOException {
    return html(sibling("analytics.html"));
  }

  @GetMapping("/schedule")
  public ResponseEntity<String> schedule() throws IOException {
    return html(sibling("schedule.html"));
  }

  private Path sibling(String name) {
    return page.getParent() == null ? Path.of(name) : page.getParent().resolve(name);
  }

  private static ResponseEntity<String> html(Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      return ResponseEntity.status(500)
          .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
          .body("page missing");
    }
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
        .body(Files.readString(file));
  }
}
