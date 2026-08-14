# ADR 0001: Compute the forecast likelihood in code, not in the model

**Status**: Accepted
**Date**: 2026-08-14
**Feature**: 001-metallica-tour-watch

## Context

The system must tell a fan how likely it is that an artist will play their market. A language model is already in the design to read promoter prose and venue calendars into structure, so the obvious shortcut is to ask it for the probability at the same time — one call, much less code.

Two requirements make that shortcut expensive.

SC-003 holds the system to calibration: across at least twenty resolved forecasts, the observed hit rate in each confidence band must land within 15 points of that band's stated confidence. Language models are poorly calibrated when asked to emit numeric probabilities — they cluster on round, confident-sounding values and do not move stably in response to marginal evidence. When the ledger reports that the 70–79 band is running at 50%, a model-emitted number offers nothing to adjust.

FR-029 lets a fan dismiss a signal he knows is wrong and see the estimate recomputed. If recomputation is a second model call, the same dismissal can produce a different answer each time it runs, and a fan who corrects the system cannot trust the correction.

## Decision

Split the responsibilities. `ForecastScorer` is a pure function over the live signal set and the current blind-spot count, producing the likelihood, announcement window, and confidence band. The model's role is confined to two jobs it is genuinely good at: `SignalInterpreterAgent` turns unstructured content into typed signals, and `ForecastNarratorAgent` explains a forecast that has already been computed.

`ForecastNarratorAgent` receives the likelihood as an input and is instructed never to restate, adjust, or contradict it.

The forecast id is derived from the sorted set of cited signal ids rather than randomly generated, so identical evidence always produces an identical forecast.

## Consequences

Weights live in `SignalKind` and `SourceTier` as reviewable data, so a miscalibrated band has something concrete to tune. Recomputation after a dismissal is exact and replayable, which `TourWatchEntityTest.recomputesDeterministicallyWhenASignalIsDismissed` asserts.

The scorer is framework-free and unit-testable with no runtime, which is why it holds the domain's most consequential judgments.

The cost is a hand-authored weight table seeded from judgment rather than data. Its initial values will be wrong; US4's calibration ledger exists to find out how wrong. Adding a genuinely new kind of evidence means editing code rather than editing a prompt.
