# ADR 0002: Event-source the watch; use key-value state for everything else

**Status**: Accepted
**Date**: 2026-08-14
**Feature**: 001-metallica-tour-watch

## Context

The specification asks, in user language, for several things that are all the same thing in disguise. Forecasts must be superseded rather than overwritten (FR-005). A fan must be able to inspect every signal behind an estimate, with source and observation time (FR-028). He must be able to dismiss a signal and have the estimate recomputed without it (FR-029). Resolved forecasts must be retained to support calibration (FR-030).

Written as a mutable current-state record, each of these becomes a separate side-table that has to be kept in agreement with the answer being displayed. Any drift between them is invisible to the fan and produces exactly the failure the evidence requirement exists to prevent: a confident number with an explanation that no longer matches it.

Not every entity in the design has this shape. A fan profile and an alert ledger have no history requirement in the spec — only a current value.

## Decision

`TourWatchEntity` is an Event Sourced Entity. Current state is derived from `TourWatchEvent`, so the audit trail is not a parallel record of the answer; it is what the answer is computed from.

Signals are marked dismissed rather than removed, so a fan can see what he dismissed and why.

`FanProfileEntity` and `AlertLedgerEntity` are Key Value Entities. Neither has a history requirement, and Principle IV says take the simpler primitive when it meets the requirement.

Because `applyEvent` must never fail, command handlers hold all validation and the state appliers are total. `TourWatchState.withVisitStatus` no-ops on a transition a command handler would have rejected, rather than throwing during replay.

Events carry computed results rather than inputs — `ForecastRecomputed` carries the `Forecast`, not the signal set — so replay never re-runs the scorer against a different clock.

## Consequences

Evidence inspection, forecast history, and dismissal-with-recompute all fall out of the journal rather than needing dedicated storage. US4's calibration ledger has the resolved outcomes it needs without further design.

`AlertDispatchConsumer` and `TourWatchView` consume the same events, so alerting and querying cannot disagree with the entity about what happened.

The cost is that events are a persisted contract. Renaming one breaks replay, which is why each carries an explicit `@TypeName`. Deriving state by replay is also more expensive than reading a row, which snapshots mitigate.
