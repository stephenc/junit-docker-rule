package pl.domzal.junit.docker.rule;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Category(test.category.Stable.class)
public class DockerRuleWaitForMessageCustomTimeTest {

    private static Logger log = LoggerFactory.getLogger(DockerRuleWaitForMessageCustomTimeTest.class);

    public static final int WAIT_FOR_MESSAGE_SHORTER_THAN_DEFAULT = 5;

    @Test
    public void shouldWaitForLogMessage() throws Throwable {

        DockerRule testee = DockerRule.builder()//
                .imageName("busybox:1.33.0")//
                .cmd("sh", "-c", "for i in 01 02 03 05 06 07 08 09 10; do (echo $i; sleep 1); done")//
                .waitFor(WaitFor.logMessage("20"))
                .waitForTimeout(WAIT_FOR_MESSAGE_SHORTER_THAN_DEFAULT)
                .stopOptions(StopOption.KILL, StopOption.INSPECTING, StopOption.REMOVE)
                .build();

        long start = System.nanoTime();

        try {
            testee.before();
            fail("startup should timeout");
        } catch (TimeoutException te) {
            //expected
            assertThat( //
                    "container log should not contain message printed after timeout", //
                    testee.getLog(), not(containsString("10")));
        } finally {
            testee.after();
        }

        long elapsed = System.nanoTime() - start;
        assertThat( //
                "wait time has been redifinded to shorter but container seems to be falling after default time anyway", //
                TimeUnit.NANOSECONDS.toSeconds(elapsed), lessThan((long)DockerRuleBuilder.WAIT_FOR_DEFAULT_SECONDS));
    }

}
