package com.example;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import com.example.application.SourceGateway;
import com.example.application.SourceRegistry;
import com.typesafe.config.Config;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the outbound boundary once, from configuration, and hands it to the components that need
 * it.
 *
 * <p>Injecting the registry rather than loading it inside the workflow is what lets a test run the
 * scout cycle against a source that fails on purpose. It also means there is exactly one place
 * where the allowlist is turned into a live gateway.
 */
@Setup
public class Bootstrap implements ServiceSetup {

  private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);

  private final Config appConfig;
  private final SourceRegistry registry;
  private final SourceGateway gateway;

  public Bootstrap(Config appConfig) {
    this.appConfig = appConfig;
    this.registry = SourceRegistry.fromConfig(appConfig);
    this.gateway = SourceGateway.withDefaultTransport(registry);
  }

  @Override
  public void onStartup() {
    warnIfSelectedModelHasNoCredential();

    if (registry.isEmpty()) {
      logger.warn(
          "No sources are declared, so no outbound requests will be made. Add a source to "
              + "sources.conf once its terms of use and access policy have been verified.");
    } else {
      logger.info("Watching {} declared source(s)", registry.all().size());
    }
  }

  /**
   * Says at startup what would otherwise surface as an opaque failure on the first model call,
   * halfway through a scout cycle.
   */
  private void warnIfSelectedModelHasNoCredential() {
    var provider = appConfig.getString("akka.javasdk.agent.model-provider");
    var path = "akka.javasdk.agent." + provider;
    if (!appConfig.hasPath(path)) {
      logger.warn("MODEL_PROVIDER is set to '{}', which has no configuration section", provider);
      return;
    }

    // Local providers are addressed rather than authenticated.
    if (provider.equals("ollama") || provider.equals("local-ai")) {
      logger.info("Using local model provider '{}'", provider);
      return;
    }

    var section = appConfig.getConfig(path);
    boolean credentialed = List.of("api-key", "access-token").stream()
        .anyMatch(field -> section.hasPath(field) && !section.getString(field).isBlank());

    if (credentialed) {
      logger.info("Using model provider '{}'", provider);
    } else {
      logger.warn(
          "Model provider '{}' has no credential set. The service will start, but any step "
              + "that calls an agent will fail. See the README for the variable it expects.",
          provider);
    }
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    return new DependencyProvider() {
      @Override
      @SuppressWarnings("unchecked")
      public <T> T getDependency(Class<T> clazz) {
        if (clazz == SourceRegistry.class) {
          return (T) registry;
        }
        if (clazz == SourceGateway.class) {
          return (T) gateway;
        }
        return null;
      }
    };
  }
}
