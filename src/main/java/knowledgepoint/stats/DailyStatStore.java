package knowledgepoint.stats;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DailyStatStore {

  private final JdbcTemplate jdbc;

  public DailyStatStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  void createTable() {
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS daily_stat (
          day TEXT PRIMARY KEY,
          watches_completed INTEGER NOT NULL,
          watches_total INTEGER NOT NULL,
          webinars_completed INTEGER NOT NULL,
          webinars_total INTEGER NOT NULL,
          homeworks_accepted INTEGER NOT NULL,
          homeworks_total INTEGER NOT NULL,
          homeworks_fix_required INTEGER NOT NULL,
          tests_completed INTEGER NOT NULL,
          tests_total INTEGER NOT NULL,
          captured_at TEXT NOT NULL
        )
        """);
  }

  public boolean exists(String day) {
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM daily_stat WHERE day = ?", Integer.class, day);
    return count != null && count > 0;
  }

  public void save(DailyStat stat) {
    jdbc.update("""
        INSERT INTO daily_stat (
          day, watches_completed, watches_total, webinars_completed, webinars_total,
          homeworks_accepted, homeworks_total, homeworks_fix_required,
          tests_completed, tests_total, captured_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(day) DO UPDATE SET
          watches_completed = excluded.watches_completed,
          watches_total = excluded.watches_total,
          webinars_completed = excluded.webinars_completed,
          webinars_total = excluded.webinars_total,
          homeworks_accepted = excluded.homeworks_accepted,
          homeworks_total = excluded.homeworks_total,
          homeworks_fix_required = excluded.homeworks_fix_required,
          tests_completed = excluded.tests_completed,
          tests_total = excluded.tests_total,
          captured_at = excluded.captured_at
        """,
        stat.day(),
        stat.watchesCompleted(),
        stat.watchesTotal(),
        stat.webinarsCompleted(),
        stat.webinarsTotal(),
        stat.homeworksAccepted(),
        stat.homeworksTotal(),
        stat.homeworksFixRequired(),
        stat.testsCompleted(),
        stat.testsTotal(),
        stat.capturedAt());
  }

  public List<DailyStat> list() {
    return jdbc.query("""
        SELECT day, watches_completed, watches_total, webinars_completed, webinars_total,
               homeworks_accepted, homeworks_total, homeworks_fix_required,
               tests_completed, tests_total, captured_at
        FROM daily_stat
        ORDER BY day
        """, (rs, row) -> new DailyStat(
        rs.getString("day"),
        rs.getInt("watches_completed"),
        rs.getInt("watches_total"),
        rs.getInt("webinars_completed"),
        rs.getInt("webinars_total"),
        rs.getInt("homeworks_accepted"),
        rs.getInt("homeworks_total"),
        rs.getInt("homeworks_fix_required"),
        rs.getInt("tests_completed"),
        rs.getInt("tests_total"),
        rs.getString("captured_at")));
  }
}
