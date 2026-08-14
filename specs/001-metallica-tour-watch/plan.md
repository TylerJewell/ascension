# Implementation Plan: Metallica Chicago Tour Watch & Free-Ticket Agent

**Branch**: `001-metallica-tour-watch` | **Date**: 2026-08-14 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-metallica-tour-watch/spec.md`

## Summary

An always-on Akka service that watches public touring signals for a registered artist/market pair, maintains a calibrated likelihood and announcement window, converts a Tier-A official signal into a confirmed visit, and tracks legitimate free-ticket opportunities with their eligibility and deadlines — alerting the fan in time to act.

The technical approach turns on three decisions. **Forecasting is split**: deterministic weighted scoring produces the number, and the model only reads prose into structure and narrates the result, because SC-003 asks for calibration and models do not emit calibrated probabilities. **State is event-sourced** where the spec asks for inspectable history, so the audit trail cannot drift from the answer — it is the answer. **All outbound access is funneled through one gateway**, which is what turns "never acquires a ticket" from a stated policy into a testable property.

## Technical Context

**Language/Version**: Java 25 (Temurin 25.0.2 LTS)
**Primary Dependencies**: Akka SDK (`akka-javasdk-parent` 3.6.3) — no additional runtime dependencies proposed
**Storage**: Akka-managed entity state; Event Sourced Entities for `TourWatch` and `TicketOpportunity`, Key Value Entities for `FanProfile` and `AlertLedger`
**Model provider**: Google AI Gemini (`googleai-gemini`), key from `GOOGLE_AI_GEMINI_API_KEY` — see research R1
**Testing**: JUnit 5 with Akka TestKit; agents tested against recorded fixtures, not live model calls
**Target Platform**: Akka runtime — local dev on port **9008**, Akka platform for deployment
**Project Type**: Backend service, HTTP API only, no UI surface
**Performance Goals**: Confirmation observed → alert delivered within 15 min (SC-001); scout cycle hourly baseline, tightening near a suspected announcement window
**Constraints**: Public sources only, read-only outbound, no payment handling, no automated entry or acquisition (FR-022 through FR-027)
**Scale/Scope**: One fan, one artist, one market at launch (Q2 default). ~12 components, single service, no multi-region requirement

## Constitution Check

*Evaluated against Akka Constitution v1.0.0 before Phase 0 and re-evaluated after Phase 1.*

### I. Akka SDK First (NON-NEGOTIABLE) — **PASS**

Every element of the design maps to an SDK primitive: Event Sourced Entities, Key Value Entities, Workflows, Agents, Consumers, Views, Timed Actions, HTTP Endpoints. No custom state store, scheduler, or messaging layer is introduced.

**Proposed external dependencies: none.** The three capabilities that might have pulled one in were each resolved inside the SDK or the standard library:
- Scheduling → SDK timed actions, not a scheduler library (R7).
- Alert delivery → outbound webhook through the existing gateway, not an email or chat vendor SDK (R8).
- Agent output validation → SDK `TextGuardrail`, not a separate validation framework (R6).

The one open dependency question is the HTTP client used inside `SourceGateway`. The plan is to use what the SDK already provides; if a source requires a client capability the SDK does not expose, that is a dependency decision to justify at that point rather than to pre-approve here.

### II. Design Principles — **PASS**

- **Domain independence**: `com.example.domain` holds `Signal`, `Forecast`, `Visit`, `EligibilityRule`, and the forecast scorer as plain Java. The scorer is a pure function over a signal set — testable with no runtime, which is the point of putting the number there rather than in an agent.
- **API isolation**: `FanEndpoint` declares its own request/response records; `contracts/http-api.md` is written against those, not against entity state. The `kind: FORECAST|CONFIRMED` discriminator exists in the API shape specifically so FR-006 is enforced at the boundary.
- **Single responsibility**: Three agents with disjoint jobs; interpretation, narration, and screening are not merged. Eligibility evaluation and legitimacy attribution stay in deterministic code because they must be testable.
- **Descriptive naming**: `TourWatchEntity`, `TicketOpportunityEntity`, `SignalInterpreterAgent`, `SourceGateway`. No `Manager`, `Service`, or bare `Event`.

### III. Test Coverage — **PASS**

Every component gets tests; two are named and load-bearing because exit conditions depend on them existing by name:
- `EvidenceRequiredTest` — a forecast citing zero signals is refused, not served.
- `OutboundHostPolicyTest` — an undeclared host or unsafe method is refused at the gateway.

`/akka:tasks` will order tests before or alongside each behavioral unit. The forecast scorer is the highest-value unit-test target in the codebase: it is pure, it encodes the domain rules, and it is the thing SC-003 will eventually indict.

### IV. Simplicity — **PASS, with one call worth stating**

Twelve components is more than a trivial service, and the honest check is whether any of them is speculative. Each traces to a requirement that would otherwise be unmet: drop `AlertLedgerEntity` and FR-014 fails; drop `CalibrationView` and FR-031 fails; drop `OpportunitySweepWorkflow` and FR-018 fails.

Choices explicitly made *smaller* than the obvious alternative:
- Eligibility and attribution are rules, not agents — three agents instead of five.
- `FanProfile` and `AlertLedger` are Key Value Entities; neither has a history requirement, so neither is event-sourced.
- No UI. The spec has no UI requirement, and conform already marks the experience and performance conditions not-applicable for that reason.
- Q2 answered as single-user, so no auth, tenancy, or isolation layer is built for a hypothetical second fan.

**Post-Phase 1 re-evaluation**: PASS, unchanged. Phase 1 design added no dependency and no component beyond those listed. The Complexity Tracking table stays empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-metallica-tour-watch/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── http-api.md
│   └── agent-contracts.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/akka:tasks — not created here)
```

