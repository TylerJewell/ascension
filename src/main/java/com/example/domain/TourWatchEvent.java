package com.example.domain;

import akka.javasdk.annotations.TypeName;

/**
 * Everything that has ever happened to a watch.
 *
 * <p>The spec asks for a forecast history that is superseded rather than overwritten, evidence a
 * fan can inspect, and signals he can dismiss with the estimate recomputed. That is a journal
 * described in user language, so the journal is the state: the audit trail cannot drift from the
 * answer because it is what the answer is derived from.
 */
public sealed interface TourWatchEvent {

  @TypeName("watch-registered")
  record WatchRegistered(ArtistRef artist, Market market, int alertThreshold, String alertWebhookUrl)
      implements TourWatchEvent {}

  @TypeName("watch-deactivated")
  record WatchDeactivated(String reason) implements TourWatchEvent {}

  @TypeName("signal-observed")
  record SignalObserved(Signal signal) implements TourWatchEvent {}

  @TypeName("signal-dismissed")
  record SignalDismissed(String signalId, String reason) implements TourWatchEvent {}

  /** Carries the computed forecast rather than the inputs, so replay never re-runs the scorer against a different clock. */
  @TypeName("forecast-recomputed")
  record ForecastRecomputed(Forecast forecast) implements TourWatchEvent {}

  /** The last supporting signal was dismissed. The forecast is withdrawn, not served empty. */
  @TypeName("forecast-withdrawn")
  record ForecastWithdrawn(String reason) implements TourWatchEvent {}

  @TypeName("forecast-narrated")
  record ForecastNarrated(String forecastId, String rationale) implements TourWatchEvent {}

  @TypeName("visit-confirmed")
  record VisitConfirmed(Visit visit) implements TourWatchEvent {}

  @TypeName("visit-changed")
  record VisitChanged(String visitId, VisitStatus newStatus) implements TourWatchEvent {}

  @TypeName("source-degraded")
  record SourceDegraded(String sourceId, String reason) implements TourWatchEvent {}

  @TypeName("source-restored")
  record SourceRestored(String sourceId) implements TourWatchEvent {}
}
