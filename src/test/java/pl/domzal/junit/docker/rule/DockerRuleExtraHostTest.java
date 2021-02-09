package pl.domzal.junit.docker.rule;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mandas.docker.client.DockerClient;
import org.mandas.docker.client.DockerClient.LogsParam;
import org.mandas.docker.client.LogStream;
import org.mandas.docker.client.exceptions.DockerException;

@Category(test.category.Stable.class)
public class DockerRuleExtraHostTest {

    private static Logger log = LoggerFactory.getLogger(DockerRuleExtraHostTest.class);

    @Rule
    public DockerRule testee = DockerRule.builder()//
            .imageName("busybox:1.33.0")//
            .extraHosts("extrahost:1.2.3.4")
            .cmd("sh", "-c", "cat /etc/hosts | grep extrahost")//
            .build();

    @Test
    public void shouldDefineExtraHost() throws InterruptedException, IOException, DockerException {

        DockerClient dockerClient = testee.getDockerClient();
        dockerClient.waitContainer(testee.getContainerId());
        log.info("done");

        LogStream stdoutLog = dockerClient.logs(testee.getContainerId(), LogsParam.stdout(), LogsParam.stderr());
        String stdout = StringUtils.trim(stdoutLog.readFully());
        log.info("log:\n{}", stdout);
        assertThat(stdout, containsString("1.2.3.4"));

    }


}
