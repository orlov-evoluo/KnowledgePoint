package knowledgepoint.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import knowledgepoint.cabinet.CabinetClient;
import knowledgepoint.cabinet.CabinetResponse;
import knowledgepoint.session.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;

@Component
public class DailySnapshot {

  private static final Logger log = LoggerFactory.getLogger(DailySnapshot.class);

  private final SessionStore sessions;
  private final CabinetClient cabinet;
  private final DailyStatStore stats;
  private final ObjectMapper json;

  public DailySnapshot(SessionStore sessions, CabinetClient cabinet, DailyStatStore stats, ObjectMapper json) {
    this.sessions = sessions;
    this.cabinet = cabinet;
    this.stats = stats;
    this.json = json;
  }

  public synchronized void capture(LocalDate day) {
    String token = sessions.authHeader();
    if (token == null) {
      log.warn("Пропуск снимка: нет токена");
      return;
    }
    try {
      long studentId = studentId(token);
      CabinetResponse journal = cabinet.get("/journal/" + studentId + "/active", token);
      if (journal.status() != 200) {
        throw new IllegalStateException("журнал " + journal.status());
      }
      stats.save(JournalIndicators.parse(day.toString(), Instant.now(), journal.body(), json));
      log.info("Снимок за {} сохранён", day);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      log.warn("Не удалось снять статистику ({})", exception.getClass().getSimpleName());
    } catch (Exception exception) {
      log.warn("Не удалось снять статистику ({})", exception.getClass().getSimpleName());
    }
  }

  private long studentId(String token) throws Exception {
    CabinetResponse profile = cabinet.get("/profile", token);
    if (profile.status() != 200) {
      throw new IllegalStateException("профиль " + profile.status());
    }
    JsonNode id = json.readTree(profile.body()).path("client").get("id");
    if (id == null || !id.canConvertToLong() || id.asLong() <= 0) {
      throw new IllegalArgumentException("нет поля client.id");
    }
    return id.asLong();
  }
}
