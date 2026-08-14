# Contract: Fan HTTP API

**Feature**: `001-metallica-tour-watch` | **Component**: `FanEndpoint` (`com.example.api`)

Request and response types are declared by the endpoint and are distinct from domain types (Principle II — API isolation). Base path `/fan`. All times ISO-8601 UTC.

---

## Watches

### `POST /fan/watches`
Register a watch (FR-001).

```json
{ "artistSlug": "metallica", "marketSlug": "chicago",
  "radiusMiles": 50, "alertThreshold": 60, "alertWebhookUrl": "https://..." }
```
→ `201` `{ "watchId": "metallica:chicago" }`
Errors: `400` radius not positive, threshold outside 0–100, or webhook URL not https.

### `DELETE /fan/watches/{watchId}`
Deactivate (FR-001). → `204`. Idempotent.

### `GET /fan/watches/{watchId}`
Current forecast plus confirmed visits.

```json
{
  "watchId": "metallica:chicago",
  "artist": "Metallica",
  "market": "Chicago",
  "kind": "FORECAST",
  "forecast": {
    "likelihood": 72,
    "announcementWindow": { "start": "2026-10-01", "end": "2026-12-15" },
    "confidence": "MEDIUM",
    "rationale": "...",
    "citedSignalIds": ["sig-9f2", "sig-a41"],
    "computedAt": "2026-08-14T11:02:00Z"
  },
  "confirmedVisits": [],
  "degradedSources": ["venue-calendar-uc"]
}
```

`kind` is `FORECAST` or `CONFIRMED` and is mandatory on every response carrying a prediction (FR-006). A client cannot render this payload without knowing which it holds. `forecast` is never present without a non-empty `citedSignalIds` (FR-004).

---

## Evidence

### `GET /fan/watches/{watchId}/evidence`
Full signal set behind the current forecast (FR-028).

```json
{ "signals": [ {
    "signalId": "sig-9f2", "kind": "ROUTING_GAP_ADJACENT",
    "summary": "...", "influence": "STRONG_POSITIVE",
    "sourceUrl": "https://...", "sourceTier": "A",
    "observedAt": "2026-08-12T14:30:00Z", "dismissed": false } ] }
```

### `POST /fan/watches/{watchId}/signals/{signalId}/dismiss`
Mark a signal unreliable; the forecast recomputes without it (FR-029).

```json
{ "reason": "Venue hold was for a different production" }
```
→ `200` with the recomputed forecast.
Errors: `409` if dismissing would leave the forecast with zero cited signals — the forecast is withdrawn rather than served empty, and the response says so.

---

## Opportunities

### `GET /fan/watches/{watchId}/opportunities?status=ACTIONABLE`
```json
{ "opportunities": [ {
    "opportunityId": "opp-3c1",
    "title": "...", "authority": { "name": "...", "category": "BROADCASTER" },
    "entryUrl": "https://...", "entryMethod": "Online form",
    "deadline": "2026-09-01T04:59:00Z", "effortMinutes": 3,
    "disclosedOdds": null, "status": "ACTIONABLE",
    "lastVerifiedAt": "2026-08-14T06:00:00Z",
    "eligibility": { "eligible": true, "unmetRules": [] } } ] }
```
Ineligible entries carry `"eligible": false` with `unmetRules` naming the specific requirement (FR-017). Rejected offers are never returned as actionable; they are visible only at `?status=REJECTED` with `rejectionReason` (FR-019).

### `POST /fan/opportunities/{opportunityId}/status`
```json
{ "status": "INTERESTED" }
```
Accepts `INTERESTED`, `ENTERED`, `DISMISSED`, `WON` (FR-021). `INTERESTED` schedules the deadline reminder; `ENTERED` and `DISMISSED` cancel it (FR-020).

**There is no endpoint that enters a contest, purchases, or reserves anything** (FR-022, FR-023). `entryUrl` is returned for Saurabh to open himself.

---

## Track record

### `GET /fan/watches/{watchId}/calibration`
```json
{ "resolvedCount": 24,
  "bands": [ { "band": "70-79", "forecastCount": 8,
               "observedHitRate": 0.50, "miscalibrated": true } ] }
```
`miscalibrated` is true when observed hit rate diverges from the band midpoint by more than 15 points (FR-031, SC-003). Below 20 resolved forecasts the response carries `"insufficientData": true` and reports no flags.

---

## Alert webhook (outbound)

Delivered to the watch's `alertWebhookUrl`.

```json
{ "alertId": "alt-77b", "watchId": "metallica:chicago",
  "kind": "CONFIRMATION",
  "headline": "Metallica — Soldier Field, Chicago",
  "timeToAct": "PT31H", "actByUtc": "2026-08-15T15:00:00Z",
  "detail": { }, "evidenceUrl": "https://.../evidence" }
```

`kind` is `FORECAST_THRESHOLD`, `CONFIRMATION`, `VISIT_CHANGED`, or `DEADLINE_REMINDER`. `timeToAct` is computed at delivery, not at trigger (FR-013). A `FORECAST_THRESHOLD` alert is never phrased as an announced date (FR-006).
