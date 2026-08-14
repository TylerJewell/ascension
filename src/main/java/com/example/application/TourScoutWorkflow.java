package com.example.application;

import static java.time.Duration.ofSeconds;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.domain.Signal;
import com.example.domain.SourceTier;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One pass over every registered source for one watch.
 *
 * <p>Partial failure is the normal case here, not the exception — some source will always be down.
 * A failed source therefore degrades the watch's confidence and the cycle moves on, rather than
 * aborting and leaving the fan with a stale forecast that looks as solid as a fresh one.
 */
@Component(id = "tour-scout")
public class TourScoutWorkflow extends Workflow<TourScoutWorkflow.ScoutState> {

  private static final Logger logger = LoggerFactory.getLogger(TourScoutWorkflow.class);

  public record ScoutState(
      String watchId,
      List<String> sourceIds,
      int cursor,
      String pendingSourceId,
      String pendingSourceUrl,
      SourceTier pendingTier,
      String pendingContent) {

    ScoutState advanced() {
      return new ScoutState(watchId, sourceIds, cursor + 1, null, null, null, null);
    }

    ScoutState withFetched(String sourceId, String url, SourceTier tier, String content) {
      return new ScoutState(watchId, sourceIds, cursor, sourceId, url, tier, content);
    }

    boolean exhausted() {
      return cursor >= sourceIds.size();
    }

    String currentSourceId() {
      return sourceIds.get(cursor);
    }
  }

  private final ComponentClient componentClient;
  private final SourceRegistry registry;
  private final SourceGateway gateway;

  public TourScoutWorkflow(ComponentClient componentClient, SourceRegistry registry, SourceGateway gateway) {
    this.componentClient = componentClient;
    this.registry = registry;
    this.gateway = gateway;
  }

  @Override
  public WorkflowSettings settings() {
    // Model calls are slow and cost money on every attempt, so the step timeout is generous and
    // the retry budget is small.
    return WorkflowSettings.builder()
        .defaultStepTimeout(ofSeconds(60))
        .build();
  }

  public Effect<Done> runCycle(String watchId) {
    if (currentState() != null && !currentState().exhausted()) {
      // A cycle is already walking the source list; starting another would double-count signals.
      return effects().reply(Done.getInstance());
    }
    var initial = new ScoutState(
        watchId, registry.all().stream().map(SourceRegistry.SourceEntry::sourceId).toList(),
        0, null, null, null, null);

    return effects()
        .updateState(initial)
        .transitionTo(TourScoutWorkflow::fetchStep)
        .thenReply(Done.getInstance());
  }

  @StepName("fetch")
  private StepEffect fetchStep() {
    if (currentState().exhausted()) {
      return stepEffects().thenTransitionTo(TourScoutWorkflow::narrateStep);
    }

    var sourceId = currentState().currentSourceId();
    var entry = registry.find(sourceId).orElseThrow();
    var result = gateway.fetch(sourceId, entry.allowedPaths().get(0));

    return switch (result) {
      case SourceGateway.FetchResult.Unavailable unavailable -> {
        logger.info("Source {} unavailable for watch {}: {}",
            sourceId, currentState().watchId(), unavailable.reason());
        componentClient
            .forEventSourcedEntity(currentState().watchId())
            .method(TourWatchEntity::degradeSource)
            .invoke(new TourWatchEntity.DegradeSource(sourceId, unavailable.reason()));
        yield stepEffects()
            .updateState(currentState().advanced())
            .thenTransitionTo(TourScoutWorkflow::fetchStep);
      }
      case SourceGateway.FetchResult.Fetched fetched -> stepEffects()
          .updateState(currentState()
              .withFetched(sourceId, fetched.url(), entry.tier(), fetched.body()))
          .thenTransitionTo(TourScoutWorkflow::interpretStep);
    };
  }

  @StepName("interpret")
  private StepEffect interpretStep() {
    var state = currentState();
    var watch = componentClient
        .forEventSourcedEntity(state.watchId())
        .method(TourWatchEntity::getWatch)
        .invoke();

    var candidates = componentClient
        .forAgent()
        .inSession(state.watchId() + ":" + state.cursor())
        .method(SignalInterpreterAgent::interpret)
        .invoke(new SignalInterpreterAgent.Request(
            watch.artist().displayName(),
            watch.market().displayName(),
            state.pendingSourceUrl(),
            state.pendingContent()));

    int index = 0;
    for (var candidate : candidates.signals()) {
      var signal = new Signal(
          "%s:%s:%d".formatted(state.pendingSourceId(), state.cursor(), index++),
          state.pendingSourceId(),
          state.pendingTier(),
          state.pendingSourceUrl(),
          Instant.now(),
          candidate.kind(),
          candidate.summary(),
          candidate.excerpt(),
          candidate.influence(),
          false);
      componentClient
          .forEventSourcedEntity(state.watchId())
          .method(TourWatchEntity::observeSignal)
          .invoke(signal);
    }

    componentClient
        .forEventSourcedEntity(state.watchId())
        .method(TourWatchEntity::restoreSource)
        .invoke(state.pendingSourceId());

    return stepEffects()
        .updateState(state.advanced())
        .thenTransitionTo(TourScoutWorkflow::fetchStep);
  }

  @StepName("narrate")
  private StepEffect narrateStep() {
    var watch = componentClient
        .forEventSourcedEntity(currentState().watchId())
        .method(TourWatchEntity::getWatch)
        .invoke();

    var forecast = watch.forecast().orElse(null);
    if (forecast == null) {
      return stepEffects().thenEnd();
    }

    var summaries = watch.liveSignals().stream()
        .map(s -> new ForecastNarratorAgent.SignalSummary(
            s.signalId(), s.kind().name(), s.summary(), s.influence().name(), s.sourceUrl()))
        .toList();

    var narration = componentClient
        .forAgent()
        .inSession(currentState().watchId() + ":narrate:" + forecast.forecastId())
        .method(ForecastNarratorAgent::narrate)
        .invoke(new ForecastNarratorAgent.Request(
            watch.artist().displayName(),
            watch.market().displayName(),
            forecast.likelihood(),
            forecast.windowStart().toString(),
            forecast.windowEnd().toString(),
            forecast.confidence().name(),
            summaries));

    componentClient
        .forEventSourcedEntity(currentState().watchId())
        .method(TourWatchEntity::narrateForecast)
        .invoke(new TourWatchEntity.NarrateForecast(
            forecast.forecastId(), narration.rationale(), narration.citedSignalIds()));

    return stepEffects().thenEnd();
  }
}
