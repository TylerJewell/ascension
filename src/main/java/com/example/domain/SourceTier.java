package com.example.domain;

/**
 * Reliability tier of a source, which governs what its signals are permitted to do.
 *
 * <p>Only a tier A source speaks for the artist, the venue, the promoter, or the official
 * ticketing outlet, so only a tier A signal may confirm a visit. Everything else can move a
 * forecast and nothing more.
 */
public enum SourceTier {
  A(1.0),
  B(0.6),
  C(0.3);

  private final double weight;

  SourceTier(double weight) {
    this.weight = weight;
  }

  /** Multiplier applied to a signal's contribution, reflecting how much the source is trusted. */
  public double weight() {
    return weight;
  }

  public boolean canConfirm() {
    return this == A;
  }
}
