package pl.domzal.junit.docker.rule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mandas.docker.client.exceptions.DockerException;
import org.mandas.docker.client.messages.ContainerInfo;
import org.mandas.docker.client.messages.NetworkSettings;

@Category(test.category.Unstable.class)
public class DockerRuleExposeTcpPortStaticTest {

    private static final Logger log = LoggerFactory.getLogger(DockerRuleExposeTcpPortStaticTest.class);

    @Rule
    public DockerRule testee = DockerRule.builder() //
            .imageName("alpine:3.13.1") //
            .expose("4444", "4444") //
            .cmd("sh", "-c", "echo started; nc -l -p 4444") //
            .waitFor(WaitFor.logMessage("started")) //
            .build();

    @Before
    public void logNetworkConfig() throws DockerException, InterruptedException {
        ContainerInfo containerInfo = testee.getDockerClient().inspectContainer(testee.getContainerId());
        NetworkSettings networkSettings = containerInfo.networkSettings();
        //networkSettings.
        log.debug("containerInfo.network: {}", networkSettings);
    }

    @Test
    public void shouldExposeSpecifiedPort() throws Throwable {
        DockerRule sender = DockerRule.builder() //
                .imageName("alpine:3.13.1") //
                .extraHosts("serv:"+ DockerRuleTestingHelper.exposedPortAddress(testee))
                .cmd("sh", "-c", "echo 12345 | nc serv 4444; echo done") //
                .waitFor(WaitFor.logMessage("done")) //
                .build();
        sender.before();
        try {
            testee.waitForLogMessage("12345", 5);
        } finally {
            sender.after();
        }
    }

}
