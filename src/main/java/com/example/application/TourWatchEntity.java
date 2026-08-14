package com.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.domain.ArtistRef;
import com.example.domain.Market;
import com.example.domain.Signal;
import com.example.domain.TourWatchEvent;
import com.example.domain.TourWatchState;
import com.example.domain.Visit;
import com.example.domain.VisitStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One fan's standing interest in one artist playing one market.
 *
 * <p>Two invariants are enforced here rather than downstream, because both are silent when broken:
 * a visit can only be confirmed by a source entitled to speak for the artist, and a forecast can
 * only be narrated in terms of signals that were actually observed.
 */
@Component(id = "tour-watch")
public class TourWatchEntity extends EventSourcedEntity<TourWatchState, TourWatchEvent> {

  public record RegisterWatch(ArtistRef artist, Market market, int alertThreshold, String alertWebhookUrl) {}

  public record DismissSignal(String signalId, String reason) {}

  /** The visit plus the signal claimed to have announced it, which is what gets checked. */
  public record ConfirmVisit(Visit visit, String sourceSignalId) {}

  public record ChangeVisitStatus(String visitId, VisitStatus newStatus) {}

  public record NarrateForecast(String forecastId, String rationale, List<String> citedSignalIds) {}

  public record DegradeSource(String sourceId, String reason) {}

  private final String entityId;

  public TourWatchEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public TourWatchState emptyState() {
    return TourWatchState.empty(entityId);
  }

  public Effect<Done> register(RegisterWatch command) {
    if (command.alertThreshold() < 0 || command.alertThreshold() > 100) {
      return effects().error("Alert threshold must be 0-100, got " + command.alertThreshold());
    }
    return effects()
        .persist(new TourWatchEvent.WatchRegistered(
            command.artist(), command.market(), command.alertThreshold(), command.alertWebhookUrl()))
        .thenReply(__ -> Done.getInstance());
  }

  public Effect<Done> deactivate(String reason) {
    if (!currentState().isRegistered() || !currentState().active()) {
      return effects().reply(Done.getInstance());
    }
    return effects()
        .persist(new TourWatchEvent.WatchDeactivated(reason))
        .thenReply(__ -> Done.getInstance());
  }

  /**
   * Records an observation and rescores in the same command, so the forecast is never one step
   * behind the evidence it is supposed to summarise.
   */
  public Effect<Done> observeSignal(Signal signal) {
    if (!currentState().isRegistered()) {
      return effects().error("Watch " + entityId + " has not been registered");
    }
    if (currentState().findSignal(signal.signalId()).isPresent()) {
      return effects().reply(Done.getInstance());
    }

    var events = new ArrayList<TourWatchEvent>();
    events.add(new TourWatchEvent.SignalObserved(signal));
    currentState()
        .withSignal(signal)
        .recompute(Instant.now())
        .ifPresent(f -> events.add(new TourWatchEvent.ForecastRecomputed(f)));

    return effects().persistAll(events).thenReply(__ -> Done.getInstance());
  }

  /**
   * A fan who knows a signal is wrong can remove it and see the estimate move. When the last live
   * signal goes, the forecast is withdrawn rather than served with nothing behind it.
   */
  public Effect<Done> dismissSignal(DismissSignal command) {
    var signal = currentState().findSignal(command.signalId());
    if (signal.isEmpty()) {
      return effects().error("No signal " + command.signalId() + " on watch " + entityId);
    }
    if (signal.get().dismissed()) {
      return effects().reply(Done.getInstance());
    }

    var events = new ArrayList<TourWatchEvent>();
    events.add(new TourWatchEvent.SignalDismissed(command.signalId(), command.reason()));

    var rescored = currentState().withSignalDismissed(command.signalId()).recompute(Instant.now());
    if (rescored.isPresent()) {
      events.add(new TourWatchEvent.ForecastRecomputed(rescored.get()));
    } else if (currentState().forecast().isPresent()) {
      events.add(new TourWatchEvent.ForecastWithdrawn("Last supporting signal was dismissed"));
    }

    return effects().persistAll(events).thenReply(__ -> Done.getInstance());
  }

  /**
   * Confirmation is gated on the tier of the signal that claims it. An aggregator or a forum can
   * move the estimate but cannot tell a fan a date exists — that is the difference between this
   * system being wrong and this system being untrustworthy.
   */
  public Effect<Done> confirmVisit(ConfirmVisit command) {
    if (!currentState().isRegistered()) {
      return effects().error("Watch " + entityId + " has not been registered");
    }
    var source = currentState().findSignal(command.sourceSignalId());
    if (source.isEmpty()) {
      return effects().error("No signal " + command.sourceSignalId() + " to confirm from");
    }
    if (!source.get().canConfirmVisit()) {
      return effects().error(
          "Signal " + command.sourceSignalId() + " may not confirm a visit: it is a "
              + source.get().kind() + " from a tier " + source.get().sourceTier() + " source");
    }
    if (!currentState().market().contains(command.visit().venue())) {
      return effects().error(
          "Venue " + command.visit().venue().name() + " is outside the watched market radius");
    }

    // A visit that is already known has changed rather than happened, so it updates in place.
    // Re-observing it unchanged emits nothing at all, which is what keeps a source that keeps
    // reporting the same announcement from alerting the fan on every cycle.
    var known = currentState().findVisit(command.visit().visitId());
    if (known.isPresent()) {
      if (known.get().status() == command.visit().status()) {
        return effects().reply(Done.getInstance());
      }
      if (!known.get().status().canTransitionTo(command.visit().status())) {
        return effects().error(
            "Cannot move visit from " + known.get().status() + " to " + command.visit().status());
      }
      return effects()
          .persist(new TourWatchEvent.VisitChanged(command.visit().visitId(), command.visit().status()))
          .thenReply(__ -> Done.getInstance());
    }

    return effects()
        .persist(new TourWatchEvent.VisitConfirmed(command.visit()))
        .thenReply(__ -> Done.getInstance());
  }

