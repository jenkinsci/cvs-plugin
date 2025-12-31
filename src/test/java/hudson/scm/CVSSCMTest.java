package hudson.scm;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.scm.browsers.ViewCVS;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class CVSSCMTest {

    private JenkinsRule jenkinsRule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jenkinsRule = rule;
    }

    /**
     * Verifies that there's no data loss.
     */
    @SuppressWarnings("deprecation")
    @Test
    void testConfigRoundtrip() throws Exception {
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
    void testUpgradeParameters() {
        CvsModule[] modules = new CvsModule[3];
        modules[0] = new CvsModule("module1", "");
        modules[1] = new CvsModule("module2", "");
        modules[2] = new CvsModule("module 3", "");
        CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), modules);
        CvsRepository[] repositories = new CvsRepository[1];
        repositories[0] = new CvsRepository("cvsroot", false, null, List.of(item),
                Arrays.asList(new ExcludedRegion("excludedRegions"),
                        new ExcludedRegion("region2")), -1, null);

        @SuppressWarnings("deprecation")
        CVSSCM scm1 = new CVSSCM("cvsroot", "module1 module2 module\\ 3", "",
                "cvsRsh", true, false, true, false,
                "excludedRegions\rregion2");
        assertEquals(1,
                scm1.getRepositories().length,
                "Unexpected number of repositories");
        assertEquals(3,
                scm1.getRepositories()[0].getRepositoryItems()[0].getModules().length,
                "Unexpected number of modules");
        for (int i = 0; i < repositories.length; i++) {
            assertEquals(repositories[i], scm1.getRepositories()[i]);
        }

    }

    @Issue("JENKINS-4456")
    @Test
    void testGlobalConfigRoundtrip() throws Exception {
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
    @Issue("JENKINS-4760")
    @Test
    void testProjectExport() throws Exception {
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

    @Issue("JENKINS-14141")
    @Test
    void testFlattenEnabled() {
        List<CvsRepository> repositories = List.of(new CvsRepository("cvsroot", false, null,
                List.of(new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), new CvsModule[]{new CvsModule("remoteName", "localName")})), new ArrayList<>(), 3, null));
        CVSSCM scm = new CVSSCM(repositories, false, false, false, false, false, false, false);
        assertFalse(scm.isLegacy());

        scm = new CVSSCM(repositories, false, true, false, false, false, false, false);
        assertTrue(scm.isLegacy());

        repositories = List.of(new CvsRepository("cvsroot", false, null,
                List.of(new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), new CvsModule[]{new CvsModule("remoteName", "localName"), new CvsModule("remoteName2", "localName2")})), new ArrayList<>(), 3, null));

        scm = new CVSSCM(repositories, false, false, false, false, false, false, false);
        assertTrue(scm.isLegacy());

    }

    @Test
    void testExcludeRegions() throws IOException, InterruptedException {
        List<CvsFile> files = new ArrayList<>();
        files.add(CvsFile.make("test.ext", "1.1", false));
        files.add(CvsFile.make("subdir/test.ext", "1.1", false));
        files.add(CvsFile.make("subdir/subdir2/test.ext", "1.1", false));
        CustomFreeStyleProject project = new CustomFreeStyleProject(jenkinsRule.getInstance(), "testProject");
        project.getLastBuild().setChangeSetComputed(true);
        CvsRepository repository = new CvsRepository("repo", false, null, List.of(),
                List.of(new ExcludedRegion("^[^/]*\\.ext$")), 3, null);
        Map<CvsRepository, List<CvsFile>> repositoryState = new HashMap<>();
        repositoryState.put(repository, new ArrayList<>());
        CvsRevisionState revisionState = new CvsRevisionState(repositoryState);


        CustomLog log = new CustomLog("test", null);
        LogTaskListener listener = new LogTaskListener(log, Level.FINE);

        CustomCvs customCvs = new CustomCvs(List.of(repository), false, false, false, false, false, false, false);
        customCvs.setRepositoryState(files);
        PollingResult pollingResult = customCvs.compareRemoteRevisionWith(project, null, null, listener, revisionState, new CvsRepository[]{repository});
        CvsRevisionState state = (CvsRevisionState) pollingResult.baseline;
        List<CvsFile> result = state.getModuleFiles().get(repository);
        assertEquals(3, result.size());

        listener.getLogger().flush();
        assertEquals("Skipping file 'test.ext' since it matches exclude pattern ^[^/]*\\.ext$", log.getContents());

        repository = new CvsRepository("repo", false, null, List.of(),
                List.of(new ExcludedRegion("[^/]*\\.ext")), 3, null);
        repositoryState = new HashMap<>();
        repositoryState.put(repository, new ArrayList<>());
        revisionState = new CvsRevisionState(repositoryState);


        log = new CustomLog("test", null);
        listener = new LogTaskListener(log, Level.FINE);

        customCvs = new CustomCvs(List.of(repository), false, false, false, false, false, false, false);
        customCvs.setRepositoryState(files);
        state = (CvsRevisionState) customCvs.compareRemoteRevisionWith(project, null, null, listener, revisionState, new CvsRepository[]{repository}).baseline;
        result = state.getModuleFiles().get(repository);
        assertEquals(3, result.size());

        listener.getLogger().flush();
        assertEquals("Skipping file 'test.ext' since it matches exclude pattern [^/]*\\.ext", log.getContents());

        repository = new CvsRepository("repo", false, null, List.of(),
                List.of(new ExcludedRegion("(?:[^/]+/)+[a-z0-9]+\\.ext")), 3, null);
        repositoryState = new HashMap<>();
        repositoryState.put(repository, new ArrayList<>());
        revisionState = new CvsRevisionState(repositoryState);


        log = new CustomLog("test", null);
        listener = new LogTaskListener(log, Level.FINE);

        customCvs = new CustomCvs(List.of(repository), false, false, false, false, false, false, false);
        customCvs.setRepositoryState(files);
        state = (CvsRevisionState) customCvs.compareRemoteRevisionWith(project, null, null, listener, revisionState, new CvsRepository[]{repository}).baseline;
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
    }

    private static class CustomFreeStyleProject extends FreeStyleProject {

        private CustomFreestyleBuild lastBuild;

        public CustomFreeStyleProject(Jenkins parent, String name) {
            super(parent, name);
            try {
                lastBuild = new CustomFreestyleBuild(this);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public CustomFreestyleBuild getLastBuild() {
            return lastBuild;
        }

        public FreeStyleBuild getLastCompletedBuild() {
            return getLastBuild();
        }
    }

    private static class CustomFreestyleBuild extends FreeStyleBuild {

        private boolean isChangeLogComputed;

        public CustomFreestyleBuild(FreeStyleProject project) throws IOException {
            super(project);
        }

        public boolean hasChangeSetComputed() {
            return isChangeLogComputed;
        }

        public void setChangeSetComputed(boolean computed) {
            this.isChangeLogComputed = computed;
        }


    }

    private static class CustomCvs extends CVSSCM {

        private List<CvsFile> files;

        public CustomCvs(List<CvsRepository> repositories, boolean canUseUpdate, boolean legacy, boolean skipChangeLog, boolean pruneEmptyDirectories, boolean disableCvsQuiet, boolean cleanOnFailedUpdate, boolean forceCleanCopy) {
            super(repositories, canUseUpdate, legacy, skipChangeLog, pruneEmptyDirectories, disableCvsQuiet, cleanOnFailedUpdate, forceCleanCopy);
        }


        protected List<CvsFile> calculateRepositoryState(final Date startTime, final Date endTime,
                                                         final CvsRepository repository, final TaskListener listener,
                                                         final EnvVars envVars, FilePath workspace) {
            return files;
        }

        public void setRepositoryState(List<CvsFile> files) {
            this.files = files;
        }
    }

}
