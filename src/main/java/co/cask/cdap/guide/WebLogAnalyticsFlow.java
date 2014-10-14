package co.cask.cdap.guide;

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
      setDescription("A flow that collects and processes weblogs").
      withFlowlets().add("logAnalytics", new WebLogAnalyticsFlowlet()).
      connect().fromStream("webLogStream").to("logAnalytics").
      build();
  }
}
