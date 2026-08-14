# Tasks: Metallica Chicago Tour Watch & Free-Ticket Agent

**Input**: Design documents from `/specs/001-metallica-tour-watch/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included. Constitution v1.0.0 Principle III makes tests mandatory for every behavioral change, and two tests (`EvidenceRequiredTest`, `OutboundHostPolicyTest`) carry project exit conditions and must exist by name.

**Organization**: Grouped by user story so each is independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: `[US1]`–`[US4]`, mapping to the spec's prioritized user stories

## Path Conventions

Single Akka service. `src/main/java/com/example/{domain,application,api}`, tests mirroring under `src/test/java/com/example/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configuration the service needs before any component exists

- [ ] T001 Configure dev port, Gemini provider, and guardrail registration in src/main/resources/application.conf
- [ ] T002 Create the outbound source registry with host, tier, path policy, and rate limit per source in src/main/resources/sources.conf
- [ ] T003 [P] Verify Java ignore patterns (target/, *.class, *.jar, .idea/, .env*) in .gitignore
- [ ] T004 [P] Create .dockerignore covering target/, .git/, .env*, and specs/

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Framework-free domain types, the pure forecast scorer, and the single outbound boundary. Every user story depends on these.

**⚠️ CRITICAL**: No user story work begins until this phase completes. `SourceGateway` lands here specifically so no later component is ever written with an unbounded HTTP client available to it.

- [ ] T005 [P] Define SourceTier enum (A, B, C) in src/main/java/com/example/domain/SourceTier.java
- [ ] T006 [P] Define SignalKind enum with the eight declared kinds in src/main/java/com/example/domain/SignalKind.java
- [ ] T007 [P] Define Influence enum (STRONG_POSITIVE..STRONG_NEGATIVE) in src/main/java/com/example/domain/Influence.java
- [ ] T008 [P] Define Market record (slug, displayName, centroidLat, centroidLon, radiusMiles) with positive-radius validation in src/main/java/com/example/domain/Market.java
- [ ] T009 [P] Define ArtistRef and VenueRef records in src/main/java/com/example/domain/ArtistRef.java and src/main/java/com/example/domain/VenueRef.java
- [ ] T010 Define Signal record requiring non-null sourceUrl and observedAt in src/main/java/com/example/domain/Signal.java (depends on T005, T006, T007)
- [ ] T011 [P] Define ConfidenceBand enum (LOW, MEDIUM, HIGH) in src/main/java/com/example/domain/ConfidenceBand.java
- [ ] T012 Define Forecast record rejecting empty citedSignalIds and windowStart after windowEnd in src/main/java/com/example/domain/Forecast.java (depends on T011)
- [ ] T013 Implement ForecastScorer as a pure function over a signal set producing likelihood, window, and confidence, with the R3 weight table and a degraded-source confidence penalty, in src/main/java/com/example/domain/ForecastScorer.java (depends on T010, T012)
- [ ] T014 Write ForecastScorerTest covering each signal kind's direction, dismissed-signal exclusion, empty-signal rejection, and confidence reduction under degraded sources in src/test/java/com/example/domain/ForecastScorerTest.java (depends on T013)
- [ ] T015 Implement SourceRegistry loading sources.conf into typed entries (sourceId, host, tier, allowedPaths, rateLimit) in src/main/java/com/example/application/SourceRegistry.java (depends on T002, T005)
- [ ] T016 Implement SourceGateway enforcing host allowlist, GET/HEAD only, path policy, and rate limits, returning a typed FetchResult with an Unavailable variant and no fallback path, in src/main/java/com/example/application/SourceGateway.java (depends on T015)
- [ ] T017 Write OutboundHostPolicyTest asserting an undeclared host is refused, a disallowed path is refused, no non-safe method is constructible, and an unavailable source yields Unavailable rather than a retry against another host, in src/test/java/com/example/application/OutboundHostPolicyTest.java (depends on T016)
- [ ] T018 [P] Define FanProfile record with ageBand, residencyState, memberships, sponsor relationships, and webhook URL — and no payment field of any kind — in src/main/java/com/example/domain/FanProfile.java
- [ ] T019 Implement FanProfileEntity as a Key Value Entity in src/main/java/com/example/application/FanProfileEntity.java (depends on T018)
- [ ] T020 Write FanProfileEntityTest covering create, update, and read-before-create in src/test/java/com/example/application/FanProfileEntityTest.java (depends on T019)

