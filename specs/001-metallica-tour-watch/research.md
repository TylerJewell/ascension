# Phase 0 Research: Metallica Chicago Tour Watch & Free-Ticket Agent

**Feature**: `001-metallica-tour-watch` | **Date**: 2026-08-14

Every `NEEDS CLARIFICATION` from Technical Context is resolved below. Decisions that depend on facts this project cannot verify from inside the repository are marked **VALIDATE** and carry a concrete validation step rather than an assumption.

---

## R1 — Model provider

**Decision**: Google AI Gemini (`provider = "googleai-gemini"`), model configured per agent, key from `GOOGLE_AI_GEMINI_API_KEY`.

**Rationale**: It is the only provider key present in the developer's environment (`ANTHROPIC_API_KEY` and `OPENAI_API_KEY` are unset). The Akka SDK supports it as a first-class provider — `akka-context/sdk/model-provider-details.html.md:531` documents the `akka.javasdk.agent.googleai-gemini` block and confirms the key falls back to that environment variable, matching the naming convention already in use.

**Alternatives considered**: Anthropic or OpenAI — either is a one-line configuration change and no code depends on the choice, but selecting one would require provisioning a key that does not exist yet. The provider is isolated behind agent configuration precisely so this can be swapped without touching domain logic.

---

## R2 — Where touring signals come from

**Decision**: A tiered source model with reliability weights baked into the source registry, not into the agent.

| Tier | Source kind | Weight | Can confirm? |
|---|---|---|---|
| A | Official ticketing outlet event API | High | Yes |
| A | Artist's own site / official feed | High | Yes |
| A | Venue or promoter event calendar | High | Yes |
| B | Established live-music listing services | Medium | No |
| C | Fan forums, aggregators, speculation | Low | No |

Only Tier A can produce a `Visit` confirmation (FR-007). Tier B and C can only move a forecast.

**Rationale**: The reliability distinction is a domain rule with legal and user-trust consequences, so it belongs in versioned configuration where it can be reviewed — not in a prompt where it can drift. This also makes FR-007 mechanically checkable: confirmation is gated on `source.tier == A`, which a test can assert directly.

**VALIDATE before implementation**: For each concrete source added to the registry, confirm (a) whether a documented public API exists and its terms permit this use, (b) what `robots.txt` allows if there is no API, and (c) the rate limit. A source that fails any of these is not added. This is a per-source task in `/akka:tasks`, not a blanket assumption — the primary candidate is an official ticketing Discovery-style API, which typically offers a free read-only tier, but that must be read and confirmed rather than presumed.

**Alternatives considered**: A single aggregator source — simpler, but it collapses the reliability tiers that FR-007 depends on and makes the system's accuracy hostage to one vendor's freshness. Broad web crawling — rejected outright; it maximizes terms-of-service exposure for marginal signal.

---

## R3 — How a forecast is actually computed

**Decision**: Hybrid. Deterministic weighted scoring produces the likelihood; the LLM interprets unstructured source content into structured signals and writes the human-readable rationale. **The model never emits the probability.**

**Rationale**: This is the most consequential design decision in the feature, and it follows directly from SC-003 (calibration within 15 points). Language models are known to be poorly calibrated when asked to state numeric probabilities — they cluster on round confident-sounding numbers and do not respond stably to marginal evidence. A forecast whose number comes from a model cannot be tuned when the calibration report says the 80% band is running at 55%; a forecast whose number comes from a weighted function of typed signals can be. Splitting the responsibilities gives the model the job it is good at (reading messy text into structure) and keeps the job it is bad at (calibrated numeric judgment) in auditable code.

It also makes FR-005 cheap: recomputation on a new signal is a pure function over the signal set, so replay after a signal is dismissed (FR-029) is exact rather than a second nondeterministic model call that might land somewhere unrelated.

**Alternatives considered**:
- *Pure LLM forecasting* — one call, much less code, and genuinely tempting for a v1. Rejected because SC-003 becomes unmeetable-by-construction and FR-029 becomes nondeterministic.
- *Pure deterministic, no model* — would work for structured API sources and nothing else. Rejected because the majority of early signal (official posts, promoter language, venue hold notes) is prose.

