# Quickstart: Metallica Chicago Tour Watch

**Feature**: `001-metallica-tour-watch` | Service runs locally on **port 9008**

## Prerequisites

Already satisfied in this environment (verified during `/akka:setup`): Java 25, Maven 3.9.13, Akka CLI 3.0.70, Akka download token, Docker.

One additional requirement for the agents:

```bash
echo $GOOGLE_AI_GEMINI_API_KEY    # must be non-empty
```

The service starts without it, but any workflow step that calls an agent will fail. Nothing else in the system needs a credential — there are no ticketing accounts, no payment details, and no per-source API keys beyond those declared in `sources.conf`.

## Configuration

`src/main/resources/application.conf`:

```hocon
akka.javasdk.dev-mode.http-port = 9008

akka.javasdk.agent.googleai-gemini {
  provider = "googleai-gemini"
  api-key  = ${?GOOGLE_AI_GEMINI_API_KEY}
  model-name = "gemini-2.5-flash"
}
```

`src/main/resources/sources.conf` declares the outbound allowlist. A host absent from this file is unreachable by the service — that is enforced by `SourceGateway`, not by convention, and `OutboundHostPolicyTest` asserts it.

## Run it

```bash
mvn compile
akka local run          # or the akka_local_run_service MCP tool
```

Port 9008 is reserved for this project in the shared local-runtime registry. Before starting, check whether the runtime is already up (`akka local status` / `akka_local_status`) — other services share the daemon, and restarting it stops them.

## Walk through the primary flow

**1. Create the fan profile** — attributes are self-declared and are what eligibility rules get evaluated against.

```bash
curl -X POST localhost:9008/fan/profile -H 'content-type: application/json' -d '{
  "fanId": "saurabh", "displayName": "Saurabh",
  "homeMarket": "chicago", "residencyState": "IL", "ageBand": "A21_PLUS",
  "fanClubMemberships": [], "sponsorRelationships": [],
  "alertWebhookUrl": "https://webhook.site/your-test-url"
}'
```

**2. Register the watch.**

```bash
curl -X POST localhost:9008/fan/watches -H 'content-type: application/json' -d '{
  "artistSlug": "metallica", "marketSlug": "chicago",
  "radiusMiles": 50, "alertThreshold": 60,
  "alertWebhookUrl": "https://webhook.site/your-test-url"
}'
```

From here the system runs unattended. The steps below are for seeing it work now rather than in three months.

**3. Read the current forecast.**

```bash
curl localhost:9008/fan/watches/metallica:chicago
```

Check `"kind"`. `FORECAST` means prediction; `CONFIRMED` means an announced date exists. The two are never conflated, and a client that ignores this field is misusing the API.

**4. Inspect the evidence.**

```bash
curl localhost:9008/fan/watches/metallica:chicago/evidence
```

Every signal carries a public `sourceUrl` and `observedAt`. If a forecast ever appears with an empty signal list, that is a defect — `EvidenceRequiredTest` exists to prevent it.

**5. Dismiss a signal you know is wrong** and watch the forecast move.

```bash
curl -X POST localhost:9008/fan/watches/metallica:chicago/signals/sig-9f2/dismiss \
  -H 'content-type: application/json' -d '{"reason":"Hold was a different production"}'
```

Recomputation is a pure function over the remaining signals, so this is deterministic — the same dismissal always produces the same forecast.

**6. List free-ticket opportunities.**

```bash
curl 'localhost:9008/fan/watches/metallica:chicago/opportunities?status=ACTIONABLE'
curl 'localhost:9008/fan/watches/metallica:chicago/opportunities?status=REJECTED'
```

The rejected list is worth reading during development: it shows what the attribution rule threw away and why. An empty actionable list with a long rejected list means the screener is working, not that it is broken.

**7. Mark one interesting**, which schedules the 24-hour reminder.

```bash
curl -X POST localhost:9008/fan/opportunities/opp-3c1/status \
  -H 'content-type: application/json' -d '{"status":"INTERESTED"}'
```

Then open `entryUrl` and enter it yourself. **The system will not enter it for you** — there is no endpoint that does, by design.

## Run the tests

```bash
mvn test
mvn test -Dtest=EvidenceRequiredTest      # forecasts cannot be served without evidence
mvn test -Dtest=OutboundHostPolicyTest    # undeclared hosts and unsafe methods are refused
```

Those two carry exit conditions. If either is failing, the corresponding conduct boundary is not being enforced regardless of what the documentation says.

## Verifying internal state

`akka_backoffice_list_components` and `akka_backoffice_get_entity_state` show live entity state against the running service — useful for confirming that `TourWatchEntity`'s journal contains the signals you expect after a scout cycle, without adding a debug endpoint for it.
