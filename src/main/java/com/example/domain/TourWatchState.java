package com.example.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The current picture of one watch, derived from its journal.
 *
 * <p>Signals are marked dismissed rather than removed: a fan who dismissed something and later
 * wants to know what he dismissed should be able to find out, and the forecast history only makes
 * sense against the full set.
 */
public record TourWatchState(
    String watchId,
    ArtistRef artist,
    Market market,
    int alertThreshold,
    String alertWebhookUrl,
    boolean active,
    List<Signal> signals,
    Forecast currentForecast,
    List<Forecast> forecastHistory,
    List<Visit> confirmedVisits,
    Set<String> degradedSources) {

  public static TourWatchState empty(String watchId) {
    return new TourWatchState(
        watchId, null, null, 100, "", false, List.of(), null, List.of(), List.of(), Set.of());
  }

  public boolean isRegistered() {
    return artist != null;
  }

  public Optional<Forecast> forecast() {
    return Optional.ofNullable(currentForecast);
  }

  public List<Signal> liveSignals() {
    return signals.stream().filter(s -> !s.dismissed()).toList();
  }

  public Optional<Signal> findSignal(String signalId) {
    return signals.stream().filter(s -> s.signalId().equals(signalId)).findFirst();
  }

  public Optional<Visit> findVisit(String visitId) {
    return confirmedVisits.stream().filter(v -> v.visitId().equals(visitId)).findFirst();
  }

  /**
   * Rescoring is a pure function of the live signals and the current blind spots, so the same
   * dismissal always produces the same forecast.
   */
  public Optional<Forecast> recompute(Instant now) {
    return ForecastScorer.score(signals, degradedSources.size(), now);
  }

  public TourWatchState registered(ArtistRef newArtist, Market newMarket, int threshold, String webhookUrl) {
    return new TourWatchState(
        watchId, newArtist, newMarket, threshold, webhookUrl, true,
        signals, currentForecast, forecastHistory, confirmedVisits, degradedSources);
  }

  public TourWatchState deactivated() {
    return new TourWatchState(
        watchId, artist, market, alertThreshold, alertWebhookUrl, false,
        signals, currentForecast, forecastHistory, confirmedVisits, degradedSources);
  }

  public TourWatchState withSignal(Signal signal) {
    if (findSignal(signal.signalId()).isPresent()) {
      return this;
    }
    var updated = new ArrayList<>(signals);
    updated.add(signal);
    return withSignals(updated);
  }

  public TourWatchState withSignalDismissed(String signalId) {
    var updated = signals.stream()
        .map(s -> s.signalId().equals(signalId) ? s.asDismissed() : s)
        .toList();
    return withSignals(updated);
  }

  private TourWatchState withSignals(List<Signal> updated) {
    return new TourWatchState(
        watchId, artist, market, alertThreshold, alertWebhookUrl, active,
        List.copyOf(updated), currentForecast, forecastHistory, confirmedVisits, degradedSources);
  }

  /** Supersedes the current forecast, keeping the old one so the fan can see how the estimate moved. */
  public TourWatchState withForecast(Forecast forecast) {
    var history = new ArrayList<>(forecastHistory);
    if (currentForecast != null) {
      history.add(currentForecast);
    }
    return new TourWatchState(
        watchId, artist, market, alertThreshold, alertWebhookUrl, active,
        signals, forecast, List.copyOf(history), confirmedVisits, degradedSources);
  }

  public TourWatchState withoutForecast() {
    var history = new ArrayList<>(forecastHistory);
    if (currentForecast != null) {
      history.add(currentForecast);
    }
    return new TourWatchState(
        watchId, artist, market, alertThreshold, alertWebhookUrl, active,
        signals, null, List.copyOf(history), confirmedVisits, degradedSources);
  }

  public TourWatchState withNarration(String forecastId, String rationale) {
    if (currentForecast == null || !currentForecast.forecastId().equals(forecastId)) {
      return this;
    }
    return new TourWatchState(
        watchId, artist, market, alertThreshold, alertWebhookUrl, active,
        signals, currentForecast.withRationale(rationale), forecastHistory, confirmedVisits, degradedSources);
  }

  public TourWatchState withVisit(Visit visit) {
    var updated = new ArrayList<>(confirmedVisits);
    updated.add(visit);
    return withVisits(updated);
  }

  /**
   * Total by construction: applying a journal event must never fail, so a transition that a
   * command handler would have rejected is a no-op here rather than an exception during replay.
   */
  public TourWatchState withVisitStatus(String visitId, VisitStatus status) {
    var updated = confirmedVisits.stream()
        .map(v -> v.visitId().equals(visitId) && v.status().canTransitionTo(status)
            ? v.withStatus(status)
            : v)
        .toList();
    return withVisits(updated);
  }

  private TourWatchState withVisits(List<Visit> updated) {
    return new TourWatchState(
        watchId, artist, market, alertThreshold, alertWebhookUrl, active,
        signals, currentForecast, forecastHistory, List.copyOf(updated), degradedSources);
  }

  public TourWatchState withDegradedSource(String sourceId) {
    var updated = new LinkedHashSet<>(degradedSources);
    updated.add(sourceId);
    return withDegraded(updated);
  }

  public TourWatchState withRestoredSource(String sourceId) {
    var updated = new LinkedHashSet<>(degradedSources);
    updated.remove(sourceId);
    return withDegraded(updated);
  }

  private TourWatchState withDegraded(Set<String> updated) {
    return new TourWatchState(
        watchId, artist, market, alertThreshold, alertWebhookUrl, active,
        signals, currentForecast, forecastHistory, confirmedVisits, Set.copyOf(updated));
  }
}
