package com.example.application;

import static org.assertj.core.api.Assertions.assertThat;
import org.awaitility.Awaitility;

import akka.javasdk.DependencyProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.domain.ArtistRef;
import com.example.domain.ConfidenceBand;
import com.example.domain.Influence;
import com.example.domain.Market;
import com.example.domain.Signal;
import com.example.domain.SignalKind;
import com.example.domain.SourceTier;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Partial failure is the normal case for this workflow, so that is what these assert.
 *
 * <p>A source going dark must cost the fan confidence rather than costing him the whole cycle, and
 * the cycle must still reach the sources after the one that failed.
 */
class TourScoutWorkflowTest extends TestKitSupport {

  private static final ArtistRef METALLICA = new ArtistRef("metallica", "Metallica");
  private static final Market CHICAGO = new Market("chicago", "Chicago", 41.8781, -87.6298, 50);

  private static final SourceRegistry.SourceEntry BROKEN = new SourceRegistry.SourceEntry(
      "broken-source", "down.example.test", SourceTier.A, List.of("/feed"), Duration.ZERO, "2026-08-14");

  private final TestModelProvider interpreterModel = new TestModelProvider();
  private final TestModelProvider narratorModel = new TestModelProvider();

  /** Always fails, standing in for the source that is down on any given day. */
  private static final SourceGateway.Transport FAILING = uri -> {
    throw new java.io.IOException("connection refused");
  };

  @Override
  protected TestKit.Settings testKitSettings() {
    var registry = new SourceRegistry(List.of(BROKEN));
    var gateway = new SourceGateway(registry, FAILING);

    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.googleai-gemini.api-key = n/a")
        .withModelProvider(SignalInterpreterAgent.class, interpreterModel)
        .withModelProvider(ForecastNarratorAgent.class, narratorModel)
        .withDependencyProvider(new DependencyProvider() {
          @Override
          @SuppressWarnings("unchecked")
          public <T> T getDependency(Class<T> clazz) {
            if (clazz == SourceRegistry.class) {
              return (T) registry;
            }
            if (clazz == SourceGateway.class) {
              return (T) gateway;
            }
            return null;
          }
        });
  }

  private void registerWatch(String watchId) {
    componentClient
        .forEventSourcedEntity(watchId)
        .method(TourWatchEntity::register)
        .invoke(new TourWatchEntity.RegisterWatch(
            METALLICA, CHICAGO, 60, "https://hooks.example.test/saurabh"));
  }

  private void observe(String watchId, String signalId, SignalKind kind) {
    componentClient
        .forEventSourcedEntity(watchId)
        .method(TourWatchEntity::observeSignal)
        .invoke(new Signal(
            signalId, "src-" + signalId, SourceTier.A, "https://example.test/" + signalId,
            Instant.now(), kind, "summary", "excerpt", Influence.POSITIVE, false));
  }

  @Test
  void recordsABlindSpotAndFinishesTheCycleWhenASourceIsDown() {
    var watchId = "metallica:chicago-degraded";
    registerWatch(watchId);
    observe(watchId, "s1", SignalKind.VENUE_HOLD);
    observe(watchId, "s2", SignalKind.OFFICIAL_TEASER);
    observe(watchId, "s3", SignalKind.CADENCE_ELAPSED);

    componentClient.forWorkflow(watchId).method(TourScoutWorkflow::runCycle).invoke(watchId);

    Awaitility.await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var watch = componentClient
              .forEventSourcedEntity(watchId)
              .method(TourWatchEntity::getWatch)
              .invoke();
          assertThat(watch.degradedSources()).contains("broken-source");
        });
  }

  @Test
  void lowersConfidenceRatherThanLeavingTheEstimateLookingAsSolidAsBefore() {
    var watchId = "metallica:chicago-confidence";
    registerWatch(watchId);
    observe(watchId, "c1", SignalKind.VENUE_HOLD);
    observe(watchId, "c2", SignalKind.OFFICIAL_TEASER);
    observe(watchId, "c3", SignalKind.CADENCE_ELAPSED);

    var before = componentClient
        .forEventSourcedEntity(watchId)
        .method(TourWatchEntity::getWatch)
        .invoke();
    assertThat(before.forecast().orElseThrow().confidence()).isEqualTo(ConfidenceBand.HIGH);

    componentClient.forWorkflow(watchId).method(TourScoutWorkflow::runCycle).invoke(watchId);

    Awaitility.await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var watch = componentClient
              .forEventSourcedEntity(watchId)
              .method(TourWatchEntity::getWatch)
              .invoke();
          assertThat(watch.forecast().orElseThrow().confidence()).isEqualTo(ConfidenceBand.MEDIUM);
        });
  }

  @Test
  void inventsNothingWhenEverySourceIsUnreachable() {
    var watchId = "metallica:chicago-empty";
    registerWatch(watchId);

    componentClient.forWorkflow(watchId).method(TourScoutWorkflow::runCycle).invoke(watchId);

    Awaitility.await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var watch = componentClient
              .forEventSourcedEntity(watchId)
              .method(TourWatchEntity::getWatch)
              .invoke();
          assertThat(watch.degradedSources()).contains("broken-source");
        });

    var watch = componentClient
        .forEventSourcedEntity(watchId)
        .method(TourWatchEntity::getWatch)
        .invoke();
    // No evidence was gathered, so there is no forecast. An honest silence beats a manufactured
    // estimate a fan might act on.
    assertThat(watch.forecast()).isEmpty();
    assertThat(watch.signals()).isEmpty();
  }

  @Test
  void neverReachesAHostThatIsNotDeclared() {
    var registry = new SourceRegistry(List.of(BROKEN));
    var gateway = new SourceGateway(registry, uri -> {
      throw new AssertionError("Gateway attempted an undeclared host: " + uri);
    });

    var result = gateway.fetch("some-ticketing-site", "/checkout");

    assertThat(result).isInstanceOf(SourceGateway.FetchResult.Unavailable.class);
    assertThat(URI.create("https://" + BROKEN.host() + "/feed").getHost()).isEqualTo("down.example.test");
  }
}
