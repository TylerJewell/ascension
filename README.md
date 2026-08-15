# Tour Watch

Register an artist and a city. The service watches public sources on its own and tells you when a date is coming.

Built with **Akka Specify**.

---

## What it does

From the specification:

- **Know before the world knows.** A watch observes public touring signals unattended and maintains a likelihood that the artist will play the market, together with the window in which an announcement is expected. When a date is officially announced, an alert goes out within 15 minutes, carrying the venue, the dates, the on-sale time, and how long is left to act.
- **Free ways in.** Legitimate no-cost routes to a confirmed date — station contests, fan-club draws, venue and sponsor giveaways — are tracked with their eligibility rules and closing deadlines, with a reminder before each one closes.
- **Show me why.** Every forecast can be opened down to the signals behind it, each with a public source and the time it was observed. A signal you know is wrong can be dismissed, and the estimate recomputes without it.
- **Keep itself honest.** Forecasts are resolved against what actually happened, and the system reports its own hit rate by confidence band.

The system finds and reminds. It never buys, reserves, or enters anything on your behalf.

**Currently running:** *Know before the world knows.* The remaining three are specified and not yet built.

---

## Running it

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once
- A Google AI Gemini API key

### 1. Set the model key

Put your Google AI Studio key in the environment as `GOOGLE_AI_GEMINI_API_KEY`.

```bash
read -rs GOOGLE_AI_GEMINI_API_KEY && export GOOGLE_AI_GEMINI_API_KEY
```

### 2. Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9008**.

### 3. Open the console

```
http://localhost:9008
```

Register a watch using the form. Defaults are filled in for Metallica in Chicago. Set the alert webhook to a URL you control — [webhook.site](https://webhook.site) works for a trial.

### 4. Add a source to watch

The service ships with no sources configured, so it will not reach out to anything until you add one. Open `src/main/resources/sources.conf` and add an entry:

```hocon
tourwatch.sources = [
  {
    source-id     = "official-events"
    host          = "events.example.com"
    tier          = "A"
    allowed-paths = ["/api/events"]
    min-request-interval-ms = 1000
    verified-on   = "2026-08-15"
  }
]
```

Use `tier = "A"` for an official ticketing outlet, artist, venue, or promoter; `"B"` for an established listing service; `"C"` for anything else. Only a tier A source can confirm a date.

Restart the service after editing.

---

## Running the tests

```bash
mvn verify
```

68 tests: 61 unit, 7 integration.

---

## Deploying

```bash
akka auth login

# Store the model key as a secret in your Akka project
akka secret create generic gemini-api --literal key=$GOOGLE_AI_GEMINI_API_KEY

# Builds the container image; note the image name and tag it prints
mvn clean install -DskipTests

akka service deploy tour-watch ascension:<tag-from-the-install-output> --push \
  --secret-env GOOGLE_AI_GEMINI_API_KEY=gemini-api/key

akka service list
```

---

*Built with Akka Specify on the Akka SDK 3.6.3.*
