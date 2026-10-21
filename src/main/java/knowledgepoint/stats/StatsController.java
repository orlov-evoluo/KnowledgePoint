package knowledgepoint.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StatsController {

  private final DailyStatStore stats;

  public StatsController(DailyStatStore stats) {
    this.stats = stats;
  }

  @GetMapping("/api/stats/days")
  public List<DayResponse> days() {
    return stats.list().stream().map(DayResponse::from).toList();
  }

  public record DayResponse(
      String day,
      Count watches,
      Count webinars,
      Homework homeworks,
      Count tests,
      String capturedAt
  ) {
    static DayResponse from(DailyStat stat) {
      return new DayResponse(
          stat.day(),
          new Count(stat.watchesCompleted(), stat.watchesTotal()),
          new Count(stat.webinarsCompleted(), stat.webinarsTotal()),
          new Homework(stat.homeworksAccepted(), stat.homeworksTotal(), stat.homeworksFixRequired()),
          new Count(stat.testsCompleted(), stat.testsTotal()),
          stat.capturedAt());
    }
  }

  public record Count(int completed, int total) {
  }

  public record Homework(int accepted, int total, int fixRequired) {
  }
}
