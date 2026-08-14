package com.example.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** What has already been sent for a watch, so the same news is not delivered twice. */
public record AlertLedger(String watchId, Set<String> sentFingerprints, Instant lastSentAt) {

  public static AlertLedger empty(String watchId) {
    return new AlertLedger(watchId, Set.of(), null);
  }

  public AlertLedger {
    sentFingerprints = sentFingerprints == null ? Set.of() : Set.copyOf(sentFingerprints);
  }

  public boolean hasSent(String fingerprint) {
    return sentFingerprints.contains(fingerprint);
  }

  public AlertLedger recording(String fingerprint, Instant sentAt) {
    var updated = new LinkedHashSet<>(sentFingerprints);
    updated.add(fingerprint);
    return new AlertLedger(watchId, updated, sentAt);
  }
}
