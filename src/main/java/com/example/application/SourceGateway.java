package com.example.application;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The only component permitted to make an outbound request.
 *
 * <p>Concentrating egress here is what makes the system's conduct boundaries properties of the
 * code rather than promises in a document. A scan for suspicious call shapes can be defeated by
 * assembling a URL from pieces; a single chokepoint cannot, because there is nowhere else for a
 * request to originate.
 *
 * <p>Three rules hold at this boundary:
 *
 * <ul>
 *   <li>The host must be in the registry. An unknown host is refused before a connection opens.
 *   <li>The path must be one the source's access policy permits.
 *   <li>Only retrieval is expressible. {@link Transport} has a single {@code get} method, so there
 *       is no way to write a request that buys, reserves, or submits anything — not by mistake and
 *       not by a later edit that forgets the rule.
 * </ul>
 *
 * <p>A refusal or failure is returned as {@link FetchResult.Unavailable}. It never falls back to
 * another host, another path, or a disguised identity; the blind spot is reported so it can lower
 * forecast confidence instead of silently narrowing the evidence.
 */
public final class SourceGateway {

  private static final Logger logger = LoggerFactory.getLogger(SourceGateway.class);

  /**
   * The transport contract. Retrieval only — deliberately not parameterised by HTTP method, since
   * a method parameter is exactly how a safe client becomes an unsafe one.
   */
  public interface Transport {
    String get(URI uri) throws IOException, InterruptedException;
  }

  public sealed interface FetchResult {
    String sourceId();

    record Fetched(String sourceId, String url, String body, Instant fetchedAt) implements FetchResult {}

    record Unavailable(String sourceId, String reason) implements FetchResult {}
  }

  private final SourceRegistry registry;
  private final Transport transport;
  private final Map<String, Instant> lastRequestAt = new ConcurrentHashMap<>();

  public SourceGateway(SourceRegistry registry, Transport transport) {
    this.registry = registry;
    this.transport = transport;
  }

  public static SourceGateway withDefaultTransport(SourceRegistry registry) {
    return new SourceGateway(registry, new JdkGetOnlyTransport());
  }

  /**
   * Retrieves a path from a registered source.
   *
   * @param sourceId a source id from the registry
   * @param path an absolute path, checked against the source's permitted paths
   */
  public FetchResult fetch(String sourceId, String path) {
    var entry = registry.find(sourceId).orElse(null);
    if (entry == null) {
      logger.warn("Refused request to unregistered source {}", sourceId);
      return new FetchResult.Unavailable(sourceId, "Source is not in the registry");
    }
    if (!entry.permitsPath(path)) {
      logger.warn("Refused path {} on source {} — outside its declared access policy", path, sourceId);
      return new FetchResult.Unavailable(sourceId, "Path is outside the source's access policy: " + path);
    }
    if (isRateLimited(entry)) {
      return new FetchResult.Unavailable(sourceId, "Rate limit for this source has not elapsed");
    }

    var uri = URI.create("https://" + entry.host() + path);
    // Re-checked against the registry after assembly, so a path that smuggles in an authority
    // component cannot redirect the request to a host nobody verified.
    if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(entry.host())) {
      logger.warn("Refused request whose assembled host {} does not match source {}", uri.getHost(), sourceId);
      return new FetchResult.Unavailable(sourceId, "Assembled URL does not resolve to the registered host");
    }

    try {
      lastRequestAt.put(entry.sourceId(), Instant.now());
      String body = transport.get(uri);
      return new FetchResult.Fetched(sourceId, uri.toString(), body, Instant.now());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new FetchResult.Unavailable(sourceId, "Interrupted while reading source");
    } catch (Exception e) {
      logger.warn("Source {} unavailable: {}", sourceId, e.toString());
      return new FetchResult.Unavailable(sourceId, "Source unavailable: " + e.getMessage());
    }
  }

  private boolean isRateLimited(SourceRegistry.SourceEntry entry) {
    var last = lastRequestAt.get(entry.sourceId());
    return last != null && Duration.between(last, Instant.now()).compareTo(entry.minRequestInterval()) < 0;
  }

  /** Retrieval over HTTPS using the JDK client. Identity is not disguised and redirects are not followed. */
  static final class JdkGetOnlyTransport implements Transport {

    private final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Override
    public String get(URI uri) throws IOException, InterruptedException {
      var request = HttpRequest.newBuilder(uri)
          .GET()
          .timeout(Duration.ofSeconds(20))
          .header("User-Agent", "tour-watch/1.0 (fan alerting; read-only)")
          .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IOException("HTTP " + response.statusCode());
      }
      return response.body();
    }
  }
}
