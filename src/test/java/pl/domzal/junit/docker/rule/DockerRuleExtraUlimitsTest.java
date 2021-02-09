package pl.domzal.junit.docker.rule;

import org.mandas.docker.client.DockerClient;
import org.mandas.docker.client.DockerClient.LogsParam;
import org.mandas.docker.client.LogStream;
import org.mandas.docker.client.exceptions.DockerException;
import org.mandas.docker.client.messages.HostConfig;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

@Category(test.category.Stable.class)
public class DockerRuleExtraUlimitsTest {

    private static Logger log = LoggerFactory.getLogger(DockerRuleExtraUlimitsTest.class);

    @Rule
    public DockerRule testee = DockerRule.builder()//
            .imageName("busybox:1.33.0")//
            .ulimit(HostConfig.Ulimit.builder().name("nofile").hard(262144L).soft(262144L).build())
            .cmd("sh", "-c", "ulimit -a | grep \"open files\"")//
            .build();

    @Test
    public void shouldDefineUlimits() throws InterruptedException, IOException, DockerException {

        DockerClient dockerClient = testee.getDockerClient();
        dockerClient.waitContainer(testee.getContainerId());
        log.info("done");

        LogStream stdoutLog = dockerClient.logs(testee.getContainerId(), LogsParam.stdout(), LogsParam.stderr());
        String stdout = StringUtils.trim(stdoutLog.readFully());
        log.info("log:\n{}", stdout);
        assertThat(stdout, containsString("262144"));

    }
}
