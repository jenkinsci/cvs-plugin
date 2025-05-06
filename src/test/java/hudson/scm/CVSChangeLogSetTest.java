package hudson.scm;

import hudson.scm.CVSChangeLogSet.CVSChangeLog;
import org.apache.commons.digester3.Digester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithJenkins
class CVSChangeLogSetTest {

    private JenkinsRule jenkinsRule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jenkinsRule = rule;
    }

    // avoid regressions like JENKINS-14711
    @Test
    void testToFile() throws Exception {
        final CVSChangeLog log = new CVSChangeLog();
        log.setChangeDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2012-08-16 10:20:30"));
        log.setUser("user");
        log.setMsg("sample entry");

        final hudson.scm.CVSChangeLogSet.File changedFile = new hudson.scm.CVSChangeLogSet.File();
        changedFile.setFullName("fileFullName");
        changedFile.setName("fileName");
        changedFile.setRevision("1.1");
        log.addFile(changedFile);

        final CVSChangeLogSet changelogSet = new CVSChangeLogSet(null, List.of(log));

        final File file = new File(jenkinsRule.createTmpDir(), "changelog_test.xml");
        changelogSet.toFile(file);

        Digester digester = new Digester();
        ArrayList<CVSChangeLogTest> r = new ArrayList<>();
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
            assertEquals("2012-08-16 10:20:30", changeDate, "Invalid change date format.");
        }

        public void setUser(final String user) {
            assertEquals("user", user, "Invalid user.");
        }

        public void setMsg(final String msg) {
            assertEquals("sample entry", msg, "Invalid message.");
        }
    }

    public static class FileTest {

        public void setFullName(final String name) {
            assertEquals("fileFullName", name, "Invalid file full name.");
        }

        public void setName(final String name) {
            assertEquals("fileName", name, "Invalid file name.");
        }

        public void setRevision(final String revision) {
            assertEquals("1.1", revision, "Invalid revision number.");
        }
    }

}