**Checkpoint**: Domain vocabulary, the scorer, and the outbound boundary exist and are tested. User stories can begin.

---

## Phase 3: User Story 1 — Know before the world knows (Priority: P1) 🎯 MVP

**Goal**: A registered watch observes public sources unattended, maintains a forecast with cited evidence, confirms a real date from a Tier A source, and alerts the fan with time remaining to act.

**Independent Test**: Register a watch, replay a fixture sequence of source content through the scout workflow, and verify the forecast rises with the evidence and a confirmation alert fires exactly once carrying date, venue, onsale time, and a computed time-to-act.

### Tests for User Story 1

- [ ] T021 [P] [US1] Write TourWatchEntityTest for registration, signal append, forecast recompute, and Tier-B confirmation rejection in src/test/java/com/example/application/TourWatchEntityTest.java
- [ ] T022 [P] [US1] Write EvidenceRequiredTest asserting a narration citing zero signals, or citing an id absent from the input set, is refused and the forecast withdrawn, in src/test/java/com/example/application/EvidenceRequiredTest.java
- [ ] T023 [P] [US1] Write AlertDedupeTest asserting an unchanged event redelivered produces no second alert while a material fact change does, in src/test/java/com/example/application/AlertDedupeTest.java
- [ ] T024 [P] [US1] Write TourScoutWorkflowTest asserting a failed source step degrades confidence and continues rather than aborting the cycle, in src/test/java/com/example/application/TourScoutWorkflowTest.java

### Implementation for User Story 1

- [ ] T025 [P] [US1] Define PresaleWindow record in src/main/java/com/example/domain/PresaleWindow.java
- [ ] T026 [US1] Define Visit record with multi-date support and status transition rules in src/main/java/com/example/domain/Visit.java (depends on T009, T025)
- [ ] T027 [US1] Define TourWatchEvent sealed interface with the nine declared events in src/main/java/com/example/domain/TourWatchEvent.java (depends on T010, T012, T026)
- [ ] T028 [US1] Define TourWatchState with event application, signal dismissal marking, and forecast history in src/main/java/com/example/domain/TourWatchState.java (depends on T027)
- [ ] T029 [US1] Implement TourWatchEntity as an Event Sourced Entity, rejecting confirmation from non-Tier-A signals and rejecting forecasts citing zero signals, in src/main/java/com/example/application/TourWatchEntity.java (depends on T028)
- [ ] T030 [P] [US1] Implement SignalInterpreterAgent returning typed signal candidates with a required verbatim excerpt, and permitting an empty result, in src/main/java/com/example/application/SignalInterpreterAgent.java
- [ ] T031 [P] [US1] Implement ForecastNarratorAgent producing rationale and citedSignalIds only, never a likelihood or a show date, in src/main/java/com/example/application/ForecastNarratorAgent.java
- [ ] T032 [US1] Implement EvidenceGuardrail as a TextGuardrail rejecting empty or out-of-set citedSignalIds in src/main/java/com/example/application/EvidenceGuardrail.java (depends on T031)
- [ ] T033 [US1] Implement TourScoutWorkflow stepping fetch → interpret → append → rescore → narrate, with per-source failure emitting SourceDegraded and continuing, in src/main/java/com/example/application/TourScoutWorkflow.java (depends on T016, T029, T030, T031)
- [ ] T034 [US1] Implement ScoutTickTimer driving the recurring scout cycle in src/main/java/com/example/application/ScoutTickTimer.java (depends on T033)
- [ ] T035 [P] [US1] Implement AlertLedgerEntity as a Key Value Entity holding sent fingerprints in src/main/java/com/example/application/AlertLedgerEntity.java
- [ ] T036 [US1] Implement AlertDispatchConsumer computing time-to-act at delivery, fingerprinting on material facts, and delivering via SourceGateway-independent outbound webhook, in src/main/java/com/example/application/AlertDispatchConsumer.java (depends on T029, T035)
- [ ] T037 [US1] Implement TourWatchView exposing current forecast and confirmed visits by watchId in src/main/java/com/example/application/TourWatchView.java (depends on T029)
- [ ] T038 [US1] Define FanApiTypes request/response records with the mandatory FORECAST|CONFIRMED discriminator in src/main/java/com/example/api/FanApiTypes.java
- [ ] T039 [US1] Implement FanEndpoint routes for profile create, watch register, watch deactivate, and watch read in src/main/java/com/example/api/FanEndpoint.java (depends on T019, T029, T037, T038)
- [ ] T040 [US1] Write FanEndpointIntegrationTest covering register → forecast → confirm → alert in src/test/java/com/example/api/FanEndpointIntegrationTest.java (depends on T039)