### Source Code (repository root)

```text
src/main/java/com/example/
├── domain/
│   ├── TourWatchState.java, TourWatchEvent.java
│   ├── Signal.java, SignalKind.java, SourceTier.java
│   ├── Forecast.java, ForecastScorer.java        # pure; produces the likelihood
│   ├── Visit.java, VenueRef.java, PresaleWindow.java
│   ├── ForecastOutcome.java, ConfidenceBand.java
│   ├── OpportunityState.java, OpportunityEvent.java, OpportunityStatus.java
│   ├── EligibilityRule.java, EligibilityEvaluator.java   # pure; deterministic
│   ├── Authority.java, AuthorityCategory.java
│   └── FanProfile.java, Market.java
├── application/
│   ├── TourWatchEntity.java                      # Event Sourced Entity
│   ├── TicketOpportunityEntity.java              # Event Sourced Entity
│   ├── FanProfileEntity.java                     # Key Value Entity
│   ├── AlertLedgerEntity.java                    # Key Value Entity
│   ├── TourScoutWorkflow.java                    # Workflow
│   ├── OpportunitySweepWorkflow.java             # Workflow
│   ├── SignalInterpreterAgent.java               # Agent
│   ├── ForecastNarratorAgent.java                # Agent
│   ├── OpportunityScreenerAgent.java             # Agent
│   ├── EvidenceGuardrail.java                    # TextGuardrail
│   ├── AttributionGuardrail.java                 # TextGuardrail
│   ├── AlertDispatchConsumer.java                # Consumer
│   ├── ScoutTickTimer.java, ReminderTimer.java   # Timed actions
│   ├── SourceGateway.java, SourceRegistry.java   # the only outbound path
│   ├── TourWatchView.java
│   ├── OpportunityView.java
│   └── CalibrationView.java
└── api/
    ├── FanEndpoint.java
    └── FanApiTypes.java                          # request/response records

src/main/resources/
├── application.conf                              # dev port 9008, gemini provider, guardrails
└── sources.conf                                  # source registry: host, tier, policy, rate limit

src/test/java/com/example/
├── domain/       ForecastScorerTest, EligibilityEvaluatorTest, VisitTransitionTest
├── application/  TourWatchEntityTest, TicketOpportunityEntityTest,
│                 EvidenceRequiredTest, OutboundHostPolicyTest,
│                 AlertDedupeTest, ReminderSchedulingTest,
│                 TourScoutWorkflowTest, CalibrationViewTest
└── api/          FanEndpointIntegrationTest
```

