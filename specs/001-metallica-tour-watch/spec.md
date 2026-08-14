# Feature Specification: Metallica Chicago Tour Watch & Free-Ticket Agent

**Feature Branch**: `001-metallica-tour-watch`
**Created**: 2026-08-14
**Status**: Draft
**Input**: User description: "Saurabh at Ascension is a huge fan of Metallica -- he would really like to know when they are coming to Chicago. Can you design an agentic AI system that can predict when they will arrive and how he can get free tickets for that. Come up with a proposal and a plan."

## Overview

Saurabh wants two things that today require constant manual vigilance: advance warning that Metallica is coming to Chicago, and a path to attending without paying. Both are *watching* problems — the information exists in public, but it appears unpredictably, across dozens of sources, and the window to act on it is often measured in hours.

This feature is an autonomous watcher. It continuously observes public signals about the artist's touring activity, maintains a running **forecast** of when a Chicago-area date is likely to be announced, converts that forecast into a **confirmation** the moment a real date appears, and — for both the forecast and the confirmed date — maintains a live list of **legitimate free-ticket opportunities** with their eligibility rules and closing deadlines, alerting Saurabh in time to act.

The system predicts and informs. It does not acquire. Every ticket-obtaining action is taken by Saurabh himself, through the opportunity's own official channel.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Know before the world knows (Priority: P1)

Saurabh tells the system he wants to see Metallica in Chicago. From then on he does nothing. The system watches public touring signals and maintains a forecast: how likely a Chicago-area date is, and in what window it is likely to be announced. When his forecast crosses a meaningful threshold, or when an actual date is confirmed, he receives an alert with the date, the venue, the onsale timing, and what he should do in the next 24 hours.

**Why this priority**: This is the core ask and the only story that is valuable entirely on its own. Even with no free-ticket capability at all, a fan who reliably hears about the Chicago date before general onsale has received the majority of the value. Everything else builds on knowing.

**Independent Test**: Register a watch for Metallica in Chicago, replay a historical sequence of public signals leading to a real announced date, and verify the system produced a rising forecast beforehand and a confirmation alert containing the correct date, venue, and onsale time.

**Acceptance Scenarios**:

1. **Given** an active watch for Metallica in the Chicago market and no announced date, **When** the system observes public signals indicating touring activity in the region (e.g. a routing gap adjacent to Chicago on an announced leg, a venue calendar hold, an official teaser), **Then** it updates the forecast likelihood and announcement window, and records the signals that caused the change.
2. **Given** an active watch, **When** a Chicago-area date is officially announced, **Then** Saurabh is alerted within 15 minutes with the date, venue, onsale date/time, and presale windows he is eligible for.
3. **Given** an active watch, **When** the forecast likelihood crosses a threshold Saurabh configured, **Then** he is alerted with the forecast, its confidence, and the evidence behind it — clearly labeled as a prediction, not an announcement.
4. **Given** a confirmed date, **When** that date is later postponed, rescheduled, or cancelled, **Then** Saurabh is alerted to the change and the confirmation is updated rather than duplicated.

---

### User Story 2 - Free ways in (Priority: P2)

Alongside the forecast and confirmation, the system maintains a live list of legitimate ways to attend without paying: radio-station contests, official fan-club member draws, venue and promoter sweepstakes, charity and partner giveaways. Each opportunity carries what it takes to enter, who is eligible, the odds if disclosed, and — critically — when it closes. Saurabh marks the ones he wants, and the system makes sure he does not miss a deadline.

**Why this priority**: This is the second half of the ask, but it depends on knowing there is a show to win tickets to. It is independently testable and independently valuable once a date exists.

**Independent Test**: With a confirmed Chicago date present, verify the system surfaces multiple distinct free-ticket opportunities, each with eligibility, entry method, and deadline; mark one as "interested" and verify a reminder is delivered before it closes.

**Acceptance Scenarios**:

1. **Given** a confirmed or high-likelihood Chicago date, **When** the system discovers a free-ticket opportunity tied to it, **Then** the opportunity is listed with its source, entry method, eligibility rules, effort estimate, and closing deadline.
2. **Given** a listed opportunity, **When** Saurabh marks it "interested", **Then** he receives a reminder at least 24 hours before it closes, and again if it is about to close unentered.
3. **Given** an opportunity whose eligibility rules Saurabh cannot satisfy (e.g. a residency, age, or membership requirement he does not meet), **When** it is discovered, **Then** it is shown as ineligible with the reason, rather than surfaced as actionable.
4. **Given** a discovered offer that shows signs of being fraudulent or a data-harvesting scheme rather than a genuine giveaway, **When** it is evaluated, **Then** it is withheld from the actionable list and recorded as rejected with the reason.
5. **Given** any opportunity, **When** Saurabh chooses to pursue it, **Then** the system provides him the official entry link and any prepared material, and he completes the entry himself.

