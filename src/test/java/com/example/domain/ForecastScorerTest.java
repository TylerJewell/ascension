package com.example.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForecastScorerTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

  private static Signal signal(String id, SignalKind kind, SourceTier tier, Influence influence) {
    return new Signal(
        id, "src-" + id, tier, "https://example.test/" + id, NOW, kind, "summary", "excerpt", influence, false);
  }

  @Test
  void producesNoForecastWhenThereIsNoEvidence() {
    assertThat(ForecastScorer.score(List.of(), 0, NOW)).isEmpty();
  }

  @Test
  void producesNoForecastWhenEverySignalHasBeenDismissed() {
    var dismissed = signal("s1", SignalKind.VENUE_HOLD, SourceTier.A, Influence.STRONG_POSITIVE).asDismissed();

    assertThat(ForecastScorer.score(List.of(dismissed), 0, NOW)).isEmpty();
  }

  @Test
  void raisesLikelihoodForSignalsThatSuggestAVisit() {
    var hold = signal("s1", SignalKind.VENUE_HOLD, SourceTier.A, Influence.STRONG_POSITIVE);

    var forecast = ForecastScorer.score(List.of(hold), 0, NOW).orElseThrow();

    assertThat(forecast.likelihood()).isGreaterThan(ForecastScorer.PRIOR);
  }

  @Test
  void lowersLikelihoodForSignalsThatArgueAgainstAVisit() {
    var hiatus = signal("s1", SignalKind.HIATUS_DECLARED, SourceTier.A, Influence.STRONG_NEGATIVE);
    var teaser = signal("s2", SignalKind.OFFICIAL_TEASER, SourceTier.A, Influence.STRONG_POSITIVE);

    var withHiatus = ForecastScorer.score(List.of(teaser, hiatus), 0, NOW).orElseThrow();
    var withoutHiatus = ForecastScorer.score(List.of(teaser), 0, NOW).orElseThrow();

    assertThat(withHiatus.likelihood()).isLessThan(withoutHiatus.likelihood());
  }

  @Test
  void weighsALessReliableSourceLessHeavily() {
    var official = signal("s1", SignalKind.VENUE_HOLD, SourceTier.A, Influence.STRONG_POSITIVE);
    var rumour = signal("s1", SignalKind.VENUE_HOLD, SourceTier.C, Influence.STRONG_POSITIVE);

    var fromOfficial = ForecastScorer.score(List.of(official), 0, NOW).orElseThrow();
    var fromRumour = ForecastScorer.score(List.of(rumour), 0, NOW).orElseThrow();

    assertThat(fromRumour.likelihood()).isLessThan(fromOfficial.likelihood());
  }

  @Test
  void excludesDismissedSignalsFromTheScoreAndTheCitations() {
    var kept = signal("s1", SignalKind.VENUE_HOLD, SourceTier.A, Influence.STRONG_POSITIVE);
    var dropped = signal("s2", SignalKind.OFFICIAL_TEASER, SourceTier.A, Influence.STRONG_POSITIVE).asDismissed();

    var forecast = ForecastScorer.score(List.of(kept, dropped), 0, NOW).orElseThrow();

    assertThat(forecast.citedSignalIds()).containsExactly("s1");
  }

  @Test
  void clampsLikelihoodToTheZeroHundredRange() {
    var announcements = List.of(
        signal("s1", SignalKind.OFFICIAL_ANNOUNCEMENT, SourceTier.A, Influence.STRONG_POSITIVE),
        signal("s2", SignalKind.OFFICIAL_ANNOUNCEMENT, SourceTier.A, Influence.STRONG_POSITIVE));

    var forecast = ForecastScorer.score(announcements, 0, NOW).orElseThrow();

    assertThat(forecast.likelihood()).isEqualTo(100);
  }

  @Test
  void lowersConfidenceForEachSourceThatHasGoneDark() {
    var signals = List.of(
        signal("s1", SignalKind.VENUE_HOLD, SourceTier.A, Influence.POSITIVE),
        signal("s2", SignalKind.CADENCE_ELAPSED, SourceTier.A, Influence.POSITIVE),
        signal("s3", SignalKind.RELEASE_CYCLE_ACTIVE, SourceTier.A, Influence.POSITIVE));

    assertThat(ForecastScorer.score(signals, 0, NOW).orElseThrow().confidence())
        .isEqualTo(ConfidenceBand.HIGH);
    assertThat(ForecastScorer.score(signals, 1, NOW).orElseThrow().confidence())
        .isEqualTo(ConfidenceBand.MEDIUM);
    assertThat(ForecastScorer.score(signals, 2, NOW).orElseThrow().confidence())
        .isEqualTo(ConfidenceBand.LOW);
  }

  @Test
  void narrowsTheAnnouncementWindowAsLikelihoodRises() {
    var weak = signal("s1", SignalKind.RELEASE_CYCLE_ACTIVE, SourceTier.C, Influence.POSITIVE);
    var strong = signal("s1", SignalKind.OFFICIAL_TEASER, SourceTier.A, Influence.STRONG_POSITIVE);

    var weakForecast = ForecastScorer.score(List.of(weak), 0, NOW).orElseThrow();
    var strongForecast = ForecastScorer.score(List.of(strong), 0, NOW).orElseThrow();

    assertThat(strongForecast.windowEnd()).isBefore(weakForecast.windowEnd());
  }

  @Test
  void scoresTheSameEvidenceIdenticallyOnEveryReplay() {
    var signals = List.of(
        signal("s1", SignalKind.VENUE_HOLD, SourceTier.A, Influence.POSITIVE),
        signal("s2", SignalKind.ROUTING_GAP_ADJACENT, SourceTier.B, Influence.STRONG_POSITIVE));

    var first = ForecastScorer.score(signals, 0, NOW).orElseThrow();
    var second = ForecastScorer.score(signals, 0, NOW).orElseThrow();

    assertThat(second).isEqualTo(first);
  }

  @Test
  void labelsTheConfidenceBandItBelongsTo() {
    var forecast = ForecastScorer.score(
            List.of(signal("s1", SignalKind.OFFICIAL_ANNOUNCEMENT, SourceTier.A, Influence.STRONG_POSITIVE)),
            0,
            NOW)
        .orElseThrow();

    assertThat(forecast.confidenceBandLabel()).isEqualTo("90-99");
  }
}
