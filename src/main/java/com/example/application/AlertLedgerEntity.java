package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.example.domain.AlertLedger;
import java.time.Instant;

/**
 * Holds what has already been delivered for a watch.
 *
 * <p>The claim-then-deliver order matters: a fingerprint is recorded before the alert goes out, so
 * a crash mid-delivery costs one missed alert rather than an endless loop of repeats at three in
 * the morning.
 */
@Component(id = "alert-ledger")
public class AlertLedgerEntity extends KeyValueEntity<AlertLedger> {

  private final String entityId;

  public AlertLedgerEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public AlertLedger emptyState() {
    return AlertLedger.empty(entityId);
  }

  /**
   * @return true when this fingerprint had not been sent before and has now been claimed, false
   *     when it was already sent and the caller should stay quiet
   */
  public Effect<Boolean> claim(String fingerprint) {
    if (currentState().hasSent(fingerprint)) {
      return effects().reply(false);
    }
    return effects()
        .updateState(currentState().recording(fingerprint, Instant.now()))
        .thenReply(true);
  }

  public ReadOnlyEffect<AlertLedger> get() {
    return effects().reply(currentState());
  }
}
