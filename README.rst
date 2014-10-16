Ingesting Data into CDAP using Apache Flume
===========================================

Ingesting real time logs data into Hadoop for analysis is a common use case which is solved with `Apache Flume <http://flume.apache.org/>`__. In this guide you will learn how to ingest data into CDAP with Apache Flume and process it in real-time.
The Cask Data Application Platform (CDAP) provides a number of pre-packaged `Datasets <http://docs.cask.co/cdap/current/en/dev-guide.html#datasets>`__, which make it easy to store and retrieve data using best-practices based implementations of common data access patterns.  In this guide, you will learn how to process and store timeseries data, using the example of real-time sensor data from a traffic monitor network.

What You Will Build
-------------------

You will build a CDAP application that uses web logs aggregated by Flume to find page view counts. You will:

* Configure Flume to ingest data into `CDAP Stream <http://docs.cask.co/cdap/current/en/dev-guide.html#streams>`__
* Build real-time `Flow <http://docs.cask.co/cdap/current/en/dev-guide.html#flows>`__ to process ingested web logs
* Build a `Service <http://docs.cask.co/cdap/current/en/dev-guide.html#services>`__ to serve analysis results via HTTP

What you will need
------------------
* `JDK 6 or JDK 7 <http://www.oracle.com/technetwork/java/javase/downloads/index.html>`__
* `Apache Maven 3.0+ <http://maven.apache.org/download.cgi>`__
*  CDAP SDK
*  `Apache Flume <http://flume.apache.org/download.html>`__

Let’s Build It!
---------------
The following sections will guide you through configuring and running Flume , and  implementing an application from scratch. 
If you want to deploy and run application right away, you can clone sources from this github repository. 
In this case feel free to skip following two sections and jump to Build and Run Application section.

Application Design
------------------
Web logs are aggregated using Flume which pushes the data to a webLogs Stream using special StreamSink from `cdap-ingest <https://github.com/caskdata/cdap-ingest>`__ library. 
Then, logs are processed in real-time with a Flow that consumes data from webLogs stream and persists computation results in a pageViews Dataset. 
WebLogAnalyticsService makes computation results that stored in pageViews Dataset accessible via HTTP.

<diagram>

First, we will build an app, deploy the app and start it. Once it is ready to accept and process the data, we will configure Flume to push data into a stream in real-time.

Application Implementation
--------------------------

The recommended way to build a CDAP application from scratch is to use maven project. Use the following directory structure::
  
    <app_dir>/pom.xml
    <app_dir>/src/main/java/co/cask/cdap/guide/WebLogAnalyticsApplication.java
    <app_dir>/src/main/java/co/cask/cdap/guide/WebLogAnalyticsFlow.java
    <app_dir>/src/main/java/co/cask/cdap/guide/PageViewCounterFlowlet.java
    <app_dir>/src/main/java/co/cask/cdap/guide/WebLogAnalyticsHandler.java

WebLogAnalyticsApplication declares that the application has a stream, flow, service and uses a dataset:

.. code:: java
  
  public class WebLogAnalyticsApplication extends AbstractApplication {
  
    @Override
    public void configure() {
      setName("WebLogAnalyticsApp");      
      addStream(new Stream("webLogsStream"));
      createDataset("pageViewTable", KeyValueTable.class);
      addFlow(new WebLogAnalyticsFlow());
      addService("WebLogAnalyticsService", new WebLogAnalyticsHandler());
    }
  }
  
WebLogAnalyticsFlow makes use of PageViewCounterFlowlet:

.. code:: java

  public class WebLogAnalyticsFlow implements Flow {
    
    @Override
    public FlowSpecification configure() {
      return FlowSpecification.Builder.with().
        setName("WebLogAnalyticsFlow").
        setDescription("A flow that collects and performs web log analysis").
        withFlowlets().add("pageViewCounter", new PageViewCounterFlowlet()).
        connect().fromStream("webLogStream").to("pageViewCounter").
        build();
    }
  }

The PageViewCounterFlowlet receives the log events from webLogsStream. It parses the log event and extracts the requested page URL from the log event. 
Then it increments respective counter in pageViewTable Dataset:

