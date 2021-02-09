package org.example;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.apache.http.client.fluent.Request;
import org.junit.Rule;
import org.junit.Test;

import pl.domzal.junit.docker.rule.DockerRule;

public class HomepageExampleTest {

    @Rule
    public DockerRule container = DockerRule.builder() //
            .imageName("nginx:1.19.6") //
            .build();

    @Test
    public void shouldExposePorts() throws InterruptedException, IOException {

        // url container homepage will be exposed under
        String homepage = "http://"+container.getDockerHost()+":"+container.getExposedContainerPort("80")+"/";

        // use fluent apache http client to retrieve homepage content
        String pageContent = Request.Get(homepage).connectTimeout(1000).socketTimeout(1000).execute().returnContent().asString();

        // make sure this is indeed nginx welcome page
        assertThat(pageContent, containsString("Welcome to nginx!"));
    }

}