**Checkpoint**: US1 is a viable MVP on its own — a fan who registers a watch hears about the Chicago date before general onsale, with evidence attached.

---

## Phase 4: User Story 2 — Free ways in (Priority: P2)

**Goal**: Legitimate free-ticket opportunities tied to a forecast or confirmed visit are discovered, attributed, eligibility-checked, kept fresh, and reminded on before their deadline.

**Independent Test**: With a confirmed visit present, feed fixture offers through the screener and verify attributable offers become actionable with eligibility resolved, unattributable offers land in REJECTED with a reason, and marking one interested schedules a reminder that fires before the deadline.

### Tests for User Story 2

- [ ] T041 [P] [US2] Write EligibilityEvaluatorTest covering each rule kind, satisfied and unmet, with the specific unmet rule named, in src/test/java/com/example/domain/EligibilityEvaluatorTest.java
- [ ] T042 [P] [US2] Write TicketOpportunityEntityTest covering the status lifecycle, unattributable rejection, and past-deadline expiry on arrival, in src/test/java/com/example/application/TicketOpportunityEntityTest.java
- [ ] T043 [P] [US2] Write ReminderSchedulingTest asserting a reminder is scheduled on interest, cancelled on entered or dismissed, and fires immediately when the deadline is already inside 24 hours, in src/test/java/com/example/application/ReminderSchedulingTest.java

### Implementation for User Story 2

- [ ] T044 [P] [US2] Define AuthorityCategory enum and Authority record in src/main/java/com/example/domain/Authority.java
- [ ] T045 [P] [US2] Define EligibilityRule record and RuleKind enum in src/main/java/com/example/domain/EligibilityRule.java
- [ ] T046 [P] [US2] Define OpportunityStatus enum with the eleven declared states in src/main/java/com/example/domain/OpportunityStatus.java
- [ ] T047 [US2] Implement EligibilityEvaluator as deterministic rule matching against FanProfile in src/main/java/com/example/domain/EligibilityEvaluator.java (depends on T018, T045)
- [ ] T048 [US2] Define OpportunityEvent sealed interface and OpportunityState with event application in src/main/java/com/example/domain/OpportunityEvent.java and src/main/java/com/example/domain/OpportunityState.java (depends on T044, T045, T046)
- [ ] T049 [US2] Implement TicketOpportunityEntity as an Event Sourced Entity gating ACTIONABLE on both attribution and eligibility in src/main/java/com/example/application/TicketOpportunityEntity.java (depends on T047, T048)
- [ ] T050 [US2] Implement OpportunityScreenerAgent extracting authority, entry method, deadline, and eligibility rules, permitted to return a null authority meaning reject, in src/main/java/com/example/application/OpportunityScreenerAgent.java (depends on T044, T045)
- [ ] T051 [US2] Implement AttributionGuardrail rejecting a non-null authority with empty attributionEvidence in src/main/java/com/example/application/AttributionGuardrail.java (depends on T050)
- [ ] T052 [US2] Implement OpportunitySweepWorkflow re-verifying open opportunities and marking closed, expired, or unreachable in src/main/java/com/example/application/OpportunitySweepWorkflow.java (depends on T016, T049)
- [ ] T053 [US2] Implement ReminderTimer scheduling at deadline minus 24 hours, cancelling on entered or dismissed in src/main/java/com/example/application/ReminderTimer.java (depends on T049)
- [ ] T054 [US2] Implement OpportunityView queryable by visit, status, and deadline in src/main/java/com/example/application/OpportunityView.java (depends on T049)
- [ ] T055 [US2] Add opportunity list and status-change routes to src/main/java/com/example/api/FanEndpoint.java, with no route that enters, purchases, or reserves anything (depends on T049, T054)
- [ ] T056 [US2] Extend AlertDispatchConsumer to deliver DEADLINE_REMINDER alerts in src/main/java/com/example/application/AlertDispatchConsumer.java (depends on T036, T053)

