package com.example;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import com.example.application.SourceGateway;
import com.example.application.SourceRegistry;
import com.typesafe.config.Config;
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

  private final SourceRegistry registry;
  private final SourceGateway gateway;

  public Bootstrap(Config appConfig) {
    this.registry = SourceRegistry.fromConfig(appConfig);
    this.gateway = SourceGateway.withDefaultTransport(registry);
  }

  @Override
  public void onStartup() {
    if (registry.isEmpty()) {
      logger.warn(
          "No sources are declared, so no outbound requests will be made. Add a source to "
              + "sources.conf once its terms of use and access policy have been verified.");
    } else {
      logger.info("Watching {} declared source(s)", registry.all().size());
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