---

### User Story 3 - Show me why (Priority: P3)

Any prediction a fan is asked to act on has to be inspectable. For any forecast or opportunity, Saurabh can see the evidence: which signals were observed, from which public sources, when, and how each moved the estimate. He can dismiss a signal he knows is wrong, and the forecast responds.

**Why this priority**: Without this the system is an oracle that occasionally cries wolf, and a fan will stop trusting it after the first miss. It is not required for the first useful version, but it is what makes the forecast survivable long-term.

**Independent Test**: Open any forecast and verify every contributing signal is listed with source, timestamp, and direction of influence; dismiss one signal and verify the forecast recomputes and records the manual override.

**Acceptance Scenarios**:

1. **Given** any forecast, **When** Saurabh inspects it, **Then** he sees every contributing signal with its public source, observation time, and whether it raised or lowered the estimate.
2. **Given** a contributing signal, **When** Saurabh marks it as unreliable, **Then** the forecast is recomputed without it and the override is retained for future recomputation.
3. **Given** any surfaced opportunity, **When** Saurabh inspects it, **Then** he sees the original source it was discovered from and when it was last verified as still open.

---

### User Story 4 - Keep itself honest (Priority: P4)

Every forecast the system makes is eventually resolved by reality — the date was announced in the predicted window, or it was not. The system records these outcomes and reports its own track record, so both Saurabh and the system can tell whether an "80% likely" from this system actually means eighty percent.

**Why this priority**: Calibration is what separates a forecast from a guess, but the system delivers value before it has enough resolved forecasts to measure. This story matters most in month six, not week one.

**Independent Test**: Seed a set of past forecasts with known outcomes and verify the system reports hit rate bucketed by stated confidence, and flags buckets that are materially miscalibrated.

**Acceptance Scenarios**:

1. **Given** a forecast with a stated announcement window, **When** that window passes with or without an announcement, **Then** the forecast is resolved as correct or incorrect and retained in the track record.
2. **Given** at least twenty resolved forecasts, **When** Saurabh views the track record, **Then** he sees observed hit rate grouped by stated confidence band.
3. **Given** a confidence band whose observed hit rate diverges materially from its stated confidence, **When** the track record is produced, **Then** that band is flagged as miscalibrated.

---

### Edge Cases

- **"Chicago" is fuzzy.** A stadium date at Soldier Field, an arena date at the United Center, and an amphitheatre date in Tinley Park are all "Metallica coming to Chicago" to a fan, but only some are inside city limits. The watch is defined over a metropolitan radius, not a municipality.
- **Multiple dates in one visit.** The artist's recent stadium pattern has been multi-night runs with different setlists. A single announcement may yield two or more dates; the watch must treat these as one visit with several attendable dates, not as duplicate confirmations.
- **The band is not touring at all.** A hiatus, an album cycle with no roadwork, or a member health event means the honest forecast is "unlikely, and here is why." The system must be able to report low likelihood indefinitely without degrading into noise or manufacturing false optimism.
- **Rumor sources are unreliable.** Fan forums and aggregation sites routinely publish speculative dates. Signals must be weighted by source reliability, and an unconfirmed rumor must never produce a confirmation alert.
- **The announcement happens at 3am local time.** Alerting must not depend on Saurabh being awake, and the value of the alert must survive being read six hours later — meaning it states remaining time to act, not just what happened.
- **A source blocks automated access.** Some sites disallow programmatic reading. Losing a source must degrade the forecast's confidence rather than silently leaving a blind spot the system does not disclose.
- **The show sells out before any free opportunity resolves.** Opportunities must be tracked independently of ticket availability, since giveaways commonly run past sellout.
- **A contest requires something Saurabh does not have.** Fan-club membership with a tenure requirement, a specific credit card, physical presence at a station — eligibility must be evaluated against his stated profile before an opportunity is presented as actionable.
- **A "free ticket" offer is bait.** Phishing and data-harvesting offers imitate giveaways. An offer that cannot be traced to a recognized promoter, venue, station, or the artist's own organization is not actionable.

## Requirements *(mandatory)*

### Functional Requirements

**Watching and forecasting**

