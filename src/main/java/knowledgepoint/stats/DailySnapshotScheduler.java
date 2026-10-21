package knowledgepoint.stats;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Component
public class DailySnapshotScheduler {

  private final DailySnapshot snapshot;
  private final DailyStatStore stats;

  public DailySnapshotScheduler(DailySnapshot snapshot, DailyStatStore stats) {
    this.snapshot = snapshot;
    this.stats = stats;
  }

  @Scheduled(cron = "0 59 20 * * *", zone = "Europe/Moscow")
  public void atDeadline() {
    snapshot.capture(LocalDate.now(SnapshotClock.MOSCOW));
  }

  @EventListener(ApplicationReadyEvent.class)
  public void catchUp() {
    ZonedDateTime now = ZonedDateTime.now(SnapshotClock.MOSCOW);
    if (!SnapshotClock.due(now)) {
      return;
    }
    LocalDate today = now.toLocalDate();
    if (stats.exists(today.toString())) {
      return;
    }
    snapshot.capture(today);
  }
}
