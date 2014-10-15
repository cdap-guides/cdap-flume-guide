package co.cask.cdap.guides;

import co.cask.cdap.api.annotation.ProcessInput;
import co.cask.cdap.api.annotation.UseDataSet;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.lib.KeyValueTable;
import co.cask.cdap.api.flow.flowlet.AbstractFlowlet;
import co.cask.cdap.api.flow.flowlet.StreamEvent;
import com.google.common.base.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flowlet recevies weblog as stream event.
 * Parses and extracts the requested page , it then increments the page-view count for the requested page
 * in the pageViewTable
 */
public class WebLogAnalyticsFlowlet extends AbstractFlowlet {
  private static final Logger LOG = LoggerFactory.getLogger(WebLogAnalyticsFlowlet.class);
  private static final Pattern ACCESS_LOG_PATTERN = Pattern.compile(
    //   IP       id    user      date          request     code     size    referrer    user agent
    "^([\\d.]+) (\\S+) (\\S+) \\[([^\\]]+)\\] \"([^\"]+)\" (\\d{3}) (\\d+) \"([^\"]+)\" \"([^\"]+)\"");
  final Pattern REQUEST_PAGE_PATTERN = Pattern.compile("(\\S+)\\s(\\S+).*");
  @UseDataSet("pageViewTable")
  KeyValueTable pageViewTable;

  @ProcessInput
  public void process(StreamEvent log) {
    String event = Charsets.UTF_8.decode(log.getBody()).toString();
    Matcher logMatcher = ACCESS_LOG_PATTERN.matcher(event);
    if (!logMatcher.matches() || logMatcher.groupCount() < 8) {
      LOG.info("Invalid event received {}", log);
      return;
    }
    String request = logMatcher.group(5);
    Matcher requestMatcher = REQUEST_PAGE_PATTERN.matcher(request);
    if (!requestMatcher.matches() || requestMatcher.groupCount() < 2) {
      LOG.info("Invalid event received {}", log);
      return;
    }
    String uri = requestMatcher.group(2);
    pageViewTable.increment(Bytes.toBytes(uri), 1L);
  }
}
