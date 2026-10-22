package knowledgepoint.stats;

public record DailyStat(
    long studentId,
    String day,
    int watchesCompleted,
    int watchesTotal,
    int webinarsCompleted,
    int webinarsTotal,
    int homeworksAccepted,
    int homeworksTotal,
    int homeworksFixRequired,
    int testsCompleted,
    int testsTotal,
    String capturedAt,
    String breakdown
) {
}
