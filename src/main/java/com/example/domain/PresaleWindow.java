package com.example.domain;

import java.time.Instant;

/**
 * A presale a fan may be entitled to enter before general onsale.
 *
 * <p>{@code requirement} names what qualifies someone — a fan club, a card, a carrier — so the
 * fan is told which windows are actually his rather than all of them.
 */
public record PresaleWindow(String name, Instant opensAt, Instant closesAt, String requirement) {

  public boolean isOpenAt(Instant moment) {
    return !moment.isBefore(opensAt) && moment.isBefore(closesAt);
  }
}
