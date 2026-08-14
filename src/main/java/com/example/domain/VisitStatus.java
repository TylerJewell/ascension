package com.example.domain;

import java.util.Set;

/** Lifecycle of an announced appearance. */
public enum VisitStatus {
  ANNOUNCED,
  RESCHEDULED,
  POSTPONED,
  CANCELLED;

  public boolean canTransitionTo(VisitStatus next) {
    return switch (this) {
      case ANNOUNCED -> Set.of(RESCHEDULED, POSTPONED, CANCELLED).contains(next);
      case RESCHEDULED -> Set.of(RESCHEDULED, POSTPONED, CANCELLED).contains(next);
      case POSTPONED -> Set.of(RESCHEDULED, CANCELLED).contains(next);
      case CANCELLED -> false;
    };
  }
}