**Scoring inputs** (initial weights, to be tuned against the calibration ledger):

| Feature | Direction |
|---|---|
| Announced tour leg with a routing gap geographically adjacent to the market | Strong + |
| Venue calendar hold matching the artist's production requirements | Strong + |
| Elapsed time since the artist last played the market vs. their historical cadence | + |
| Active album/release cycle | + |
| Official teaser or countdown referencing the region | Strong + |
| Announced leg that routes past the market without stopping | Strong − |
| Publicly stated hiatus or health event | Strong − |

---

## R4 — Free-ticket opportunity sources

**Decision**: Four categories, each with a named attribution authority: (1) broadcast station promotions, (2) the artist's official fan club and charity organization, (3) venue and promoter sweepstakes, (4) sponsor and partner giveaways. An offer that cannot be attributed to a named authority in one of these four categories is rejected under FR-019 and recorded with the reason.

**Rationale**: FR-019 needs a falsifiable definition of "legitimate," and "traceable to a named authority in a known category" is one. It is deliberately conservative — it will reject some real giveaways from obscure sources. That asymmetry is correct: a missed contest costs Saurabh one entry, while a phishing offer surfaced as actionable costs him his personal data.

**Alternatives considered**: Model-judged legitimacy with no structural rule — rejected as unfalsifiable; "the model thought it looked real" cannot be tested, and this is exactly the class of check the exit-condition review is meant to reject.

---

## R5 — Component decomposition on the Akka SDK

**Decision**:

| Component | Type | Why this type |
|---|---|---|
| `TourWatchEntity` | Event Sourced Entity | The spec asks for a superseded-not-overwritten forecast history (FR-005), full evidence inspection (FR-028), signal dismissal with recomputation (FR-029), and outcome retention (FR-030). That is an event journal described in user language. Deriving current forecast from the event history means the audit trail cannot drift from the answer, because it *is* the answer. |
| `TicketOpportunityEntity` | Event Sourced Entity | Status is a lifecycle with meaningful transitions (discovered → verified → interested → entered → won/closed), and FR-018 requires knowing *when* it was last verified, not just its current state. |
| `FanProfileEntity` | Key Value Entity | A flat attribute set read for eligibility evaluation. No history requirement, no transitions. Key Value is the simpler primitive and Principle IV says take it. |
| `AlertLedgerEntity` | Key Value Entity | Holds delivered-alert fingerprints so FR-014 can suppress duplicates. A set membership check; no history needed. |
| `TourScoutWorkflow` | Workflow | The observation cycle is multi-step with partial failure as the normal case — several sources, some of which will be down. Durable execution with per-step retry is exactly the semantics FR-027 needs: a failed source step degrades confidence rather than aborting the cycle. |
| `OpportunitySweepWorkflow` | Workflow | Same shape for re-verification (FR-018). |
| `SignalInterpreterAgent` | Agent | Unstructured source content → typed `Signal` candidates. |
| `ForecastNarratorAgent` | Agent | Scored forecast → plain-language rationale citing signal ids. Does not produce the number. |
| `OpportunityScreenerAgent` | Agent | Offer text → structured eligibility, deadline, odds, attribution authority. |
| `AlertDispatchConsumer` | Consumer | Reacts to entity events, dedupes via `AlertLedgerEntity`, delivers. |
| `TourWatchView`, `OpportunityView`, `CalibrationView` | Views | Query surfaces for current forecast, opportunities by deadline, and hit rate by confidence band (FR-031). |
| `FanEndpoint` | HTTP Endpoint | Saurabh's surface. |

**Rationale**: Each component has one job (Principle II, single responsibility). The agent count is held to three because eligibility evaluation and legitimacy attribution are rule matching, not judgment — making them agents would add nondeterminism to decisions that must be testable.

