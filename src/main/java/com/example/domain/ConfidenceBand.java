package com.example.domain;

/** How much weight to put on a forecast, separate from how likely it says the visit is. */
public enum ConfidenceBand {
  LOW,
  MEDIUM,
  HIGH;

  /**
   * A source going dark leaves a hole in the evidence. The forecast built on the remaining
   * sources is not thereby more reliable, so each blind spot costs one level.
   */
  public ConfidenceBand downgrade() {
    return switch (this) {
      case HIGH -> MEDIUM;
      case MEDIUM -> LOW;
      case LOW -> LOW;
    };
  }
}
