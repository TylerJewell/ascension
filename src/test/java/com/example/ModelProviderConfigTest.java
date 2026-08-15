package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import com.typesafe.config.ConfigFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The provider sections are configuration rather than code, so nothing else would catch a typo in
 * them until an agent call failed at runtime, halfway through a scout cycle.
 */
class ModelProviderConfigTest {

  private static final List<String> SUPPORTED = List.of(
      "googleai-gemini", "anthropic", "openai", "mistral-ai",
      "vertex-ai", "azure-openai", "bedrock", "hugging-face",
      "ollama", "local-ai");

  @Test
  void everySupportedProviderHasAResolvableSection() {
    var config = ConfigFactory.load();

    for (var name : SUPPORTED) {
      var path = "akka.javasdk.agent." + name;
      assertThat(config.hasPath(path)).as("section %s exists", path).isTrue();
      assertThat(config.getString(path + ".provider"))
          .as("%s declares its own provider name", name)
          .isEqualTo(name);
    }
  }

  @Test
  void defaultsToGeminiWhenNoProviderIsSelected() {
    var config = ConfigFactory.load();

    // MODEL_PROVIDER is unset in the test environment, so the default stands.
    assertThat(config.getString("akka.javasdk.agent.model-provider")).isEqualTo("googleai-gemini");
  }

  @Test
  void carriesADefaultModelForTheProvidersThatCanHaveOne() {
    var config = ConfigFactory.load();

    assertThat(config.getString("akka.javasdk.agent.googleai-gemini.model-name")).isNotBlank();
    assertThat(config.getString("akka.javasdk.agent.anthropic.model-name")).isNotBlank();
    assertThat(config.getString("akka.javasdk.agent.openai.model-name")).isNotBlank();
  }

  @Test
  void selectingAProviderPicksUpThatSectionsModel() {
    // Stands in for MODEL_PROVIDER=anthropic, which cannot be set inside a running JVM.
    var config = ConfigFactory.parseString("akka.javasdk.agent.model-provider = anthropic")
        .withFallback(ConfigFactory.load())
        .resolve();

    var selected = config.getString("akka.javasdk.agent.model-provider");

    assertThat(selected).isEqualTo("anthropic");
    assertThat(config.getString("akka.javasdk.agent." + selected + ".model-name")).isNotBlank();
  }

  @Test
  void keepsTheGuardrailBoundToTheNarratorWhicheverProviderIsUsed() {
    var config = ConfigFactory.load();
    var guardrail = config.getConfig("akka.javasdk.agent.guardrails").getConfig("\"forecast evidence\"");

    assertThat(guardrail.getStringList("agents")).contains("forecast-narrator-agent");
    assertThat(guardrail.getBoolean("report-only")).isFalse();
  }
}
