# Quickstart: Metallica Chicago Tour Watch

**Feature**: `001-metallica-tour-watch` | Service runs locally on **port 9008**
**Built so far**: User Story 1 (the MVP). US2–US4 are specified and tasked but not implemented.

## Prerequisites

Already satisfied in this environment (verified during `/akka:setup`): Java 25, Maven 3.9.13, Akka CLI 3.0.70, Akka download token, Docker.

One additional requirement for the agents:

```bash
echo $GOOGLE_AI_GEMINI_API_KEY    # must be non-empty
```

The service starts and every test passes without it. It is needed only when the scout cycle reaches a step that calls an agent. Nothing else here needs a credential — there are no ticketing accounts, no payment details, and no per-source keys beyond whatever a verified source requires.

## The service makes no outbound requests yet — on purpose

`src/main/resources/sources.conf` is the complete list of hosts the service may reach, and it ships **empty**. No source is added until its terms of use and machine-readable access policy have been read and confirmed to permit this use (tasks.md T068). Until then `SourceGateway` refuses everything and `Bootstrap` logs a warning at startup.

That means a freshly started service will observe nothing and produce no forecast. That is the correct behaviour, not a bug: the alternative is shipping a host that nobody checked.

To add a verified source:

```hocon
tourwatch.sources = [
  {
    source-id    = "example-official-events"
    host         = "events.example.com"
    tier         = "A"                      # A may confirm a date; B and C may only move a forecast
    allowed-paths = ["/api/events"]
    min-request-interval-ms = 1000
    verified-on  = "2026-08-14"
  }
]
```

## Run it

```bash
mvn compile
akka local run          # or the akka_local_run_service MCP tool
```

Port 9008 is reserved for this project in the shared local-runtime registry. Check whether the runtime is already up (`akka local status`) before starting — other services share the daemon, and restarting it stops them.

## Walk through the primary flow

**1. Create the fan profile.** Attributes are self-declared; nothing is inferred from third parties.

```bash
curl -X POST localhost:9008/fan/profile -H 'content-type: application/json' -d '{
  "fanId": "saurabh",
  "displayName": "Saurabh",
  "homeMarket": "chicago",
  "residencyState": "IL",
  "ageBand": "A21_PLUS",
  "fanClubs": [],
  "sponsorRelationships": [],
  "alertWebhookUrl": "https://webhook.site/your-test-url"
}'
```

`ageBand` is one of `UNDER_18`, `A18_20`, `A21_PLUS` — a band rather than a birthdate, because that is all a contest's age rule needs to be answered.

**2. Register the watch.** The market is a metropolitan area, so it carries a centre and a radius rather than a city name.

```bash
curl -X POST localhost:9008/fan/watches -H 'content-type: application/json' -d '{
  "artistSlug": "metallica",
  "artistName": "Metallica",
  "marketSlug": "chicago",
  "marketName": "Chicago",
  "centroidLat": 41.8781,
  "centroidLon": -87.6298,
  "radiusMiles": 50,
  "alertThreshold": 60,
  "alertWebhookUrl": "https://webhook.site/your-test-url"
}'
```

A 50-mile radius from downtown reaches Soldier Field, the United Center, Wrigley Field, Allstate Arena, and the Tinley Park amphitheatre — all of which a fan means by "Chicago". The webhook must be `https`; the request is rejected otherwise.

Registering schedules the first scout cycle five seconds out, then hourly.

**3. Read the watch.**

```bash
curl localhost:9008/fan/watches/metallica:chicago
```

Check `"kind"` first. `FORECAST` means prediction; `CONFIRMED` means an announced date exists. The field is mandatory precisely so a client cannot render this payload without knowing which it holds — a prediction displayed as an announcement is the most damaging thing this API could do.

`degradedSources` lists sources that were unreachable on the last cycle. A non-empty list means the forecast rests on less than the full evidence, and its `confidence` has been lowered by one level per blind spot.

**4. List active watches.**

```bash
curl localhost:9008/fan/watches
```

**5. Deactivate.**

```bash
curl -X DELETE localhost:9008/fan/watches/metallica:chicago
```

This also cancels the scout timer, so the watch stops observing rather than quietly continuing.

## What is not built yet

These are specified in `spec.md`, designed in `contracts/http-api.md`, and tasked in `tasks.md` — the routes simply do not exist yet:

| Story | Missing | Tasks |
|---|---|---|
| US2 | Free-ticket opportunities, eligibility, deadline reminders | T041–T056 |
| US3 | Evidence inspection and signal dismissal over HTTP | T057–T061 |
| US4 | Forecast resolution and the calibration report | T062–T067 |

Signal dismissal and evidence *do* exist on `TourWatchEntity` and are covered by tests; only the HTTP surface for them is deferred to US3.

## Run the tests

```bash
mvn test        # 61 unit tests
mvn verify      # adds 6 integration tests that boot the whole service
```

Two carry project exit conditions:

```bash
mvn test -Dtest=EvidenceRequiredTest      # a forecast cannot be served without evidence
mvn test -Dtest=OutboundHostPolicyTest    # undeclared hosts and unsafe methods are refused
```

If either is failing, the corresponding conduct boundary is not being enforced regardless of what the documentation says. `OutboundHostPolicyTest` includes a structural assertion that `SourceGateway.Transport` exposes exactly one method, `get(URI)` — so widening the outbound boundary cannot happen quietly.

## Verifying internal state

`akka_backoffice_list_components` and `akka_backoffice_get_entity_state` show live entity state against the running service — useful for confirming `TourWatchEntity`'s journal holds the signals you expect after a cycle, without adding a debug endpoint for it.
