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
}
