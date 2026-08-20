package io.akka.gptresearcher.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

/**
 * The HTTP surface, end to end against a running runtime — the whole capability driven the way
 * a caller drives it, since this port has no other interface (see {@code gui/manifest.json}).
 */
public class ResearchEndpointIntegrationTest extends TestKitSupport {

  @Test
  public void researchesAQueryAgainstTheSeedCorpus() {
    var response =
        httpClient
            .POST("/research")
            .withRequestBody(new ResearchEndpoint.ResearchRequest("akka workflows", false))
            .responseBodyAs(ResearchEndpoint.ResearchResponse.class)
            .invoke()
            .body();

    assertThat(response.query()).isEqualTo("akka workflows");
    assertThat(response.sourceCount()).isGreaterThan(0);
    assertThat(response.context()).isNotEmpty();
    assertThat(response.curated()).isFalse();
  }

  @Test
  public void curationCanBeToggledOnPerRequest() {
    var response =
        httpClient
            .POST("/research")
            .withRequestBody(new ResearchEndpoint.ResearchRequest("akka", true))
            .responseBodyAs(ResearchEndpoint.ResearchResponse.class)
            .invoke()
            .body();

    assertThat(response.curated()).isTrue();
  }

  @Test
  public void unmatchedQueryReturnsEmptyContext() {
    var response =
        httpClient
            .POST("/research")
            .withRequestBody(new ResearchEndpoint.ResearchRequest("zzz-nonexistent-topic-zzz", false))
            .responseBodyAs(ResearchEndpoint.ResearchResponse.class)
            .invoke()
            .body();

    assertThat(response.context()).isEmpty();
    assertThat(response.sourceCount()).isZero();
  }
}
