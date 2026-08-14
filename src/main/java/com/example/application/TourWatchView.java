package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.domain.TourWatchEvent;
import java.util.List;

/**
 * Query surface over watches.
 *
 * <p>A single watch can be read straight from its entity; this exists so the watches can be
 * enumerated, which an entity keyed by id cannot do.
 */
@Component(id = "tour-watches")
public class TourWatchView extends View {

  public record WatchEntry(
      String watchId,
      String artistName,
      String marketName,
      boolean active,
      boolean hasForecast,
      int likelihood,
      String windowStart,
      String windowEnd,
      String confidence,
      String rationale,
      int confirmedVisits) {}

  public record WatchEntries(List<WatchEntry> watches) {}

  @Consume.FromEventSourcedEntity(TourWatchEntity.class)
  public static class TourWatchUpdater extends TableUpdater<WatchEntry> {

    public Effect<WatchEntry> onEvent(TourWatchEvent event) {
      var watchId = updateContext().eventSubject().orElse("");
      return switch (event) {
        case TourWatchEvent.WatchRegistered e -> effects()
            .updateRow(new WatchEntry(
                watchId, e.artist().displayName(), e.market().displayName(),
                true, false, 0, "", "", "", "", 0));

        case TourWatchEvent.WatchDeactivated e -> withRow(row -> new WatchEntry(
            row.watchId(), row.artistName(), row.marketName(), false, row.hasForecast(),
            row.likelihood(), row.windowStart(), row.windowEnd(), row.confidence(),
            row.rationale(), row.confirmedVisits()));

        case TourWatchEvent.ForecastRecomputed e -> withRow(row -> new WatchEntry(
            row.watchId(), row.artistName(), row.marketName(), row.active(), true,
            e.forecast().likelihood(), e.forecast().windowStart().toString(),
            e.forecast().windowEnd().toString(), e.forecast().confidence().name(),
            // Rationale is cleared because it described the superseded estimate; showing the
            // old explanation next to the new number would be worse than showing none.
            "", row.confirmedVisits()));

        case TourWatchEvent.ForecastNarrated e -> withRow(row -> new WatchEntry(
            row.watchId(), row.artistName(), row.marketName(), row.active(), row.hasForecast(),
            row.likelihood(), row.windowStart(), row.windowEnd(), row.confidence(),
            e.rationale(), row.confirmedVisits()));

        case TourWatchEvent.ForecastWithdrawn e -> withRow(row -> new WatchEntry(
            row.watchId(), row.artistName(), row.marketName(), row.active(), false,
            0, "", "", "", "", row.confirmedVisits()));

        case TourWatchEvent.VisitConfirmed e -> withRow(row -> new WatchEntry(
            row.watchId(), row.artistName(), row.marketName(), row.active(), row.hasForecast(),
            row.likelihood(), row.windowStart(), row.windowEnd(), row.confidence(),
            row.rationale(), row.confirmedVisits() + 1));

        default -> effects().ignore();
      };
    }

    private Effect<WatchEntry> withRow(java.util.function.UnaryOperator<WatchEntry> update) {
      var row = rowState();
      return row == null ? effects().ignore() : effects().updateRow(update.apply(row));
    }
  }

  @Query("SELECT * FROM tour_watches WHERE watchId = :watchId")
  public QueryEffect<WatchEntry> getWatch(String watchId) {
    return queryResult();
  }

  @Query("SELECT * AS watches FROM tour_watches WHERE active = true")
  public QueryEffect<WatchEntries> activeWatches() {
    return queryResult();
  }
}