.. code:: java

  public class PageViewCounterFlowlet extends AbstractFlowlet {
    private static final Logger LOG = LoggerFactory.getLogger(PageViewCounterFlowlet.class);
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

For example, given the following event::

  192.168.139.1 - - [14/Jan/2014:08:40:43 -0400] "GET https://accounts.example.org/signup HTTP/1.0" 200 809 "http://www.example.org" "example v4.10.5 (www.example.org)"

the extracted requested page URL is https://accounts.example.org/signup. This will be used as a counter key in pageViewTable Dataset.

WebLogAnalyticsHandler returns a map of webpage and their page-views counts for HTTP GET request at /views:

.. code:: java

  public class WebLogAnalyticsHandler extends AbstractHttpServiceHandler {
    @UseDataSet("pageViewTable")
    KeyValueTable pageViewTable;
  
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

Build and Run Application
-------------------------
The WebLogAnalyticsAppliation can be built and packaged using standard Apache Maven commands::

  mvn clean package

Note that the remaining commands assume that the cdap-cli.sh script is available on your PATH. If this is not the case, please add it::

  export PATH=$PATH:<CDAP home>/bin

We can then deploy the application to a standalone CDAP installation and start the flow and service::

  bin/cdap-cli.sh deploy app WebLogAnalyticsApplication.jar
  bin/cdap-cli.sh start flow WebLogAnalyticsApp.WebLogAnalyticsFlow
  bin/cdap-cli.sh start service WebLogAnalyticsApp.WebLogAnalyticsService

Once the flow is started, it is ready to receive the web logs from stream. Now let’s configure and start Flume to push web logs into a Stream.

Ingest Data with Flume
----------------------
In the provided sources for this guide you can find Apache web server’s access.log file that we will use as a source of data. If you have access to live Apache web server’s access logs you can use them instead.

In order to configure Apache Flume to push web logs to a CDAP Stream you need to create a simple flow which includes:

* Flume source that tail access logs
* in-memory channel
* Flume sink that sends log lines into CDAP Stream

In this example we will configure the source to tail access.log and sink to send data to webLogsStream.

Download Flume
--------------
* You can download Apache Flume distribution at : `Apache Flume Download <http://flume.apache.org/download.html>`__

* Once downloaded , extract the archive into <flume-base-dir>::

    tar -xvf apache-flume-*-bin.tar.gz
  
Configure Flume Flow
--------------------
Download CDAP flume sink jar::

  cd <flume-base-dir>/lib
  curl --remote-name https://oss.sonatype.org/content/repositories/releases/co/cask/cdap/cdap-flume/1.0.1/cdap-flume-1.0.1.jar

CDAP Flume sink requires newer version of `Guava <https://code.google.com/p/guava-libraries/>`__ library than that is usually shipped with Flume. You need to replace the existing guava library with guava-17.0.jar::
  
  rm <flume-base-dir>/lib/guava-<existing-version>.jar
  cd <flume-base-dir>/lib
  curl --remote-name http://search.maven.org/remotecontent?filepath=com/google/guava/guava/17.0/guava-17.0.jar

Now let’s configure the flow by creating the configuration file weblog-analysis.conf at <flume-base-dir>/conf with the following contents::

  a1.sources = r1
  a1.channels = c1
  a1.sources.r1.type = exec
  a1.sources.r1.command = tail -F <cdap-flume-ingest-guide-basedir>/access.log
  a1.sources.r1.channels = c1
  a1.sinks = k1
  a1.sinks.k1.type = co.cask.cdap.flume.StreamSink
  a1.sinks.k1.channel = c1
  a1.sinks.k1.host  = 127.0.0.1
  a1.sinks.k1.port = 10000
  a1.sinks.k1.streamName = webLogsStream
  a1.channels.c1.type = memory
  a1.channels.c1.capacity = 1000
  a1.channels.c1.transactionCapacity = 100

Replace <cdap-flume-ingest-guide-basedir> in the configuration file to point to the cdap-flume-ingest-guide resources. 
Alternatively, you can point it to /tmp/access.log and create /tmp/access.log with following sample contents::

  192.168.99.124 - - [14/Jan/2014:06:51:04 -0400] "GET https://accounts.example.org/signup HTTP/1.1" 200 392 "http://www.example.org" "Mozilla/5.0 (compatible; YandexBot/3.0; +http://www.example.org/bots)"
  192.168.67.103 - - [14/Jan/2014:08:03:05 -0400] "GET https://accounts.example.org/login HTTP/1.1" 404 182 "http://www.example.org" "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
  192.168.67.103 - - [14/Jan/2014:08:03:05 -0400] "GET https://accounts.example.org/signup HTTP/1.1" 200 394 "http://www.example.org" "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
  192.168.139.1 - - [14/Jan/2014:08:40:43 -0400] "GET https://accounts.example.org/login HTTP/1.0" 404 208 "http://www.example.org" "example v4.10.5 (www.example.org)"
  192.168.139.1 - - [14/Jan/2014:08:40:43 -0400] "GET https://accounts.example.org/signup HTTP/1.0" 200 809 "http://www.example.org" "example v4.10.5 (www.example.org)"
  192.168.139.1 - - [14/Jan/2014:08:40:43 -0400] "GET https://www.example.org/ HTTP/1.0" 200 809 "-" "example v4.10.5 (www.example.org)"

Run Flume Flow with Agent
-------------------------
To run a Flume flow, start an agent with flow’s configuration::

  cd <flume-base-dir>
  ./bin/flume-ng agent --conf conf --conf-file conf/weblog-analysis.conf  --name a1 -Dflume.root.logger=INFO,console

Once agent is started it begins to push data to a CDAP Stream. The CDAP application started earlier processes the log events as soon as data is received.Now you can query computed page views statistics.

Query Results
-------------
WebLogAnalyticsService exposes HTTP endpoint for you to query the results of processing::

  curl -v -X GET http://localhost:10000/v2/apps/WebLogAnalyticsApp/services/WebLogAnalyticsService/methods/views

Example Output::

  {https://www.example.org/=1, https://accounts.example.org/signup=3, https://accounts.example.org/login=2}

Related Topics
--------------
`Wise tutorial <https://github.com/caskdata/cdap-apps/tree/develop/Wise>`__

Extend This Example
-------------------
To make application more useful, you can try to extend it by:

* persisting logs and their stats into a dataset in PageViewCounterFlowlet
* find top visited pages by maintaining top pages in a dataset and updating them from PageViewCounterFlowlet
* calculate bounce ratio of web pages with batch processing

Share & Discuss!
----------------
Have a question? Discuss at `CDAP User Mailing List <https://groups.google.com/forum/#!forum/cdap-user>`_
  