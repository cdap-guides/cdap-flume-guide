package co.cask.cdap.guides;

import co.cask.cdap.api.flow.AbstractFlow;

/**
 * WebLogAnalyticsFlow with a single Flowlet {@link PageViewCounterFlowlet}
 */
public class WebLogAnalyticsFlow extends AbstractFlow {

  @Override
  public void configure() {
    setName("WebLogAnalyticsFlow");
    setDescription("A flow that collects and performs web log analysis");
    addFlowlet("pageViewCounter", new PageViewCounterFlowlet());
    connectStream("webLogs", "pageViewCounter");
  }
}
