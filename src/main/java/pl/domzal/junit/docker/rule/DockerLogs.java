package pl.domzal.junit.docker.rule;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.mandas.docker.client.DockerClient;
import org.mandas.docker.client.DockerClient.LogsParam;
import org.mandas.docker.client.LogStream;

import pl.domzal.junit.docker.rule.logs.LogPrinter;
import pl.domzal.junit.docker.rule.logs.LogSplitter;
import pl.domzal.junit.docker.rule.wait.LineListener;

/**
 * Docker container log binding feature.
 */
class DockerLogs implements Closeable {

    private static Logger log = LoggerFactory.getLogger(DockerLogs.class);

    private static final int NO_OF_THREADS = 4;
    private static final int SHORT_ID_LEN = 12;

    private final DockerClient dockerClient;
    private final String containerId;
    private final LineListener lineListener;

    private PrintStream stdoutWriter = System.out;
    private PrintStream stderrWriter = System.err;

    private final ThreadFactory threadFactory = new ThreadFactory() {
        private final AtomicInteger id = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, String.format("dockerlog-pool-%d", id.incrementAndGet()));
            t.setDaemon(true);
            return t;
        }
    };

    private final ExecutorService executor = Executors.newFixedThreadPool(NO_OF_THREADS, threadFactory);

    DockerLogs(DockerClient dockerClient, String containerId, LineListener lineListener) {
        this.dockerClient = dockerClient;
        this.containerId = containerId;
        this.lineListener = lineListener;
    }

    void setStderrWriter(PrintStream stderrWriter) {
        this.stderrWriter = stderrWriter;
    }

    void setStdoutWriter(PrintStream stdoutWriter) {
        this.stdoutWriter = stdoutWriter;
    }

    public void start() throws IOException, InterruptedException {
        final String containerShortId = StringUtils.left(containerId, SHORT_ID_LEN);
        final LogSplitter logSplitter = new LogSplitter();
        if (lineListener != null) {
            executor.submit(new LogPrinter("", logSplitter.getCombinedInput(), null, lineListener));
        }
        if (stdoutWriter != null) {
            executor.submit(new LogPrinter(containerShortId+"-stdout> ", logSplitter.getStdoutInput(), stdoutWriter, null));
        }
        if (stderrWriter != null) {
            executor.submit(new LogPrinter(containerShortId+"-stderr> ", logSplitter.getStderrInput(), stderrWriter, null));
        }
        executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                log.trace("{} attaching to logs", containerShortId);
                try (LogStream logs = dockerClient.logs(containerId, LogsParam.stdout(), LogsParam.stderr(), LogsParam.follow())) {
                    logs.attach(logSplitter.getStdoutOutput(), logSplitter.getStderrOutput());
                } finally {
                    logSplitter.close();
                    log.trace("{} dettached from logs", containerShortId);
                }
                return null;
            }
        });
    }

    @Override
    public void close() {
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.warn("interrupted", e);
        }
        executor.shutdown();
    }

}
