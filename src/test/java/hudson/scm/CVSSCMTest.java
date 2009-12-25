package hudson.scm;

import hudson.scm.browsers.ViewCVS;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import hudson.model.FreeStyleProject;

import java.lang.reflect.Field;
import java.net.URL;

/**
 * @author Kohsuke Kawaguchi
 */
public class CVSSCMTest extends HudsonTestCase {
    /**
     * Verifies that there's no data loss.
     */
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = createFreeStyleProject();

        // verify values
        CVSSCM scm1 = new CVSSCM("cvsroot", "module", "branch", "cvsRsh", true, true, true, "excludedRegions");
        p.setScm(scm1);
        roundtrip(p);
        assertEquals(scm1, (CVSSCM)p.getScm());

        // all boolean fields need to be tried with two values
        scm1 = new CVSSCM("x", "y", "z", "w", false, false, false, "t");
        p.setScm(scm1);

        roundtrip(p);
        assertEquals(scm1, (CVSSCM)p.getScm());
    }

    @Bug(4456)
    public void testGlobalConfigRoundtrip() throws Exception {
        CVSSCM.DescriptorImpl d = hudson.getDescriptorByType(CVSSCM.DescriptorImpl.class);
        d.setCvspassFile("a");
        d.setCvsExe("b");

        submit(createWebClient().goTo("configure").getFormByName("config"));
        assertEquals("a",d.getCvspassFile());
        assertEquals("b",d.getCvsExe());
    }

    private void roundtrip(FreeStyleProject p) throws Exception {
        submit(new WebClient().getPage(p,"configure").getFormByName("config"));
    }

    private void assertEquals(CVSSCM scm1, CVSSCM scm2) {
        assertEquals(scm1.getCvsRoot(),scm2.getCvsRoot());
        assertEquals(scm1.getAllModules(),scm2.getAllModules());
        assertEquals(scm1.getBranch(),scm2.getBranch());
        assertEquals(scm1.getCvsRsh(),scm2.getCvsRsh());
        assertEquals(scm1.getExcludedRegions(),scm2.getExcludedRegions());
        assertEquals(scm1.getCanUseUpdate(),scm2.getCanUseUpdate());
        assertEquals(scm1.isFlatten(),scm2.isFlatten());
        assertEquals(scm1.isTag(),scm2.isTag());
    }

    @Email("https://hudson.dev.java.net/servlets/BrowseList?list=users&by=thread&from=2222483")
    @Bug(4760)
    public void testProjectExport() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        CVSSCM scm = new CVSSCM(":pserver:nowhere.net/cvs/foo", ".", null, null, true, true, false, null);
        p.setScm(scm);
        Field repositoryBrowser = scm.getClass().getDeclaredField("repositoryBrowser");
        repositoryBrowser.setAccessible(true);
        repositoryBrowser.set(scm, new ViewCVS(new URL("http://nowhere.net/viewcvs/")));
        new WebClient().goTo(p.getUrl()+"api/xml", "application/xml");
        new WebClient().goTo(p.getUrl()+"api/xml?depth=999", "application/xml");
    }
}
