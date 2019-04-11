package pl.domzal.junit.docker.rule;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ListImagesParam;
import com.spotify.docker.client.DockerClient.LogsParam;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.DockerRequestException;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ContainerState;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Image;
import com.spotify.docker.client.messages.PortBinding;
import com.spotify.docker.client.shaded.com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.domzal.junit.docker.rule.ex.ImagePullException;
import pl.domzal.junit.docker.rule.ex.PortNotExposedException;
import pl.domzal.junit.docker.rule.wait.LineListener;
import pl.domzal.junit.docker.rule.wait.LineListenerProxy;
import pl.domzal.junit.docker.rule.wait.LogChecker;
import pl.domzal.junit.docker.rule.wait.StartCondition;
import pl.domzal.junit.docker.rule.wait.StartConditionCheck;

/**
 * Simple docker container junit {@link Rule}.<br/>
 * Instances should be created via builder:
 * <pre>
 *  &#064;Rule
 *  DockerRule container = DockerRule.builder()
 *      . //configuration directives
 *      .build();
 * </pre>
 * <br/>
 * Inspired by and loosely based on <a href="https://gist.github.com/mosheeshel/c427b43c36b256731a0b">osheeshel/DockerContainerRule</a>.
 */
public class DockerRule extends ExternalResource {

    private static Logger log = LoggerFactory.getLogger(DockerRule.class);

    private static final int STOP_TIMEOUT = 5;
    private static final int SHORT_ID_LEN = 12;

    private final DockerRuleBuilder builder;
    private final String imageNameWithTag;
    private final DockerClient dockerClient;

    private ContainerCreation container;
    private String containerShortId;
    private String containerIp;
    private String containerGateway;
    private Map<String, List<PortBinding>> containerPorts;
    
    private ContainerInfo containerInfo;

    private DockerLogs dockerLogs;

    private boolean isStarted = false;

