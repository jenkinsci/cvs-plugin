package hudson.scm;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CvsChangeLogHelperTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testMapCvsLog() throws IOException {
        String logContents = "cvs rlog: Logging doc\n"
                + "\n"
                + "RCS file: /Users/Shared/cvs/doc/rand,v\n"
                + "head: 1.1\n"
                + "branch:\n"
                + "locks: strict\n"
                + "access list:\n"
                + "symbolic names:\n"
                + "keyword substitution: kv\n"
                + "total revisions: 1; selected revisions: 1\n"
                + "description:\n"
                + "----------------------------\n"
                + "revision 1.1\n"
                + "date: 2011-12-28 20:22:31 +0000;  author: Michael;  state: Exp;  commitid: 0nRgbNtAi8rCNZMv;\n"
                + "adding in a test file\n"
                + "with a multi-line commit\n"
                + "and a line of dashes\n"
                + "----------------------------\n"
                + "in the middle\n"
                + "============================"
                + "========= and a couple of lines of line starting with ==========="
                + "=============================================================================\n";

        CvsModule module = new CvsModule("doc", null);
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), new CvsModule[]{module});
        CvsRepository repository = new CvsRepository(
                ":local:/Users/Shared/cvs", false, null,
                Arrays.asList(item),
                new ArrayList<ExcludedRegion>(), -1);
        String lineSeperator = System.getProperty("line.separator");
        assertEquals("adding in a test file" + lineSeperator + "with a multi-line commit" + lineSeperator
                + "and a line of dashes" +lineSeperator + "----------------------------" + lineSeperator + "in the middle" , new StringCvsLog(logContents)
                .mapCvsLog(repository.getCvsRoot(), item.getLocation())
                .getChanges().get(0).getMsg());
    }

    @Test
    public void testMapNonFilteredCvsLog() throws IOException, URISyntaxException {
        String logContents = getFileContents("cvsRlogOutput_ISSUE-13227.txt");

        CvsModule module = new CvsModule("portalInt", null);
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.BranchRepositoryLocation("d-chg00017366_op_brc_prod-op-2012-04-19", false), new CvsModule[]{module});
        CvsRepository repository = new CvsRepository(":pserver:user:password@host:port:/usr/local/cvs/repcvs/", false, null, Arrays.asList(item), new ArrayList<ExcludedRegion>(), -1);
        CvsChangeSet cvsChangeSet = new StringCvsLog(logContents).mapCvsLog(repository.getCvsRoot(), item.getLocation());
        assertEquals(4, cvsChangeSet.getChanges().size());
    }

    @Test
    public void testMapNonFilteredCvsLog2() throws IOException, URISyntaxException {
        String logContents = getFileContents("cvsRlogOutput2.txt");

        CvsModule module = new CvsModule("branch2", null);
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.BranchRepositoryLocation(/*"d-chg00017366_op_brc_prod-op-2012-04-19"*/ "branch2", false), new CvsModule[]{module});
        CvsRepository repository = new CvsRepository(":pserver:user:password@host:port:/homepages/25/d83630321/htdocs/cvs", false, null, Arrays.asList(item), new ArrayList<ExcludedRegion>(), -1);
        CvsChangeSet set = new StringCvsLog(logContents).mapCvsLog(repository.getCvsRoot(), item.getLocation());
        assertEquals(3, set.getChanges().size());
    }

    @Test
    public void testMapNonFilteredLogHead() throws IOException, URISyntaxException {
        String logContents = getFileContents("cvsRlogOutputHead.txt");

        CvsModule module = new CvsModule("product", null);
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), new CvsModule[]{module});
        CvsRepository repository = new CvsRepository(":pserver:host:/srv/cvs/repositories/iqdoq", false, null, Arrays.asList(item), new ArrayList<ExcludedRegion>(), -1);
        assertTrue(new StringCvsLog(logContents).mapCvsLog(repository.getCvsRoot(), item.getLocation()).getChanges().isEmpty());
    }


    private String getFileContents(String fileName) throws IOException, URISyntaxException {
        File changeLogFile = new File(CvsChangeLogHelperTest.class.getResource(fileName).toURI());
        int len = (int)changeLogFile.length();
        InputStream in = new FileInputStream(changeLogFile); byte[] b  = new byte[len]; int total = 0;  while (total < len) {   int result = in.read(b, total, len - total);   if (result == -1) {     break;   }   total += result; }
        return new String(b, Charset.forName("UTF-8"));
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