  public Effect<Done> changeVisitStatus(ChangeVisitStatus command) {
    var visit = currentState().findVisit(command.visitId());
    if (visit.isEmpty()) {
      return effects().error("No visit " + command.visitId() + " on watch " + entityId);
    }
    if (!visit.get().status().canTransitionTo(command.newStatus())) {
      return effects().error(
          "Cannot move visit from " + visit.get().status() + " to " + command.newStatus());
    }
    return effects()
        .persist(new TourWatchEvent.VisitChanged(command.visitId(), command.newStatus()))
        .thenReply(__ -> Done.getInstance());
  }

  /**
   * The authoritative evidence check. A narration that cites nothing, or cites something that was
   * never observed, is refused — the model's fluency is not evidence.
   */
  public Effect<Done> narrateForecast(NarrateForecast command) {
    var forecast = currentState().forecast();
    if (forecast.isEmpty()) {
      return effects().error("There is no current forecast to narrate");
    }
    if (!forecast.get().forecastId().equals(command.forecastId())) {
      return effects().error("Narration is for a superseded forecast " + command.forecastId());
    }
    if (command.citedSignalIds() == null || command.citedSignalIds().isEmpty()) {
      return effects().error("A narrated forecast must cite at least one signal");
    }
    Set<String> live = Set.copyOf(
        currentState().liveSignals().stream().map(Signal::signalId).toList());
    var unknown = command.citedSignalIds().stream().filter(id -> !live.contains(id)).toList();
    if (!unknown.isEmpty()) {
      return effects().error("Narration cites signals that were never observed: " + unknown);
    }

    return effects()
        .persist(new TourWatchEvent.ForecastNarrated(command.forecastId(), command.rationale()))
        .thenReply(__ -> Done.getInstance());
  }

  public Effect<Done> degradeSource(DegradeSource command) {
    if (currentState().degradedSources().contains(command.sourceId())) {
      return effects().reply(Done.getInstance());
    }
    var events = new ArrayList<TourWatchEvent>();
    events.add(new TourWatchEvent.SourceDegraded(command.sourceId(), command.reason()));
    // A blind spot lowers confidence rather than leaving the estimate looking as solid as before.
    currentState()
        .withDegradedSource(command.sourceId())
        .recompute(Instant.now())
        .ifPresent(f -> events.add(new TourWatchEvent.ForecastRecomputed(f)));
    return effects().persistAll(events).thenReply(__ -> Done.getInstance());
  }

  public Effect<Done> restoreSource(String sourceId) {
    if (!currentState().degradedSources().contains(sourceId)) {
      return effects().reply(Done.getInstance());
    }
    var events = new ArrayList<TourWatchEvent>();
    events.add(new TourWatchEvent.SourceRestored(sourceId));
    currentState()
        .withRestoredSource(sourceId)
        .recompute(Instant.now())
        .ifPresent(f -> events.add(new TourWatchEvent.ForecastRecomputed(f)));
    return effects().persistAll(events).thenReply(__ -> Done.getInstance());
  }

  public ReadOnlyEffect<TourWatchState> getWatch() {
    return effects().reply(currentState());
  }

  @Override
  public TourWatchState applyEvent(TourWatchEvent event) {
    return switch (event) {
      case TourWatchEvent.WatchRegistered e ->
          currentState().registered(e.artist(), e.market(), e.alertThreshold(), e.alertWebhookUrl());
      case TourWatchEvent.WatchDeactivated e -> currentState().deactivated();
      case TourWatchEvent.SignalObserved e -> currentState().withSignal(e.signal());
      case TourWatchEvent.SignalDismissed e -> currentState().withSignalDismissed(e.signalId());
      case TourWatchEvent.ForecastRecomputed e -> currentState().withForecast(e.forecast());
      case TourWatchEvent.ForecastWithdrawn e -> currentState().withoutForecast();
      case TourWatchEvent.ForecastNarrated e -> currentState().withNarration(e.forecastId(), e.rationale());
      case TourWatchEvent.VisitConfirmed e -> currentState().withVisit(e.visit());
      case TourWatchEvent.VisitChanged e -> currentState().withVisitStatus(e.visitId(), e.newStatus());
      case TourWatchEvent.SourceDegraded e -> currentState().withDegradedSource(e.sourceId());
      case TourWatchEvent.SourceRestored e -> currentState().withRestoredSource(e.sourceId());
    };
  }
}