- **FR-001**: Users MUST be able to register a watch specifying an artist and a geographic market, and deactivate it at any time.
- **FR-002**: System MUST continuously observe public sources for signals relevant to an active watch, without user prompting.
- **FR-003**: System MUST maintain, for each active watch, a current likelihood that the artist will play the market and an expected announcement window.
- **FR-004**: System MUST record every signal that contributes to a forecast with its public source, observation timestamp, source reliability, and direction of influence.
- **FR-005**: System MUST recompute the forecast when a new contributing signal is observed, and retain the prior value as history.
- **FR-006**: System MUST distinguish a *forecast* from a *confirmation* in every alert and display, such that a prediction can never be read as an announced date.
- **FR-007**: System MUST create a confirmation only from a signal originating with the artist, the venue, the promoter, or the official ticketing outlet — never from an aggregator, forum, or unattributed report.
- **FR-008**: System MUST treat a market as a metropolitan area with a configurable radius, and include venues within that radius regardless of municipality.
- **FR-009**: System MUST represent multiple dates announced for the same visit as one visit containing several attendable dates.
- **FR-010**: System MUST update an existing confirmation in place when a date is rescheduled, postponed, or cancelled, and alert the user to the change.

**Alerting**

- **FR-011**: System MUST alert the user within 15 minutes of observing a confirmation, including date, venue, onsale time, and any presale windows the user is eligible for.
- **FR-012**: System MUST alert the user when a forecast crosses a user-configured likelihood threshold.
- **FR-013**: Every alert MUST state the remaining time to act on it, computed at delivery, not merely the time the triggering event occurred.
- **FR-014**: System MUST NOT send more than one alert for the same event unless the underlying facts changed.

**Free-ticket opportunities**

- **FR-015**: System MUST discover and maintain a list of no-cost ticket opportunities associated with a forecast or confirmed visit.
- **FR-016**: Each opportunity MUST carry its discovery source, official entry link, entry method, eligibility rules, closing deadline, and disclosed odds where the promoter states them.
- **FR-017**: System MUST evaluate each opportunity's eligibility rules against the user's stated profile and mark ineligible opportunities with the specific unmet requirement.
- **FR-018**: System MUST re-verify each open opportunity's status on a recurring basis and mark it closed, expired, or unreachable when it no longer holds.
- **FR-019**: System MUST withhold from the actionable list any offer that cannot be attributed to a recognized artist organization, promoter, venue, broadcaster, or sponsor, and record the reason for withholding.
- **FR-020**: System MUST remind the user at least 24 hours before the deadline of any opportunity they marked as interesting and have not marked as entered.
- **FR-021**: Users MUST be able to mark an opportunity as interested, entered, dismissed, or won.

**Conduct boundaries**

- **FR-022**: System MUST NOT purchase, reserve, or attempt to acquire any ticket, paid or free, on the user's behalf. Entry actions are performed by the user through the opportunity's official channel.
- **FR-023**: System MUST NOT submit contest or giveaway entries automatically on the user's behalf. *(Pending clarification Q1; if the answer changes this, the requirement changes with it.)*
- **FR-024**: System MUST NOT circumvent access controls, solve or bypass human-verification challenges, disguise its identity to evade bot detection, or use more than one identity per user per opportunity.
- **FR-025**: System MUST honor the machine-readable access policy of every source it reads, and MUST record a source as unavailable rather than working around a refusal.
- **FR-026**: System MUST NOT store the user's payment credentials.
- **FR-027**: When a source becomes unavailable, System MUST reflect the resulting blind spot as reduced confidence in the affected forecast rather than leaving the estimate unchanged.

**Explanation and self-assessment**

- **FR-028**: Users MUST be able to view the complete evidence behind any forecast or opportunity.
- **FR-029**: Users MUST be able to dismiss an individual signal as unreliable and have the forecast recomputed without it.
- **FR-030**: System MUST resolve each forecast as correct or incorrect once its announcement window has passed, and retain the outcome.
- **FR-031**: System MUST report observed hit rate grouped by stated confidence band, and flag bands whose observed rate materially diverges from the stated confidence.

### Key Entities

