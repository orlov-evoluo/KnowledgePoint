package knowledgepoint.proxy;

import knowledgepoint.cabinet.CabinetClient;
import knowledgepoint.cabinet.CabinetResponse;
import knowledgepoint.session.SessionStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class CabinetProxyController {

  private static final List<Pattern> ALLOWED = List.of(
      Pattern.compile("^/profile$"),
      Pattern.compile("^/children$"),
      Pattern.compile("^/journal/\\d+/(active|allfinished|finished|archived|cancelled)$"),
      Pattern.compile("^/journal/\\d+/detail/\\d+$"),
      Pattern.compile("^/schedule$"),
      Pattern.compile("^/schedule/upcoming$")
  );
  private static final List<String> FORWARDED = List.of("startDate", "endDate", "startAt", "endAt");
  private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

  private final SessionStore sessions;
  private final CabinetClient cabinet;

  public CabinetProxyController(SessionStore sessions, CabinetClient cabinet) {
    this.sessions = sessions;
    this.cabinet = cabinet;
  }

  @GetMapping("/api/**")
  public ResponseEntity<String> proxy(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if (!uri.startsWith("/api/") || uri.contains("..")) {
      return json(404, "{\"error\":\"not found\"}");
    }
    String path = uri.substring(4);
    if (ALLOWED.stream().noneMatch(pattern -> pattern.matcher(path).matches())) {
      return json(404, "{\"error\":\"not found\"}");
    }
    String token = sessions.authHeader();
    if (token == null) {
      return json(503, "{\"error\":\"Сохраните токен кабинета\"}");
    }
    try {
      CabinetResponse response = cabinet.get(target(path, request), token);
      return json(response.status(), response.body());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return json(502, "{\"error\":\"Кабинет не ответил\"}");
    } catch (Exception exception) {
      return json(502, "{\"error\":\"Кабинет не ответил\"}");
    }
  }

  private String target(String path, HttpServletRequest request) {
    StringBuilder builder = new StringBuilder(path);
    boolean first = true;
    for (String key : FORWARDED) {
      String value = request.getParameter(key);
      if (value == null || !DAY.matcher(value).matches()) {
        continue;
      }
      builder.append(first ? '?' : '&').append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
      first = false;
    }
    return builder.toString();
  }

  private static ResponseEntity<String> json(int status, String body) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }
}
