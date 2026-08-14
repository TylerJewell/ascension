# Contract: Agent and Gateway Boundaries

**Feature**: `001-metallica-tour-watch`

Three agents, each with one job and a structured output. None of them produces a probability, and none of them reaches the network directly.

---

## SignalInterpreterAgent

**In**: fetched source content (text), source registry entry (`sourceId`, `sourceTier`, `sourceUrl`), the watch's artist and market.

**Out**: zero or more signal candidates.

```json
{ "signals": [ {
    "kind": "VENUE_HOLD",
    "summary": "Calendar shows an unnamed stadium hold for a two-night run",
    "influence": "STRONG_POSITIVE",
    "observedAt": "2026-08-12T14:30:00Z",
    "excerpt": "..." } ] }
```

**Constraints**
- `kind` must be one of the declared `SignalKind` values; an unrecognized kind is a rejected response, not a new category.
- `sourceUrl` and `sourceTier` are supplied by the caller from the registry — the agent cannot assert its own provenance or upgrade its own tier.
- Returning zero signals is a valid, common answer. The prompt must make that explicit; an agent that feels obliged to find something manufactures signal, and manufactured signal is what SC-004 (at most one false confirmation per year) has to survive.

**Guardrail**: output rejected if `excerpt` is absent or does not appear in the supplied content. This is what keeps a signal traceable to text that actually exists.

---

## ForecastNarratorAgent

**In**: the computed forecast (likelihood, window, confidence — already determined by the scorer), and the full cited signal set.

**Out**:

```json
{ "rationale": "...", "citedSignalIds": ["sig-9f2", "sig-a41"] }
```

**Constraints**
- **Must not** restate, adjust, or contradict the likelihood. The number is an input. If the rationale text implies a different confidence than the input, that is a defect.
- `citedSignalIds` must be a non-empty subset of the supplied signals.
- Must not assert a show date. Only a `Visit` carries dates; a forecast carries an announcement window (FR-006).

**Guardrail** (`EvidenceRequiredTest`, exit condition `forecast-carries-evidence`): output rejected when `citedSignalIds` is empty or contains an id not in the input set. A rejected narration withdraws the forecast rather than serving it bare.

---

## OpportunityScreenerAgent

**In**: discovered offer content, source URL.

**Out**:

```json
{ "title": "...",
  "authority": { "name": "...", "category": "BROADCASTER" },
  "entryUrl": "https://...", "entryMethod": "Online form",
  "deadline": "2026-09-01T04:59:00Z",
  "eligibilityRules": [ { "kind": "AGE_MINIMUM", "parameter": "18",
                          "description": "Entrants must be 18 or older" } ],
  "disclosedOdds": null,
  "effortMinutes": 3,
  "attributionEvidence": "..." }
```

**Constraints**
- `authority.category` must be one of the five declared categories. `null` is permitted and means **reject** (FR-019) — the agent is explicitly allowed to say it cannot attribute the offer, and saying so is the correct answer for anything it cannot trace.
- `disclosedOdds` is populated only when the promoter states odds. Estimating odds is out of contract.
- `eligibilityRules` are extracted, never evaluated — evaluation is deterministic code against `FanProfile` (R5).

**Guardrail**: output rejected when `authority` is non-null but `attributionEvidence` is empty. Attribution is the whole legitimacy test; an unevidenced authority claim is worse than no claim, because it launders an unknown offer into the actionable list.

---

## SourceGateway

Not an agent. The single component in the system permitted to make an outbound request (R6).

**Enforced at the boundary**
1. Host must appear in the declared read-only allowlist. Anything else is refused before a connection opens.
2. Method must be `GET` or `HEAD`. No other method is constructible through this interface.
3. The source's machine-readable access policy is honored; a disallowed path is refused (FR-025).
4. Per-source rate limits are respected.
5. A refusal or failure returns a typed unavailable result. It never falls back to an alternate route, an alternate host, or a disguised identity (FR-024).

**On unavailability**: emits `SourceDegraded` for the watch, which lowers forecast confidence (FR-027). The blind spot is surfaced in the API response's `degradedSources`, so a fan is never shown a confident forecast built on a source that has silently gone dark.

**Test** (`OutboundHostPolicyTest`, exit condition `outbound-hosts-read-only`): asserts an undeclared host is refused, a non-safe method is not constructible, a disallowed path is refused, and no fallback path exists. This is the test that makes "the system never acquires a ticket" a property of the code rather than a promise in a document.
