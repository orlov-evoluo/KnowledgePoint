package knowledgepoint.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JournalIndicators {

  private static final Pattern QUALITY = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*%");

  private JournalIndicators() {
  }

  public static DailyStat parse(
      long studentId,
      String day,
      Instant capturedAt,
      String journalJson,
      Map<Long, String> details,
      ObjectMapper json
  ) throws IOException {
    JsonNode journal = json.readTree(journalJson);
    Map<Long, JsonNode> rich = new HashMap<>();
    JsonNode richCourses = journal.path("stats").path("courses");

    int testsCompleted = 0;
    int testsTotal = 0;
    if (richCourses.isArray()) {
      for (JsonNode course : richCourses) {
        long id = course.path("id").asLong(0);
        if (id > 0) {
          rich.put(id, course);
        }
        JsonNode test = course.get("test");
        if (test == null || test.isNull()) {
          continue;
        }
        testsCompleted += test.path("completed").asInt(0);
        testsTotal += test.path("total").asInt(0);
      }
    }

    ArrayNode courses = json.createArrayNode();
    ArrayNode tests = json.createArrayNode();
    ArrayNode homeworks = json.createArrayNode();
    ArrayNode revisions = json.createArrayNode();
    long qualityWeight = 0;
    double qualitySum = 0;
    boolean qualityPending = false;

    JsonNode listed = journal.path("courses");
    if (listed.isArray()) {
      for (JsonNode course : listed) {
        long id = course.path("id").asLong(0);
        if (id <= 0) {
          continue;
        }
        String detailJson = details.get(id);
        if (detailJson == null) {
          throw new IllegalArgumentException("нет подробностей курса");
        }
        JsonNode detail = json.readTree(detailJson);
        JsonNode stats = rich.get(id);
        JsonNode test = stats == null ? null : stats.get("test");
        int courseTestsCompleted = test == null || test.isNull() ? 0 : test.path("completed").asInt(0);
        int courseTestsTotal = test == null || test.isNull() ? 0 : test.path("total").asInt(0);
        Double quality = quality(detail);
        boolean needTutor = course.has("needTutor")
            ? course.path("needTutor").asBoolean(true)
            : detail.path("course").path("needTutor").asBoolean(true);
        JsonNode homework = course.path("homeworks");

        ObjectNode courseNode = courses.addObject();
        courseNode.put("id", id);
        courseNode.put("name", text(course, "courseName", text(detail.path("course"), "courseName", "Курс")));
        courseNode.put("needTutor", needTutor);
        courseNode.put("testsCompleted", courseTestsCompleted);
        courseNode.put("testsTotal", courseTestsTotal);
        if (quality == null) {
          courseNode.putNull("testQuality");
        } else {
          courseNode.put("testQuality", quality);
        }
        courseNode.put("homeworksAccepted", homework.path("accepted").asInt(0));
        courseNode.put("homeworksTotal", homework.path("total").asInt(0));
        courseNode.put("fixRequired", homework.path("fixRequired").asInt(0));

        if (courseTestsTotal > 0 && quality == null) {
          qualityPending = true;
        }
        if (quality != null && courseTestsCompleted > 0) {
          qualitySum += quality * courseTestsCompleted;
          qualityWeight += courseTestsCompleted;
        }

        JsonNode lessons = detail.path("lessons");
        if (!lessons.isArray()) {
          continue;
        }
        for (JsonNode lesson : lessons) {
          long lessonId = lesson.path("id").asLong(0);
          if (lessonId <= 0) {
            continue;
          }
          String name = text(lesson, "name", "Урок");
          addTest(tests, lesson, id, lessonId, name);
          String status = homeworkStatus(lesson, needTutor);
          if (status == null) {
            continue;
          }
          ObjectNode work = homeworks.addObject();
          work.put("courseId", id);
          work.put("lessonId", lessonId);
          work.put("name", name);
          work.put("status", status);
          if ("fix_required".equals(status)) {
            ObjectNode revision = revisions.addObject();
            revision.put("courseId", id);
            revision.put("lessonId", lessonId);
            revision.put("name", name);
          }
        }
      }
    }

    ObjectNode summary = json.createObjectNode();
    summary.put("testsCompleted", testsCompleted);
    summary.put("testsTotal", testsTotal);
    if (qualityPending || qualityWeight == 0) {
      summary.putNull("testQuality");
    } else {
      summary.put("testQuality", round2(qualitySum / qualityWeight));
    }
    summary.put("homeworksAccepted", number(journal, "general.homeworks.accepted"));
    summary.put("homeworksTotal", number(journal, "general.homeworks.total"));
    summary.put("fixRequired", number(journal, "general.homeworks.fixRequired"));

    ObjectNode breakdown = json.createObjectNode();
    breakdown.set("summary", summary);
    breakdown.set("courses", courses);
    breakdown.set("tests", tests);
    breakdown.set("homeworks", homeworks);
    breakdown.set("revisions", revisions);

    return new DailyStat(
        studentId,
        day,
        number(journal, "general.watches.completed"),
        number(journal, "general.watches.total"),
        number(journal, "general.webinars.completed"),
        number(journal, "general.webinars.total"),
        summary.path("homeworksAccepted").asInt(),
        summary.path("homeworksTotal").asInt(),
        summary.path("fixRequired").asInt(),
        testsCompleted,
        testsTotal,
        capturedAt.toString(),
        json.writeValueAsString(breakdown));
  }

  private static void addTest(ArrayNode tests, JsonNode lesson, long courseId, long lessonId, String name) {
    JsonNode test = lesson.get("test");
    String key = lesson.path("testKey").asText("");
    boolean hasTest = !key.isBlank() || (test != null && !test.isNull());
    if (!hasTest) {
      return;
    }
    Double quality = null;
    boolean done = false;
    if (test != null && !test.isNull()) {
      JsonNode ratio = test.get("successRation");
      if (ratio != null && ratio.isNumber()) {
        quality = round2(ratio.asDouble());
        done = true;
      }
      JsonNode score = test.get("score");
      if (score != null && !score.isNull()) {
        done = true;
      }
    }
    ObjectNode node = tests.addObject();
    node.put("courseId", courseId);
    node.put("lessonId", lessonId);
    node.put("name", name);
    node.put("done", done);
    if (quality == null) {
      node.putNull("quality");
    } else {
      node.put("quality", quality);
    }
  }

  private static String homeworkStatus(JsonNode lesson, boolean needTutor) {
    if (!needTutor) {
      return "self";
    }
    JsonNode homework = lesson.get("homework");
    if (homework == null || homework.isNull()) {
      return null;
    }
    String status = lesson.path("status").asText("");
    return switch (status) {
      case "accepted", "rejected", "fix_required" -> status;
      default -> null;
    };
  }

  private static Double quality(JsonNode detail) {
    JsonNode stats = detail.path("course").path("generalStats");
    if (!stats.isArray()) {
      return null;
    }
    for (JsonNode item : stats) {
      if (!"test".equals(item.path("type").asText())) {
        continue;
      }
      Matcher matcher = QUALITY.matcher(item.path("subTitle").asText(""));
      if (matcher.find()) {
        return round2(Double.parseDouble(matcher.group(1).replace(',', '.')));
      }
    }
    return null;
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private static String text(JsonNode node, String field, String fallback) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return fallback;
    }
    String text = value.asText("").trim();
    return text.isEmpty() ? fallback : text;
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
