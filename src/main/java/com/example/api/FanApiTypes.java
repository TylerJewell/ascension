package com.example.api;

import com.example.domain.Forecast;
import com.example.domain.TourWatchState;
import com.example.domain.Visit;
import java.util.List;

/**
 * The shapes the fan-facing API speaks in, kept separate from the domain so the internal model can
 * change without breaking clients.
 */
public final class FanApiTypes {

  private FanApiTypes() {}

  public record CreateProfileRequest(
      String fanId,
      String displayName,
      String homeMarket,
      String residencyState,
      String ageBand,
      List<String> fanClubs,
      List<String> sponsorRelationships,
      String alertWebhookUrl) {}

  public record RegisterWatchRequest(
      String artistSlug,
      String artistName,
      String marketSlug,
      String marketName,
      double centroidLat,
      double centroidLon,
      int radiusMiles,
      int alertThreshold,
      String alertWebhookUrl) {}

  public record RegisterWatchResponse(String watchId) {}

  public record WindowView(String start, String end) {}

  public record ForecastView(
      int likelihood,
      WindowView announcementWindow,
      String confidence,
      String rationale,
      List<String> citedSignalIds,
      String computedAt) {}

  public record PresaleView(String name, String opensAt, String closesAt, String requirement) {}

  public record VisitView(
      String visitId,
      String venue,
      String city,
      List<String> dates,
      String onsaleAt,
      String status,
      String sourceUrl,
      List<PresaleView> presales) {}

  /**
   * {@code kind} is mandatory and is either {@code FORECAST} or {@code CONFIRMED}. It exists so a
   * client cannot render this payload without knowing which one it holds — a prediction shown as
   * an announcement is the single most damaging thing this API could do.
   */
  public record WatchResponse(
      String watchId,
      String artist,
      String market,
      boolean active,
      String kind,
      ForecastView forecast,
      List<VisitView> confirmedVisits,
      List<String> degradedSources) {}

  public record WatchSummary(String watchId, String artist, String market, boolean active, int likelihood) {}

  public record WatchList(List<WatchSummary> watches) {}

  public static WatchResponse toApi(TourWatchState state) {
    return new WatchResponse(
        state.watchId(),
        state.artist() == null ? "" : state.artist().displayName(),
        state.market() == null ? "" : state.market().displayName(),
        state.active(),
        state.confirmedVisits().isEmpty() ? "FORECAST" : "CONFIRMED",
        state.forecast().map(FanApiTypes::toApi).orElse(null),
        state.confirmedVisits().stream().map(FanApiTypes::toApi).toList(),
        List.copyOf(state.degradedSources()));
  }

  public static ForecastView toApi(Forecast forecast) {
    return new ForecastView(
        forecast.likelihood(),
        new WindowView(forecast.windowStart().toString(), forecast.windowEnd().toString()),
        forecast.confidence().name(),
        forecast.rationale(),
        forecast.citedSignalIds(),
        forecast.computedAt().toString());
  }

  public static VisitView toApi(Visit visit) {
    return new VisitView(
        visit.visitId(),
        visit.venue().name(),
        visit.venue().city(),
        visit.dates().stream().map(Object::toString).toList(),
        visit.onsaleAt() == null ? null : visit.onsaleAt().toString(),
        visit.status().name(),
        visit.sourceUrl(),
        visit.presales().stream()
            .map(p -> new PresaleView(
                p.name(),
                p.opensAt() == null ? null : p.opensAt().toString(),
                p.closesAt() == null ? null : p.closesAt().toString(),
                p.requirement()))
            .toList());
  }
}
