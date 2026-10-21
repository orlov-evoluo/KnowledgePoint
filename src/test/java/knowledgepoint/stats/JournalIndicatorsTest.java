package knowledgepoint.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JournalIndicatorsTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void readsTheMainCounters() throws Exception {
    String journal = """
        {
          "general": {
            "watches": {"completed": 10, "total": 12},
            "webinars": {"completed": 4, "total": 6},
            "homeworks": {"accepted": 3, "total": 8, "fixRequired": 1}
          },
          "stats": {"courses": [
            {"test": {"completed": 2, "total": 3}},
            {"test": null}
          ]}
        }
        """;

    DailyStat stat = JournalIndicators.parse("2026-10-21", Instant.parse("2026-10-21T17:59:00Z"), journal, json);

    assertEquals(10, stat.watchesCompleted());
    assertEquals(12, stat.watchesTotal());
    assertEquals(4, stat.webinarsCompleted());
    assertEquals(3, stat.homeworksAccepted());
    assertEquals(1, stat.homeworksFixRequired());
    assertEquals(2, stat.testsCompleted());
    assertEquals(3, stat.testsTotal());
  }

  @Test
  void rejectsAJournalWithoutCounters() {
    assertThrows(IllegalArgumentException.class, () ->
        JournalIndicators.parse("2026-10-21", Instant.EPOCH, "{}", json));
  }
}
