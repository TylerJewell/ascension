package com.example.domain;

import java.time.Instant;

/**
 * One observation from one public source at one moment.
 *
 * <p>A signal that cannot say where it came from and when is not constructible. Everything the
 * fan is eventually shown traces back to one of these, so provenance is a constructor precondition
 * rather than something checked at the display layer.
 */
public record Signal(
    String signalId,
    String sourceId,
    SourceTier sourceTier,
    String sourceUrl,
    Instant observedAt,
    SignalKind kind,
    String summary,
    String excerpt,
    Influence influence,
    boolean dismissed) {

  public Signal {
    if (signalId == null || signalId.isBlank()) {
      throw new IllegalArgumentException("Signal id is required");
    }
    if (sourceUrl == null || sourceUrl.isBlank()) {
      throw new IllegalArgumentException("Signal must carry the public source it came from");
    }
    if (observedAt == null) {
      throw new IllegalArgumentException("Signal must carry the time it was observed");
    }
    if (sourceTier == null || kind == null || influence == null) {
      throw new IllegalArgumentException("Signal tier, kind, and influence are required");
    }
  }

  public Signal asDismissed() {
    return new Signal(
        signalId, sourceId, sourceTier, sourceUrl, observedAt, kind, summary, excerpt, influence, true);
  }

  /** Whether this signal is entitled to turn a forecast into a confirmed visit. */
  public boolean canConfirmVisit() {
    return !dismissed && kind.isAnnouncement() && sourceTier.canConfirm();
  }

  /** Signed contribution to the likelihood score: kind sets the direction, tier and influence the size. */
  public double contribution() {
    if (dismissed) {
      return 0.0;
    }
    return kind.baseScore() * influence.magnitude() * sourceTier.weight();
  }
}
