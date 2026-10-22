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
    if (tableExists("daily_stat") && !columnExists("daily_stat", "student_id")) {
      jdbc.execute("ALTER TABLE daily_stat RENAME TO daily_stat_legacy");
    }
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS daily_stat (
          student_id INTEGER NOT NULL,
          day TEXT NOT NULL,
          watches_completed INTEGER NOT NULL,
          watches_total INTEGER NOT NULL,
          webinars_completed INTEGER NOT NULL,
          webinars_total INTEGER NOT NULL,
          homeworks_accepted INTEGER NOT NULL,
          homeworks_total INTEGER NOT NULL,
          homeworks_fix_required INTEGER NOT NULL,
          tests_completed INTEGER NOT NULL,
          tests_total INTEGER NOT NULL,
          captured_at TEXT NOT NULL,
          breakdown TEXT,
          PRIMARY KEY (student_id, day)
        )
        """);
    if (!columnExists("daily_stat", "breakdown")) {
      jdbc.execute("ALTER TABLE daily_stat ADD COLUMN breakdown TEXT");
    }
  }

  public void adoptLegacy(long studentId) {
    if (!tableExists("daily_stat_legacy")) {
      return;
    }
    Integer own = jdbc.queryForObject(
        "SELECT COUNT(*) FROM daily_stat WHERE student_id = ?",
        Integer.class,
        studentId);
    if (own == null || own == 0) {
      jdbc.update("""
          INSERT INTO daily_stat (
            student_id, day, watches_completed, watches_total, webinars_completed, webinars_total,
            homeworks_accepted, homeworks_total, homeworks_fix_required,
            tests_completed, tests_total, captured_at
          )
          SELECT ?, day, watches_completed, watches_total, webinars_completed, webinars_total,
                 homeworks_accepted, homeworks_total, homeworks_fix_required,
                 tests_completed, tests_total, captured_at
          FROM daily_stat_legacy
          """, studentId);
    }
    jdbc.execute("DROP TABLE daily_stat_legacy");
  }

  public void save(DailyStat stat) {
    jdbc.update("""
        INSERT INTO daily_stat (
          student_id, day, watches_completed, watches_total, webinars_completed, webinars_total,
          homeworks_accepted, homeworks_total, homeworks_fix_required,
          tests_completed, tests_total, captured_at, breakdown
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(student_id, day) DO UPDATE SET
          watches_completed = excluded.watches_completed,
          watches_total = excluded.watches_total,
          webinars_completed = excluded.webinars_completed,
          webinars_total = excluded.webinars_total,
          homeworks_accepted = excluded.homeworks_accepted,
          homeworks_total = excluded.homeworks_total,
          homeworks_fix_required = excluded.homeworks_fix_required,
          tests_completed = excluded.tests_completed,
          tests_total = excluded.tests_total,
          captured_at = excluded.captured_at,
          breakdown = excluded.breakdown
        """,
        stat.studentId(),
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
        stat.capturedAt(),
        stat.breakdown());
  }

  public List<DailyStat> list(long studentId) {
    return jdbc.query("""
        SELECT student_id, day, watches_completed, watches_total, webinars_completed, webinars_total,
               homeworks_accepted, homeworks_total, homeworks_fix_required,
               tests_completed, tests_total, captured_at, breakdown
        FROM daily_stat
        WHERE student_id = ?
        ORDER BY day
        """, (rs, row) -> new DailyStat(
        rs.getLong("student_id"),
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
        rs.getString("captured_at"),
        rs.getString("breakdown")), studentId);
  }

  private boolean tableExists(String name) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
        Integer.class,
        name);
    return count != null && count > 0;
  }

  private boolean columnExists(String table, String column) {
    List<String> names = jdbc.query("PRAGMA table_info(" + table + ")", (rs, row) -> rs.getString("name"));
    return names.contains(column);
  }
}
