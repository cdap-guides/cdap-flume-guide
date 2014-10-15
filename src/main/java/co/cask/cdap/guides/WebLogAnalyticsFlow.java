package co.cask.cdap.guides;

import co.cask.cdap.api.flow.Flow;
import co.cask.cdap.api.flow.FlowSpecification;

/**
 * WebLogAnalyticsFlow with a single Flowlet {@link WebLogAnalyticsFlowlet}
 */
public class WebLogAnalyticsFlow implements Flow {

  @Override
  public FlowSpecification configure() {
    return FlowSpecification.Builder.with().
      setName("WebLogAnalyticsFlow").
      setDescription("A flow that collects and performs web log analysis").
      withFlowlets().add("WebLogAnalyticsFlowlet", new WebLogAnalyticsFlowlet()).
      connect().fromStream("webLogStream").to("WebLogAnalyticsFlowlet").
      build();
  }
}
