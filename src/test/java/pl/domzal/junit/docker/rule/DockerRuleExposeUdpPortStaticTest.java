package pl.domzal.junit.docker.rule;

import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.NetworkSettings;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Category(test.category.Stable.class)
public class DockerRuleExposeUdpPortStaticTest {

    private static final Logger log = LoggerFactory.getLogger(DockerRuleExposeUdpPortStaticTest.class);
    
    @Rule
    public DockerRule testee = DockerRule.builder()//
            .imageName("alpine:3.4")//
            .expose("4445", "4445/udp")//
            .cmd("sh", "-c", "echo started; nc -l -u -p 4445")
            .waitFor(WaitFor.logMessage("started"))
            .build();

    @Before
    public void logNetworkConfig() throws DockerException, InterruptedException {
        ContainerInfo containerInfo = testee.getDockerClient().inspectContainer(testee.getContainerId());
        NetworkSettings networkSettings = containerInfo.networkSettings();
        //networkSettings.
        log.debug("containerInfo.network: {}", networkSettings);
    }

    @Test
    public void shouldExposeSpecifiedUdpPort() throws Throwable {
        DockerRule sender = DockerRule.builder()//
                .imageName("alpine:3.4")//
                .extraHosts("serv:"+ DockerRuleTestingHelper.exposedPortAddress(testee))
                .cmd("sh", "-c", "echo 12345 | nc -u serv 4445")//
                .build();
        sender.before();
        try {
            testee.waitForLogMessage("12345", 5);
        } finally {
            sender.after();
        }
    }

}
