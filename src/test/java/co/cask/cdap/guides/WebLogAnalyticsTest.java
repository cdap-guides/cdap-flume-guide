package co.cask.cdap.guides;

import co.cask.cdap.test.ApplicationManager;
import co.cask.cdap.test.RuntimeMetrics;
import co.cask.cdap.test.RuntimeStats;
import co.cask.cdap.test.ServiceManager;
import co.cask.cdap.test.StreamWriter;
import co.cask.cdap.test.TestBase;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Test for {@link WebLogAnalyticsApplication}
 */
public class WebLogAnalyticsTest extends TestBase {

  private static final Gson GSON = new Gson();

  @Test
  public void testPageViews() throws TimeoutException, InterruptedException, IOException {
    // Deploy the WebLogAnalytics application
    ApplicationManager appManager = deployApplication(WebLogAnalyticsApplication.class);

    // Start WebLogAnalyticsFlow
    appManager.startFlow("WebLogAnalyticsFlow");

    // Start WebLogAnalyticsService
    ServiceManager serviceManager = appManager.startService("WebLogAnalyticsService");
    serviceManager.waitForStatus(true);

    // Send stream events to the "webLogs" Stream
    StreamWriter streamWriter = appManager.getStreamWriter("webLogs");

    streamWriter.send("192.168.99.124 - - [14/Jan/2014:08:12:02 -0400] \"GET /?C=M;O=A HTTP/1.1\" 200 393 \"-\" " +
                        "\"Mozilla/5.0 (compatible; YandexBot/3.0; +http://www.example.org/bots)\"");

    streamWriter.send("192.168.58.16 - - [14/Jan/2014:08:50:05 -0400] \"GET / HTTP/1.0\" 404 208 " +
                        "\"http://www.example.org\" \"MSIE 7.0; Windows NT 5.1; Trident/4.0; .NET CLR 2.0.50727;" +
                        " .NET CLR 3.0.4506.2152; .NET CLR\"");

    streamWriter.send("192.168.12.72 - - [14/Jan/2014:10:06:52 -0400] \"GET /products HTTP/1.1\" 200 581 " +
                        "\"http://www.example.org\" \"Chrome/19.0.1084.15 Safari/536.5\"");

    streamWriter.send("192.168.99.124 - - [14/Jan/2014:06:51:04 -0400] \"GET https://accounts.example.org/signup " +
                        "HTTP/1.1\" 200 392 \"http://www.example.org\" \"Mozilla/5.0 (compatible; YandexBot/3.0; " +
                        "+http://www.example.org/bots)\"");

    streamWriter.send("192.168.139.1 - - [14/Jan/2014:08:40:43 -0400] \"GET https://accounts.example.org/signup " +
                        "HTTP/1.0\" 200 809 \"http://www.example.org\" \"example v4.10.5 (www.example.org)\"");

    try {

      RuntimeMetrics countMetrics = RuntimeStats.getFlowletMetrics("WebLogAnalyticsApp",
                                                                   "WebLogAnalyticsFlow",
                                                                   "pageViewCounter");
      countMetrics.waitForProcessed(5, 5, TimeUnit.SECONDS);

      // Test service to retrieve page views map.
      URL url = new URL(serviceManager.getServiceURL(), "views");
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();


      Assert.assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
      String viewsJson;
      try {
        viewsJson = new String(ByteStreams.toByteArray(conn.getInputStream()), Charsets.UTF_8);
      } finally {
        conn.disconnect();
      }
      Map<String, Long> pageViews = GSON.fromJson(viewsJson, new TypeToken<Map<String, Long>>() {
      }.getType());
      Assert.assertEquals(4, pageViews.size());
      Assert.assertEquals(2L, (long) pageViews.get("https://accounts.example.org/signup"));
    } finally {
      serviceManager.stop();
      serviceManager.waitForStatus(false);
      appManager.stopAll();
    }
  }
}
