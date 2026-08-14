package com.example.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * What the fan has told us about himself, held only so eligibility rules can be evaluated.
 *
 * <p>There is deliberately no payment field here or anywhere in this model. The system never
 * buys anything, so it has no reason to hold the means to.
 *
 * <p>Age is a band rather than a birthdate: contests ask whether an entrant is over 18 or 21, and
 * that is all this needs to answer.
 */
public record FanProfile(
    String fanId,
    String displayName,
    String homeMarket,
    String residencyState,
    AgeBand ageBand,
    List<Membership> memberships,
    List<String> sponsorRelationships,
    String alertWebhookUrl) {

  public enum AgeBand {
    UNDER_18(0),
    A18_20(18),
    A21_PLUS(21);

    private final int minimumAge;

    AgeBand(int minimumAge) {
      this.minimumAge = minimumAge;
    }

    public boolean meets(int requiredAge) {
      return minimumAge >= requiredAge;
    }
  }

  /** Membership in a fan club or similar organisation, with the date it began. */
  public record Membership(String organisation, LocalDate since) {}

  public FanProfile {
    if (fanId == null || fanId.isBlank()) {
      throw new IllegalArgumentException("Fan id is required");
    }
    memberships = memberships == null ? List.of() : List.copyOf(memberships);
    sponsorRelationships = sponsorRelationships == null ? List.of() : List.copyOf(sponsorRelationships);
  }

  public Optional<Membership> membershipIn(String organisation) {
    return memberships.stream()
        .filter(m -> m.organisation().equalsIgnoreCase(organisation))
        .findFirst();
  }

  public boolean hasSponsorRelationship(String sponsor) {
    return sponsorRelationships.stream().anyMatch(s -> s.equalsIgnoreCase(sponsor));
  }

  /**
   * Loose match used for presale requirements, which are free text from the promoter rather than
   * structured rules. A blank requirement is open to everyone.
   */
  public boolean satisfiesRequirement(String requirement) {
    if (requirement == null || requirement.isBlank()) {
      return true;
    }
    return membershipIn(requirement).isPresent() || hasSponsorRelationship(requirement);
  }
}
