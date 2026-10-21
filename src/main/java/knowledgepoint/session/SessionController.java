package knowledgepoint.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import knowledgepoint.web.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/session")
public class SessionController {

  private final SessionStore store;
  private final ObjectMapper json;

  public SessionController(SessionStore store, ObjectMapper json) {
    this.store = store;
    this.json = json;
  }

  @GetMapping
  public SessionState get() {
    return store.state();
  }

  @DeleteMapping
  public SessionState delete() {
    store.clear();
    return store.state();
  }

  @PostMapping
  public ResponseEntity<?> save(HttpServletRequest request) throws IOException {
    byte[] bytes = request.getInputStream().readNBytes(8193);
    if (bytes.length > 8192) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Не удалось сохранить токен"));
    }
    String raw = bytes.length == 0 ? "{}" : new String(bytes, StandardCharsets.UTF_8);
    JsonNode tree;
    try {
      tree = json.readTree(raw);
    } catch (IOException exception) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Не удалось сохранить токен"));
    }
    if (tree == null || !tree.isObject()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Не удалось сохранить токен"));
    }
    JsonNode tokenNode = tree.get("token");
    String token = tokenNode == null || tokenNode.isNull() ? null : tokenNode.asText();
    String normalized = SessionStore.normalize(token);
    if (normalized == null || normalized.length() < 20) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Вставьте токен кабинета"));
    }
    store.save(normalized);
    return ResponseEntity.ok(store.state());
  }
}
