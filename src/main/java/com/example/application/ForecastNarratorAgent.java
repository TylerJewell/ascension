package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Description;
import java.util.List;

/**
 * Explains a forecast that has already been computed.
 *
 * <p>The likelihood, window, and confidence arrive as inputs. This agent's only job is to say why,
 * in terms of signals that were observed. It must not restate, adjust, or contradict the number —
 * if the prose implies a different confidence than the input, that is a defect, not a nuance.
 */
@Component(id = "forecast-narrator-agent")
public class ForecastNarratorAgent extends Agent {

  public record SignalSummary(String signalId, String kind, String summary, String influence, String sourceUrl) {}

  public record Request(
      String artistName,
      String marketName,
      int likelihood,
      String windowStart,
      String windowEnd,
      String confidence,
      List<SignalSummary> signals) {}

  public record Narration(
      @Description("Two or three sentences explaining the estimate in terms of the observed signals")
          String rationale,
      @Description("The ids of the signals this explanation actually rests on; must not be empty")
          List<String> citedSignalIds) {}

  private static final String SYSTEM_MESSAGE =
      """
      You explain a touring forecast to a fan. The likelihood, announcement window, and confidence
      have already been computed and are given to you. You are writing the explanation only.

      Rules:
      - Never state, adjust, hedge, or contradict the likelihood you were given. It is an input.
      - Cite only signal ids from the list provided. Every id in citedSignalIds must appear in that
        list, and the list must not be empty.
      - The window is when an announcement is expected, not when the show is. Never state or imply
        a show date; no date has been announced.
      - Say plainly when the evidence is thin. A fan who is told the estimate is weak can decide
        what to do about it; a fan who is told a weak estimate confidently cannot.
      """
          .stripIndent();

  public Effect<Narration> narrate(Request request) {
    var signalLines = request.signals().stream()
        .map(s -> "- %s [%s, %s] %s (%s)".formatted(
            s.signalId(), s.kind(), s.influence(), s.summary(), s.sourceUrl()))
        .toList();

    var userMessage =
        """
        Artist: %s
        Market: %s
        Computed likelihood: %d%%
        Announcement window: %s to %s
        Confidence: %s

        Observed signals:
        %s
        """
            .formatted(
                request.artistName(),
                request.marketName(),
                request.likelihood(),
                request.windowStart(),
                request.windowEnd(),
                request.confidence(),
                String.join("\n", signalLines));

    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(userMessage)
        .responseConformsTo(Narration.class)
        .thenReply();
  }
}
