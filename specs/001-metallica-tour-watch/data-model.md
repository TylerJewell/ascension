# Phase 1 Data Model: Metallica Chicago Tour Watch & Free-Ticket Agent

**Feature**: `001-metallica-tour-watch` | **Date**: 2026-08-14

Domain types live in `com.example.domain` and are free of framework types (Principle II — domain independence). Entity state is derived from events; nothing below is stored in a form that can disagree with its own history.

---

## TourWatch — Event Sourced Entity

**Key**: `watchId` (`{artistSlug}:{marketSlug}`, e.g. `metallica:chicago`)

### State

| Field | Type | Notes |
|---|---|---|
| `watchId` | String | |
| `artist` | `ArtistRef` | slug + display name |
| `market` | `Market` | slug, display name, centroid, `radiusMiles` |
| `alertThreshold` | int (0–100) | forecast likelihood at which FR-012 fires |
| `active` | boolean | |
| `signals` | `List<Signal>` | append-only; dismissal marks, never removes |
| `currentForecast` | `Forecast` | derived; never set directly |
| `forecastHistory` | `List<Forecast>` | superseded values, newest last (FR-005) |
| `confirmedVisits` | `List<Visit>` | |
| `resolvedOutcomes` | `List<ForecastOutcome>` | retained for calibration (FR-030) |
| `degradedSources` | `Set<String>` | sources currently unreachable; drives the confidence penalty (FR-027) |

### Events

| Event | Carries |
|---|---|
| `WatchRegistered` | artist, market, radius, threshold |
| `WatchDeactivated` | reason |
| `SignalObserved` | signal |
| `SignalDismissed` | signalId, dismissedBy, reason (FR-029) |
| `ForecastRecomputed` | new forecast, superseded forecast |
| `VisitConfirmed` | visit |
| `VisitChanged` | visitId, new status, changed fields (FR-010) |
| `SourceDegraded` / `SourceRestored` | sourceId (FR-027) |
| `ForecastResolved` | outcome (FR-030) |

### Invariants