**Checkpoint**: US1 and US2 both work independently.

---

## Phase 5: User Story 3 — Show me why (Priority: P3)

**Goal**: Every forecast and opportunity is inspectable down to its sources, and a signal the fan knows is wrong can be dismissed with the forecast recomputing deterministically.

**Independent Test**: Read the evidence for a forecast, confirm every signal carries a public source and observation time, dismiss one, and confirm the forecast recomputes to the same value every time the same dismissal is replayed.

### Tests for User Story 3

- [ ] T057 [P] [US3] Write SignalDismissalTest asserting recomputation is deterministic across replays and that dismissing the last cited signal withdraws the forecast rather than serving it empty, in src/test/java/com/example/application/SignalDismissalTest.java

### Implementation for User Story 3

- [ ] T058 [US3] Add the evidence route returning every signal with source, tier, observation time, influence, and dismissed flag to src/main/java/com/example/api/FanEndpoint.java (depends on T039)
- [ ] T059 [US3] Add the signal dismissal route returning the recomputed forecast, and returning a conflict when dismissal would leave zero cited signals, to src/main/java/com/example/api/FanEndpoint.java (depends on T029, T058)
- [ ] T060 [US3] Surface degradedSources on the watch read response so a blind spot is visible rather than silent, in src/main/java/com/example/api/FanApiTypes.java and src/main/java/com/example/api/FanEndpoint.java (depends on T038, T039)
- [ ] T061 [US3] Add opportunity source and last-verified fields to the opportunity response in src/main/java/com/example/api/FanApiTypes.java (depends on T055)

**Checkpoint**: All forecasts and opportunities are inspectable and correctable.

---

## Phase 6: User Story 4 — Keep itself honest (Priority: P4)

**Goal**: Forecasts are resolved against reality and the system reports its own hit rate by confidence band, flagging miscalibration.

**Independent Test**: Seed resolved forecasts with known outcomes and verify hit rate is grouped by band, divergence beyond 15 points is flagged, and fewer than twenty resolutions reports insufficient data rather than a misleading number.

### Tests for User Story 4

- [ ] T062 [P] [US4] Write CalibrationViewTest covering band grouping, the 15-point divergence flag, and the insufficient-data response below twenty resolutions, in src/test/java/com/example/application/CalibrationViewTest.java

### Implementation for User Story 4

- [ ] T063 [P] [US4] Define ForecastOutcome record with stated likelihood, confidence band, and the three outcome values in src/main/java/com/example/domain/ForecastOutcome.java
- [ ] T064 [US4] Implement forecast resolution on window expiry or confirmation, emitting ForecastResolved, in src/main/java/com/example/application/TourWatchEntity.java (depends on T029, T063)
- [ ] T065 [US4] Implement ForecastResolutionTimer resolving forecasts whose announcement window has passed in src/main/java/com/example/application/ForecastResolutionTimer.java (depends on T064)
- [ ] T066 [US4] Implement CalibrationView grouping outcomes by confidence band with observed hit rate in src/main/java/com/example/application/CalibrationView.java (depends on T063, T064)
- [ ] T067 [US4] Add the calibration route with the insufficient-data guard to src/main/java/com/example/api/FanEndpoint.java (depends on T066)

