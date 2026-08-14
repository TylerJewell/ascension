package com.example.application;

import akka.javasdk.agent.TextGuardrail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Refuses a narration that rests on nothing.
 *
 * <p>Enforced in the runtime rather than at the call site: a later caller that forgets to check
 * cannot bypass it, which is the whole reason this lives in configuration instead of in a helper
 * method somebody has to remember to invoke.
 *
 * <p>This is the outer half of the evidence rule. It can see the response text but not what was
 * observed, so it checks that citations exist; {@link TourWatchEntity#narrateForecast} checks that
 * they name signals the watch actually holds.
 */
public class EvidenceGuardrail implements TextGuardrail {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Result evaluate(String text) {
    JsonNode root;
    try {
      root = MAPPER.readTree(text);
    } catch (Exception e) {
      // An unreadable reply is refused rather than let through. Passing text nobody can check
      // is the failure this guardrail exists to prevent.
      return new Result(false, "Narration was not readable as a structured response");
    }

    var cited = root.get("citedSignalIds");
    if (cited == null || !cited.isArray() || cited.isEmpty()) {
      return new Result(false, "Narration cites no signal, so nothing supports it");
    }

    for (JsonNode id : cited) {
      if (id.asText().isBlank()) {
        return new Result(false, "Narration cites a blank signal id");
      }
    }

    return Result.OK;
  }
}
