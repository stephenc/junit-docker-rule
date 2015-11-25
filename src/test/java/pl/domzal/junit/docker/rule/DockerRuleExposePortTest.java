package pl.domzal.junit.docker.rule;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.domzal.junit.docker.rule.DockerRule;

public class DockerRuleExposePortTest {

    private static Logger log = LoggerFactory.getLogger(DockerRuleExposePortTest.class);

    @ClassRule
    public static DockerRule testee = DockerRule.builder()//
            .setImageName("nginx")//
            .setExposedPorts("80")//
            .build();

    private String nginxHome;

    @Before
    public void setupHomepage() {
        nginxHome = "http://"+testee.getDockerHost()+":"+testee.getExposedContainerPort("80")+"/";
        log.info("homepage: {}", nginxHome);
    }

    @Test
    public void shouldExposeNginxHttpPort() throws InterruptedException, IOException {
        Thread.sleep(1000);
        assertTrue(AssertHtml.pageContainsString(nginxHome, "Welcome to nginx!"));
    }


}
