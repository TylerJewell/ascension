# ADR 0003: Route all outbound source access through one retrieval-only gateway

**Status**: Accepted
**Date**: 2026-08-14
**Feature**: 001-metallica-tour-watch

## Context

This system exists to help a fan get tickets, and it must never acquire one itself (FR-022). It must not evade bot detection or solve human-verification challenges (FR-024), must honour each source's access policy (FR-025), and must report a source it cannot read as a blind spot rather than working around the refusal (FR-027).

Stated as policy, none of that is checkable. The project's first attempt at enforcement was a source scan for suspicious call shapes — `checkout`, `addToCart`, `placeOrder`. The adversarial review of that check found the obvious defeat: a URL assembled from concatenated pieces, or a client method named something else, passes the scan while the invariant is false. A scan can be a tripwire; it cannot be the boundary.

The property that actually needs to hold is about where requests can originate, not about what they are named.

## Decision

`SourceGateway` is the only component permitted to make an outbound request to a source. No other component holds an HTTP client.

Its transport seam, `SourceGateway.Transport`, declares exactly one method: `String get(URI)`. There is no method parameter, so a non-safe request is not expressible through this interface — not by mistake, and not by a later edit that forgets the rule. Widening it requires changing the interface, which `OutboundHostPolicyTest` asserts by reflection and therefore makes a visible, deliberate act.

The host allowlist lives in `sources.conf` as data, with each entry recording the date its terms and access policy were verified. A host absent from the file is unreachable. The file ships empty: no source is added before verification, so an unverified host cannot arrive by default.

Refusals and failures return a typed `Unavailable`. There is no fallback to another host, another path, or a disguised identity; the blind spot propagates to the watch as `SourceDegraded` and lowers forecast confidence.

Alert delivery to the fan's own webhook is a separate, narrower path (`AlertDelivery`). It is never given a URL from fetched content or model output — only the one registered on the watch — so it cannot become a route to a ticketing host.

## Consequences

"The system never acquires a ticket" becomes a property of the code rather than a claim in a document, and the exit condition `outbound-hosts-read-only` has something real to check. The registry answers "what can this service reach?" by reading one short file instead of auditing call sites.

Because the gateway and registry are injected via `Bootstrap` rather than loaded internally, the scout cycle can be tested against a source that fails on purpose — without that seam, the failure-handling path would be untestable and the test would be theatre.

The costs are real. A source needing an unusual client capability cannot get it without revisiting this decision. Shipping an empty registry means a freshly started service observes nothing until verification work is done, which looks like a broken system to anyone who has not read this. And there are two egress paths rather than one, so the single-chokepoint claim is precise rather than absolute: it holds for source access, which is where acquisition risk lives.
