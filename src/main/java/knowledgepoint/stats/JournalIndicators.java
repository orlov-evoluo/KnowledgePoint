package knowledgepoint.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

public final class JournalIndicators {

  private JournalIndicators() {
  }

  public static DailyStat parse(String day, Instant capturedAt, String journalJson, ObjectMapper json) throws IOException {
    JsonNode journal = json.readTree(journalJson);
    int testsCompleted = 0;
    int testsTotal = 0;
    JsonNode courses = journal.path("stats").path("courses");
    if (courses.isArray()) {
      for (JsonNode course : courses) {
        JsonNode test = course.get("test");
        if (test == null || test.isNull()) {
          continue;
        }
        testsCompleted += test.path("completed").asInt(0);
        testsTotal += test.path("total").asInt(0);
      }
    }
    return new DailyStat(
        day,
        number(journal, "general.watches.completed"),
        number(journal, "general.watches.total"),
        number(journal, "general.webinars.completed"),
        number(journal, "general.webinars.total"),
        number(journal, "general.homeworks.accepted"),
        number(journal, "general.homeworks.total"),
        number(journal, "general.homeworks.fixRequired"),
        testsCompleted,
        testsTotal,
        capturedAt.toString());
  }

  private static int number(JsonNode root, String path) {
    JsonNode node = root;
    for (String part : path.split("\\.")) {
      node = node == null ? null : node.get(part);
    }
    if (node == null || !node.isNumber()) {
      throw new IllegalArgumentException("нет поля " + path);
    }
    return node.asInt();
  }
}
