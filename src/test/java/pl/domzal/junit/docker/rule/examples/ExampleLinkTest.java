package pl.domzal.junit.docker.rule.examples;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;

import pl.domzal.junit.docker.rule.DockerRule;

/**
 * Static container linking. &quot;web&quot; links to &quot;db&quot; and pings it.
 */
@Category(test.category.Stable.class)
public class ExampleLinkTest {

    private static DockerRule db = DockerRule.builder()
            .imageName("alpine:3.13.1")
            // container must be named to be target of static link
            .name("db")
            .cmd("sh", "-c", "sleep 100")
            .build();

    private static DockerRule web = DockerRule.builder()
            .imageName("alpine:3.13.1")
            // static link point to named container
            .link("db")
            .cmd("sh", "-c", "ping -w 1 db")
            .build();

    /**
     * Recommended method to make sure linked container are initialized in required order.
     */
    @ClassRule
    public static RuleChain containersChain = RuleChain.outerRule(db).around(web);

    @Test
    public void shouldPassEnvVariables() throws Throwable {
        web.waitForExit();
        String containerOutput = web.getLog();
        assertThat(containerOutput, containsString("1 packets received"));
    }

}
