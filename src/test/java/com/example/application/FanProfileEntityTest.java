package com.example.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.domain.FanProfile;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FanProfileEntityTest {

  private static FanProfile saurabh() {
    return new FanProfile(
        "saurabh",
        "Saurabh",
        "chicago",
        "IL",
        FanProfile.AgeBand.A21_PLUS,
        List.of(new FanProfile.Membership("fifth-member", LocalDate.of(2019, 3, 1))),
        List.of(),
        "https://hooks.example.test/saurabh");
  }

  @Test
  void storesAndReturnsTheProfile() {
    var testKit = KeyValueEntityTestKit.of("saurabh", FanProfileEntity::new);

    testKit.method(FanProfileEntity::save).invoke(saurabh());
    var stored = testKit.method(FanProfileEntity::get).invoke().getReply();

    assertThat(stored.displayName()).isEqualTo("Saurabh");
    assertThat(stored.membershipIn("fifth-member")).isPresent();
  }

  @Test
  void returnsAnEmptyProfileBeforeAnythingIsStored() {
    var testKit = KeyValueEntityTestKit.of("nobody", FanProfileEntity::new);

    var stored = testKit.method(FanProfileEntity::get).invoke().getReply();

    assertThat(stored.fanId()).isEqualTo("nobody");
    assertThat(stored.memberships()).isEmpty();
  }

  @Test
  void rejectsAProfileWhoseIdDoesNotMatchTheEntity() {
    var testKit = KeyValueEntityTestKit.of("someone-else", FanProfileEntity::new);

    var result = testKit.method(FanProfileEntity::save).invoke(saurabh());

    assertThat(result.isError()).isTrue();
  }

  @Test
  void holdsNoPaymentFieldOfAnyKind() {
    // FR-026 as a structural assertion: the system never buys anything, so it never has cause
    // to hold the means to.
    var forbidden = List.of("card", "cvv", "cvc", "expiry", "payment");

    var fields = List.of(FanProfile.class.getRecordComponents()).stream()
        .map(c -> c.getName().toLowerCase())
        .toList();

    assertThat(fields).noneMatch(field -> forbidden.stream().anyMatch(field::contains));
  }
}