    DockerRule(DockerRuleBuilder builder) {
        this.builder = builder;
        this.imageNameWithTag = imageNameWithTag(builder.imageName());
        try {
            dockerClient = DefaultDockerClient.fromEnv().build();
            log.debug("server.info: {}", dockerClient.info());
            log.debug("server.version: {}", dockerClient.version());
            if (builder.imageAlwaysPull() || ! imageAvaliable(dockerClient, imageNameWithTag)) {
                dockerClient.pull(imageNameWithTag);
            }
        } catch (ImageNotFoundException e) {
            throw new ImagePullException(String.format("Image '%s' not found", imageNameWithTag), e);
        } catch (DockerCertificateException | DockerException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Builder to specify parameters and produce {@link DockerRule} instance.
     */
    public static DockerRuleBuilder builder() {
        return new DockerRuleBuilder();
    }

    /**
     * Create and start container.<br/>
     * This is {@link ExternalResource#before()} made available as public - it may be helpful in scenarios
     * when you want to use {@link DockerRule} and operate it manually.
     */
    @Override
    public final void before() throws Throwable {
        HostConfig.Builder hostConfigBuilder = HostConfig.builder()
                .publishAllPorts(builder.publishAllPorts())//
                .portBindings(builder.hostPortBindings())//
                .binds(builder.binds())//
                .links(links());
        if (builder.restartPolicy() != null) {
            hostConfigBuilder.restartPolicy(builder.restartPolicy().getRestartPolicy());
        }
        hostConfigBuilder.memory(builder.memory());
        hostConfigBuilder.memoryReservation(builder.memoryReservation());
        hostConfigBuilder.memorySwap(builder.memorySwap());
        hostConfigBuilder.memorySwappiness(builder.memorySwappiness());
        HostConfig hostConfig = hostConfigBuilder
                .extraHosts(emptyToNull(builder.extraHosts()))
                .ulimits(emptyToNull(builder.getUlimits()))
                .build();
        ContainerConfig containerConfig = ContainerConfig.builder()
                .hostConfig(hostConfig)
                .image(imageNameWithTag)
                .env(emptyToNull(builder.env()))
                .networkDisabled(false)
                .exposedPorts(emptyToNull(builder.containerExposedPorts()))
                .entrypoint(emptyToNull(builder.entrypoint()))
                .labels(emptyToNull(builder.getLabels()))
                .cmd(emptyToNull(builder.cmd())).build();
        try {
            if (StringUtils.isNotBlank(builder.name())) {
                this.container = dockerClient.createContainer(containerConfig, builder.name());
            } else {
                this.container = dockerClient.createContainer(containerConfig);
            }
            try {
                this.containerShortId = StringUtils.left(container.id(), SHORT_ID_LEN);
                log.info("container {} created, id {}, short id {}", imageNameWithTag, container.id(),
                        containerShortId);
                log.debug("rule before {}", containerShortId);

                dockerClient.startContainer(container.id());
                log.debug("{} started", containerShortId);
                try {
                    LineListenerProxy proxyLineListener = new LineListenerProxy();
                    attachLogs(dockerClient, container.id(), proxyLineListener);

                    ContainerInfo containerInfo = dockerClient.inspectContainer(container.id());
                    containerIp = containerInfo.networkSettings().ipAddress();
                    containerPorts = containerInfo.networkSettings().ports();
                    containerGateway = containerInfo.networkSettings().gateway();
                    this.containerInfo = containerInfo;

                    executeWaitForConditions(proxyLineListener);
                    logNetworkSettings();

                    isStarted = true;
                } catch (DockerException | InterruptedException e) {
                    log.debug("aborting start of {}", containerShortId, e);
                    try {
                        dockerClient.stopContainer(container.id(), 1);
                    } catch (DockerException | InterruptedException e2) {
                        e.addSuppressed(e2);
                    }
                    throw e;
                }
            } catch (DockerException | InterruptedException e) {
                try {
                    log.warn("{} startup failed", containerShortId, e);
                    if (dockerLogs != null) {
                        dockerLogs.close();
                    }
                    ContainerState state = dockerClient.inspectContainer(container.id()).state();
                    log.debug("{} state {}", containerShortId, state);
                    if (state.running()) {
                        if (builder.stopOptions().contains(StopOption.KILL)) {
                            dockerClient.killContainer(container.id());
                            log.info("{} killed", containerShortId);
                        } else {
                            dockerClient.stopContainer(container.id(), STOP_TIMEOUT);
                            log.info("{} stopped", containerShortId);
                        }
                    }
                    if (builder.stopOptions().contains(StopOption.REMOVE)) {
                        dockerClient.removeContainer(container.id(), DockerClient.RemoveContainerParam.removeVolumes());
                        log.info("{} deleted", containerShortId);
                        container = null;
                    }
                } catch (DockerException | InterruptedException e2) {
                    e.addSuppressed(e2);
                }
                throw e;
            }
        } catch (DockerRequestException e) {
            throw new IllegalStateException(e.getResponseBody(), e);
        } catch (DockerException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T[] emptyToNull(T[] array) {
        if (array == null || array.length == 0) {
            return null;
        }
        return array;
    }

    private static <T, C extends Collection<T>> C emptyToNull(C collection) {
        if (collection == null || collection.isEmpty()) {
            return null;
        }
        return collection;
    }

    private static <K, V, M extends Map<K,V>> M emptyToNull(M collection) {
        if (collection == null || collection.isEmpty()) {
            return null;
        }
        return collection;
    }

    private boolean isStarted() {
        return isStarted;
    }

    private List<String> links() {
        List<String> resolvedLinks = new ArrayList<>();
        resolvedLinks.addAll(builder.staticLinks());
        for (Pair<DockerRule,String> dynamicLink : builder.getDynamicLinks()) {
            DockerRule rule = dynamicLink.getKey();
            String alias = dynamicLink.getValue();
            if (!rule.isStarted()) {
                throw new IllegalStateException(String.format("container linked via alias '%s' is not started, make sure rule definitions assures target container will be started first", alias));
            }
            resolvedLinks.add(rule.getContainerId() + ":" + alias);
        }
        return resolvedLinks;
    }

    private void executeWaitForConditions(LineListenerProxy proxyLineListener) throws TimeoutException {
        List<StartConditionCheck> conditions = Lists.newArrayList();
        for (StartCondition conditionBuilder : builder.getWaitFor()) {
            conditions.add(conditionBuilder.build(this));
        }
        registerConditionLineListeners(conditions, proxyLineListener);
        // execute waiting
        for (StartConditionCheck condition : conditions) {
            WaitForContainer.waitForCondition(condition, builder.waitForSeconds(), describe());
        }
    }

    private void registerConditionLineListeners(List<StartConditionCheck> conditions, LineListenerProxy proxyLineListener) {
        for (StartConditionCheck condition : conditions) {
            if (condition instanceof LineListener) {
                proxyLineListener.add((LineListener) condition);
            }
        }
    }

    Integer findExternalPort(Integer internalPort) {
        try {
            return Integer.parseInt(findExternalPort(Integer.toString(internalPort)));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Internal rule problem - unable to parse exposed port number", e);
        }
    }

    private String findExternalPort(String internalPort) {
        String portAndProtocol = Ports.portWithProtocol(internalPort);
        if (! (containerPorts.containsKey(portAndProtocol)
                && containerPorts.get(portAndProtocol)!=null
                && containerPorts.get(portAndProtocol).size()>0)) {
            throw new PortNotExposedException(String.format("Port %s is not exposed (exposed port info: %s)", portAndProtocol, containerPorts));
        }
        List<PortBinding> portBindings = containerPorts.get(portAndProtocol);
        String firstExposedPort = portBindings.get(0).hostPort();
        if (portBindings.size() > 1) {
            log.warn("{} port {} is bound to multiple external ports, assuming first one: {}", containerShortId, internalPort, firstExposedPort);
        }
        return firstExposedPort;
    }

    private void attachLogs(DockerClient dockerClient, String containerId, LineListener lineListener) throws IOException, InterruptedException {
        dockerLogs = new DockerLogs(dockerClient, containerId, lineListener);
        if (builder.stdoutWriter()!=null) {
            dockerLogs.setStdoutWriter(builder.stdoutWriter());
        }
        if (builder.stderrWriter()!=null) {
            dockerLogs.setStderrWriter(builder.stderrWriter());
        }
        dockerLogs.start();
    }

    private boolean imageAvaliable(DockerClient dockerClient, String imageName) throws DockerException, InterruptedException {
        String imageNameWithTag = imageNameWithTag(imageName);
        List<Image> listImages = dockerClient.listImages(ListImagesParam.danglingImages(false));
        for (Image image : listImages) {
            if (image.repoTags() != null && image.repoTags().contains(imageNameWithTag)) {
                log.debug("image '{}' found", imageNameWithTag);
                return true;
            }
        }
        log.debug("image '{}' not found", imageNameWithTag);
        return false;
    }

    private String imageNameWithTag(String imageName) {
        if (! StringUtils.contains(imageName, ':')) {
            return imageName + ":latest";
        } else {
            return imageName;
        }
    }

    /**
     * Stop and remove container.<br/>
     * This is {@link ExternalResource#before()} made available as public - it may be helpful in scenarios
     * when you want to use {@link DockerRule} and operate it manually.
     */
    @Override
    public final void after() {
        log.debug("after {}", containerShortId);
        try {
            dockerLogs.close();
            ContainerState state = dockerClient.inspectContainer(container.id()).state();
            log.debug("{} state {}", containerShortId, state);
            if (state.running()) {
                if (builder.stopOptions().contains(StopOption.KILL)) {
                    dockerClient.killContainer(container.id());
                    log.info("{} killed", containerShortId);
                } else {
                    dockerClient.stopContainer(container.id(), STOP_TIMEOUT);
                    log.info("{} stopped", containerShortId);
                }
            }
            if (builder.stopOptions().contains(StopOption.REMOVE)) {
                dockerClient.removeContainer(container.id(), DockerClient.RemoveContainerParam.removeVolumes());
                log.info("{} deleted", containerShortId);
                container = null;
            }
        } catch (DockerException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Address of docker host. <b>Please note this is address of docker host as seen by docker client library
     * so it may not be valid docker host address in different contexts</b>.
     * <br/>
     * For example, if tests are run in unix-like environment with docker host on the same machine,
     * it will contain 'localhost' and will not point to docker host from inside container.
     * In such cases one should use {@link #getDockerContainerGateway()}.
     */
    public final String getDockerHost() {
        return dockerClient.getHost();
    }

    /**
     * Address of docker container gateway.
     */
    public final String getDockerContainerGateway() {
        return containerGateway;
    }

    /**
     * Address of docker container.
     */
    public String getContainerIp() {
        return containerIp;
    }

    /**
     * Get host dynamic port given container port was mapped to.
     *
     * @param containerPort Container port. Typically it matches Dockerfile EXPOSE directive.
     * @return Host port container port is published on.
     */
    public final String getExposedContainerPort(String containerPort) {
        return findExternalPort(containerPort);
    }

    private void logNetworkSettings() {
        log.info("{} docker host: {}, ip: {}, gateway: {}, exposed ports: {}", containerShortId, dockerClient.getHost(), containerIp, containerGateway, containerPorts);
    }

    private String describe() {
        if (StringUtils.isAllBlank(containerShortId, builder.name(), builder.imageName())) {
            return super.toString();
        }
        return (StringUtils.isNotBlank(builder.name()) ? String.format("'%s' ", builder.name()) : "")
                + (StringUtils.isNotBlank(containerShortId) ? containerShortId + " " : "")
                + builder.imageName();
    }

    /**
     * Stop and wait till given string will show in container output.
     *
     * @param searchString String to wait for in container output.
     * @param waitTime Wait time.
     * @throws TimeoutException On wait timeout.
     *
     * @deprecated Use {@link #waitForLogMessage(String, int)} instead.
     */
    public void waitFor(final String searchString, int waitTime) throws TimeoutException {
        waitForLogMessage(searchString, waitTime);
    }

    /**
     * Stop and wait till given string will show in container output.
     *
     * @param logSearchString String to wait for in container output.
     * @param waitTime Wait time.
     * @throws TimeoutException On wait timeout.
     */
    public void waitForLogMessage(final String logSearchString, int waitTime) throws TimeoutException {
        WaitForContainer.waitForCondition(new LogChecker(this, logSearchString), waitTime, describe());
    }

    /**
     * Block until container exit.
     */
    public void waitForExit() throws InterruptedException {
        try {
            dockerClient.waitContainer(container.id());
        } catch (DockerException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Container log.
     */
    public String getLog() {
        try (LogStream stream = dockerClient.logs(container.id(), LogsParam.stdout(), LogsParam.stderr());) {
            String fullLog = stream.readFully();
            if (log.isTraceEnabled()) {
                log.trace("{} full log: {}", containerShortId, StringUtils.replace(fullLog, "\n", "|"));
            }
            return fullLog;
        } catch (DockerException | InterruptedException e) {
            throw new IllegalStateException(e);
        }

    }

    /**
     * Id of container (null if it is not yet been created or has been stopped).
     */
    public String getContainerId() {
        return (container!=null ? container.id() : null);
    }

    /**
     * Underlying library @{link ContainerInfo} data structure returned by {@link DockerClient#inspectContainer(String)} at container start.
     *
     * @return Started container info or <code>null</code> if container was not yet started.
     */
    public ContainerInfo getContainerInfo() {
        return containerInfo;
    }

    /**
     * {@link DockerClient} for direct container manipulation.
     */
    public DockerClient getDockerClient() {
        return dockerClient;
    }

}
