package com.example.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * An estimate that the artist will play the market, and when the announcement is expected.
 *
 * <p>The window is when a date is expected to be <em>announced</em>, not when the show is. A
 * forecast never carries show dates; only a {@link Visit} does. The distinction is what keeps a
 * prediction from being read as news.
 */
public record Forecast(
    String forecastId,
    int likelihood,
    LocalDate windowStart,
    LocalDate windowEnd,
    ConfidenceBand confidence,
    List<String> citedSignalIds,
    String rationale,
    Instant computedAt) {

  public Forecast {
    if (likelihood < 0 || likelihood > 100) {
      throw new IllegalArgumentException("Likelihood must be 0-100, got " + likelihood);
    }
    if (citedSignalIds == null || citedSignalIds.isEmpty()) {
      throw new IllegalArgumentException("A forecast must cite at least one signal");
    }
    if (windowStart == null || windowEnd == null || windowStart.isAfter(windowEnd)) {
      throw new IllegalArgumentException("Announcement window start must not be after its end");
    }
    citedSignalIds = List.copyOf(citedSignalIds);
  }

  public Forecast withRationale(String newRationale) {
    return new Forecast(
        forecastId, likelihood, windowStart, windowEnd, confidence, citedSignalIds, newRationale, computedAt);
  }

  /** Decile bucket the calibration ledger groups this forecast into, e.g. "70-79". */
  public String confidenceBandLabel() {
    int floor = Math.min(90, (likelihood / 10) * 10);
    return floor + "-" + (floor + 9);
  }
}
