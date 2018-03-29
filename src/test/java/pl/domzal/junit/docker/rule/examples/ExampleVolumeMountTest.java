package pl.domzal.junit.docker.rule.examples;

import java.io.File;
import java.io.IOException;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import pl.domzal.junit.docker.rule.DockerRule;

/**
 * Is it possible to mount specified local folder as container volume. <br/>
 * But please note that in boot2docker environment it is only possible to mount
 * folder when it is subfolder of user homedir.
 */
@Category(test.category.Stable.class)
public class ExampleVolumeMountTest {

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder(baseDir());

    private static File baseDir() {
        String className = ExampleVolumeMountTest.class.getName();
        URL url = ExampleVolumeMountTest.class
                .getResource("/" + className.replace('.', '/') + ".class");
        if (url == null) {
            return null;
        }
        try {
            Path path = Paths.get(url.toURI()).getParent(); // ClassName
            for (int i = className.indexOf('.'); i != -1; i = className.indexOf('.', i + 1)) {
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

    @BeforeClass
    public static void setup() throws IOException {
        File testFile = tempFolder.newFile("somefile");
        FileUtils.write(testFile, "1234567890");
    }

    @Rule
    public DockerRule testee = DockerRule.builder()
            .imageName("busybox:1.25.1")
            // mounting requires specifying file and target path
            .mountFrom(tempFolder.getRoot()).to("/somedir", "ro")
            .cmd("sh", "-c", "cat /somedir/somefile")
            .build();

    @Test(timeout = 10000)
    public void shouldReadMountFromJavaFile() throws Throwable {
        testee.waitForLogMessage("1234567890", 10);
    }

}
