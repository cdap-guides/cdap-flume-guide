package co.cask.cdap.guides;

import co.cask.cdap.api.flow.Flow;
import co.cask.cdap.api.flow.FlowSpecification;

/**
 * WebLogAnalyticsFlow with a single Flowlet {@link PageViewCounterFlowlet}
 */
public class WebLogAnalyticsFlow implements Flow {

  @Override
  public FlowSpecification configure() {
    return FlowSpecification.Builder.with().
      setName("WebLogAnalyticsFlow").
      setDescription("A flow that collects and performs web log analysis").
      withFlowlets().add("pageViewCounter", new PageViewCounterFlowlet()).
      connect().fromStream("webLogs").to("pageViewCounter").
      build();
  }
}
