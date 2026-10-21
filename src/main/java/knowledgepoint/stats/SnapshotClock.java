package knowledgepoint.stats;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class SnapshotClock {

  public static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
  public static final LocalTime DEADLINE = LocalTime.of(20, 59);

  private SnapshotClock() {
  }

  public static boolean due(ZonedDateTime now) {
    return !now.withZoneSameInstant(MOSCOW).toLocalTime().isBefore(DEADLINE);
  }
}
