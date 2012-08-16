package hudson.scm;

import hudson.scm.CVSChangeLogSet.CVSChangeLog;
import hudson.util.Digester2;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.digester.Digester;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

public class CVSChangeLogSetTest extends HudsonTestCase {

    // avoid regressions like JENKINS-14711
    @Test
    public void testToFile() throws Exception {
        final CVSChangeLog log = new CVSChangeLog();
        log.setChangeDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2012-08-16 10:20:30"));
        log.setUser("user");
        log.setMsg("sample entry");

        final hudson.scm.CVSChangeLogSet.File changedFile = new hudson.scm.CVSChangeLogSet.File();
        changedFile.setFullName("fileFullName");
        changedFile.setName("fileName");
        changedFile.setRevision("1.1");
        log.addFile(changedFile);

        final CVSChangeLogSet changelogSet = new CVSChangeLogSet(null, Arrays.asList(log));

        final File file = new File(createTmpDir(), "changelog_test.xml");
        changelogSet.toFile(file);

        Digester digester = new Digester2();
        ArrayList<CVSChangeLogTest> r = new ArrayList<CVSChangeLogTest>();
        digester.push(r);

        digester.addObjectCreate("*/entry", CVSChangeLogTest.class);
        digester.addBeanPropertySetter("*/entry/changeDate", "changeDate");
        digester.addBeanPropertySetter("*/entry/author", "user");
        digester.addBeanPropertySetter("*/entry/msg");

        digester.addObjectCreate("*/entry/file", FileTest.class);
        digester.addBeanPropertySetter("*/entry/file/name");
        digester.addBeanPropertySetter("*/entry/file/fullName");
        digester.addBeanPropertySetter("*/entry/file/revision");

        digester.parse(file);
    }

    public static class CVSChangeLogTest {

        public void setChangeDate(final String changeDate) {
            assertEquals("Invalid change date format.", "2012-08-16 10:20:30", changeDate);
        }

        public void setUser(final String user) {
            assertEquals("Invalid user.", "user", user);
        }

        public void setMsg(final String msg) {
            assertEquals("Invalid message.", "sample entry", msg);
        }
    }

    public static class FileTest {

        public void setFullName(final String name) {
            assertEquals("Invalid file full name.", "fileFullName", name);
        }

        public void setName(final String name) {
            assertEquals("Invalid file name.", "fileName", name);
        }

        public void setRevision(final String revision) {
            assertEquals("Invalid revision number.", "1.1", revision);
        }
    }
}
