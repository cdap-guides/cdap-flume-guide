package co.cask.cdap.guides;

import co.cask.cdap.api.annotation.UseDataSet;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.lib.KeyValue;
import co.cask.cdap.api.dataset.lib.KeyValueTable;
import co.cask.cdap.api.service.http.AbstractHttpServiceHandler;
import co.cask.cdap.api.service.http.HttpServiceRequest;
import co.cask.cdap.api.service.http.HttpServiceResponder;
import com.google.common.collect.Maps;

import java.util.Iterator;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * Handler with a single endpoint that returns the map of web-page and their corresponding page view count.
 */
public class WebLogAnalyticsHandler extends AbstractHttpServiceHandler {
  @UseDataSet("pageViewTable")
  private KeyValueTable pageViewTable;

  @Path("views")
  @GET
  public void getViews(HttpServiceRequest request, HttpServiceResponder responder) {
    Iterator<KeyValue<byte[], byte[]>> pageViewScan = pageViewTable.scan(null, null);
    Map<String, Long> pageViews = Maps.newHashMap();
    while (pageViewScan.hasNext()) {
     KeyValue<byte[], byte[]> uri = pageViewScan.next();
     pageViews.put(new String(uri.getKey()), Bytes.toLong(uri.getValue()));
    }
    responder.sendJson(200, pageViews);
  }
}
