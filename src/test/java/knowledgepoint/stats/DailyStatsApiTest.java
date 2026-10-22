package knowledgepoint.stats;

import knowledgepoint.cabinet.CabinetClient;
import knowledgepoint.cabinet.CabinetResponse;
import knowledgepoint.cabinet.CabinetStudent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DailyStatsApiTest {

  private static final Path DIR = createDir();
  private static final String TOKEN = "Bearer dummy-token-value-not-real-123456";

  @Autowired
  private MockMvc mvc;

  @Autowired
  private DailyStatStore stats;

  @Autowired
  private JdbcTemplate jdbc;

  @MockitoBean
  private CabinetStudent students;

  @MockitoBean
  private CabinetClient cabinet;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    String database = DIR.resolve("session.sqlite").toAbsolutePath().toString().replace('\\', '/');
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + database);
    registry.add("knowledgepoint.page", () -> DIR.resolve("index.html").toString());
    registry.add("TOCHKA_TOKEN", () -> "");
  }

  @BeforeEach
  void empty() {
    jdbc.update("DELETE FROM daily_stat");
    jdbc.update("DELETE FROM session");
  }

  @Test
  void listsOnlyTheStudentIdentifiedByTheBrowserToken() throws Exception {
    mvc.perform(get("/api/stats/days"))
        .andExpect(status().isServiceUnavailable());

    when(students.identify(anyString())).thenReturn(new CabinetStudent.StudentAccess(401, 0));
    mvc.perform(get("/api/stats/days").header("Authorization", TOKEN))
        .andExpect(status().isUnauthorized());

    when(students.identify(anyString())).thenReturn(new CabinetStudent.StudentAccess(200, 7));
    stats.save(new DailyStat(7, "2026-10-21", 10, 12, 4, 6, 3, 8, 1, 2, 3, "2026-10-21T17:59:00Z", null));
    stats.save(new DailyStat(8, "2026-10-21", 1, 1, 1, 1, 1, 1, 1, 1, 1, "2026-10-21T17:59:00Z", null));

    mvc.perform(get("/api/stats/days").header("Authorization", TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].day").value("2026-10-21"))
        .andExpect(jsonPath("$[0].homeworks.accepted").value(3))
        .andExpect(jsonPath("$[0].homeworks.fixRequired").value(1))
        .andExpect(jsonPath("$[0].tests.completed").value(2))
        .andExpect(jsonPath("$[0].tests.total").value(3))
        .andExpect(jsonPath("$[0].watches").doesNotExist())
        .andExpect(jsonPath("$[0].studentId").doesNotExist())
        .andExpect(jsonPath("$[0].token").doesNotExist());
  }

  @Test
  void savesAndUpdatesTodayForThatStudent() throws Exception {
    when(students.identify(anyString())).thenReturn(new CabinetStudent.StudentAccess(200, 7));
    when(cabinet.get(eq("/journal/7/active"), anyString())).thenReturn(new CabinetResponse(200, """
        {
          "general": {
            "watches": {"completed": 1, "total": 2},
            "webinars": {"completed": 1, "total": 1},
            "homeworks": {"accepted": 0, "total": 2, "fixRequired": 1}
          },
          "courses": [
            {"id": 5, "courseName": "Алгебра", "needTutor": true, "homeworks": {"accepted": 0, "total": 2, "fixRequired": 1}}
          ],
          "stats": {"courses": [{"id": 5, "test": {"completed": 1, "total": 2}}]}
        }
        """));
    when(cabinet.get(eq("/journal/7/detail/5"), anyString())).thenReturn(new CabinetResponse(200, """
        {
          "course": {"generalStats": [{"type": "test", "subTitle": "В среднем верно 75%"}]},
          "lessons": [
            {"id": 11, "name": "Дроби", "status": "fix_required", "homework": {}, "testKey": "t1", "test": {"successRation": 80, "score": 8}}
          ]
        }
        """));

    String today = LocalDate.now(SnapshotClock.MOSCOW).toString();
    mvc.perform(post("/api/stats/days").header("Authorization", TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.day").value(today))
        .andExpect(jsonPath("$.tests.completed").value(1))
        .andExpect(jsonPath("$.testQuality").value(75))
        .andExpect(jsonPath("$.watches").doesNotExist())
        .andExpect(jsonPath("$.breakdown.courses[0].name").value("Алгебра"))
        .andExpect(jsonPath("$.breakdown.tests[0].quality").value(80))
        .andExpect(jsonPath("$.breakdown.homeworks[0].status").value("fix_required"))
        .andExpect(jsonPath("$.breakdown.revisions[0].lessonId").value(11));

    when(cabinet.get(eq("/journal/7/active"), anyString())).thenReturn(new CabinetResponse(200, """
        {
          "general": {
            "watches": {"completed": 2, "total": 2},
            "webinars": {"completed": 1, "total": 1},
            "homeworks": {"accepted": 1, "total": 2, "fixRequired": 0}
          },
          "courses": [
            {"id": 5, "courseName": "Алгебра", "needTutor": true, "homeworks": {"accepted": 1, "total": 2, "fixRequired": 0}}
          ],
          "stats": {"courses": [{"id": 5, "test": {"completed": 2, "total": 2}}]}
        }
        """));
    when(cabinet.get(eq("/journal/7/detail/5"), anyString())).thenReturn(new CabinetResponse(200, """
        {
          "course": {"generalStats": [{"type": "test", "subTitle": "В среднем верно 80%"}]},
          "lessons": [
            {"id": 11, "name": "Дроби", "status": "accepted", "homework": {}, "testKey": "t1", "test": {"successRation": 90, "score": 9}}
          ]
        }
        """));
    mvc.perform(post("/api/stats/days").header("Authorization", TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tests.completed").value(2))
        .andExpect(jsonPath("$.homeworks.accepted").value(1))
        .andExpect(jsonPath("$.breakdown.revisions.length()").value(0));

    mvc.perform(get("/api/stats/days").header("Authorization", TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].tests.completed").value(2))
        .andExpect(jsonPath("$[0].breakdown.tests[0].quality").value(90));
  }

  @Test
  void refreshedTokenKeepsTheSameStudentHistory() throws Exception {
    String journal = """
        {
          "general": {
            "watches": {"completed": 1, "total": 2},
            "webinars": {"completed": 1, "total": 1},
            "homeworks": {"accepted": 1, "total": 2, "fixRequired": 0}
          },
          "stats": {"courses": [{"test": {"completed": 4, "total": 5}}]}
        }
        """;
    when(students.identify("Bearer old-token-value-not-real-123456")).thenReturn(new CabinetStudent.StudentAccess(200, 7));
    when(students.identify("Bearer new-token-value-not-real-654321")).thenReturn(new CabinetStudent.StudentAccess(200, 7));
    when(students.identify("Bearer other-student-token-value-888888")).thenReturn(new CabinetStudent.StudentAccess(200, 9));
    when(cabinet.get(eq("/journal/7/active"), anyString())).thenReturn(new CabinetResponse(200, journal));
    stats.save(new DailyStat(9, "2026-10-20", 1, 1, 1, 1, 1, 1, 1, 9, 9, "2026-10-20T17:59:00Z", null));

    mvc.perform(post("/api/stats/days").header("Authorization", "Bearer new-token-value-not-real-654321"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tests.completed").value(4));

    mvc.perform(get("/api/stats/days").header("Authorization", "Bearer old-token-value-not-real-123456"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].tests.completed").value(4));

    mvc.perform(get("/api/stats/days").header("Authorization", "Bearer other-student-token-value-888888"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].tests.completed").value(9));
  }

  private static Path createDir() {
    try {
      Path dir = Files.createTempDirectory("knowledge-point-stats");
      Files.writeString(dir.resolve("index.html"), "<!doctype html><title>Успеваемость</title>");
      Files.writeString(dir.resolve("analytics.html"), "<!doctype html><title>Динамика</title>");
      return dir;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