- **Watch**: A standing request by a user to be told about an artist in a market. Holds the artist, the market and radius, the alert threshold, and active/inactive state.
- **Signal**: A single observation from a public source that bears on whether the artist will play the market — a routing gap, a venue hold, an official post, a promoter listing. Holds source, source reliability, observation time, content summary, and influence direction.
- **Forecast**: The current estimate for a watch — likelihood, expected announcement window, confidence, and the set of signals supporting it. Superseded rather than overwritten, so history is inspectable.
- **Visit**: A confirmed appearance by the artist in the market, containing one or more attendable dates. Holds venue, dates, onsale and presale times, and current status (announced, rescheduled, cancelled).
- **Opportunity**: A no-cost path to attending a visit. Holds source, entry method, official link, eligibility rules, deadline, disclosed odds, verification time, and status.
- **User Profile**: The attributes an opportunity's eligibility rules are evaluated against — home market, age band, fan-club membership and tenure, relevant sponsor relationships. Held only to the extent eligibility evaluation requires.
- **Alert**: A delivered notification, its trigger, its delivery time, and whether it concerned a forecast, a confirmation, a change, or a deadline.
- **Forecast Outcome**: The resolution of a past forecast against what actually happened, retained for the track record.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: When a Chicago-area date is publicly announced, Saurabh is notified within 15 minutes, and before general onsale opens in at least 95% of cases.
- **SC-002**: For markets that eventually receive an announced date, the system raises likelihood above its alert threshold at least 30 days before the official announcement in at least 60% of cases.
- **SC-003**: Across at least twenty resolved forecasts, observed hit rate within each stated confidence band falls within 15 percentage points of that band's stated confidence.
- **SC-004**: The system produces no more than one false confirmation — an alert announcing a date that was never real — per calendar year.
- **SC-005**: For each confirmed Chicago visit, at least five distinct legitimate free-ticket opportunities are surfaced, and at least 90% of them are still open at the moment they are delivered.
- **SC-006**: Of opportunities Saurabh marks as interesting, 100% generate a reminder at least 24 hours before their deadline.
- **SC-007**: Saurabh can go from receiving an alert to arriving at an official entry page in under 3 minutes without leaving the system to hunt for the link.
- **SC-008**: 100% of forecasts and opportunities presented to Saurabh display their supporting evidence with a public source and observation date.
- **SC-009**: Zero tickets are purchased, reserved, or acquired by the system across its operating lifetime.
- **SC-010**: The system operates unattended for 90 days without Saurabh manually checking any external source for Metallica Chicago news.

## Assumptions

These were decided without asking, using industry-standard defaults. Each can be revisited.

1. **Single artist, generalizable.** The system is built around one artist and one market because that is the request, but the entities are artist- and market-neutral so a second watch requires no redesign.
2. **Public sources only.** Everything observed is publicly accessible without payment or credentials. No scraping behind logins, no purchased data feeds in the first version.
3. **Chicago means the metropolitan area**, default radius 50 miles, which captures Soldier Field, the United Center, Wrigley Field, Allstate Arena, and the Tinley Park amphitheatre.
4. **Alert delivery is push-based** to a channel Saurabh already watches, since the value of these alerts decays in hours. A daily digest is insufficient for confirmations.
5. **Forecast confidence is expressed as a likelihood plus a window**, not a single predicted date, because a single date implies precision the underlying signals do not support.
6. **Eligibility is evaluated against a profile Saurabh states himself.** The system does not infer or verify his attributes from third parties.
7. **Opportunity re-verification runs at least daily**, and more frequently as a deadline approaches.
8. **Retention**: signals and resolved forecasts are retained for the life of the watch plus one year, to support calibration. Profile data is retained only while a watch is active.
9. **The system is advisory.** Its output is information and reminders; every consequential action is Saurabh's.
10. **No claim is made about Metallica's current touring status.** The system discovers that at runtime; nothing in this specification presumes a tour is or is not underway.

## Out of Scope

- Purchasing tickets, paid or free, or holding funds or payment instruments.
- Queue automation, presale-code harvesting, bot-driven checkout, or any technique whose purpose is to obtain tickets faster than an unaided human.
- Resale-market monitoring, price prediction, or arbitrage.
- Circumventing human-verification challenges, rate limits, geographic restrictions, or per-person purchase limits.
- Creating or operating multiple identities to increase contest odds.
- Travel, lodging, or itinerary planning around a confirmed date.
- Social features — sharing, group coordination, or fan community functions.

## Open Questions

Three decisions materially change scope and were left open rather than guessed.

- **FR-023 / Q1**: Whether the system prepares entries for Saurabh to submit, or submits them for him. Marked **[NEEDS CLARIFICATION: entry autonomy — prepare-and-notify, or auto-submit with stored personal details?]**. Default assumed in this draft: prepare-and-notify.
- **Q2**: Whether this serves Saurabh alone or any Ascension employee. Marked **[NEEDS CLARIFICATION: single-user tool or multi-tenant service, which determines whether identity, isolation, and per-user profile governance are in scope?]**. Default assumed in this draft: single user.
- **Q3**: Whether the system assists with *paid* ticket acquisition — presale timing, onsale readiness — in addition to free opportunities. Marked **[NEEDS CLARIFICATION: is paid-purchase assistance (informational only) in scope, or strictly free opportunities?]**. Default assumed in this draft: informational presale timing is included via FR-011; no purchase assistance beyond that.
