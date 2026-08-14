package com.example.domain;

/**
 * How strongly an observed signal pushes the estimate, as judged when the signal was read.
 *
 * <p>This scales the kind's base score. A venue hold reported as tentative and one reported as
 * firm are the same {@link SignalKind} but not the same evidence.
 */
public enum Influence {
  STRONG_POSITIVE(1.0),
  POSITIVE(0.6),
  NEUTRAL(0.0),
  NEGATIVE(0.6),
  STRONG_NEGATIVE(1.0);

  private final double magnitude;

  Influence(double magnitude) {
    this.magnitude = magnitude;
  }

  /**
   * Magnitude only. Direction lives in the signal kind's base score, so a negative kind reported
   * as STRONG_NEGATIVE stays negative rather than flipping sign twice.
   */
  public double magnitude() {
    return magnitude;
  }
}
