package com.example.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.application.ForecastNarratorAgent;
import com.example.application.SignalInterpreterAgent;
import com.example.application.TourWatchEntity;
import com.example.domain.Influence;
import com.example.domain.Signal;
import com.example.domain.SignalKind;
import com.example.domain.SourceTier;
import com.example.domain.VenueRef;
import com.example.domain.Visit;
import com.example.domain.VisitStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the whole service the way a fan does: register, watch the forecast form, see a real
 * date confirmed.
 *
 * <p>The models are mocked. What is under test is the wiring — that the endpoint, entity, view, and
 * consumer agree with each other — not whether a language model writes good prose.
 */
class FanEndpointIntegrationTest extends TestKitSupport {

  private final TestModelProvider interpreterModel = new TestModelProvider();
  private final TestModelProvider narratorModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.googleai-gemini.api-key = n/a")
        .withModelProvider(SignalInterpreterAgent.class, interpreterModel)
        .withModelProvider(ForecastNarratorAgent.class, narratorModel);
  }

  private static FanApiTypes.RegisterWatchRequest chicagoWatch() {
    return new FanApiTypes.RegisterWatchRequest(
        "metallica", "Metallica", "chicago", "Chicago",
        41.8781, -87.6298, 50, 60, "https://hooks.example.test/saurabh");
  }

  private static Signal officialAnnouncement() {
    return new Signal(
        "official-1", "src-official", SourceTier.A, "https://example.test/announcement",
        Instant.now(), SignalKind.OFFICIAL_ANNOUNCEMENT, "A Chicago date was announced",
        "Metallica play Soldier Field", Influence.STRONG_POSITIVE, false);
  }

  @Test
  void registersAWatchAndReportsItAsAForecastUntilADateExists() {
    var registered = httpClient
        .POST("/fan/watches")
        .withRequestBody(chicagoWatch())
        .responseBodyAs(FanApiTypes.RegisterWatchResponse.class)
        .invoke()
        .body();

    assertThat(registered.watchId()).isEqualTo("metallica:chicago");

    var watch = httpClient
        .GET("/fan/watches/metallica:chicago")
        .responseBodyAs(FanApiTypes.WatchResponse.class)
        .invoke()
        .body();

    assertThat(watch.artist()).isEqualTo("Metallica");
    assertThat(watch.active()).isTrue();
    // No date exists yet, so the payload must not be readable as an announcement.
    assertThat(watch.kind()).isEqualTo("FORECAST");
    assertThat(watch.confirmedVisits()).isEmpty();
  }

  @Test
  void reportsAForecastWithTheEvidenceThatProducedIt() {
    httpClient.POST("/fan/watches").withRequestBody(chicagoWatch()).invoke();

    componentClient
        .forEventSourcedEntity("metallica:chicago")
        .method(TourWatchEntity::observeSignal)
        .invoke(new Signal(
            "hold-1", "src-venue", SourceTier.A, "https://example.test/calendar", Instant.now(),
            SignalKind.VENUE_HOLD, "A stadium hold appeared", "two-night hold",
            Influence.STRONG_POSITIVE, false));

    var watch = httpClient
        .GET("/fan/watches/metallica:chicago")
        .responseBodyAs(FanApiTypes.WatchResponse.class)
        .invoke()
        .body();

    assertThat(watch.kind()).isEqualTo("FORECAST");
    assertThat(watch.forecast()).isNotNull();
    assertThat(watch.forecast().citedSignalIds()).containsExactly("hold-1");
    assertThat(watch.forecast().announcementWindow().start()).isNotBlank();
  }

  @Test
  void switchesToConfirmedOnceARealDateIsAnnounced() {
    httpClient.POST("/fan/watches").withRequestBody(chicagoWatch()).invoke();

    componentClient
        .forEventSourcedEntity("metallica:chicago")
        .method(TourWatchEntity::observeSignal)
        .invoke(officialAnnouncement());

    var visit = new Visit(
        "v-soldier-field",
        new VenueRef("Soldier Field", "Chicago", 41.8623, -87.6167),
        List.of(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 9)),
        Instant.parse("2026-09-01T15:00:00Z"),
        List.of(),
        VisitStatus.ANNOUNCED,
        "https://example.test/announcement");

    componentClient
        .forEventSourcedEntity("metallica:chicago")
        .method(TourWatchEntity::confirmVisit)
        .invoke(new TourWatchEntity.ConfirmVisit(visit, "official-1"));

    var watch = httpClient
        .GET("/fan/watches/metallica:chicago")
        .responseBodyAs(FanApiTypes.WatchResponse.class)
        .invoke()
        .body();

    assertThat(watch.kind()).isEqualTo("CONFIRMED");
    assertThat(watch.confirmedVisits()).hasSize(1);
    assertThat(watch.confirmedVisits().get(0).dates()).hasSize(2);
    assertThat(watch.confirmedVisits().get(0).venue()).isEqualTo("Soldier Field");
    assertThat(watch.confirmedVisits().get(0).sourceUrl()).isNotBlank();
  }

  @Test
  void rejectsAWatchWhoseWebhookIsNotHttps() {
    var insecure = new FanApiTypes.RegisterWatchRequest(
        "metallica", "Metallica", "chicago", "Chicago",
        41.8781, -87.6298, 50, 60, "http://hooks.example.test/saurabh");

    var response = httpClient.POST("/fan/watches").withRequestBody(insecure).invoke();

    assertThat(response.status().intValue()).isEqualTo(400);
  }

  @Test
  void rejectsANonPositiveMarketRadius() {
    var bad = new FanApiTypes.RegisterWatchRequest(
        "metallica", "Metallica", "chicago", "Chicago",
        41.8781, -87.6298, 0, 60, "https://hooks.example.test/saurabh");

    var response = httpClient.POST("/fan/watches").withRequestBody(bad).invoke();

    assertThat(response.status().intValue()).isEqualTo(400);
  }

  @Test
  void exposesNoRouteThatAcquiresATicket() {
    // FR-022 and FR-023 at the API surface. The fan is given the official link and enters
    // himself; there is deliberately nothing here that would do it for him.
    var acquisitionRoutes = List.of(
        "/fan/watches/metallica:chicago/buy",
        "/fan/watches/metallica:chicago/enter",
        "/fan/opportunities/opp-1/enter");

    for (var route : acquisitionRoutes) {
      var response = httpClient.POST(route).withRequestBody("{}").invoke();
      assertThat(response.status().intValue())
          .as("route %s must not exist", route)
          .isIn(404, 405);
    }
  }
}
