package com.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.example.domain.FanProfile;
import java.util.List;

/**
 * The fan's self-declared attributes, held so eligibility rules have something to evaluate against.
 *
 * <p>A key value entity rather than an event sourced one: nothing in the spec asks for the history
 * of a fan's residency or age band, only its current value.
 */
@Component(id = "fan-profile")
public class FanProfileEntity extends KeyValueEntity<FanProfile> {

  private final String entityId;

  public FanProfileEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public FanProfile emptyState() {
    return new FanProfile(entityId, "", "", "", FanProfile.AgeBand.UNDER_18, List.of(), List.of(), "");
  }

  public Effect<Done> save(FanProfile profile) {
    if (!profile.fanId().equals(entityId)) {
      return effects().error("Profile fan id " + profile.fanId() + " does not match entity " + entityId);
    }
    return effects().updateState(profile).thenReply(Done.getInstance());
  }

  public ReadOnlyEffect<FanProfile> get() {
    return effects().reply(currentState());
  }
}
