package hudson.scm;

import java.io.File;
import java.net.URISyntaxException;

import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

public class CVSChangeLogParserTest extends HudsonTestCase {

    // borrowed from core/test/.../TestResultTest
    private File getDataFile(String name) throws URISyntaxException {
        return new File(CVSChangeLogParserTest.class.getResource(name).toURI());
    }

    // verify fix for JENKINS-12586
    @Test
    public void testParseOldFile() throws Exception {
        CVSChangeLogSet result = new CVSChangeLogParser().parse(null, getDataFile("changelogOldFormat.xml"));
        assertNotNull(result);
    }

    @Test
    public void testCurrentFormat() throws Exception {
        CVSChangeLogSet result = new CVSChangeLogParser().parse(null, getDataFile("changelogCurrentFormat.xml"));
        assertNotNull(result);
    }

    // verify fix for JENKINS-14711
    @Test
    public void testJENKINS_14711() throws Exception {
        CVSChangeLogSet result = new CVSChangeLogParser().parse(null, getDataFile("changelogRegression14711.xml"));
        assertNotNull(result);
    }
}
