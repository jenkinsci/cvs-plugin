package hudson.scm;

import hudson.model.FreeStyleProject;
import hudson.scm.browsers.ViewCVS;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * @author Kohsuke Kawaguchi
 */
public class CVSSCMTest {

    @Rule
    public VisibleJenkinsRule jenkinsRule = new VisibleJenkinsRule();

    
    //we have to create an inner class since createFreeStyleProject is protected in early versions of JenkinsRule
    private static class VisibleJenkinsRule extends JenkinsRule {
        public FreeStyleProject createFreeStyleProject() throws IOException {
            return super.createFreeStyleProject();
        }
    }

    /**
     * Verifies that there's no data loss.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = jenkinsRule.createFreeStyleProject();

        // verify values
        CVSSCM scm1 = new CVSSCM("cvsroot", "module", "branch", "cvsRsh", true,
                        true, true, true, "excludedRegions");
        p.setScm(scm1);
        roundtrip(p);
        assertScmEquals(scm1, (CVSSCM) p.getScm());

        // all boolean fields need to be tried with two values
        scm1 = new CVSSCM("x", "y", "z", "w", false, false, false, false, "t");
        p.setScm(scm1);

        roundtrip(p);
        assertScmEquals(scm1, (CVSSCM) p.getScm());
    }

    @Test
    public void testUpgradeParameters() {
        CvsModule[] modules = new CvsModule[3];
        modules[0] = new CvsModule("module1", "");
        modules[1] = new CvsModule("module2", "");
        modules[2] = new CvsModule("module 3", "");
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), modules);
        CvsRepository[] repositories = new CvsRepository[1];
        repositories[0] = new CvsRepository("cvsroot", false, null, Arrays.asList(item),
                        Arrays.asList(new ExcludedRegion("excludedRegions"),
                                        new ExcludedRegion("region2")), -1);

        @SuppressWarnings("deprecation")
        CVSSCM scm1 = new CVSSCM("cvsroot", "module1 module2 module\\ 3", "",
                        "cvsRsh", true, false, true, false,
                        "excludedRegions\rregion2");
        assertEquals("Unexpected number of repositories", 1,
                scm1.getRepositories().length);
        assertEquals("Unexpected number of modules", 3,
                scm1.getRepositories()[0].getRepositoryItems()[0].getModules().length);
        for (int i = 0; i < repositories.length; i++) {
            assertEquals(repositories[i], scm1.getRepositories()[i]);
        }

    }

    @Bug(4456)
    @Test
    public void testGlobalConfigRoundtrip() throws Exception {
        CVSSCM.DescriptorImpl d = jenkinsRule.getInstance()
                        .getDescriptorByType(CVSSCM.DescriptorImpl.class);
        
        Field field = d.getClass().getDeclaredField("compressionLevel");
        field.setAccessible(true);
        field.setInt(d, 1);

        jenkinsRule.submit(jenkinsRule.createWebClient().goTo("configure").getFormByName("config"));
        assertEquals(1, d.getCompressionLevel());
    }

    private void roundtrip(final FreeStyleProject p) throws Exception {
        jenkinsRule.submit(jenkinsRule.createWebClient().getPage(p, "configure").getFormByName("config"));
    }

    private void assertScmEquals(final CVSSCM scm1, final CVSSCM scm2) {
        assertEquals(scm1.isCanUseUpdate(), scm2.isCanUseUpdate());
        assertEquals(scm1.isFlatten(), scm2.isFlatten());
        assertEquals(scm1.getRepositories().length,
                        scm2.getRepositories().length);
        for (int i = 0; i < scm1.getRepositories().length; i++) {
            assertEquals(scm1.getRepositories()[i], scm2.getRepositories()[i]);
        }
    }

    @Email("https://hudson.dev.java.net/servlets/BrowseList?list=users&by=thread&from=2222483")
    @Bug(4760)
    @Test
    public void testProjectExport() throws Exception {
        FreeStyleProject p = jenkinsRule.createFreeStyleProject();
        jenkinsRule.assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        @SuppressWarnings("deprecation")
        CVSSCM scm = new CVSSCM(":pserver:nowhere.net/cvs/foo", ".", null,
                        null, true, false, true, false, null);
        p.setScm(scm);
        Field repositoryBrowser = scm.getClass().getDeclaredField(
                        "repositoryBrowser");
        repositoryBrowser.setAccessible(true);
        repositoryBrowser.set(scm, new ViewCVS(new URL(
                        "http://nowhere.net/viewcvs/")));
        jenkinsRule.createWebClient().goTo(p.getUrl() + "api/xml", "application/xml");
        jenkinsRule.createWebClient().goTo(p.getUrl() + "api/xml?depth=999",
                "application/xml");
    }
    
    @Bug(14141)
    @Test
    public void testFlattenEnabled() {
        List<CvsRepository> repositories = Arrays.asList(new CvsRepository("cvsroot", false, null,
                Arrays.asList(new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), new CvsModule[]{new CvsModule("remoteName", "localName")})), new ArrayList<ExcludedRegion>(), 3));
        CVSSCM scm = new CVSSCM(repositories, false, false, null, false, false, false, false, false);
        assertFalse(scm.isLegacy());

        scm = new CVSSCM(repositories, false, true, null, false, false, false, false, false);
        assertTrue(scm.isLegacy());

        repositories = Arrays.asList(new CvsRepository("cvsroot", false, null,
                Arrays.asList(new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), new CvsModule[]{new CvsModule("remoteName", "localName"), new CvsModule("remoteName2", "localName2")})), new ArrayList<ExcludedRegion>(), 3));

        scm = new CVSSCM(repositories, false, false, null, false, false, false, false, false);
        assertTrue(scm.isLegacy());

    }
}
