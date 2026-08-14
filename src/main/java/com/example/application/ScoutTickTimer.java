package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.timer.TimerScheduler;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the recurring observation cycle for one watch.
 *
 * <p>Each tick reschedules the next one, so a watch keeps observing for as long as it is active
 * without anything outside the service having to remember it exists.
 */
@Component(id = "scout-tick")
public class ScoutTickTimer extends TimedAction {

  private static final Logger logger = LoggerFactory.getLogger(ScoutTickTimer.class);

  public static final Duration INTERVAL = Duration.ofHours(1);

  private final ComponentClient componentClient;
  private final TimerScheduler timerScheduler;

  public ScoutTickTimer(ComponentClient componentClient, TimerScheduler timerScheduler) {
    this.componentClient = componentClient;
    this.timerScheduler = timerScheduler;
  }

  public static String timerName(String watchId) {
    return "scout-tick-" + watchId;
  }

  public Effect tick(String watchId) {
    var watch = componentClient
        .forEventSourcedEntity(watchId)
        .method(TourWatchEntity::getWatch)
        .invoke();

    if (!watch.active()) {
      logger.info("Watch {} is inactive; stopping the scout cycle", watchId);
      return effects().done();
    }

    componentClient
        .forWorkflow(watchId)
        .method(TourScoutWorkflow::runCycle)
        .invoke(watchId);

    scheduleNext(timerScheduler, componentClient, watchId, INTERVAL);
    return effects().done();
  }

  /** Also used to start the cycle when a watch is first registered. */
  public static void scheduleNext(
      TimerScheduler timerScheduler, ComponentClient componentClient, String watchId, Duration delay) {
    timerScheduler.createSingleTimer(
        timerName(watchId),
        delay,
        componentClient.forTimedAction().method(ScoutTickTimer::tick).deferred(watchId));
  }
}
