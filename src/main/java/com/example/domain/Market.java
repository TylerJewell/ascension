package com.example.domain;

/**
 * A metropolitan area, not a municipality.
 *
 * <p>A fan who wants to see the band "in Chicago" means the stadium downtown and the amphitheatre
 * an hour out equally, so a market carries a radius and venues are judged against it.
 */
public record Market(String slug, String displayName, double centroidLat, double centroidLon, int radiusMiles) {

  public static final int DEFAULT_RADIUS_MILES = 50;

  public Market {
    if (slug == null || slug.isBlank()) {
      throw new IllegalArgumentException("Market slug is required");
    }
    if (radiusMiles <= 0) {
      throw new IllegalArgumentException("Market radius must be positive, got " + radiusMiles);
    }
  }

  /** Great-circle distance in miles, used to decide whether a venue falls inside the market. */
  public double distanceMilesTo(double lat, double lon) {
    double earthRadiusMiles = 3958.8;
    double dLat = Math.toRadians(lat - centroidLat);
    double dLon = Math.toRadians(lon - centroidLon);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(centroidLat)) * Math.cos(Math.toRadians(lat))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return earthRadiusMiles * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  public boolean contains(VenueRef venue) {
    return distanceMilesTo(venue.latitude(), venue.longitude()) <= radiusMiles;
  }
}
