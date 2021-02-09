package pl.domzal.junit.docker.rule.examples;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.TimeoutException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mandas.docker.client.exceptions.DockerException;

import pl.domzal.junit.docker.rule.DockerRule;
import pl.domzal.junit.docker.rule.WaitFor;

/**
 * Wait for sequence of message in container log at start.
 */
@Category(test.category.Stable.class)
public class ExampleWaitForLogMessageSequenceAtStartTest {

    private static Logger log = LoggerFactory.getLogger(ExampleWaitForLogMessageSequenceAtStartTest.class);

    @Rule
    public DockerRule testee = DockerRule.builder()//
            .imageName("alpine:3.13.1")//
            .cmd("sh", "-c", "for i in 'this is' 'some starting sequence' 'and now' 'it is' 'finished' 'no need' 'to wait' more; do (echo $i; sleep 1); done")//
            .waitFor(WaitFor.logMessageSequence("some starting sequence", "finished"))
            .build();

    @Test
    public void shouldWaitForLogMessage() throws InterruptedException, TimeoutException, DockerException {
        String stdout = testee.getLog();
        assertThat(stdout, containsString("finished"));
        assertThat(stdout, not(containsString("more")));
    }

}
