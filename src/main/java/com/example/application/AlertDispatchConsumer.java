package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.example.domain.Alert;
import com.example.domain.TourWatchEvent;
import com.example.domain.TourWatchState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns journal events into the alerts a fan actually receives.
 *
 * <p>Every alert is claimed in the ledger before it is sent, so the same news is never delivered
 * twice. The rendered payload carries time remaining computed at this moment rather than the time
 * the event happened — an alert that sat unread for six hours must still be actionable on its face.
 */
@Component(id = "alert-dispatch-consumer")
@Consume.FromEventSourcedEntity(TourWatchEntity.class)
public class AlertDispatchConsumer extends Consumer {

  private static final Logger logger = LoggerFactory.getLogger(AlertDispatchConsumer.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** What the fan's webhook receives. */
  public record AlertPayload(
      String watchId,
      String kind,
      String headline,
      String timeToAct,
      String actByUtc,
      String detail,
      String evidenceUrl) {}

  private final ComponentClient componentClient;
  private final AlertDelivery delivery;

  public AlertDispatchConsumer(ComponentClient componentClient) {
    this(componentClient, new AlertDelivery());
  }

  AlertDispatchConsumer(ComponentClient componentClient, AlertDelivery delivery) {
    this.componentClient = componentClient;
    this.delivery = delivery;
  }

  public Effect onEvent(TourWatchEvent event) {
    var watchId = messageContext().eventSubject().orElse(null);
    if (watchId == null) {
      return effects().ignore();
    }
    var watch = componentClient
        .forEventSourcedEntity(watchId)
        .method(TourWatchEntity::getWatch)
        .invoke();

    return alertFor(watchId, watch, event)
        .map(alert -> dispatch(watch, alert))
        .orElseGet(() -> effects().ignore());
  }

  private Optional<Alert> alertFor(String watchId, TourWatchState watch, TourWatchEvent event) {
    return switch (event) {
      case TourWatchEvent.VisitConfirmed e -> Optional.of(new Alert(
          watchId,
          Alert.AlertKind.CONFIRMATION,
          e.visit().visitId(),
          "%s — %s, %s".formatted(
              watch.artist().displayName(), e.visit().venue().name(), e.visit().venue().city()),
          e.visit().dates().toString() + "|" + e.visit().status(),
          e.visit().onsaleAt(),
          e.visit().sourceUrl()));

      case TourWatchEvent.VisitChanged e -> watch.findVisit(e.visitId())
          .map(visit -> new Alert(
              watchId,
              Alert.AlertKind.VISIT_CHANGED,
              e.visitId(),
              "%s — %s is now %s".formatted(
                  watch.artist().displayName(), visit.venue().name(), e.newStatus()),
              e.newStatus().name(),
              visit.onsaleAt(),
              visit.sourceUrl()));

      case TourWatchEvent.ForecastRecomputed e -> {
        if (e.forecast().likelihood() < watch.alertThreshold()) {
          yield Optional.empty();
        }
        yield Optional.of(new Alert(
            watchId,
            Alert.AlertKind.FORECAST_THRESHOLD,
            e.forecast().forecastId(),
            // Named a forecast on its face: a prediction must never be readable as an announcement.
            "Forecast: %s in %s is %d%% likely, announcement expected %s to %s".formatted(
                watch.artist().displayName(),
                watch.market().displayName(),
                e.forecast().likelihood(),
                e.forecast().windowStart(),
                e.forecast().windowEnd()),
            String.valueOf(e.forecast().likelihood()),
            null,
            ""));
      }

      default -> Optional.empty();
    };
  }

  private Effect dispatch(TourWatchState watch, Alert alert) {
    boolean claimed = componentClient
        .forKeyValueEntity(alert.watchId())
        .method(AlertLedgerEntity::claim)
        .invoke(alert.fingerprint());

    if (!claimed) {
      logger.debug("Suppressed duplicate alert {} for {}", alert.kind(), alert.watchId());
      return effects().ignore();
    }

    var now = Instant.now();
    var payload = new AlertPayload(
        alert.watchId(),
        alert.kind().name(),
        alert.headline(),
        alert.timeToAct(now).toString(),
        alert.actByUtc() == null ? null : alert.actByUtc().toString(),
        alert.materialFacts(),
        alert.evidenceUrl());

    try {
      delivery.deliver(watch.alertWebhookUrl(), MAPPER.writeValueAsString(payload));
    } catch (Exception e) {
      logger.warn("Could not render alert for {}: {}", alert.watchId(), e.toString());
    }
    return effects().done();
  }
}
