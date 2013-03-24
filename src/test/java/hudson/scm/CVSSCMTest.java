package hudson.scm;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.browsers.ViewCVS;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * @author Kohsuke Kawaguchi
 */
public class CVSSCMTest {

    @Rule
    public VisibleJenkinsRule jenkinsRule = new VisibleJenkinsRule();

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
                                        new ExcludedRegion("region2")), -1, null);

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
                Arrays.asList(new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), new CvsModule[]{new CvsModule("remoteName", "localName")})), new ArrayList<ExcludedRegion>(), 3, null));
        CVSSCM scm = new CVSSCM(repositories, false, false, false, false, false, false, false);
        assertFalse(scm.isLegacy());

        scm = new CVSSCM(repositories, false, true, false, false, false, false, false);
        assertTrue(scm.isLegacy());

        repositories = Arrays.asList(new CvsRepository("cvsroot", false, null,
                Arrays.asList(new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), new CvsModule[]{new CvsModule("remoteName", "localName"), new CvsModule("remoteName2", "localName2")})), new ArrayList<ExcludedRegion>(), 3, null));

        scm = new CVSSCM(repositories, false, false, false, false, false, false, false);
        assertTrue(scm.isLegacy());

    }

    @Test
    public void testExcludeRegions() throws IOException, InterruptedException {
        List<CvsFile> files = new ArrayList<CvsFile>();
        files.add(new CvsFile("test.ext", "1.1", false));
        files.add(new CvsFile("subdir/test.ext", "1.1", false));
        files.add(new CvsFile("subdir/subdir2/test.ext", "1.1", false));
        Project project = new CustomFreeStyleProject(jenkinsRule.getInstance(), "testProject");

        CvsRepository repository = new CvsRepository("repo", false, null, Arrays.<CvsRepositoryItem>asList(),
                Arrays.<ExcludedRegion>asList(new ExcludedRegion("^[^/]*\\.ext$")), 3, null);
        Map<CvsRepository, List<CvsFile>> repositoryState = new HashMap<CvsRepository, List<CvsFile>>();
        repositoryState.put(repository, new ArrayList<CvsFile>());
        CvsRevisionState revisionState = new CvsRevisionState(repositoryState);


        CustomLog log = new CustomLog("test", null);
        LogTaskListener listener = new LogTaskListener(log, Level.FINE);

        CustomCvs customCvs = new CustomCvs(Arrays.asList(repository), false, false, false, false, false, false, false);
        customCvs.setRepositoryState(files);
        CvsRevisionState state = (CvsRevisionState)customCvs.compareRemoteRevisionWith(project, null, listener, revisionState, new CvsRepository[]{repository}).baseline;
        List<CvsFile> result = state.getModuleFiles().get(repository);
        assertEquals(3, result.size());

        listener.getLogger().flush();
        assertEquals("Skipping file 'test.ext' since it matches exclude pattern ^[^/]*\\.ext$", log.getContents());

        repository = new CvsRepository("repo", false, null, Arrays.<CvsRepositoryItem>asList(),
                Arrays.<ExcludedRegion>asList(new ExcludedRegion("[^/]*\\.ext")), 3, null);
        repositoryState = new HashMap<CvsRepository, List<CvsFile>>();
        repositoryState.put(repository, new ArrayList<CvsFile>());
        revisionState = new CvsRevisionState(repositoryState);


        log = new CustomLog("test", null);
        listener = new LogTaskListener(log, Level.FINE);

        customCvs = new CustomCvs(Arrays.asList(repository), false, false, false, false, false, false, false);
        customCvs.setRepositoryState(files);
        state = (CvsRevisionState)customCvs.compareRemoteRevisionWith(project, null, listener, revisionState, new CvsRepository[]{repository}).baseline;
        result = state.getModuleFiles().get(repository);
        assertEquals(3, result.size());

        listener.getLogger().flush();
        assertEquals("Skipping file 'test.ext' since it matches exclude pattern [^/]*\\.ext", log.getContents());

        repository = new CvsRepository("repo", false, null, Arrays.<CvsRepositoryItem>asList(),
                Arrays.<ExcludedRegion>asList(new ExcludedRegion("(?:[^/]+/)+[a-z0-9]+\\.ext")), 3, null);
        repositoryState = new HashMap<CvsRepository, List<CvsFile>>();
        repositoryState.put(repository, new ArrayList<CvsFile>());
        revisionState = new CvsRevisionState(repositoryState);


        log = new CustomLog("test", null);
        listener = new LogTaskListener(log, Level.FINE);

        customCvs = new CustomCvs(Arrays.asList(repository), false, false, false, false, false, false, false);
        customCvs.setRepositoryState(files);
        state = (CvsRevisionState)customCvs.compareRemoteRevisionWith(project, null, listener, revisionState, new CvsRepository[]{repository}).baseline;
        result = state.getModuleFiles().get(repository);
        assertEquals(3, result.size());

        listener.getLogger().flush();
        assertEquals("Skipping file 'subdir/test.ext' since it matches exclude pattern (?:[^/]+/)+[a-z0-9]+\\.ext\rSkipping file 'subdir/subdir2/test.ext' since it matches exclude pattern (?:[^/]+/)+[a-z0-9]+\\.ext", log.getContents());

    }

    private static class CustomLog extends Logger {

        private String contents = "";
        private String lineBreak = "";

        public CustomLog(String name, String resourceBundle) {
            super(name, resourceBundle);
        }

        public void log(LogRecord record) {
            contents += lineBreak + record.getMessage();
            lineBreak = "\r";
        }

        public String getContents() {
            return contents;
        }
};

    private static class CustomFreeStyleProject extends FreeStyleProject {

        public CustomFreeStyleProject(Jenkins parent, String name) {
            super(parent, name);
        }

        public FreeStyleBuild getLastBuild() {
            try {
                return new FreeStyleBuild(this);
            } catch (IOException e) {
                throw new RuntimeException("Could not create build", e);
            }
        }

        public FreeStyleBuild getLastCompletedBuild() {
            return getLastBuild();
        }
    }

    private static class CustomCvs extends CVSSCM {

        private List<CvsFile> files;

        public CustomCvs(List<CvsRepository> repositories, boolean canUseUpdate, boolean legacy, boolean skipChangeLog, boolean pruneEmptyDirectories, boolean disableCvsQuiet, boolean cleanOnFailedUpdate, boolean forceCleanCopy) {
            super(repositories, canUseUpdate, legacy, skipChangeLog, pruneEmptyDirectories, disableCvsQuiet, cleanOnFailedUpdate, forceCleanCopy);
        }


        protected List<CvsFile> calculateRepositoryState(final Date startTime, final Date endTime,
                                                         final CvsRepository repository, final TaskListener listener,
                                                         final EnvVars envVars) {
            return files;
        }

        public void setRepositoryState(List<CvsFile> files) {
            this.files = files;
        }
    }
}
