package com.example.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.domain.Alert;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The no-duplicate-alerts rule (FR-014) and the time-remaining rule (FR-013).
 *
 * <p>Both matter at three in the morning: the first is why a source that keeps reporting the same
 * announcement does not wake a fan repeatedly, and the second is why an alert read hours later
 * still says how long is left rather than how long was left.
 */
class AlertDedupeTest {

  private static Alert confirmation(String visitId, String facts) {
    return new Alert(
        "metallica:chicago",
        Alert.AlertKind.CONFIRMATION,
        visitId,
        "Metallica — Soldier Field, Chicago",
        facts,
        Instant.parse("2026-09-01T15:00:00Z"),
        "https://example.test/announcement");
  }

  @Test
  void claimsAnAlertOnceAndRefusesTheSameOneAfterwards() {
    var testKit = KeyValueEntityTestKit.of("metallica:chicago", AlertLedgerEntity::new);
    var alert = confirmation("v-1", "[2026-11-07]|ANNOUNCED");

    var first = testKit.method(AlertLedgerEntity::claim).invoke(alert.fingerprint()).getReply();
    var second = testKit.method(AlertLedgerEntity::claim).invoke(alert.fingerprint()).getReply();

    assertThat(first).isTrue();
    assertThat(second).isFalse();
  }

  @Test
  void allowsAnAlertThroughWhenTheUnderlyingFactsHaveChanged() {
    var testKit = KeyValueEntityTestKit.of("metallica:chicago", AlertLedgerEntity::new);

    testKit.method(AlertLedgerEntity::claim)
        .invoke(confirmation("v-1", "[2026-11-07]|ANNOUNCED").fingerprint());
    var afterChange = testKit.method(AlertLedgerEntity::claim)
        .invoke(confirmation("v-1", "[2026-11-07]|POSTPONED").fingerprint())
        .getReply();

    assertThat(afterChange).isTrue();
  }

  @Test
  void distinguishesAlertsAboutDifferentSubjects() {
    var testKit = KeyValueEntityTestKit.of("metallica:chicago", AlertLedgerEntity::new);

    testKit.method(AlertLedgerEntity::claim).invoke(confirmation("v-1", "same").fingerprint());
    var otherVisit = testKit.method(AlertLedgerEntity::claim)
        .invoke(confirmation("v-2", "same").fingerprint())
        .getReply();

    assertThat(otherVisit).isTrue();
  }

  @Test
  void computesTimeRemainingAtTheMomentOfDeliveryNotWhenTheEventHappened() {
    var alert = confirmation("v-1", "facts");

    var earlier = alert.timeToAct(Instant.parse("2026-09-01T09:00:00Z"));
    var later = alert.timeToAct(Instant.parse("2026-09-01T14:00:00Z"));

    assertThat(earlier).isEqualTo(Duration.ofHours(6));
    assertThat(later).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void reportsNoTimeRemainingOnceTheDeadlineHasPassed() {
    var alert = confirmation("v-1", "facts");

    assertThat(alert.timeToAct(Instant.parse("2026-09-02T00:00:00Z"))).isZero();
  }

  @Test
  void reportsNoTimeRemainingForAnAlertWithNoDeadline() {
    var forecastAlert = new Alert(
        "metallica:chicago", Alert.AlertKind.FORECAST_THRESHOLD, "fc-1",
        "Forecast: 72% likely", "72", null, "");

    assertThat(forecastAlert.timeToAct(Instant.now())).isZero();
  }
}
