package pl.domzal.junit.docker.rule;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.LogsParam;
import com.spotify.docker.client.LogStream;

import pl.domzal.junit.docker.rule.ex.InvalidVolumeFrom;

@Category(test.category.Volumes.class)
public class DockerRuleVolumeMountTest {

    private static Logger log = LoggerFactory.getLogger(DockerRuleVolumeMountTest.class);

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder(baseDir());

    private File testDir;
    private String testDirPath;
    private File testFile;
    private String testFilename;
    private String testFileContent = "1234567890";
    private String testFileContentChanged = "0987654321";

    private static File baseDir() {
        String className = DockerRuleVolumeMountTest.class.getName();
        URL url = DockerRuleVolumeMountTest.class
                .getResource("/"+className.replace('.', '/') + ".class");
        if (url == null) {
            return null;
        }
        try {
            Path path = Paths.get(url.toURI()).getParent(); // ClassName
            for (int i = className.indexOf('.'); i != -1; i = className.indexOf('.', i+1)) {
                path = path.getParent(); // packages
            }
            if ("test-classes".equals(path.getFileName().toString())) {
                path = path.getParent(); //
            } else {
                return null;
            }
            return path.toFile();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    @Before
    public void setup() throws IOException {

        testFile = tempFolder.newFile();
        testFilename = testFile.getName();
        try (Writer out = new FileWriter(testFile)) {
            IOUtils.write(testFileContent, out);
        }
        testDir = tempFolder.getRoot();
        log.debug("testDir: {}", testDir.getAbsolutePath());
        testDirPath = DockerRuleMountBuilder.toUnixStylePath(testDir.getAbsolutePath());
        log.debug("testDirPath: {}", testDirPath);
    }

    private DockerRule testee;

    @After
    public void tearDown() {
        // make sure testee will bee cleaned up after test cases
        // commit tear down is more convienient than 'try {} finally in every test case'
        if (testee != null) {
            testee.after();
        }
    }

    @Test
    public void shouldFailWhenMountFromDoesNotExist() {
        try {
            File nonexistingHostDir = testDir.toPath().resolve("nonexisting").toFile();
            log.debug("mountFrom: "+nonexistingHostDir.getAbsolutePath());
            testee = DockerRule.builder()//
                    .imageName("busybox:1.25.1")//
                    .mountFrom(nonexistingHostDir).to("/somedir", "ro")//
                    .build();
            fail("should fail with "+InvalidVolumeFrom.class.getSimpleName());
        } catch (InvalidVolumeFrom e) {
            //expected
            assertThat(e.getMessage(), containsString("nonexisting"));
        }
    }

    @Test
    public void shouldFailWhenMountFromOutsideHomeDirOnWindowsOrOsx() {
        if (SystemUtils.IS_OS_MAC_OSX || SystemUtils.IS_OS_WINDOWS) {
            try {
                testee = DockerRule.builder()//
                        .imageName("busybox:1.25.1")//
                        .mountFrom("/somehostdir").to("/somedir", "ro")//
                        .build();
                fail("should fail with "+InvalidVolumeFrom.class.getSimpleName());
            } catch (InvalidVolumeFrom e) {
                //expected
            }
        }
    }

    @Test
    public void shouldReadMountFromUnixStyle() throws Throwable {
        testee = DockerRule.builder()//
                .imageName("busybox:1.25.1")//
                .mountFrom(testDirPath).to("/somedir", "ro")//
                .cmd("sh", "-c", "cat /somedir/"+testFilename)//
                .build();
        testee.before();

        DockerClient dockerClient = testee.getDockerClient();
        dockerClient.waitContainer(testee.getContainerId());

        LogStream stdoutLog = dockerClient.logs(testee.getContainerId(), LogsParam.stdout(), LogsParam.stderr());
        String stdout = StringUtils.trim(stdoutLog.readFully());
        log.info("log:\n{}", stdout);
        assertThat(stdout, containsString(testFileContent));
    }

    @Test
    public void shouldReadMountFromJavaFile() throws Throwable {
        testee = DockerRule.builder()//
                .imageName("busybox:1.25.1")//
                .mountFrom(testDir).to("/somedir", "ro")//
                .cmd("sh", "-c", "cat /somedir/"+testFilename)//
                .build();
        testee.before();

        DockerClient dockerClient = testee.getDockerClient();
        dockerClient.waitContainer(testee.getContainerId());

        LogStream stdoutLog = dockerClient.logs(testee.getContainerId(), LogsParam.stdout(), LogsParam.stderr());
        String stdout = StringUtils.trim(stdoutLog.readFully());
        log.info("log:\n{}", stdout);
        assertThat(stdout, containsString(testFileContent));
    }

    @Test
    public void shouldWriteRwMountedFile() throws Throwable {
        testee = DockerRule.builder()//
                .imageName("busybox:1.25.1")//
                .mountFrom(testDirPath).to("/somedir")//
                .cmd("sh", "-c", "echo "+testFileContentChanged+" > /somedir/"+testFilename)//
                .build();
        testee.before();

        DockerClient dockerClient = testee.getDockerClient();
        dockerClient.waitContainer(testee.getContainerId());

        LogStream stdoutLog = dockerClient.logs(testee.getContainerId(), LogsParam.stdout(), LogsParam.stderr());
        String stdout = StringUtils.trim(stdoutLog.readFully());
        log.info("log:\n{}", stdout);
        try (InputStream is = new FileInputStream(testFile)) {
            String fileContent = StringUtils.join(IOUtils.readLines(is), " ");
            assertThat(fileContent, containsString(testFileContentChanged));
        }
    }

    @Test
    public void shouldNotWriteRoMountedFile() throws Throwable {
        testee = DockerRule.builder()//
                .imageName("busybox:1.25.1")//
                .mountFrom(testDirPath).to("/somedir", "ro")//
                .cmd("sh", "-c", "echo "+testFileContentChanged+" > /somedir/"+testFilename)//
                .build();
        testee.before();

        DockerClient dockerClient = testee.getDockerClient();
        dockerClient.waitContainer(testee.getContainerId());

        LogStream stdoutLog = dockerClient.logs(testee.getContainerId(), LogsParam.stdout(), LogsParam.stderr());
        String stdout = StringUtils.trim(stdoutLog.readFully());
        log.info("log:\n{}", stdout);
        try (InputStream is = new FileInputStream(testFile)) {
            String fileContent = StringUtils.join(IOUtils.readLines(is), " ");
            assertThat(fileContent, containsString(testFileContent));
        }
    }

}
