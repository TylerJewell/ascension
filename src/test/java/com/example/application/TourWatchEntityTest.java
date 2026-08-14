package com.example.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.example.domain.ArtistRef;
import com.example.domain.ConfidenceBand;
import com.example.domain.Influence;
import com.example.domain.Market;
import com.example.domain.Signal;
import com.example.domain.SignalKind;
import com.example.domain.SourceTier;
import com.example.domain.TourWatchEvent;
import com.example.domain.TourWatchState;
import com.example.domain.VenueRef;
import com.example.domain.Visit;
import com.example.domain.VisitStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TourWatchEntityTest {

  private static final ArtistRef METALLICA = new ArtistRef("metallica", "Metallica");
  private static final Market CHICAGO = new Market("chicago", "Chicago", 41.8781, -87.6298, 50);
  private static final VenueRef SOLDIER_FIELD = new VenueRef("Soldier Field", "Chicago", 41.8623, -87.6167);

  private static EventSourcedTestKit<TourWatchState, TourWatchEvent, TourWatchEntity> registeredWatch() {
    var testKit = EventSourcedTestKit.of("metallica:chicago", TourWatchEntity::new);
    testKit.method(TourWatchEntity::register)
        .invoke(new TourWatchEntity.RegisterWatch(METALLICA, CHICAGO, 60, "https://hooks.example.test/s"));
    return testKit;
  }

  private static Signal signal(String id, SignalKind kind, SourceTier tier) {
    return new Signal(
        id, "src-" + id, tier, "https://example.test/" + id, Instant.now(),
        kind, "summary", "excerpt", Influence.STRONG_POSITIVE, false);
  }

  @Test
  void registersAWatch() {
    var testKit = registeredWatch();

    assertThat(testKit.getState().isRegistered()).isTrue();
    assertThat(testKit.getState().active()).isTrue();
    assertThat(testKit.getState().market().radiusMiles()).isEqualTo(50);
  }

  @Test
  void rejectsAnAlertThresholdOutsideTheValidRange() {
    var testKit = EventSourcedTestKit.of("metallica:chicago", TourWatchEntity::new);

    var result = testKit.method(TourWatchEntity::register)
        .invoke(new TourWatchEntity.RegisterWatch(METALLICA, CHICAGO, 140, "https://hooks.example.test/s"));

    assertThat(result.isError()).isTrue();
  }

  @Test
  void recordsASignalAndRescoresInTheSameCommand() {
    var testKit = registeredWatch();

    var result = testKit.method(TourWatchEntity::observeSignal)
        .invoke(signal("s1", SignalKind.VENUE_HOLD, SourceTier.A));

    assertThat(result.getAllEvents())
        .anySatisfy(e -> assertThat(e).isInstanceOf(TourWatchEvent.SignalObserved.class))
        .anySatisfy(e -> assertThat(e).isInstanceOf(TourWatchEvent.ForecastRecomputed.class));
    assertThat(testKit.getState().forecast()).isPresent();
  }

  @Test
  void ignoresASignalItHasAlreadyRecorded() {
    var testKit = registeredWatch();
    testKit.method(TourWatchEntity::observeSignal).invoke(signal("s1", SignalKind.VENUE_HOLD, SourceTier.A));

    var repeat = testKit.method(TourWatchEntity::observeSignal)
        .invoke(signal("s1", SignalKind.VENUE_HOLD, SourceTier.A));

    assertThat(repeat.getAllEvents()).isEmpty();
    assertThat(testKit.getState().signals()).hasSize(1);
  }

  @Test
  void keepsTheSupersededForecastInHistory() {
    var testKit = registeredWatch();
    testKit.method(TourWatchEntity::observeSignal).invoke(signal("s1", SignalKind.VENUE_HOLD, SourceTier.A));
    var first = testKit.getState().forecast().orElseThrow();

    testKit.method(TourWatchEntity::observeSignal).invoke(signal("s2", SignalKind.OFFICIAL_TEASER, SourceTier.A));

    assertThat(testKit.getState().forecastHistory()).contains(first);
    assertThat(testKit.getState().forecast().orElseThrow()).isNotEqualTo(first);
  }

  @Test
  void refusesToConfirmAVisitFromASourceThatCannotSpeakForTheArtist() {
    var testKit = registeredWatch();
    testKit.method(TourWatchEntity::observeSignal)
        .invoke(signal("rumour", SignalKind.OFFICIAL_ANNOUNCEMENT, SourceTier.C));

    var result = testKit.method(TourWatchEntity::confirmVisit)
        .invoke(new TourWatchEntity.ConfirmVisit(soldierFieldVisit(), "rumour"));

    assertThat(result.isError()).isTrue();
    assertThat(testKit.getState().confirmedVisits()).isEmpty();
  }

  @Test
  void refusesToConfirmAVisitFromASignalThatIsNotAnAnnouncement() {
    var testKit = registeredWatch();
    testKit.method(TourWatchEntity::observeSignal).invoke(signal("hold", SignalKind.VENUE_HOLD, SourceTier.A));

    var result = testKit.method(TourWatchEntity::confirmVisit)
        .invoke(new TourWatchEntity.ConfirmVisit(soldierFieldVisit(), "hold"));

    assertThat(result.isError()).isTrue();
  }

  @Test
  void confirmsAVisitFromAnOfficialAnnouncement() {
    var testKit = registeredWatch();
    testKit.method(TourWatchEntity::observeSignal)
        .invoke(signal("official", SignalKind.OFFICIAL_ANNOUNCEMENT, SourceTier.A));

    var result = testKit.method(TourWatchEntity::confirmVisit)
        .invoke(new TourWatchEntity.ConfirmVisit(soldierFieldVisit(), "official"));

    assertThat(result.isError()).isFalse();
    assertThat(testKit.getState().confirmedVisits()).hasSize(1);
  }

  @Test
  void refusesAVenueOutsideTheWatchedRadius() {
    var testKit = registeredWatch();
    testKit.method(TourWatchEntity::observeSignal)
        .invoke(signal("official", SignalKind.OFFICIAL_ANNOUNCEMENT, SourceTier.A));
    var distant = new Visit(
        "v-distant", new VenueRef("MetLife Stadium", "East Rutherford", 40.8135, -74.0745),
        List.of(LocalDate.of(2026, 11, 1)), Instant.now(), List.of(),
        VisitStatus.ANNOUNCED, "https://example.test/announcement");

    var result = testKit.method(TourWatchEntity::confirmVisit)
        .invoke(new TourWatchEntity.ConfirmVisit(distant, "official"));

    assertThat(result.isError()).isTrue();
  }

  @Test
  void treatsAKnownVisitAsChangedRatherThanConfirmedTwice() {
    var testKit = registeredWatch();
    testKit.method(TourWatchEntity::observeSignal)
        .invoke(signal("official", SignalKind.OFFICIAL_ANNOUNCEMENT, SourceTier.A));
    testKit.method(TourWatchEntity::confirmVisit)
        .invoke(new TourWatchEntity.ConfirmVisit(soldierFieldVisit(), "official"));

    var again = testKit.method(TourWatchEntity::confirmVisit)
        .invoke(new TourWatchEntity.ConfirmVisit(soldierFieldVisit(), "official"));

    assertThat(again.getAllEvents()).isEmpty();
    assertThat(testKit.getState().confirmedVisits()).hasSize(1);
  }

  @Test
  void recordsAChangeWhenAKnownVisitIsReObservedWithANewStatus() {
    var testKit = registeredWatch();
    testKit.method(TourWatchEntity::observeSignal)
        .invoke(signal("official", SignalKind.OFFICIAL_ANNOUNCEMENT, SourceTier.A));
    testKit.method(TourWatchEntity::confirmVisit)
        .invoke(new TourWatchEntity.ConfirmVisit(soldierFieldVisit(), "official"));

    var postponed = soldierFieldVisit().withStatus(VisitStatus.POSTPONED);
    var result = testKit.method(TourWatchEntity::confirmVisit)
        .invoke(new TourWatchEntity.ConfirmVisit(postponed, "official"));

    assertThat(result.getAllEvents()).allSatisfy(e ->
        assertThat(e).isInstanceOf(TourWatchEvent.VisitChanged.class));
    assertThat(testKit.getState().findVisit("v-1").orElseThrow().status())
        .isEqualTo(VisitStatus.POSTPONED);
  }

  @Test
  void carriesEveryDateOfAMultiNightRunAsOneVisit() {
    var testKit = registeredWatch();
    testKit.method(TourWatchEntity::observeSignal)
        .invoke(signal("official", SignalKind.OFFICIAL_ANNOUNCEMENT, SourceTier.A));
    var twoNights = new Visit(
        "v-1", SOLDIER_FIELD,
        List.of(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 9)),
        Instant.now(), List.of(), VisitStatus.ANNOUNCED, "https://example.test/announcement");

    testKit.method(TourWatchEntity::confirmVisit)
        .invoke(new TourWatchEntity.ConfirmVisit(twoNights, "official"));

    assertThat(testKit.getState().confirmedVisits()).hasSize(1);
    assertThat(testKit.getState().confirmedVisits().get(0).dates()).hasSize(2);
  }

  @Test
  void refusesAnImpossibleVisitStatusTransition() {
    var testKit = registeredWatch();
    testKit.method(TourWatchEntity::observeSignal)
        .invoke(signal("official", SignalKind.OFFICIAL_ANNOUNCEMENT, SourceTier.A));
    testKit.method(TourWatchEntity::confirmVisit)
        .invoke(new TourWatchEntity.ConfirmVisit(soldierFieldVisit(), "official"));
    testKit.method(TourWatchEntity::changeVisitStatus)
        .invoke(new TourWatchEntity.ChangeVisitStatus("v-1", VisitStatus.CANCELLED));

    var result = testKit.method(TourWatchEntity::changeVisitStatus)
        .invoke(new TourWatchEntity.ChangeVisitStatus("v-1", VisitStatus.ANNOUNCED));

    assertThat(result.isError()).isTrue();
  }

  @Test
  void lowersConfidenceWhenASourceGoesDarkAndRaisesItWhenTheSourceComesBack() {
    var testKit = registeredWatch();
    testKit.method(TourWatchEntity::observeSignal).invoke(signal("s1", SignalKind.VENUE_HOLD, SourceTier.A));
    testKit.method(TourWatchEntity::observeSignal).invoke(signal("s2", SignalKind.OFFICIAL_TEASER, SourceTier.A));
    testKit.method(TourWatchEntity::observeSignal).invoke(signal("s3", SignalKind.CADENCE_ELAPSED, SourceTier.A));
    assertThat(testKit.getState().forecast().orElseThrow().confidence()).isEqualTo(ConfidenceBand.HIGH);

    testKit.method(TourWatchEntity::degradeSource)
        .invoke(new TourWatchEntity.DegradeSource("src-s1", "connection refused"));
    assertThat(testKit.getState().forecast().orElseThrow().confidence()).isEqualTo(ConfidenceBand.MEDIUM);

    testKit.method(TourWatchEntity::restoreSource).invoke("src-s1");
    assertThat(testKit.getState().forecast().orElseThrow().confidence()).isEqualTo(ConfidenceBand.HIGH);
  }

  @Test
  void withdrawsTheForecastWhenTheLastSupportingSignalIsDismissed() {
    var testKit = registeredWatch();
    testKit.method(TourWatchEntity::observeSignal).invoke(signal("s1", SignalKind.VENUE_HOLD, SourceTier.A));

    testKit.method(TourWatchEntity::dismissSignal)
        .invoke(new TourWatchEntity.DismissSignal("s1", "Hold was a different production"));

    assertThat(testKit.getState().forecast()).isEmpty();
    assertThat(testKit.getState().findSignal("s1").orElseThrow().dismissed()).isTrue();
  }

  @Test
  void recomputesDeterministicallyWhenASignalIsDismissed() {
    var testKit = registeredWatch();
    testKit.method(TourWatchEntity::observeSignal).invoke(signal("s1", SignalKind.VENUE_HOLD, SourceTier.A));
    testKit.method(TourWatchEntity::observeSignal).invoke(signal("s2", SignalKind.OFFICIAL_TEASER, SourceTier.A));
    testKit.method(TourWatchEntity::dismissSignal).invoke(new TourWatchEntity.DismissSignal("s2", "wrong"));
    var afterDismissal = testKit.getState().forecast().orElseThrow();

    var replay = EventSourcedTestKit.of("metallica:chicago", TourWatchEntity::new);
    replay.method(TourWatchEntity::register)
        .invoke(new TourWatchEntity.RegisterWatch(METALLICA, CHICAGO, 60, "https://hooks.example.test/s"));
    replay.method(TourWatchEntity::observeSignal).invoke(signal("s1", SignalKind.VENUE_HOLD, SourceTier.A));
    replay.method(TourWatchEntity::observeSignal).invoke(signal("s2", SignalKind.OFFICIAL_TEASER, SourceTier.A));
    replay.method(TourWatchEntity::dismissSignal).invoke(new TourWatchEntity.DismissSignal("s2", "wrong"));

    assertThat(replay.getState().forecast().orElseThrow().likelihood())
        .isEqualTo(afterDismissal.likelihood());
    assertThat(replay.getState().forecast().orElseThrow().citedSignalIds())
        .isEqualTo(afterDismissal.citedSignalIds());
  }

  @Test
  void refusesASignalBeforeTheWatchIsRegistered() {
    var testKit = EventSourcedTestKit.of("metallica:chicago", TourWatchEntity::new);

    var result = testKit.method(TourWatchEntity::observeSignal)
        .invoke(signal("s1", SignalKind.VENUE_HOLD, SourceTier.A));

    assertThat(result.isError()).isTrue();
  }

  private static Visit soldierFieldVisit() {
    return new Visit(
        "v-1", SOLDIER_FIELD, List.of(LocalDate.of(2026, 11, 7)),
        Instant.parse("2026-09-01T15:00:00Z"), List.of(),
        VisitStatus.ANNOUNCED, "https://example.test/announcement");
  }
}
