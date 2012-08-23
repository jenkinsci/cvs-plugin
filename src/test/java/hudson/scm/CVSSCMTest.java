package hudson.scm;

import hudson.model.FreeStyleProject;
import hudson.scm.browsers.ViewCVS;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.HudsonTestCase;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;

/**
 * @author Kohsuke Kawaguchi
 */
public class CVSSCMTest extends HudsonTestCase {
    /**
     * Verifies that there's no data loss.
     */
    @SuppressWarnings("deprecation")
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = createFreeStyleProject();

        // verify values
        CVSSCM scm1 = new CVSSCM("cvsroot", "module", "branch", "cvsRsh", true,
                        true, true, true, "excludedRegions");
        p.setScm(scm1);
        roundtrip(p);
        assertEquals(scm1, (CVSSCM) p.getScm());

        // all boolean fields need to be tried with two values
        scm1 = new CVSSCM("x", "y", "z", "w", false, false, false, false, "t");
        p.setScm(scm1);

        roundtrip(p);
        assertEquals(scm1, (CVSSCM) p.getScm());
    }

    public void testUpgradeParameters() {
        CvsModule[] modules = new CvsModule[3];
        modules[0] = new CvsModule("module1", "");
        modules[1] = new CvsModule("module2", "");
        modules[2] = new CvsModule("module 3", "");
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), modules);
        CvsRepository[] repositories = new CvsRepository[1];
        repositories[0] = new CvsRepository("cvsroot", false, null, Arrays.asList(new CvsRepositoryItem[]{item}),
                        Arrays.asList(new ExcludedRegion[] {
                                        new ExcludedRegion("excludedRegions"),
                                        new ExcludedRegion("region2") }), -1);

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
    public void testGlobalConfigRoundtrip() throws Exception {
        CVSSCM.DescriptorImpl d = hudson
                        .getDescriptorByType(CVSSCM.DescriptorImpl.class);
        
        Field field = d.getClass().getField("compressionLevel");
        field.setAccessible(true);
        field.setInt(d, 1);

        submit(createWebClient().goTo("configure").getFormByName("config"));
        assertEquals(1, d.getCompressionLevel());
    }

    private void roundtrip(final FreeStyleProject p) throws Exception {
        submit(new WebClient().getPage(p, "configure").getFormByName("config"));
    }

    private void assertEquals(final CVSSCM scm1, final CVSSCM scm2) {
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
    public void testProjectExport() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        @SuppressWarnings("deprecation")
        CVSSCM scm = new CVSSCM(":pserver:nowhere.net/cvs/foo", ".", null,
                        null, true, false, true, false, null);
        p.setScm(scm);
        Field repositoryBrowser = scm.getClass().getDeclaredField(
                        "repositoryBrowser");
        repositoryBrowser.setAccessible(true);
        repositoryBrowser.set(scm, new ViewCVS(new URL(
                        "http://nowhere.net/viewcvs/")));
        new WebClient().goTo(p.getUrl() + "api/xml", "application/xml");
        new WebClient().goTo(p.getUrl() + "api/xml?depth=999",
                        "application/xml");
    }
}
