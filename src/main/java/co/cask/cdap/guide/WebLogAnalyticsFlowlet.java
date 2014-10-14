package co.cask.cdap.guide;

import co.cask.cdap.api.annotation.ProcessInput;
import co.cask.cdap.api.annotation.UseDataSet;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.lib.KeyValueTable;
import co.cask.cdap.api.flow.flowlet.AbstractFlowlet;
import co.cask.cdap.api.flow.flowlet.StreamEvent;
import com.google.common.base.Charsets;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class WebLogAnalyticsFlowlet extends AbstractFlowlet {
  static final Pattern ACCESS_LOG_PATTERN = Pattern.compile(
    //   IP       id    user      date          request     code     size    referrer    user agent
    "^([\\d.]+) (\\S+) (\\S+) \\[([^\\]]+)\\] \"([^\"]+)\" (\\d{3}) (\\d+) \"([^\"]+)\" \"([^\"]+)\"");

  @UseDataSet("pageViewTable")
  KeyValueTable pageViewTable;

  @ProcessInput
  public void process(StreamEvent log) {
    String event = Charsets.UTF_8.decode(log.getBody()).toString();
    Matcher matcher = ACCESS_LOG_PATTERN.matcher(event);
    if (!matcher.matches() || matcher.groupCount() < 8) {
      return;
    }
    String request = matcher.group(5);
    int startIndex = request.indexOf(" ");
    int endIndex = request.indexOf(" ", startIndex + 1);
    String uri = request.substring(startIndex + 1, endIndex);
    pageViewTable.increment(Bytes.toBytes(uri), 1L);
  }
}