**Structure Decision**: Single Akka service using the `domain` / `application` / `api` layering already established by the scaffold. Domain holds framework-free logic — critically the two pure functions (`ForecastScorer`, `EligibilityEvaluator`) that carry the decisions the system must be able to defend. Application holds SDK components. API holds the endpoint and its own types. No frontend directory: the feature has no UI surface.

The source allowlist lives in `sources.conf` rather than in code, so the set of hosts the system may reach is reviewable as configuration and testable as data.

## Phase Summary

**Phase 0 — Research**: complete → [research.md](./research.md). Nine decisions recorded with rationale and alternatives. Every `NEEDS CLARIFICATION` resolved; per-source terms and robots verification carried forward as explicit **VALIDATE** tasks rather than assumed.

**Phase 1 — Design & Contracts**: complete → [data-model.md](./data-model.md), [contracts/http-api.md](./contracts/http-api.md), [contracts/agent-contracts.md](./contracts/agent-contracts.md), [quickstart.md](./quickstart.md).

**Phase 2 — Tasks**: not produced here. `/akka:tasks` generates `tasks.md`.

## Suggested delivery order

The spec's story priorities already form a sensible slicing, and each slice is independently demonstrable.

1. **Foundation** — domain types, `ForecastScorer`, `SourceGateway` + `sources.conf`, `OutboundHostPolicyTest`. The gateway lands first because every later component depends on it and because building it last would mean writing network code with no boundary to put it behind.
2. **P1, know before the world knows** — `TourWatchEntity`, `SignalInterpreterAgent`, `ForecastNarratorAgent` + `EvidenceGuardrail`, `TourScoutWorkflow`, `AlertDispatchConsumer` + `AlertLedgerEntity`, `TourWatchView`, endpoint. Ships a viable MVP on its own.
3. **P2, free ways in** — `TicketOpportunityEntity`, `OpportunityScreenerAgent` + `AttributionGuardrail`, `EligibilityEvaluator`, `OpportunitySweepWorkflow`, `ReminderTimer`, `OpportunityView`.
4. **P3, show me why** — evidence and dismissal endpoints, forecast replay on dismissal.
5. **P4, keep itself honest** — `ForecastOutcome` resolution, `CalibrationView`.

## Risks

| Risk | Consequence | Mitigation |
|---|---|---|
| A candidate source's terms forbid programmatic reading | Fewer sources, weaker forecast | Per-source VALIDATE task; the tiered registry degrades rather than breaks, and `degradedSources` makes the loss visible instead of silent |
| Initial scoring weights are badly wrong | Early forecasts miscalibrated | Expected, and why P4 exists. Weights are configuration; the calibration ledger is the feedback loop |
| Signal volume is too thin to forecast usefully | Likelihood sits near a prior and says little | An honest low-confidence forecast is a valid output (spec edge case); the system is not permitted to manufacture optimism |
| SC-002 (30-day lead on 60% of announcements) unverifiable for months | Cannot gate release on it | Already noted in the requirements checklist as a standing target, not a launch gate |
| Q1 answered as auto-submit after build starts | PII handling and per-site terms enter scope | No component is discarded; `FanEndpoint` gains a submission surface. The gateway's safe-method rule is what would need revisiting, deliberately |

## Complexity Tracking

No constitution violations. Table intentionally empty.
