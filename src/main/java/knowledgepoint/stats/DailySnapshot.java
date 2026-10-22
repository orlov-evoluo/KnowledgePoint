package knowledgepoint.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import knowledgepoint.cabinet.CabinetClient;
import knowledgepoint.cabinet.CabinetResponse;
import knowledgepoint.cabinet.CabinetStudent;
import knowledgepoint.session.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DailySnapshot {

  private static final Logger log = LoggerFactory.getLogger(DailySnapshot.class);

  private final CabinetStudent students;
  private final CabinetClient cabinet;
  private final DailyStatStore stats;
  private final ObjectMapper json;

  public DailySnapshot(CabinetStudent students, CabinetClient cabinet, DailyStatStore stats, ObjectMapper json) {
    this.students = students;
    this.cabinet = cabinet;
    this.stats = stats;
    this.json = json;
  }

  public synchronized Capture captureToday(String authorization) {
    CabinetStudent.StudentAccess access = students.identify(authorization);
    if (access.status() != 200) {
      return Capture.failed(access.status());
    }
    String token = SessionStore.normalize(authorization);
    try {
      CabinetResponse journal = cabinet.get("/journal/" + access.studentId() + "/active", token);
      if (journal.status() != 200) {
        return Capture.failed(journal.status() == 401 ? 401 : 502);
      }
      String day = LocalDate.now(SnapshotClock.MOSCOW).toString();
      Map<Long, String> details = courseDetails(access.studentId(), token, journal.body());
      DailyStat stat = JournalIndicators.parse(access.studentId(), day, Instant.now(), journal.body(), details, json);
      stats.adoptLegacy(access.studentId());
      stats.save(stat);
      log.info("Снимок за {} сохранён", day);
      return Capture.saved(stat);
    } catch (DetailFailure exception) {
      return Capture.failed(exception.status);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      log.warn("Не удалось снять статистику ({})", exception.getClass().getSimpleName());
      return Capture.failed(502);
    } catch (Exception exception) {
      log.warn("Не удалось снять статистику ({})", exception.getClass().getSimpleName());
      return Capture.failed(502);
    }
  }

  private Map<Long, String> courseDetails(long studentId, String token, String journalBody)
      throws IOException, InterruptedException, DetailFailure {
    JsonNode courses = json.readTree(journalBody).path("courses");
    Map<Long, String> details = new LinkedHashMap<>();
    if (!courses.isArray()) {
      return details;
    }
    for (JsonNode course : courses) {
      long id = course.path("id").asLong(0);
      if (id <= 0 || details.containsKey(id)) {
        continue;
      }
      CabinetResponse detail = cabinet.get("/journal/" + studentId + "/detail/" + id, token);
      if (detail.status() != 200) {
        throw new DetailFailure(detail.status() == 401 ? 401 : 502);
      }
      details.put(id, detail.body());
    }
    return details;
  }

  private static final class DetailFailure extends Exception {
    private final int status;

    private DetailFailure(int status) {
      this.status = status;
    }
  }

  public record Capture(int status, DailyStat stat) {
    static Capture saved(DailyStat stat) {
      return new Capture(200, stat);
    }

    static Capture failed(int status) {
      return new Capture(status, null);
    }
  }
}