**Alternatives considered**: A single `TourWatchAgent` doing observation, forecasting, and opportunity screening in one loop. Rejected under Principle II — and practically, it would make the evidence-required guardrail impossible to scope, since one agent's output would mix three kinds of claim.

---

## R6 — Enforcing the conduct boundaries in the architecture

**Decision**: All outbound network access goes through one `SourceGateway` in the application layer. It enforces a declared host allowlist, permits only safe idempotent methods, honors each source's machine-readable access policy, and refuses anything else at the boundary. No component holds an HTTP client of its own.

**Rationale**: This is what makes the `outbound-hosts-read-only` exit condition testable rather than aspirational. A scan for suspicious call shapes can be defeated by string concatenation; a single chokepoint with a test that asserts an undeclared host is refused cannot be, because there is nowhere else for a request to originate. Concentrating the boundary is also what turns "the system never buys a ticket" from a promise into a property.

**Decision**: The evidence requirement (FR-004/FR-016/FR-028) is enforced as an Akka **guardrail** on agent output, not as a caller-side check. `akka-context/sdk/agents/guardrails.html.md:14` describes guardrails as runtime-enforced validation of what goes into and out of an agent, enabled by configuration — so the check cannot be bypassed by a new call site added later.

---

## R7 — Scheduling the observation cycle

**Decision**: Akka timed actions drive both the recurring scout tick and the per-opportunity deadline reminder. The reminder timer is scheduled when an opportunity is marked interesting and cancelled when it is marked entered or dismissed.

**Rationale**: FR-020 demands a reminder at a specific time relative to a per-opportunity deadline. A single polling sweep would have to scan every opportunity on every tick to find the ones nearing close; a timer per interested opportunity fires exactly when needed and is durable across restarts. Poll frequency for the scout tick starts hourly and increases near a suspected announcement window.

**Alternatives considered**: One global polling sweep for reminders — simpler to write, but SC-006 requires 100% delivery, and a poll interval creates a window where a deadline can be missed. The timer is the primitive that matches the requirement.

---

## R8 — Alert delivery channel

**Decision**: Delivery is an outbound webhook to a URL configured per watch, with the alert body rendered by the system. The HTTP endpoint also exposes alerts for pull.

**Rationale**: SC-001 requires notification within 15 minutes of a confirmation, which rules out digest-style delivery, and Assumption 4 requires push to a channel Saurabh already watches. A webhook reaches any of them — chat, email relay, phone push — without this project taking a dependency on a specific vendor account or holding channel credentials. It is also the smallest thing that satisfies the requirement (Principle IV).

**Alternatives considered**: Direct email (SMTP) or a chat vendor SDK — each adds a dependency and credential handling for one delivery path, when a webhook covers all of them.

---

## R9 — Testing approach

**Decision**: Akka TestKit for entity, workflow, and view behavior. Agent behavior tested against recorded fixtures rather than live model calls. Two named tests carry exit conditions and must exist by name: `EvidenceRequiredTest` and `OutboundHostPolicyTest`.

**Rationale**: Principle III requires tests for every behavioral change, and the two named tests are the load-bearing ones — they are what make the conduct boundaries real rather than documented. Fixture-based agent testing keeps the suite deterministic and free of model spend; the interesting assertions are about the *structure* the agent returns and what happens when it returns something malformed, both of which fixtures cover better than live calls.

---

## Open items carried forward

| Item | Status |
|---|---|
| Concrete source list with terms/robots verification | **VALIDATE** — one task per source in `/akka:tasks`; no source ships unverified |
| Initial scoring weights (R3) | Seeded from judgment, tuned against the calibration ledger once forecasts resolve |
| Q1 entry autonomy | Proceeding on the spec default: prepare-and-notify. Reverses cleanly if answered otherwise — no component is removed, `FanEndpoint` gains a submission surface and PII handling enters scope |
| Q2 single vs. multi-user | Proceeding single-user. `FanProfileEntity` is already keyed, so multi-tenancy is an auth and isolation addition, not a redesign |
| Q3 paid-purchase assistance | Proceeding informational-only; presale timing already carried on `Visit` |