**Checkpoint**: All four stories independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T068 Verify terms of use and machine-readable access policy for every source before adding it to src/main/resources/sources.conf, recording the verification date per source; a source that fails verification is not added
- [ ] T069 [P] Tune the R3 scoring weights in src/main/java/com/example/domain/ForecastScorer.java against the calibration ledger once resolutions exist
- [ ] T070 [P] Add structured logging for scout cycles, source degradation, and alert delivery across src/main/java/com/example/application/
- [ ] T071 Run the quickstart walkthrough in specs/001-metallica-tour-watch/quickstart.md end to end against a locally running service on port 9008
- [ ] T072 Run the project exit conditions and confirm forecast-carries-evidence and outbound-hosts-read-only pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup — **blocks all user stories**
- **US1 (Phase 3)**: depends on Foundational
- **US2 (Phase 4)**: depends on Foundational; consumes US1's `Visit` but is testable against a seeded visit
- **US3 (Phase 5)**: depends on US1 (inspects US1's forecast) and touches US2's response shape in T061
- **US4 (Phase 6)**: depends on US1 (resolves US1's forecasts)
- **Polish (Phase 7)**: depends on all desired stories

### User Story Dependencies

- **US1 (P1)**: independent after Foundational. The MVP.
- **US2 (P2)**: independent after Foundational when given a seeded visit; integrates with US1 in production.
- **US3 (P3)**: needs US1's forecast to inspect. Not independent of US1 by nature — inspection requires something to inspect.
- **US4 (P4)**: needs US1's forecasts to resolve. Same reasoning.

### Within Each User Story

Tests written first and failing → domain records → entity → agents and guardrails → workflow and timers → view → endpoint → integration test.

### Parallel Opportunities

- Phase 1: T003, T004 together.
- Phase 2: T005–T009 together (all independent records/enums); T018 alongside any of them.
- Phase 3: T021–T024 together; T025 alongside them; T030 and T031 together; T035 alongside T033.
- Phase 4: T041–T043 together; T044–T046 together.
- Phase 6: T062 and T063 together.
- Across stories: once Phase 2 completes, US1 and US2 can be staffed in parallel; US3 and US4 must wait on US1.

---

## Parallel Example: User Story 1

```bash
# Tests first, all independent files:
Task: "TourWatchEntityTest in src/test/java/com/example/application/TourWatchEntityTest.java"
Task: "EvidenceRequiredTest in src/test/java/com/example/application/EvidenceRequiredTest.java"
Task: "AlertDedupeTest in src/test/java/com/example/application/AlertDedupeTest.java"
Task: "TourScoutWorkflowTest in src/test/java/com/example/application/TourScoutWorkflowTest.java"

# Then the two agents, which share no file:
Task: "SignalInterpreterAgent in src/main/java/com/example/application/SignalInterpreterAgent.java"
Task: "ForecastNarratorAgent in src/main/java/com/example/application/ForecastNarratorAgent.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 Setup
2. Phase 2 Foundational — blocks everything, and `SourceGateway` here is what keeps the conduct boundaries real
3. Phase 3 US1
4. **STOP and VALIDATE**: register a watch, replay fixtures, confirm the alert fires once with evidence
5. Demo

### Incremental Delivery

Setup + Foundational → US1 (MVP) → US2 → US3 → US4. Each adds value without breaking the last.

---

## Notes

- `[P]` means different files with no incomplete dependency.
- T017 and T022 carry project exit conditions. If either is deleted or renamed, the corresponding conduct boundary stops being enforced regardless of what the code does.
- T068 is a research obligation, not a coding task: no source enters `sources.conf` without its terms and access policy verified first.
- Commit after each task or logical group.
