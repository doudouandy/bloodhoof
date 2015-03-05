package com.gfxc.bloodhoof;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLog;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.util.RolloverFileOutputStream;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.log.Slf4jLog;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class JettyDaemon {
    private static final String DEFAULT_TMP_DIR = "/tmp";
    private static final int DEFAULT_MAX_QUEUE = 256;
    private static final int DEFAULT_MAX_THREADS = 256;
    private static final int DEFAULT_MIN_THREADS = 16;
    private static final int DEFAULT_THREAD_IDLE_TIME = 60000;
    private static final int DEFAULT_PORT = 8080;
    private static final int LOG_RETAIN_DAYS = 3;

    private static final String TMP_DIR = System.getProperty("java.io.tmpdir", DEFAULT_TMP_DIR);
    private static final String TIME_ZONE = System.getProperty("user.timezone", TimeZone.getDefault().getID());

    private static final String APP_KEY = System.getProperty("app.key", "jetty");
    private static final int APP_PORT = Integer.valueOf(System.getProperty("app.port", String.valueOf(DEFAULT_PORT)));
    private static final int APP_LOG_DAYS = Integer.valueOf(System.getProperty("app.logDays", String.valueOf(LOG_RETAIN_DAYS)));

    private static final String JETTY_LOG = System.getProperty("jetty.log", "/tmp");
    private static final String JETTY_WEBROOT = System.getProperty("jetty.webroot");
    private static final int JETTY_MAX_QUEUE = Integer.valueOf(System.getProperty("jetty.maxQueue", String.valueOf(DEFAULT_MAX_QUEUE)));
    private static final int JETTY_MIN_THREADS = Integer.valueOf(System.getProperty("jetty.minThreads", String.valueOf(DEFAULT_MIN_THREADS)));
    private static final int JETTY_MAX_THREADS = Integer.valueOf(System.getProperty("jetty.maxThreads", String.valueOf(DEFAULT_MAX_THREADS)));
    private static final int JETTY_THREAD_IDLE_TIME = Integer.valueOf(System.getProperty("jetty.threadIdleTime", String.valueOf(DEFAULT_THREAD_IDLE_TIME)));
    private static final int JETTY_ACCEPTORS = Integer
            .valueOf(System.getProperty("jetty.acceptors", String.valueOf(Runtime.getRuntime().availableProcessors())));
    private static final int JETTY_SELECTORS = Integer
            .valueOf(System.getProperty("jetty.selectors", String.valueOf(Runtime.getRuntime().availableProcessors())));

    public static void main(String[] args) throws Exception {
        initLog();
        Server server = initServer();
        initConnector(server);
        initHandlers(server);
        server.start();
        server.join();
    }

    // 日志
    private static void initLog() throws Exception {
        File logDir = new File(String.format("%s/%s", JETTY_LOG, APP_KEY));
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        OutputStream outputStream = new RolloverFileOutputStream(String.format("%s/%s.log.yyyy_mm_dd", logDir.toString(), APP_KEY), true, APP_LOG_DAYS,
                TimeZone.getTimeZone(TIME_ZONE), "yyyy-MM-dd", "");
        PrintStream printStream = new PrintStream(outputStream);
        System.setOut(printStream);
        System.setErr(printStream);
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        Logger logger = new Slf4jLog();
        Log.setLog(logger);
    }

    // 初始化线程池
    private static Server initServer() {
        // 请求队列
        BlockingQueue<Runnable> runnableQueue = new ArrayBlockingQueue<Runnable>(JETTY_MAX_QUEUE);
        // 处理请求的线程池
        ThreadPool threadPool = new QueuedThreadPool(JETTY_MAX_THREADS, JETTY_MIN_THREADS, JETTY_THREAD_IDLE_TIME, runnableQueue);
        // Jetty Server
        Server server = new Server(threadPool);
        return server;
    }

    private static void initConnector(Server server) {
        // Http配置
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(false);
        // Http连接工厂
        ConnectionFactory connectionFactory = new HttpConnectionFactory(httpConfig);
        // 连接器
        ServerConnector connector = new ServerConnector(server, JETTY_ACCEPTORS, JETTY_SELECTORS, connectionFactory);
        connector.setPort(APP_PORT);
        server.addConnector(connector);
    }

    private static void initHandlers(Server server) {
        // Handler容器
        HandlerCollection handlers = new HandlerCollection();
        initRequestLogHandler(handlers);
        initWebAppHandler(handlers);
        // 设置Handler
        server.setHandler(handlers);
    }

    private static void initRequestLogHandler(HandlerCollection handlers) {
        // 处理请求日志的Handler
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        Slf4jRequestLog requestLog = new Slf4jRequestLog();
        requestLog.setLogLatency(true);
        requestLog.setLogTimeZone(TIME_ZONE);
        requestLog.setLogLocale(Locale.SIMPLIFIED_CHINESE);
        requestLog.setLogDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        requestLog.setLogServer(true);
        requestLog.setExtended(true);
        requestLog.setPreferProxiedForAddress(true);
        requestLogHandler.setRequestLog(requestLog);
        handlers.addHandler(requestLogHandler);
    }

    private static void initWebAppHandler(HandlerCollection handlers) {
        // 处理WebApp的Handler
        WebAppContext webAppHandler = new WebAppContext();
        webAppHandler.setTempDirectory(new File(TMP_DIR));
        webAppHandler.setPersistTempDirectory(true);
        webAppHandler.setParentLoaderPriority(true);
        webAppHandler.setResourceBase(String.format("%s/%s", JETTY_WEBROOT, APP_KEY));
        handlers.addHandler(webAppHandler);
    }
}
