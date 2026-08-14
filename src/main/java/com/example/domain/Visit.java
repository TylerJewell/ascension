package com.example.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A confirmed appearance in the market, carrying every date of that visit.
 *
 * <p>A multi-night run is one visit with several dates rather than several visits, so a fan is
 * told once that the band is coming and then which nights are available.
 */
public record Visit(
    String visitId,
    VenueRef venue,
    List<LocalDate> dates,
    Instant onsaleAt,
    List<PresaleWindow> presales,
    VisitStatus status,
    String sourceUrl) {

  public Visit {
    if (visitId == null || visitId.isBlank()) {
      throw new IllegalArgumentException("Visit id is required");
    }
    if (dates == null || dates.isEmpty()) {
      throw new IllegalArgumentException("A visit must have at least one date");
    }
    if (sourceUrl == null || sourceUrl.isBlank()) {
      throw new IllegalArgumentException("A confirmed visit must carry the source that announced it");
    }
    dates = dates.stream().sorted().toList();
    presales = presales == null ? List.of() : List.copyOf(presales);
  }

  public Visit withStatus(VisitStatus next) {
    if (!status.canTransitionTo(next)) {
      throw new IllegalArgumentException("Cannot move visit from " + status + " to " + next);
    }
    return new Visit(visitId, venue, dates, onsaleAt, presales, next, sourceUrl);
  }

  public Visit withDates(List<LocalDate> newDates) {
    return new Visit(visitId, venue, newDates, onsaleAt, presales, status, sourceUrl);
  }

  /** Presales the fan qualifies for, given the memberships and relationships he has declared. */
  public List<PresaleWindow> presalesFor(FanProfile profile) {
    return presales.stream().filter(p -> profile.satisfiesRequirement(p.requirement())).toList();
  }
}
