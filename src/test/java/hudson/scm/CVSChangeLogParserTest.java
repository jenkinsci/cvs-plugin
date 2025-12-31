package hudson.scm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@WithJenkins
class CVSChangeLogParserTest {

    private JenkinsRule jenkinsRule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jenkinsRule = rule;
    }

    // borrowed from core/test/.../TestResultTest
    private File getDataFile(String name) throws URISyntaxException {
        return new File(CVSChangeLogParserTest.class.getResource(name).toURI());
    }

    // verify fix for JENKINS-12586
    @Test
    void testParseOldFile() throws Exception {
        CVSChangeLogSet result = new CVSChangeLogParser().parse(null, getDataFile("changelogOldFormat.xml"));
        assertNotNull(result);
    }

    @Test
    void testCurrentFormat() throws Exception {
        CVSChangeLogSet result = new CVSChangeLogParser().parse(null, getDataFile("changelogCurrentFormat.xml"));
        assertNotNull(result);
    }

    // verify fix for JENKINS-14711
    @Test
    void testJENKINS_14711() throws Exception {
        CVSChangeLogSet result = new CVSChangeLogParser().parse(null, getDataFile("changelogRegression14711.xml"));
        assertNotNull(result);
    }

}
