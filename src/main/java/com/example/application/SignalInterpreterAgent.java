package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Description;
import com.example.domain.Influence;
import com.example.domain.SignalKind;
import java.util.List;

/**
 * Reads fetched source content into typed signal candidates.
 *
 * <p>This is the job a model is genuinely good at: turning promoter prose and calendar notes into
 * structure. It is deliberately not asked how likely the visit is — that number is computed in
 * {@link com.example.domain.ForecastScorer} where it can be tuned and tested.
 *
 * <p>The agent cannot assert its own provenance. Source, tier, and URL are supplied by the caller
 * from the registry, so nothing the model writes can upgrade a rumour into an official source.
 */
@Component(id = "signal-interpreter-agent")
public class SignalInterpreterAgent extends Agent {

  public record Request(String artistName, String marketName, String sourceUrl, String content) {}

  public record SignalCandidate(
      @Description("One of the declared signal kinds") SignalKind kind,
      @Description("One sentence a fan can read, describing what was observed") String summary,
      @Description("How strongly this pushes the estimate") Influence influence,
      @Description("A verbatim quote from the supplied content that supports this signal") String excerpt) {}

  public record SignalCandidates(
      @Description("Signals found in the content; empty when the content says nothing relevant")
          List<SignalCandidate> signals) {}

  private static final String SYSTEM_MESSAGE =
      """
      You read publicly posted content about live music and extract only what it actually says
      about whether a named artist will play a named market.

      Rules:
      - Return a signal only when the content supports it. Returning an empty list is a correct,
        common, and expected answer. Do not manufacture a signal to seem useful; a fabricated
        signal moves a real person's expectations about a real event.
      - Every signal must include an excerpt quoted verbatim from the supplied content.
      - Do not estimate probabilities, and do not state or infer show dates. You are describing
        what the content says, not what it means for the outcome.
      - Use ROUTING_GAP_ADJACENT when an announced tour leg leaves an unfilled slot near the
        market, ROUTING_PAST_WITHOUT_STOP when a leg passes the market by, VENUE_HOLD for a
        calendar hold, OFFICIAL_TEASER for a teaser or countdown, RELEASE_CYCLE_ACTIVE for an
        active album cycle, CADENCE_ELAPSED when a long time has passed since the last visit,
        HIATUS_DECLARED for a stated break, and OFFICIAL_ANNOUNCEMENT only when a date has
        actually been announced.
      """
          .stripIndent();

  public Effect<SignalCandidates> interpret(Request request) {
    var userMessage =
        """
        Artist: %s
        Market: %s
        Source: %s

        Content:
        %s
        """
            .formatted(request.artistName(), request.marketName(), request.sourceUrl(), request.content());

    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(userMessage)
        .responseConformsTo(SignalCandidates.class)
        .thenReply();
  }
}
