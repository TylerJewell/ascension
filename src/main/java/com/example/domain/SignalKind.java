package com.example.domain;

/**
 * The kinds of observation that bear on whether an artist will play a market.
 *
 * <p>The score each kind carries is the domain's judgment about touring behaviour, kept here as
 * data rather than in a prompt so it can be reviewed, tested, and tuned against the calibration
 * ledger.
 */
public enum SignalKind {
  /** An announced leg leaves a routing gap next to the market. */
  ROUTING_GAP_ADJACENT(25),
  /** An announced leg routes past the market without stopping. */
  ROUTING_PAST_WITHOUT_STOP(-30),
  /** A venue calendar shows a hold matching the artist's production requirements. */
  VENUE_HOLD(25),
  /** An official teaser or countdown referencing the region. */
  OFFICIAL_TEASER(30),
  /** The artist is in an active album or release cycle. */
  RELEASE_CYCLE_ACTIVE(10),
  /** Time since the artist last played the market exceeds their historical cadence. */
  CADENCE_ELAPSED(10),
  /** A publicly stated hiatus, or a health event that stops touring. */
  HIATUS_DECLARED(-40),
  /** A date has actually been announced. */
  OFFICIAL_ANNOUNCEMENT(100);

  private final int baseScore;

  SignalKind(int baseScore) {
    this.baseScore = baseScore;
  }

  public int baseScore() {
    return baseScore;
  }

  /** Only an actual announcement can turn a forecast into a confirmed visit. */
  public boolean isAnnouncement() {
    return this == OFFICIAL_ANNOUNCEMENT;
  }
}
