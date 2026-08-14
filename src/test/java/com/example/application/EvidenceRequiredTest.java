package com.example.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.example.domain.ArtistRef;
import com.example.domain.Influence;
import com.example.domain.Market;
import com.example.domain.Signal;
import com.example.domain.SignalKind;
import com.example.domain.SourceTier;
import com.example.domain.TourWatchEvent;
import com.example.domain.TourWatchState;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Carries the project exit condition {@code forecast-carries-evidence}.
 *
 * <p>The rule has two halves and both are tested here. The guardrail sees the model's reply but
 * not what was observed, so it can only insist that citations exist. The entity holds the observed
 * signals, so it is what rejects citations naming something that was never seen. Either half alone
 * leaves a way for an unsupported claim to reach a fan.
 */
class EvidenceRequiredTest {

  private static final ArtistRef METALLICA = new ArtistRef("metallica", "Metallica");
  private static final Market CHICAGO = new Market("chicago", "Chicago", 41.8781, -87.6298, 50);

  private static EventSourcedTestKit<TourWatchState, TourWatchEvent, TourWatchEntity> watchWithSignal() {
    var testKit = EventSourcedTestKit.of("metallica:chicago", TourWatchEntity::new);
    testKit.method(TourWatchEntity::register)
        .invoke(new TourWatchEntity.RegisterWatch(METALLICA, CHICAGO, 60, "https://hooks.example.test/s"));
    testKit.method(TourWatchEntity::observeSignal).invoke(new Signal(
        "s1", "src-1", SourceTier.A, "https://example.test/1", Instant.now(),
        SignalKind.VENUE_HOLD, "A stadium hold appeared", "verbatim excerpt",
        Influence.STRONG_POSITIVE, false));
    return testKit;
  }

  // --- The runtime half: a reply with no citations never reaches the entity ---

  @Test
  void guardrailRefusesANarrationThatCitesNothing() {
    var result = new EvidenceGuardrail().evaluate("{\"rationale\":\"It seems likely.\",\"citedSignalIds\":[]}");

    assertThat(result.passed()).isFalse();
  }

  @Test
  void guardrailRefusesANarrationWithNoCitationFieldAtAll() {
    var result = new EvidenceGuardrail().evaluate("{\"rationale\":\"It seems likely.\"}");

    assertThat(result.passed()).isFalse();
  }

  @Test
  void guardrailRefusesAReplyItCannotRead() {
    // Refused rather than waved through: text nobody can check is exactly what this guards against.
    var result = new EvidenceGuardrail().evaluate("Metallica are definitely coming to Chicago.");

    assertThat(result.passed()).isFalse();
  }

  @Test
  void guardrailRefusesABlankCitation() {
    var result = new EvidenceGuardrail().evaluate("{\"rationale\":\"x\",\"citedSignalIds\":[\"  \"]}");

    assertThat(result.passed()).isFalse();
  }

  @Test
  void guardrailAllowsANarrationThatCitesSomething() {
    var result = new EvidenceGuardrail()
        .evaluate("{\"rationale\":\"A stadium hold was posted.\",\"citedSignalIds\":[\"s1\"]}");

    assertThat(result.passed()).isTrue();
  }

  // --- The authoritative half: citations must name signals the watch actually holds ---

  @Test
  void entityRefusesANarrationThatCitesNoSignal() {
    var testKit = watchWithSignal();
    var forecastId = testKit.getState().forecast().orElseThrow().forecastId();

    var result = testKit.method(TourWatchEntity::narrateForecast)
        .invoke(new TourWatchEntity.NarrateForecast(forecastId, "Looks good.", List.of()));

    assertThat(result.isError()).isTrue();
    assertThat(testKit.getState().forecast().orElseThrow().rationale()).isEmpty();
  }

  @Test
  void entityRefusesANarrationCitingASignalThatWasNeverObserved() {
    var testKit = watchWithSignal();
    var forecastId = testKit.getState().forecast().orElseThrow().forecastId();

    var result = testKit.method(TourWatchEntity::narrateForecast)
        .invoke(new TourWatchEntity.NarrateForecast(forecastId, "Sources say so.", List.of("invented")));

    assertThat(result.isError()).isTrue();
  }

  @Test
  void entityRefusesANarrationCitingASignalThatHasBeenDismissed() {
    var testKit = watchWithSignal();
    testKit.method(TourWatchEntity::observeSignal).invoke(new Signal(
        "s2", "src-2", SourceTier.A, "https://example.test/2", Instant.now(),
        SignalKind.OFFICIAL_TEASER, "A teaser", "excerpt", Influence.STRONG_POSITIVE, false));
    testKit.method(TourWatchEntity::dismissSignal)
        .invoke(new TourWatchEntity.DismissSignal("s2", "Teaser was for another city"));
    var forecastId = testKit.getState().forecast().orElseThrow().forecastId();

    var result = testKit.method(TourWatchEntity::narrateForecast)
        .invoke(new TourWatchEntity.NarrateForecast(forecastId, "The teaser says so.", List.of("s2")));

    assertThat(result.isError()).isTrue();
  }

  @Test
  void entityRefusesANarrationForASupersededForecast() {
    var testKit = watchWithSignal();
    var staleForecastId = testKit.getState().forecast().orElseThrow().forecastId();
    testKit.method(TourWatchEntity::observeSignal).invoke(new Signal(
        "s2", "src-2", SourceTier.A, "https://example.test/2", Instant.now(),
        SignalKind.OFFICIAL_TEASER, "A teaser", "excerpt", Influence.STRONG_POSITIVE, false));

    var result = testKit.method(TourWatchEntity::narrateForecast)
        .invoke(new TourWatchEntity.NarrateForecast(staleForecastId, "Still true.", List.of("s1")));

    assertThat(result.isError()).isTrue();
  }

  @Test
  void entityAcceptsANarrationGroundedInObservedSignals() {
    var testKit = watchWithSignal();
    var forecastId = testKit.getState().forecast().orElseThrow().forecastId();

    var result = testKit.method(TourWatchEntity::narrateForecast)
        .invoke(new TourWatchEntity.NarrateForecast(
            forecastId, "A stadium hold matching their production was posted.", List.of("s1")));

    assertThat(result.isError()).isFalse();
    assertThat(testKit.getState().forecast().orElseThrow().rationale()).contains("stadium hold");
  }

  @Test
  void aForecastCanNeverBeConstructedWithoutCitations() {
    // The record itself refuses, so no code path anywhere can assemble an unsupported forecast.
    assertThat(
            org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new com.example.domain.Forecast(
                    "fc-1", 50, java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(30),
                    com.example.domain.ConfidenceBand.LOW, List.of(), "", Instant.now())))
        .hasMessageContaining("cite at least one signal");
  }
}
