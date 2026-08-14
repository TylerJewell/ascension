package com.example.domain;

/** A place a visit can happen, located so it can be judged against a market radius. */
public record VenueRef(String name, String city, double latitude, double longitude) {

  public VenueRef {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Venue name is required");
    }
  }
}
