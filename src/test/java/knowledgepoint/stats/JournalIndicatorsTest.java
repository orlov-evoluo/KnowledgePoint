package knowledgepoint.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    DailyStat stat = JournalIndicators.parse(7, "2026-10-21", Instant.parse("2026-10-21T17:59:00Z"), journal, Map.of(), json);
    JsonNode breakdown = json.readTree(stat.breakdown());

    assertEquals(7, stat.studentId());
    assertEquals(3, stat.homeworksAccepted());
    assertEquals(1, stat.homeworksFixRequired());
    assertEquals(2, stat.testsCompleted());
    assertEquals(3, stat.testsTotal());
    assertTrue(breakdown.path("summary").path("testQuality").isNull());
    assertEquals(0, breakdown.path("courses").size());
  }

  @Test
  void storesCoursesTestsHomeworksAndRevisions() throws Exception {
    String journal = """
        {
          "general": {
            "watches": {"completed": 1, "total": 2},
            "webinars": {"completed": 1, "total": 1},
            "homeworks": {"accepted": 1, "total": 2, "fixRequired": 1}
          },
          "courses": [
            {"id": 5, "courseName": "Алгебра", "needTutor": true, "homeworks": {"accepted": 0, "total": 2, "fixRequired": 1}},
            {"id": 6, "courseName": "Музыка", "needTutor": false, "homeworks": {"accepted": 0, "total": 0, "fixRequired": 0}}
          ],
          "stats": {"courses": [
            {"id": 5, "test": {"completed": 1, "total": 2}},
            {"id": 6, "test": {"completed": 1, "total": 1}}
          ]}
        }
        """;
    String algebra = """
        {
          "course": {"generalStats": [{"type": "test", "subTitle": "В среднем верно 75%"}]},
          "lessons": [
            {"id": 11, "name": "Дроби", "status": "fix_required", "homework": {}, "testKey": "t1", "test": {"successRation": 80, "score": 8}},
            {"id": 12, "name": "Уравнения", "status": "rejected", "homework": {}, "testKey": "t2"},
            {"id": 13, "name": "Без домашки", "homework": null, "isWatched": true}
          ]
        }
        """;
    String music = """
        {
          "course": {"generalStats": [{"type": "test", "subTitle": "В среднем верно 52%"}]},
          "lessons": [
            {"id": 21, "name": "Гаммы", "testKey": "m1", "test": {"successRation": 52, "score": 5}}
          ]
        }
        """;

    DailyStat stat = JournalIndicators.parse(
        7,
        "2026-10-21",
        Instant.parse("2026-10-21T17:59:00Z"),
        journal,
        Map.of(5L, algebra, 6L, music),
        json);
    JsonNode breakdown = json.readTree(stat.breakdown());

    assertEquals(64, breakdown.path("summary").path("testQuality").asInt());
    assertEquals("Алгебра", breakdown.path("courses").get(0).path("name").asText());
    assertEquals(1, breakdown.path("courses").get(0).path("testsCompleted").asInt());
    assertEquals(75, breakdown.path("courses").get(0).path("testQuality").asInt());
    assertFalse(breakdown.path("courses").get(1).path("needTutor").asBoolean());
    assertEquals(3, breakdown.path("tests").size());
    assertEquals(80, breakdown.path("tests").get(0).path("quality").asInt());
    assertFalse(breakdown.path("tests").get(1).path("done").asBoolean());
    assertEquals("fix_required", breakdown.path("homeworks").get(0).path("status").asText());
    assertEquals("rejected", breakdown.path("homeworks").get(1).path("status").asText());
    assertEquals("self", breakdown.path("homeworks").get(2).path("status").asText());
    assertEquals(1, breakdown.path("revisions").size());
    assertEquals(11, breakdown.path("revisions").get(0).path("lessonId").asInt());
    assertFalse(stat.breakdown().contains("isWatched"));
  }

  @Test
  void rejectsAJournalWithoutCounters() {
    assertThrows(IllegalArgumentException.class, () ->
        JournalIndicators.parse(7, "2026-10-21", Instant.EPOCH, "{}", Map.of(), json));
  }

  @Test
  void rejectsACourseWithoutDetails() {
    String journal = """
        {
          "general": {
            "watches": {"completed": 1, "total": 1},
            "webinars": {"completed": 1, "total": 1},
            "homeworks": {"accepted": 0, "total": 1, "fixRequired": 0}
          },
          "courses": [{"id": 5, "courseName": "Алгебра", "needTutor": true}]
        }
        """;
    assertThrows(IllegalArgumentException.class, () ->
        JournalIndicators.parse(7, "2026-10-21", Instant.EPOCH, journal, Map.of(), json));
  }
}
