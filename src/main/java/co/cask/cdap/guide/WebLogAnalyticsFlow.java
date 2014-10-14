package co.cask.cdap.guide;

import co.cask.cdap.api.flow.FlowSpecification;

/**
 *
 */
public class WebLogAnalyticsFlow implements co.cask.cdap.api.flow.Flow {

  @Override
  public FlowSpecification configure() {
    return FlowSpecification.Builder.with().
      setName("WebLogAnalyticsFlow").
      setDescription("A flow that collects and processes weblogs").
      withFlowlets().add("logAnalytics", new WebLogAnalyticsFlowlet()).
      connect().fromStream("webLogStream").to("logAnalytics").
      build();
  }
}
