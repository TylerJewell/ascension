package com.example.application;

import com.example.domain.SourceTier;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The complete set of hosts this service is permitted to read, loaded from {@code sources.conf}.
 *
 * <p>The registry is data, not code, so the question "what can this service reach?" is answered by
 * reading one configuration file rather than by auditing call sites. An empty registry is a valid
 * and safe state: it means the service makes no outbound requests at all.
 *
 * <p>{@code allowedPaths} is the source's own machine-readable access policy, transcribed when the
 * source was verified. The gateway enforces what was transcribed; keeping it in sync with the
 * source is part of re-verifying a source, not something discovered at request time.
 */
public final class SourceRegistry {

  public record SourceEntry(
      String sourceId,
      String host,
      SourceTier tier,
      List<String> allowedPaths,
      Duration minRequestInterval,
      String verifiedOn) {

    public SourceEntry {
      if (sourceId == null || sourceId.isBlank()) {
        throw new IllegalArgumentException("Source id is required");
      }
      if (host == null || host.isBlank()) {
        throw new IllegalArgumentException("Source host is required");
      }
      allowedPaths = List.copyOf(allowedPaths);
    }

    public boolean permitsPath(String path) {
      return allowedPaths.stream().anyMatch(path::startsWith);
    }
  }

  private final Map<String, SourceEntry> byId;

  public SourceRegistry(List<SourceEntry> entries) {
    this.byId = entries.stream()
        .collect(Collectors.toMap(SourceEntry::sourceId, Function.identity()));
  }

  /** Loads the registry shipped with the service. */
  public static SourceRegistry loadDefault() {
    return fromConfig(ConfigFactory.load("sources"));
  }

  public static SourceRegistry fromConfig(Config config) {
    if (!config.hasPath("tourwatch.sources")) {
      return new SourceRegistry(List.of());
    }
    var entries = config.getConfigList("tourwatch.sources").stream()
        .map(SourceRegistry::toEntry)
        .toList();
    return new SourceRegistry(entries);
  }

  private static SourceEntry toEntry(Config c) {
    return new SourceEntry(
        c.getString("source-id"),
        c.getString("host"),
        SourceTier.valueOf(c.getString("tier")),
        c.getStringList("allowed-paths"),
        Duration.ofMillis(c.getLong("min-request-interval-ms")),
        c.getString("verified-on"));
  }

  public Optional<SourceEntry> find(String sourceId) {
    return Optional.ofNullable(byId.get(sourceId));
  }

  public boolean allowsHost(String host) {
    return byId.values().stream().anyMatch(e -> e.host().equalsIgnoreCase(host));
  }

  public List<SourceEntry> all() {
    return List.copyOf(byId.values());
  }

  public boolean isEmpty() {
    return byId.isEmpty();
  }
}
