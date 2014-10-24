Ingesting Data into CDAP using Apache Flume
===========================================

Ingesting realtime log data into Hadoop for analysis is a common use
case which can be solved with [Apache Flume](http://flume.apache.org/).
In this guide, you will learn how to ingest data into CDAP with Apache
Flume and process it in realtime.

What You Will Build
-------------------

You will build a CDAP application that uses web logs aggregated by Flume
to find page view counts. You will:

-   Configure Flume to ingest data into a 
    [CDAP Stream](http://docs.cask.co/cdap/current/en/dev-guide.html#streams)
-   Build a realtime
    [Flow](http://docs.cask.co/cdap/current/en/dev-guide.html#flows) to
    process the ingested web logs
-   Build a
    [Service](http://docs.cask.co/cdap/current/en/dev-guide.html#services)
    to serve the analysis results via HTTP

What You Will Need
------------------

-   [JDK 6 or JDK 7](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
-   [Apache Maven 3.0+](http://maven.apache.org/download.cgi)
-   [CDAP SDK](http://docs.cdap.io/cdap/current/en/getstarted.html#download-and-setup)
-   [Apache Flume](http://flume.apache.org/download.html)

Let’s Build It!
---------------

The following sections will guide you through configuring and running
Flume, and implementing an application from scratch. If you want to
deploy and run the application right away, you can clone the sources
from this GitHub repository. In that case, feel free to skip the
following two sections and jump directly to the
Build and Run Application\_ section.

Application Design
------------------

Web logs are aggregated using Flume which pushes the data to a `webLogs`
stream using a special StreamSink from the
[cdap-ingest](https://github.com/caskdata/cdap-ingest) library. Then,
logs are processed in realtime with a Flow that consumes data from the
`webLogs` stream and persists the computation results in a `pageViews`
Dataset. The `WebLogAnalyticsService` makes the computation results
stored in the `pageViews` Dataset accessible via HTTP.

![(AppDesign)](docs/images/app-design.png)

First, we will build the app, then deploy the app and start it. Once it
is ready to accept and process the data, we will configure Flume to push
data into the stream in realtime.

Application Implementation
--------------------------

The recommended way to build a CDAP application from scratch is to use a
maven project. Use this directory structure:

``` {.sourceCode .console}
<app_dir>/pom.xml
<app_dir>/src/main/java/co/cask/cdap/guide/WebLogAnalyticsApplication.java
<app_dir>/src/main/java/co/cask/cdap/guide/WebLogAnalyticsFlow.java
<app_dir>/src/main/java/co/cask/cdap/guide/PageViewCounterFlowlet.java
<app_dir>/src/main/java/co/cask/cdap/guide/WebLogAnalyticsHandler.java
```

`WebLogAnalyticsApplication` declares that the application has a stream,
a flow, a service and uses a dataset:

``` {.sourceCode .java}
public class WebLogAnalyticsApplication extends AbstractApplication {

  @Override
  public void configure() {
    setName("WebLogAnalyticsApp");      
    addStream(new Stream("webLogs"));
    createDataset("pageViewTable", KeyValueTable.class);
    addFlow(new WebLogAnalyticsFlow());
    addService("WebLogAnalyticsService", new WebLogAnalyticsHandler());
  }
}
```

`WebLogAnalyticsFlow` makes use of the `PageViewCounterFlowlet`:

``` {.sourceCode .java}
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
```

The `PageViewCounterFlowlet` receives the log events from the `webLogs`
stream. It parses the log event and extracts the requested page URL from
the log event. Then it increments respective counter in pageViewTable
Dataset:

``` {.sourceCode .java}
public class PageViewCounterFlowlet extends AbstractFlowlet {
  private static final Logger LOG = LoggerFactory.getLogger(PageViewCounterFlowlet.class);
  private static final Pattern ACCESS_LOG_PATTERN = Pattern.compile(
    //   IP       id    user      date          request     code     size    referrer    user agent
    "^([\\d.]+) (\\S+) (\\S+) \\[([^\\]]+)\\] \"([^\"]+)\" (\\d{3}) (\\d+) \"([^\"]+)\" \"([^\"]+)\"");
  private static final Pattern REQUEST_PAGE_PATTERN = Pattern.compile("(\\S+)\\s(\\S+).*");

  @UseDataSet("pageViewTable")
  private KeyValueTable pageViewTable;

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
```

For example, given the following event:

``` {.sourceCode .console}
192.168.139.1 - - [14/Jan/2014:08:40:43 -0400] "GET https://accounts.example.org/signup HTTP/1.0" 200 809 "http://www.example.org" "example v4.10.5 (www.example.org)"
```

the extracted requested page URL is
`https://accounts.example.org/signup`. This will be used as a counter
key in the `pageViewTable` Dataset.

`WebLogAnalyticsHandler` returns a map of the webpage and its page-views
counts for an HTTP GET request at `/views`:

``` {.sourceCode .java}
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
    responder.sendString(200, pageViews.toString(), Charsets.UTF_8);
  }
}
```

Build and Run Application
-------------------------

The `WebLogAnalyticsApp` can be built and packaged using standard
Apache Maven commands:

    mvn clean package

Note that the remaining commands assume that the `cdap-cli.sh` script is
available on your PATH. If this is not the case, please add it:

    export PATH=$PATH:<CDAP home>/bin

If you haven't started already CDAP standalone, start it with the
following commands:

    cdap.sh start

We can then deploy the application to a standalone CDAP installation and
start the flow and service:

    cdap-cli.sh deploy app target/cdap-flume-guide-1.0.0.jar
    cdap-cli.sh start flow WebLogAnalyticsApp.WebLogAnalyticsFlow
    cdap-cli.sh start service WebLogAnalyticsApp.WebLogAnalyticsService

Once the flow has started, it is ready to receive the web logs from the
stream. Now, let’s configure and start Flume to push web logs into the
stream.

Ingest Data with Flume
----------------------

In the provided sources for this guide, you can find an Apache web
server’s `access.log` file that we will use as a source of data. If you
have access to live Apache web server’s access logs, you can use them
instead.

In order to configure Apache Flume to push web logs to a CDAP Stream,
you need to create a simple flow which includes:

-   Flume source that tail access logs
-   In-memory channel
-   Flume sink that sends log lines into CDAP Stream

In this example, we will configure the source to tail `access.log` and
`sink` to send data to the `webLogs` stream.

Download Flume
--------------

-   You can download the Apache Flume distribution at [Apache Flume
    download.](http://flume.apache.org/download.html)
-   Once downloaded, extract the archive into `<flume-base-dir>`:

        tar -xvf apache-flume-*-bin.tar.gz

Configure Flume Flow
--------------------

Download the CDAP Flume sink jar:

    cd <flume-base-dir>/lib
    curl --remote-name https://oss.sonatype.org/content/repositories/releases/co/cask/cdap/cdap-flume/1.0.1/cdap-flume-1.0.1.jar

The CDAP Flume sink requires a newer version of
[Guava](https://code.google.com/p/guava-libraries/) library than that is
usually shipped with Flume. You need to replace the existing guava
library with `guava-17.0.jar`:

    # these commands are executed at <flume-base-dir>/lib
    rm guava-*.jar
    curl --remote-name https://repo1.maven.org/maven2/com/google/guava/guava/17.0/guava-17.0.jar

Now, let’s configure the flow by creating the configuration file
`weblog-analysis.conf` at `<flume-base-dir>/conf` with these contents:

    a1.sources = r1
    a1.channels = c1
    a1.sources.r1.type = exec
    a1.sources.r1.command = tail -F <cdap-flume-ingest-guide-basedir>/data/access.log
    a1.sources.r1.channels = c1
    a1.sinks = k1
    a1.sinks.k1.type = co.cask.cdap.flume.StreamSink
    a1.sinks.k1.channel = c1
    a1.sinks.k1.host  = 127.0.0.1
    a1.sinks.k1.port = 10000
    a1.sinks.k1.streamName = webLogs
    a1.channels.c1.type = memory
    a1.channels.c1.capacity = 1000
    a1.channels.c1.transactionCapacity = 100

Change `<cdap-flume-ingest-guide-basedir>` in the configuration file to
point to the cdap-flume-ingest-guide directory. Alternatively, you can
point it to `/tmp/access.log` and create `/tmp/access.log` with these
sample contents:

    192.168.99.124 - - [14/Jan/2014:06:51:04 -0400] "GET https://accounts.example.org/signup HTTP/1.1" 200 392 "http://www.example.org" "Mozilla/5.0 (compatible; YandexBot/3.0; +http://www.example.org/bots)"
    192.168.67.103 - - [14/Jan/2014:08:03:05 -0400] "GET https://accounts.example.org/login HTTP/1.1" 404 182 "http://www.example.org" "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
    192.168.67.103 - - [14/Jan/2014:08:03:05 -0400] "GET https://accounts.example.org/signup HTTP/1.1" 200 394 "http://www.example.org" "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
    192.168.139.1 - - [14/Jan/2014:08:40:43 -0400] "GET https://accounts.example.org/login HTTP/1.0" 404 208 "http://www.example.org" "example v4.10.5 (www.example.org)"
    192.168.139.1 - - [14/Jan/2014:08:40:43 -0400] "GET https://accounts.example.org/signup HTTP/1.0" 200 809 "http://www.example.org" "example v4.10.5 (www.example.org)"
    192.168.139.1 - - [14/Jan/2014:08:40:43 -0400] "GET https://www.example.org/ HTTP/1.0" 200 809 "-" "example v4.10.5 (www.example.org)"

Run Flume Flow with Agent
-------------------------

To run a Flume flow, start an agent with the flow’s configuration:

    cd <flume-base-dir>
    ./bin/flume-ng agent --conf conf --conf-file conf/weblog-analysis.conf  --name a1 -Dflume.root.logger=INFO,console

Once the agent has started, it begins to push data to the CDAP Stream.
The CDAP application started earlier processes the log events as soon as
data is received. Then you can query the computed page views statistics.

Query Results
-------------

`WebLogAnalyticsService` exposes an HTTP endpoint for you to query the
results of processing:

    curl -v -X GET http://localhost:10000/v2/apps/WebLogAnalyticsApp/services/WebLogAnalyticsService/methods/views

Example output:

    {https://www.example.org/=1, https://accounts.example.org/signup=3, https://accounts.example.org/login=2}

Related Topics
--------------

-   [Wise: Web Analytics](http://docs.cask.co/tutorial/current/en/tutorial2.html)
    tutorial, part of CDAP

Extend This Example
-------------------

To make this application more useful, you can extend it by:

-   find the top visited pages by maintaining the top pages in a dataset
    and updating them from the PageViewCounterFlowlet
-   calculate the bounce ratio of web pages, with batch processing

Share and Discuss!
------------------

Have a question? Discuss at the [CDAP User Mailing List](https://groups.google.com/forum/#!forum/cdap-user)

