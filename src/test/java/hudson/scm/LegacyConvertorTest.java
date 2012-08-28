package hudson.scm;

import java.io.File;
import java.net.URISyntaxException;

import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

public class LegacyConvertorTest extends HudsonTestCase {

    // borrowed from core/test/.../TestResultTest, this is probably too complicated
    private String getDataFile(String name) throws URISyntaxException {
        return new File(LegacyConvertorTest.class.getResource(name).toURI()).getAbsolutePath();
    }

    // check JENKINS-12582 CVS-Plugin: Password file "${user.home}/.cvspass" is ignored
    @Test
    public final void testGetPassword() throws URISyntaxException {
        // FIXME: not sure if setting user.home is safe in all cases?
        String home=System.getProperty("user.home");
        System.setProperty("user.home", getDataFile(""));
        LegacyConvertor instance = LegacyConvertor.getInstance();
        assertEquals("password", instance.getPassword(":pserver:user@example.com/usr/local/cvsroot"));
        assertEquals("password", instance.getPassword(":pserver:user@example.com:/usr/local/cvsroot"));

        System.setProperty("user.home", home);
    }

}
