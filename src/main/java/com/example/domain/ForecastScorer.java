package com.example.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Turns a set of observed signals into a likelihood and an announcement window.
 *
 * <p>The number is computed here, in code, and never by a model. Two properties depend on that.
 * Calibration is tunable: when the ledger says the 70-79 band is running at 50%, there are weights
 * to move. And recomputation is exact: dismissing a signal and rescoring yields the same forecast
 * every time, which is what lets a fan correct the system and trust the correction.
 *
 * <p>Pure function, no framework types — unit testable with no runtime.
 */
public final class ForecastScorer {

  /**
   * Starting likelihood before any evidence. A major-market visit in the next year is not
   * far-fetched for a touring act, and not likely either.
   */
  static final int PRIOR = 10;

  private static final int MIN_LEAD_DAYS = 14;
  private static final int MAX_WINDOW_DAYS = 180;
  private static final int MIN_WINDOW_DAYS = 30;

  private ForecastScorer() {}

  /**
   * @return the forecast supported by the given signals, or empty when no live signal remains —
   *     there is no such thing as a forecast with nothing behind it.
   */
  public static Optional<Forecast> score(List<Signal> signals, int degradedSourceCount, Instant now) {
    var live = signals.stream().filter(s -> !s.dismissed()).toList();
    if (live.isEmpty()) {
      return Optional.empty();
    }

    double raw = PRIOR + live.stream().mapToDouble(Signal::contribution).sum();
    int likelihood = (int) Math.round(Math.max(0, Math.min(100, raw)));

    LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate windowStart = today.plusDays(MIN_LEAD_DAYS);
    // A stronger signal set implies the announcement is nearer, so the window tightens as
    // likelihood rises rather than staying a fixed six months wide.
    LocalDate windowEnd = today.plusDays(Math.max(MIN_WINDOW_DAYS, MAX_WINDOW_DAYS - likelihood));

    var citedIds = live.stream().map(Signal::signalId).sorted().toList();

    return Optional.of(
        new Forecast(
            forecastId(citedIds),
            likelihood,
            windowStart,
            windowEnd,
            confidenceFor(live.size(), degradedSourceCount),
            citedIds,
            "",
            now));
  }

  /**
   * Confidence tracks how much evidence there is and how much of the source set is currently
   * readable, which is independent of which way that evidence points.
   */
  static ConfidenceBand confidenceFor(int liveSignalCount, int degradedSourceCount) {
    ConfidenceBand band =
        liveSignalCount >= 3 ? ConfidenceBand.HIGH : ConfidenceBand.MEDIUM;
    for (int i = 0; i < degradedSourceCount; i++) {
      band = band.downgrade();
    }
    return band;
  }

  /**
   * Derived from the cited set so the same evidence always yields the same id. A random id would
   * make dismissal replay produce a different forecast each time it ran.
   */
  private static String forecastId(List<String> citedIds) {
    int hash = citedIds.stream().sorted(Comparator.naturalOrder()).toList().hashCode();
    return "fc-" + Integer.toHexString(hash);
  }
}
