package com.example.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delivers an alert to the fan's own webhook.
 *
 * <p>This is a second egress path, separate from {@link SourceGateway}, and the separation is
 * deliberate rather than an oversight. The gateway reads sources and is retrieval-only because a
 * write to a source is how a system starts acquiring things. This path only ever posts to a URL
 * the fan registered on his own watch, and it is never given a URL from source content or model
 * output — so it cannot become a route to a ticketing host.
 */
public class AlertDelivery {

  private static final Logger logger = LoggerFactory.getLogger(AlertDelivery.class);

  private final HttpClient client = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NEVER)
      .connectTimeout(Duration.ofSeconds(10))
      .build();

  /**
   * @param webhookUrl must come from the watch's registered configuration, never from fetched
   *     content
   * @return true when the fan's endpoint accepted the alert
   */
  public boolean deliver(String webhookUrl, String jsonBody) {
    if (webhookUrl == null || webhookUrl.isBlank()) {
      logger.warn("Watch has no alert webhook configured; alert not delivered");
      return false;
    }
    if (!webhookUrl.startsWith("https://")) {
      logger.warn("Refusing to deliver an alert over a non-HTTPS webhook");
      return false;
    }
    try {
      var request = HttpRequest.newBuilder(URI.create(webhookUrl))
          .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
          .header("Content-Type", "application/json")
          .timeout(Duration.ofSeconds(15))
          .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() < 400;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      logger.warn("Alert delivery failed: {}", e.toString());
      return false;
    }
  }
}
