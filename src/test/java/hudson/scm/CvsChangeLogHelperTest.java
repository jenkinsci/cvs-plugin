package hudson.scm;

import hudson.EnvVars;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class CvsChangeLogHelperTest {

    private JenkinsRule jenkinsRule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jenkinsRule = rule;
    }

    @Test
    void testMapCvsLog() throws Exception {
        String logContents = """
                cvs rlog: Logging doc
                
                RCS file: /Users/Shared/cvs/doc/rand,v
                head: 1.1
                branch:
                locks: strict
                access list:
                symbolic names:
                keyword substitution: kv
                total revisions: 1; selected revisions: 1
                description:
                ----------------------------
                revision 1.1
                date: 2011-12-28 20:22:31 +0000;  author: Michael;  state: Exp;  commitid: 0nRgbNtAi8rCNZMv;
                adding in a test file
                with a multi-line commit
                and a line of dashes
                ----------------------------
                in the middle
                ============================\
                ========= and a couple of lines of line starting with ===========\
                =============================================================================
                """;

        CvsModule module = new CvsModule("doc", null);
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), new CvsModule[]{module});
        CvsRepository repository = new CvsRepository(
                ":local:/Users/Shared/cvs", false, null,
                List.of(item),
                new ArrayList<>(), -1, null);
        String lineSeperator = System.lineSeparator();
        assertEquals("adding in a test file" + lineSeperator + "with a multi-line commit" + lineSeperator
                + "and a line of dashes" + lineSeperator + "----------------------------" + lineSeperator + "in the middle", new StringCvsLog(logContents)
                .mapCvsLog(repository.getCvsRoot(), item.getLocation(), repository, new EnvVars())
                .getChanges().get(0).getMsg());
    }

    @Test
    void testMapNonFilteredCvsLog() throws Exception {
        String logContents = getFileContents("cvsRlogOutput_ISSUE-13227.txt");

        CvsModule module = new CvsModule("portalInt", null);
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.BranchRepositoryLocation("d-chg00017366_op_brc_prod-op-2012-04-19", false), new CvsModule[]{module});
        CvsRepository repository = new CvsRepository(":pserver:user:password@host:port:/usr/local/cvs/repcvs/", false, null, List.of(item), new ArrayList<>(), -1, null);
        CvsChangeSet cvsChangeSet = new StringCvsLog(logContents).mapCvsLog(repository.getCvsRoot(), item.getLocation(), repository, new EnvVars());
        assertEquals(4, cvsChangeSet.getChanges().size());
    }

    @Test
    void testMapNonFilteredCvsLog2() throws Exception {
        String logContents = getFileContents("cvsRlogOutput2.txt");

        CvsModule module = new CvsModule("branch2", null);
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.BranchRepositoryLocation(/*"d-chg00017366_op_brc_prod-op-2012-04-19"*/ "branch2", false), new CvsModule[]{module});
        CvsRepository repository = new CvsRepository(":pserver:user:password@host:port:/homepages/25/d83630321/htdocs/cvs", false, null, List.of(item), new ArrayList<>(), -1, null);
        CvsChangeSet set = new StringCvsLog(logContents).mapCvsLog(repository.getCvsRoot(), item.getLocation(), null, new EnvVars());
        assertEquals(3, set.getChanges().size());
    }

    @Test
    void testMapNonFilteredLogHead() throws Exception {
        String logContents = getFileContents("cvsRlogOutputHead.txt");

        CvsModule module = new CvsModule("product", null);
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), new CvsModule[]{module});
        CvsRepository repository = new CvsRepository(":pserver:host:/srv/cvs/repositories/iqdoq", false, null, List.of(item), new ArrayList<>(), -1, null);
        assertTrue(new StringCvsLog(logContents).mapCvsLog(repository.getCvsRoot(), item.getLocation(), repository, new EnvVars()).getChanges().isEmpty());
    }


    private String getFileContents(String fileName) throws Exception {
        File changeLogFile = new File(CvsChangeLogHelperTest.class.getResource(fileName).toURI());
        int len = (int) changeLogFile.length();
        try (InputStream in = new FileInputStream(changeLogFile)) {
            byte[] b = new byte[len];
            int total = 0;
            while (total < len) {
                int result = in.read(b, total, len - total);
                if (result == -1) {
                    break;
                }
                total += result;
            }
            return new String(b, StandardCharsets.UTF_8);
        }
    }

    public static class StringCvsLog extends CvsLog {
        private final String text;

        public StringCvsLog(String text) {
            this.text = text;
        }

        protected Reader read() throws IOException {
            return new StringReader(text);
        }

        protected void dispose() {
        }
    }

}