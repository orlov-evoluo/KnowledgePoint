package knowledgepoint.proxy;

import knowledgepoint.session.SessionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class CabinetProxyController {

  private static final List<Pattern> ALLOWED = List.of(
      Pattern.compile("^/profile$"),
      Pattern.compile("^/children$"),
      Pattern.compile("^/journal/\\d+/(active|allfinished|finished|archived|cancelled)$"),
      Pattern.compile("^/journal/\\d+/detail/\\d+$")
  );

  private final SessionStore sessions;
  private final String upstream;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

  public CabinetProxyController(SessionStore sessions, @Value("${knowledgepoint.upstream}") String upstream) {
    this.sessions = sessions;
    this.upstream = upstream.endsWith("/") ? upstream.substring(0, upstream.length() - 1) : upstream;
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
    HttpRequest upstreamRequest = HttpRequest.newBuilder(target(path, request))
        .timeout(Duration.ofSeconds(30))
        .header("Authorization", token)
        .header("Accept", "application/json")
        .GET()
        .build();
    try {
      HttpResponse<String> response = http.send(upstreamRequest, HttpResponse.BodyHandlers.ofString());
      return json(response.statusCode(), response.body());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return json(502, "{\"error\":\"Кабинет не ответил\"}");
    } catch (Exception exception) {
      return json(502, "{\"error\":\"Кабинет не ответил\"}");
    }
  }

  private URI target(String path, HttpServletRequest request) {
    StringBuilder builder = new StringBuilder(upstream).append(path);
    String start = request.getParameter("startDate");
    String end = request.getParameter("endDate");
    if (start != null || end != null) {
      builder.append('?');
      if (start != null) {
        builder.append("startDate=").append(URLEncoder.encode(start, StandardCharsets.UTF_8));
      }
      if (start != null && end != null) {
        builder.append('&');
      }
      if (end != null) {
        builder.append("endDate=").append(URLEncoder.encode(end, StandardCharsets.UTF_8));
      }
    }
    return URI.create(builder.toString());
  }

  private static ResponseEntity<String> json(int status, String body) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }
}
