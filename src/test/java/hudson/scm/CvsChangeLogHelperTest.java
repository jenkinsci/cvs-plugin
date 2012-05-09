package hudson.scm;

import hudson.EnvVars;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;

public class CvsChangeLogHelperTest extends HudsonTestCase {

    @Test
    public void testMapCvsLog() {
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
                        + "=============================================================================\n";

        CvsModule module = new CvsModule("doc", null);
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), new CvsModule[]{module});
        CvsRepository repository = new CvsRepository(
                        ":local:/Users/Shared/cvs", false, null,
                        Arrays.asList(new CvsRepositoryItem[] {item}),
                        new ArrayList<ExcludedRegion>(), -1);
        assertEquals("adding in a test file", CvsChangeLogHelper.getInstance()
                        .mapCvsLog(logContents, repository, item, module, new EnvVars())
                        .getChanges().get(0).getMsg());
    }

    @Test
    public void testMapNonFilteredCvsLog() throws IOException, URISyntaxException {
        File changeLogFile = new File(CvsChangeLogHelperTest.class.getResource("cvsRlogOutput_ISSUE-13227.txt").toURI());
        int len = (int)changeLogFile.length();
        InputStream in = new FileInputStream(changeLogFile); byte[] b  = new byte[len]; int total = 0;  while (total < len) {   int result = in.read(b, total, len - total);   if (result == -1) {     break;   }   total += result; }
        String logContents = new String(b, Charset.forName("UTF-8"));

        CvsModule module = new CvsModule("portalInt", null);
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.BranchRepositoryLocation(/*"d-chg00017366_op_brc_prod-op-2012-04-19"*/ "d-chg00017366_op_impl_2012-05-02_v20", false), new CvsModule[]{module});
        CvsRepository repository = new CvsRepository(":pserver:user:password@host:port:/usr/local/cvs/repcvs/", false, null, Arrays.asList(new CvsRepositoryItem[]{item}), new ArrayList<ExcludedRegion>(), -1);
        assertEquals(4, CvsChangeLogHelper.getInstance().mapCvsLog(logContents, repository, item, module, new EnvVars()).getChanges().size());
    }

}