- `VisitConfirmed` is rejected unless the originating signal's source tier is **A** (FR-007). This is a command-handler rejection, not a filter — an attempt to confirm from a Tier B/C signal is an error, not a silent no-op.
- `ForecastRecomputed` is rejected if the resulting forecast cites zero non-dismissed signals (FR-004). A forecast with no evidence does not exist in the journal.
- Confirming a visit whose `visitId` already exists produces `VisitChanged`, never a second `VisitConfirmed` (FR-010, and the mechanism behind FR-014's no-duplicate-alerts).
- `radiusMiles` defaults to 50 and must be positive (FR-008).
- Dismissing an already-dismissed signal is idempotent.

---

## Signal — Value Object

| Field | Type | Notes |
|---|---|---|
| `signalId` | String | |
| `sourceId` | String | into the source registry |
| `sourceTier` | `A` \| `B` \| `C` | reliability tier (R2) |
| `sourceUrl` | String | required, public (FR-004) |
| `observedAt` | Instant | required (FR-004) |
| `kind` | `SignalKind` | see below |
| `summary` | String | human-readable, from `SignalInterpreterAgent` |
| `influence` | `STRONG_POSITIVE` \| `POSITIVE` \| `NEUTRAL` \| `NEGATIVE` \| `STRONG_NEGATIVE` | |
| `dismissed` | boolean | |

`SignalKind`: `ROUTING_GAP_ADJACENT`, `ROUTING_PAST_WITHOUT_STOP`, `VENUE_HOLD`, `OFFICIAL_TEASER`, `RELEASE_CYCLE_ACTIVE`, `CADENCE_ELAPSED`, `HIATUS_DECLARED`, `OFFICIAL_ANNOUNCEMENT`.

**Validation**: `sourceUrl` and `observedAt` are mandatory — a signal that cannot say where it came from and when cannot be constructed. Only `OFFICIAL_ANNOUNCEMENT` from a Tier A source may drive a confirmation.

---

## Forecast — Value Object

| Field | Type | Notes |
|---|---|---|
| `forecastId` | String | |
| `likelihood` | int (0–100) | computed by the scorer, never by a model (R3) |
| `windowStart` / `windowEnd` | LocalDate | expected *announcement* window, not show date |
| `confidence` | `LOW` \| `MEDIUM` \| `HIGH` | reduced when sources are degraded (FR-027) |
| `citedSignalIds` | `List<String>` | must be non-empty (FR-004) |
| `rationale` | String | from `ForecastNarratorAgent` |
| `computedAt` | Instant | |

**Validation**: `citedSignalIds` non-empty; every cited id must exist on the watch and not be dismissed; `windowStart <= windowEnd`.

---

## Visit — Value Object

| Field | Type | Notes |
|---|---|---|
| `visitId` | String | |
| `venue` | `Venue` | name, city, distance from market centroid |
| `dates` | `List<LocalDate>` | multiple dates = one visit (FR-009) |
| `onsaleAt` | Instant | |
| `presales` | `List<PresaleWindow>` | name, opens, closes, eligibility requirement |
| `status` | `ANNOUNCED` \| `RESCHEDULED` \| `POSTPONED` \| `CANCELLED` | |
| `sourceUrl` | String | Tier A origin |

**Validation**: `dates` non-empty; venue must fall within the watch's radius (FR-008).

**Transitions**: `ANNOUNCED → RESCHEDULED | POSTPONED | CANCELLED`; `POSTPONED → CANCELLED | RESCHEDULED`; `CANCELLED` is terminal. Every transition emits `VisitChanged` and triggers an alert (FR-010).

---

## TicketOpportunity — Event Sourced Entity

**Key**: `opportunityId`

### State

| Field | Type | Notes |
|---|---|---|
| `opportunityId` | String | |
| `visitId` | String | may reference a forecast rather than a confirmed visit |
| `title` | String | |
| `authority` | `Authority` | name + category: `BROADCASTER`, `FAN_CLUB`, `CHARITY`, `VENUE_PROMOTER`, `SPONSOR` (R4) |
| `sourceUrl` / `entryUrl` | String | discovery source and official entry link (FR-016) |
| `entryMethod` | String | |
| `eligibilityRules` | `List<EligibilityRule>` | |
| `deadline` | Instant | |
| `disclosedOdds` | String, nullable | only when the promoter states them |
| `effortMinutes` | int | |
| `status` | `OpportunityStatus` | |
| `lastVerifiedAt` | Instant | FR-018 |
| `rejectionReason` | String, nullable | FR-019 |
| `ineligibilityReason` | String, nullable | FR-017 |

`OpportunityStatus`: `DISCOVERED`, `ACTIONABLE`, `INELIGIBLE`, `REJECTED`, `INTERESTED`, `ENTERED`, `WON`, `DISMISSED`, `CLOSED`, `EXPIRED`, `UNREACHABLE`.

### Events

`OpportunityDiscovered`, `OpportunityScreened` (attribution outcome), `EligibilityEvaluated`, `OpportunityVerified`, `MarkedInterested`, `MarkedEntered`, `MarkedWon`, `Dismissed`, `OpportunityClosed`, `ReminderScheduled`, `ReminderSent`.

### Invariants

- Reaching `ACTIONABLE` requires a non-null `authority` in one of the five categories (FR-019). Anything else lands in `REJECTED` with a reason recorded — never dropped silently.
- Reaching `ACTIONABLE` requires all `eligibilityRules` to evaluate satisfied against the `FanProfile`; otherwise `INELIGIBLE` with the specific unmet rule named (FR-017).
- `MarkedInterested` schedules a reminder timer at `deadline - 24h` (FR-020). If the deadline is already inside 24 hours, the reminder fires immediately rather than being skipped — SC-006 admits no exceptions.
- `MarkedEntered` and `Dismissed` cancel any pending reminder.
- `deadline` must be in the future at discovery; a past-deadline opportunity is `EXPIRED` on arrival.

---

## EligibilityRule — Value Object

| Field | Type |
|---|---|
| `ruleId` | String |
| `kind` | `RESIDENCY` \| `AGE_MINIMUM` \| `MEMBERSHIP` \| `MEMBERSHIP_TENURE` \| `SPONSOR_RELATIONSHIP` \| `PHYSICAL_PRESENCE` |
| `parameter` | String |
| `description` | String |

Evaluated as deterministic rule matching against `FanProfile` — not by a model (R5).

---

## FanProfile — Key Value Entity

**Key**: `fanId`

| Field | Type | Notes |
|---|---|---|
| `fanId` | String | |
| `displayName` | String | |
| `homeMarket` | String | |
| `ageBand` | `UNDER_18` \| `A18_20` \| `A21_PLUS` | band, not birthdate — the minimum needed to evaluate `AGE_MINIMUM` |
| `residencyState` | String | |
| `fanClubMemberships` | `List<Membership>` | org, since date |
| `sponsorRelationships` | `List<String>` | |
| `alertWebhookUrl` | String | R8 |

**Validation**: no payment field exists on this type, or anywhere in the model (FR-026 — enforced by exit condition `no-stored-payment-credentials`). Attributes are self-declared and never inferred from third parties (Assumption 6).

---

## AlertLedger — Key Value Entity

**Key**: `watchId`

| Field | Type | Notes |
|---|---|---|
| `sentFingerprints` | `Set<String>` | hash of (event kind, subject id, material facts) |
| `lastSentAt` | Instant | |

**Invariant**: `AlertDispatchConsumer` delivers only when the fingerprint is absent (FR-014). Because the fingerprint covers material facts rather than event identity, a genuine change re-alerts while a redelivery does not.

---

## ForecastOutcome — Value Object

| Field | Type | Notes |
|---|---|---|
| `forecastId` | String | |
| `statedLikelihood` | int | the number as forecast |
| `confidenceBand` | String | decile bucket, e.g. `70-79` |
| `windowEnd` | LocalDate | |
| `outcome` | `ANNOUNCED_IN_WINDOW` \| `NOT_ANNOUNCED_IN_WINDOW` \| `ANNOUNCED_OUTSIDE_WINDOW` | |
| `resolvedAt` | Instant | |

`CalibrationView` groups these by `confidenceBand` and compares observed hit rate to the band midpoint, flagging divergence beyond 15 points (FR-031, SC-003).

---

## Relationships

```text
FanProfile ──1:N──> TourWatch ──1:N──> Signal
                        │                 │
                        │ derives         │ cites
                        ▼                 │
                    Forecast <────────────┘
                        │ resolves to
                        ▼
                 ForecastOutcome

TourWatch ──1:N──> Visit ──1:N──> TicketOpportunity ──1:N──> EligibilityRule
                                          │
                                          │ evaluated against
                                          ▼
                                     FanProfile

TourWatch ──1:1──> AlertLedger
```
