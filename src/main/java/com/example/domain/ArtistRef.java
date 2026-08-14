package com.example.domain;

/** The artist a watch is about. */
public record ArtistRef(String slug, String displayName) {

  public ArtistRef {
    if (slug == null || slug.isBlank()) {
      throw new IllegalArgumentException("Artist slug is required");
    }
  }
}
