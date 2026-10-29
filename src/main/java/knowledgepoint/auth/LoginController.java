package knowledgepoint.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import knowledgepoint.cabinet.CabinetClient;
import knowledgepoint.cabinet.CabinetResponse;
import knowledgepoint.web.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class LoginController {

  private final CabinetClient cabinet;
  private final ObjectMapper json;

  public LoginController(CabinetClient cabinet, ObjectMapper json) {
    this.cabinet = cabinet;
    this.json = json;
  }

  @PostMapping("/api/auth/login")
  public ResponseEntity<?> login(HttpServletRequest request) throws IOException {
    byte[] bytes = request.getInputStream().readNBytes(8193);
    if (bytes.length > 8192) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Введите логин и пароль"));
    }
    String raw = bytes.length == 0 ? "{}" : new String(bytes, StandardCharsets.UTF_8);
    JsonNode tree;
    try {
      tree = json.readTree(raw);
    } catch (IOException exception) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Введите логин и пароль"));
    }
    if (tree == null || !tree.isObject()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Введите логин и пароль"));
    }
    String username = text(tree.get("username"));
    String password = password(tree.get("password"));
    if (username == null || password == null || username.length() > 200 || password.length() > 200) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Введите логин и пароль"));
    }
    ObjectNode body = json.createObjectNode();
    body.put("username", username);
    body.put("password", password);
    CabinetResponse response;
    try {
      response = cabinet.post(
          CabinetLogin.PATH,
          json.writeValueAsString(body),
          Map.of("x-service-version", CabinetLogin.SERVICE_VERSION));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return ResponseEntity.status(502).body(new ErrorResponse("Кабинет не ответил"));
    } catch (IOException exception) {
      return ResponseEntity.status(502).body(new ErrorResponse("Кабинет не ответил"));
    }
    if (response.status() == 400 || response.status() == 401 || response.status() == 403) {
      return ResponseEntity.status(401).body(new ErrorResponse("Неверный логин или пароль"));
    }
    if (response.status() < 200 || response.status() >= 300) {
      return ResponseEntity.status(502).body(new ErrorResponse("Кабинет не принял вход"));
    }
    String access = accessToken(response.body());
    if (access == null || access.length() < 20) {
      return ResponseEntity.status(502).body(new ErrorResponse("Кабинет не вернул токен"));
    }
    return ResponseEntity.ok(Map.of("token", access));
  }

  private String accessToken(String body) {
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      JsonNode token = json.readTree(body).path("token").get("accessToken");
      if (token == null || !token.isTextual()) {
        return null;
      }
      String value = token.asText().trim();
      return value.isEmpty() ? null : value;
    } catch (IOException exception) {
      return null;
    }
  }

  private static String text(JsonNode node) {
    if (node == null || node.isNull() || !node.isTextual()) {
      return null;
    }
    String value = node.asText().trim();
    return value.isEmpty() ? null : value;
  }

  private static String password(JsonNode node) {
    if (node == null || node.isNull() || !node.isTextual()) {
      return null;
    }
    String value = node.asText();
    return value.isBlank() ? null : value;
  }
}
