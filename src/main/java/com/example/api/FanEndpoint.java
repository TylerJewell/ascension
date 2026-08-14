package com.example.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import akka.javasdk.timer.TimerScheduler;
import com.example.application.ScoutTickTimer;
import com.example.application.TourWatchEntity;
import com.example.application.TourWatchView;
import com.example.domain.ArtistRef;
import com.example.domain.FanProfile;
import com.example.domain.Market;
import com.example.application.FanProfileEntity;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * The fan's surface.
 *
 * <p>There is deliberately no route here that enters a contest, purchases, or reserves anything.
 * The system tells Saurabh what is happening and when it closes; every consequential action stays
 * his.
 */
@HttpEndpoint("/fan")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class FanEndpoint {

  private final ComponentClient componentClient;
  private final TimerScheduler timerScheduler;

  public FanEndpoint(ComponentClient componentClient, TimerScheduler timerScheduler) {
    this.componentClient = componentClient;
    this.timerScheduler = timerScheduler;
  }

  @Post("/profile")
  public HttpResponse createProfile(FanApiTypes.CreateProfileRequest request) {
    if (request.fanId() == null || request.fanId().isBlank()) {
      throw HttpException.badRequest("fanId is required");
    }

    FanProfile.AgeBand ageBand;
    try {
      ageBand = FanProfile.AgeBand.valueOf(request.ageBand());
    } catch (IllegalArgumentException e) {
      throw HttpException.badRequest("ageBand must be UNDER_18, A18_20, or A21_PLUS");
    }

    var memberships = (request.fanClubs() == null ? List.<String>of() : request.fanClubs()).stream()
        // Tenure is unknown at profile creation, so it starts today. A contest that requires a
        // year of membership will read as unmet rather than silently assumed satisfied.
        .map(org -> new FanProfile.Membership(org, LocalDate.now()))
        .toList();

    var profile = new FanProfile(
        request.fanId(),
        request.displayName(),
        request.homeMarket(),
        request.residencyState(),
        ageBand,
        memberships,
        request.sponsorRelationships() == null ? List.of() : request.sponsorRelationships(),
        request.alertWebhookUrl());

    componentClient
        .forKeyValueEntity(request.fanId())
        .method(FanProfileEntity::save)
        .invoke(profile);

    return HttpResponses.created();
  }

  @Post("/watches")
  public FanApiTypes.RegisterWatchResponse registerWatch(FanApiTypes.RegisterWatchRequest request) {
    if (request.alertThreshold() < 0 || request.alertThreshold() > 100) {
      throw HttpException.badRequest("alertThreshold must be between 0 and 100");
    }
    if (request.radiusMiles() <= 0) {
      throw HttpException.badRequest("radiusMiles must be positive");
    }
    if (request.alertWebhookUrl() == null || !request.alertWebhookUrl().startsWith("https://")) {
      throw HttpException.badRequest("alertWebhookUrl must be an https URL");
    }

    var watchId = request.artistSlug() + ":" + request.marketSlug();
    var artist = new ArtistRef(request.artistSlug(), request.artistName());
    var market = new Market(
        request.marketSlug(), request.marketName(),
        request.centroidLat(), request.centroidLon(), request.radiusMiles());

    componentClient
        .forEventSourcedEntity(watchId)
        .method(TourWatchEntity::register)
        .invoke(new TourWatchEntity.RegisterWatch(
            artist, market, request.alertThreshold(), request.alertWebhookUrl()));

    // The first cycle runs promptly rather than an hour from now, so registering a watch produces
    // something to look at instead of an empty page.
    ScoutTickTimer.scheduleNext(timerScheduler, componentClient, watchId, Duration.ofSeconds(5));

    return new FanApiTypes.RegisterWatchResponse(watchId);
  }

  @Get("/watches/{watchId}")
  public FanApiTypes.WatchResponse getWatch(String watchId) {
    var state = componentClient
        .forEventSourcedEntity(watchId)
        .method(TourWatchEntity::getWatch)
        .invoke();

    if (!state.isRegistered()) {
      throw HttpException.notFound();
    }
    return FanApiTypes.toApi(state);
  }

  @Get("/watches")
  public FanApiTypes.WatchList listWatches() {
    var entries = componentClient
        .forView()
        .method(TourWatchView::activeWatches)
        .invoke();

    return new FanApiTypes.WatchList(entries.watches().stream()
        .map(w -> new FanApiTypes.WatchSummary(
            w.watchId(), w.artistName(), w.marketName(), w.active(), w.likelihood()))
        .toList());
  }

  @Delete("/watches/{watchId}")
  public HttpResponse deactivateWatch(String watchId) {
    componentClient
        .forEventSourcedEntity(watchId)
        .method(TourWatchEntity::deactivate)
        .invoke("Deactivated by the fan");

    timerScheduler.delete(ScoutTickTimer.timerName(watchId));
    return HttpResponses.noContent();
  }
}
