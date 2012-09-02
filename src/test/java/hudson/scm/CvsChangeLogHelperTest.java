package hudson.scm;

import hudson.EnvVars;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

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

public class CvsChangeLogHelperTest extends HudsonTestCase {

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
                Arrays.asList(new CvsRepositoryItem[] {item}),
                new ArrayList<ExcludedRegion>(), -1);
        String lineSeperator = System.getProperty("line.separator");
        assertEquals("adding in a test file" + lineSeperator + "with a multi-line commit" + lineSeperator
                + "and a line of dashes" +lineSeperator + "----------------------------" + lineSeperator + "in the middle" , new StringCvsLog(logContents)
                .mapCvsLog(repository.getCvsRoot(), item.getLocation())
                .getChanges().get(0).getMsg());
    }

    public void testMapNonFilteredCvsLog() throws IOException, URISyntaxException {
        File changeLogFile = new File(CvsChangeLogHelperTest.class.getResource("cvsRlogOutput_ISSUE-13227.txt").toURI());
        int len = (int)changeLogFile.length();
        InputStream in = new FileInputStream(changeLogFile); byte[] b  = new byte[len]; int total = 0;  while (total < len) {   int result = in.read(b, total, len - total);   if (result == -1) {     break;   }   total += result; }
        String logContents = new String(b, Charset.forName("UTF-8"));

        CvsModule module = new CvsModule("portalInt", null);
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.BranchRepositoryLocation("d-chg00017366_op_brc_prod-op-2012-04-19", false), new CvsModule[]{module});
        CvsRepository repository = new CvsRepository(":pserver:user:password@host:port:/usr/local/cvs/repcvs/", false, null, Arrays.asList(new CvsRepositoryItem[]{item}), new ArrayList<ExcludedRegion>(), -1);
        CvsChangeSet cvsChangeSet = new StringCvsLog(logContents).mapCvsLog(repository.getCvsRoot(), item.getLocation());
        assertEquals(4, cvsChangeSet.getChanges().size());
    }

    public void testMapNonFilteredCvsLog2() throws IOException, URISyntaxException {
        File changeLogFile = new File(CvsChangeLogHelperTest.class.getResource("cvsRlogOutput2.txt").toURI());
        int len = (int)changeLogFile.length();
        InputStream in = new FileInputStream(changeLogFile); byte[] b  = new byte[len]; int total = 0;  while (total < len) {   int result = in.read(b, total, len - total);   if (result == -1) {     break;   }   total += result; }
        String logContents = new String(b, Charset.forName("UTF-8"));

        CvsModule module = new CvsModule("branch2", null);
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.BranchRepositoryLocation(/*"d-chg00017366_op_brc_prod-op-2012-04-19"*/ "branch2", false), new CvsModule[]{module});
        CvsRepository repository = new CvsRepository(":pserver:user:password@host:port:/homepages/25/d83630321/htdocs/cvs", false, null, Arrays.asList(new CvsRepositoryItem[]{item}), new ArrayList<ExcludedRegion>(), -1);
        CvsChangeSet set = new StringCvsLog(logContents).mapCvsLog(repository.getCvsRoot(), item.getLocation());
        assertEquals(3, set.getChanges().size());
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