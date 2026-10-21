package knowledgepoint.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DailyStatsApiTest {

  private static final Path DIR = createDir();

  @Autowired
  private MockMvc mvc;

  @Autowired
  private DailyStatStore stats;

  @Autowired
  private JdbcTemplate jdbc;

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
  }

  @Test
  void listsSavedDaysWithoutAToken() throws Exception {
    mvc.perform(get("/api/stats/days"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    stats.save(new DailyStat("2026-10-21", 10, 12, 4, 6, 3, 8, 1, 2, 3, "2026-10-21T17:59:00Z"));

    mvc.perform(get("/api/stats/days"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].day").value("2026-10-21"))
        .andExpect(jsonPath("$[0].watches.completed").value(10))
        .andExpect(jsonPath("$[0].watches.total").value(12))
        .andExpect(jsonPath("$[0].webinars.completed").value(4))
        .andExpect(jsonPath("$[0].homeworks.accepted").value(3))
        .andExpect(jsonPath("$[0].homeworks.fixRequired").value(1))
        .andExpect(jsonPath("$[0].tests.completed").value(2))
        .andExpect(jsonPath("$[0].tests.total").value(3))
        .andExpect(jsonPath("$[0].token").doesNotExist());
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
