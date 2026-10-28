package knowledgepoint.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import knowledgepoint.cabinet.CabinetStudent;
import knowledgepoint.session.SessionStore;
import knowledgepoint.web.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class StatsController {

  private final DailyStatStore stats;
  private final DailySnapshot snapshot;
  private final CabinetStudent students;
  private final ObjectMapper json;

  public StatsController(DailyStatStore stats, DailySnapshot snapshot, CabinetStudent students, ObjectMapper json) {
    this.stats = stats;
    this.snapshot = snapshot;
    this.students = students;
    this.json = json;
  }

  @GetMapping("/api/stats/days")
  public ResponseEntity<?> days(HttpServletRequest request) {
    if (missing(request)) {
      return error(503);
    }
    CabinetStudent.StudentAccess access = students.identify(request.getHeader("Authorization"));
    if (access.status() != 200) {
      return error(access.status());
    }
    List<DayResponse> days = stats.list(access.studentId()).stream().map(this::day).toList();
    return ResponseEntity.ok(days);
  }

  @PostMapping("/api/stats/days")
  public ResponseEntity<?> captureToday(HttpServletRequest request) {
    if (missing(request)) {
      return error(503);
    }
    DailySnapshot.Capture capture = snapshot.captureToday(request.getHeader("Authorization"));
    if (capture.status() != 200 || capture.stat() == null) {
      return error(capture.status());
    }
    return ResponseEntity.ok(day(capture.stat()));
  }

  private static boolean missing(HttpServletRequest request) {
    String token = SessionStore.normalize(request.getHeader("Authorization"));
    return token == null || token.length() < 20;
  }

  private static ResponseEntity<ErrorResponse> error(int status) {
    int http = status == 401 || status == 503 ? status : 502;
    String message = switch (http) {
      case 401 -> "Кабинет отклонил токен";
      case 503 -> "Сохраните токен кабинета";
      default -> "Кабинет не ответил";
    };
    return ResponseEntity.status(http).body(new ErrorResponse(message));
  }

  private DayResponse day(DailyStat stat) {
    JsonNode breakdown = readBreakdown(stat.breakdown());
    Double quality = null;
    JsonNode stored = breakdown == null ? null : breakdown.path("summary").get("testQuality");
    if (stored != null && stored.isNumber()) {
      quality = stored.asDouble();
    }
    return new DayResponse(
        stat.day(),
        new Homework(stat.homeworksAccepted(), stat.homeworksTotal(), stat.homeworksFixRequired()),
        new Count(stat.testsCompleted(), stat.testsTotal()),
        quality,
        breakdown,
        stat.capturedAt());
  }

  private JsonNode readBreakdown(String stored) {
    if (stored == null || stored.isBlank()) {
      return null;
    }
    try {
      return json.readTree(stored);
    } catch (IOException exception) {
      return null;
    }
  }

  public record DayResponse(
      String day,
      Homework homeworks,
      Count tests,
      Double testQuality,
      JsonNode breakdown,
      String capturedAt
  ) {
  }

  public record Count(int completed, int total) {
  }

  public record Homework(int accepted, int total, int fixRequired) {
  }
}
