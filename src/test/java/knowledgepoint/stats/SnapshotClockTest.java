package knowledgepoint.stats;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotClockTest {

  @Test
  void deadlineIsInclusiveInMoscow() {
    ZonedDateTime before = ZonedDateTime.of(LocalDateTime.of(2026, 10, 21, 20, 58, 59), SnapshotClock.MOSCOW);
    ZonedDateTime at = ZonedDateTime.of(LocalDateTime.of(2026, 10, 21, 20, 59, 0), SnapshotClock.MOSCOW);

    assertFalse(SnapshotClock.due(before));
    assertTrue(SnapshotClock.due(at));
  }
}
