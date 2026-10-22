package knowledgepoint.session;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Component
public class SessionStore {

  private final JdbcTemplate jdbc;
  private final String environmentToken;

  public SessionStore(JdbcTemplate jdbc, @Value("${TOCHKA_TOKEN:}") String environmentToken) {
    this.jdbc = jdbc;
    this.environmentToken = environmentToken;
  }

  @PostConstruct
  void createTable() {
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS session (
          id INTEGER PRIMARY KEY CHECK (id = 1),
          token TEXT NOT NULL,
          updated_at TEXT NOT NULL
        )
        """);
  }

  public SessionState state() {
    boolean stored = storedToken() != null;
    return new SessionState(authHeader() != null, stored);
  }

  public void save(String token) {
    jdbc.update("""
        INSERT INTO session (id, token, updated_at)
        VALUES (1, ?, ?)
        ON CONFLICT(id) DO UPDATE SET token = excluded.token, updated_at = excluded.updated_at
        """, token, Instant.now().toString());
  }

  public void clear() {
    jdbc.update("DELETE FROM session WHERE id = 1");
  }

  public String authHeader() {
    String stored = normalize(storedToken());
    if (stored != null) {
      return stored;
    }
    return normalize(environmentToken);
  }

  public boolean matches(String presented) {
    String expected = authHeader();
    String actual = normalize(presented);
    if (expected == null || actual == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        actual.getBytes(StandardCharsets.UTF_8));
  }

  public static String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    if (value.isEmpty()) {
      return null;
    }
    if (value.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
      return value;
    }
    return "Bearer " + value;
  }

  private String storedToken() {
    List<String> tokens = jdbc.query(
        "SELECT token FROM session WHERE id = 1",
        (rs, row) -> rs.getString("token"));
    if (tokens.isEmpty()) {
      return null;
    }
    String value = tokens.get(0);
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
