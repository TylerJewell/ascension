package com.example.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.domain.SourceTier;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Carries the project exit condition {@code outbound-hosts-read-only}.
 *
 * <p>These assertions are what make "the system never acquires a ticket" a property of the code.
 * If this class is deleted or renamed, that boundary stops being enforced regardless of what the
 * rest of the codebase looks like.
 */
class OutboundHostPolicyTest {

  private static final SourceRegistry.SourceEntry DECLARED = new SourceRegistry.SourceEntry(
      "declared-source",
      "events.declared.test",
      SourceTier.A,
      List.of("/api/events"),
      Duration.ZERO,
      "2026-08-14");

  /** Records what the gateway actually attempted, so "it did not try" can be asserted. */
  private static final class RecordingTransport implements SourceGateway.Transport {
    final List<URI> attempted = new ArrayList<>();
    private final boolean fail;

    RecordingTransport(boolean fail) {
      this.fail = fail;
    }

    @Override
    public String get(URI uri) throws java.io.IOException {
      attempted.add(uri);
      if (fail) {
        throw new java.io.IOException("source is down");
      }
      return "body";
    }
  }

  @Test
  void refusesAHostThatIsNotInTheRegistry() {
    var transport = new RecordingTransport(false);
    var gateway = new SourceGateway(new SourceRegistry(List.of(DECLARED)), transport);

    var result = gateway.fetch("some-other-source", "/api/events");

    assertThat(result).isInstanceOf(SourceGateway.FetchResult.Unavailable.class);
    assertThat(transport.attempted).isEmpty();
  }

  @Test
  void refusesEverythingWhenTheRegistryIsEmpty() {
    var transport = new RecordingTransport(false);
    var gateway = new SourceGateway(new SourceRegistry(List.of()), transport);

    assertThat(gateway.fetch("declared-source", "/api/events"))
        .isInstanceOf(SourceGateway.FetchResult.Unavailable.class);
    assertThat(transport.attempted).isEmpty();
  }

  @Test
  void refusesAPathOutsideTheSourcesDeclaredAccessPolicy() {
    var transport = new RecordingTransport(false);
    var gateway = new SourceGateway(new SourceRegistry(List.of(DECLARED)), transport);

    var result = gateway.fetch("declared-source", "/admin/private");

    assertThat(result).isInstanceOf(SourceGateway.FetchResult.Unavailable.class);
    assertThat(transport.attempted).isEmpty();
  }

  @Test
  void refusesAPathThatWouldRedirectTheRequestToAnotherHost() {
    var transport = new RecordingTransport(false);
    var gateway = new SourceGateway(new SourceRegistry(List.of(DECLARED)), transport);

    // A leading "//" turns the rest of the path into an authority, which would otherwise send
    // the request somewhere nobody verified.
    var result = gateway.fetch("declared-source", "/api/events/..//evil.test/x");

    if (result instanceof SourceGateway.FetchResult.Fetched fetched) {
      assertThat(URI.create(fetched.url()).getHost()).isEqualTo("events.declared.test");
    }
    assertThat(transport.attempted).allSatisfy(uri -> assertThat(uri.getHost()).isEqualTo("events.declared.test"));
  }

  @Test
  void allowsADeclaredHostOnADeclaredPath() {
    var transport = new RecordingTransport(false);
    var gateway = new SourceGateway(new SourceRegistry(List.of(DECLARED)), transport);

    var result = gateway.fetch("declared-source", "/api/events");

    assertThat(result).isInstanceOf(SourceGateway.FetchResult.Fetched.class);
    assertThat(transport.attempted).hasSize(1);
    assertThat(transport.attempted.get(0).getHost()).isEqualTo("events.declared.test");
  }

  @Test
  void reportsAFailedSourceAsUnavailableWithoutTryingAnywhereElse() {
    var transport = new RecordingTransport(true);
    var gateway = new SourceGateway(new SourceRegistry(List.of(DECLARED)), transport);

    var result = gateway.fetch("declared-source", "/api/events");

    assertThat(result).isInstanceOf(SourceGateway.FetchResult.Unavailable.class);
    assertThat(transport.attempted).hasSize(1);
  }

  @Test
  void honoursTheSourcesRateLimit() {
    var limited = new SourceRegistry.SourceEntry(
        "limited", "slow.declared.test", SourceTier.B, List.of("/feed"), Duration.ofMinutes(5), "2026-08-14");
    var transport = new RecordingTransport(false);
    var gateway = new SourceGateway(new SourceRegistry(List.of(limited)), transport);

    gateway.fetch("limited", "/feed");
    var second = gateway.fetch("limited", "/feed");

    assertThat(second).isInstanceOf(SourceGateway.FetchResult.Unavailable.class);
    assertThat(transport.attempted).hasSize(1);
  }

  /**
   * The structural half of the boundary. Retrieval is the only verb the transport can express, so
   * no future edit can send a purchase, a reservation, or a form submission through this seam
   * without first widening this interface — which this assertion makes a visible, deliberate act.
   */
  @Test
  void exposesNoWayToExpressARequestOtherThanRetrieval() {
    var methods = SourceGateway.Transport.class.getDeclaredMethods();

    assertThat(methods).hasSize(1);
    assertThat(methods[0].getName()).isEqualTo("get");
    assertThat(methods[0].getParameterTypes()).containsExactly(URI.class);
  }
}
