/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package co.cask.cdap.guides;

import co.cask.cdap.api.app.AbstractApplication;
import co.cask.cdap.api.data.stream.Stream;
import co.cask.cdap.api.dataset.lib.KeyValueTable;

/**
 * This is a simple WebLogAnalyticsApplication example that uses one stream, one dataset, one flow and one service.
 * <uL>
 *   <li>A stream to send web logs to.</li>
 *   <li>A flow with a single flowlet that reads the stream and stores the webpage and their view-count
 *   in a KeyValueTable</li>
 *   <li>A service the returns the summary of web page and their page view count from the KeyValueTable </li>
 * </uL>
 */
public class WebLogAnalyticsApplication extends AbstractApplication {

  @Override
  public void configure() {
    setName("WebLogAnalyticsApp");
    setDescription("WebLog Analytics Application to see Pageview stats using the Cask Data Application Platform");
    addStream(new Stream("webLogStream"));
    createDataset("pageViewTable", KeyValueTable.class);
    addFlow(new WebLogAnalyticsFlow());
    addService("WebLogAnalyticsService", new WebLogAnalyticsHandler());
  }
}

