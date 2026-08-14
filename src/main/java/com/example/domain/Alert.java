package com.example.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Something worth waking a fan up for.
 *
 * <p>{@code actByUtc} is the deadline the alert is about — an onsale time, a presale close. Time
 * remaining is derived from it at delivery rather than stored, because an alert read six hours
 * after it fired must still say how long is left, not how long was left.
 */
public record Alert(
    String watchId,
    AlertKind kind,
    String subjectId,
    String headline,
    String materialFacts,
    Instant actByUtc,
    String evidenceUrl) {

  public enum AlertKind {
    FORECAST_THRESHOLD,
    CONFIRMATION,
    VISIT_CHANGED,
    DEADLINE_REMINDER
  }

  /**
   * Identity for deduplication. Built from the material facts rather than the event, so a
   * redelivery of the same news is suppressed while a genuine change alerts again.
   */
  public String fingerprint() {
    return String.join("|", watchId, kind.name(), subjectId, materialFacts);
  }

  /** Time left to act, computed at the moment of delivery. Zero when the deadline has passed. */
  public Duration timeToAct(Instant deliveredAt) {
    if (actByUtc == null) {
      return Duration.ZERO;
    }
    var remaining = Duration.between(deliveredAt, actByUtc);
    return remaining.isNegative() ? Duration.ZERO : remaining;
  }
}
