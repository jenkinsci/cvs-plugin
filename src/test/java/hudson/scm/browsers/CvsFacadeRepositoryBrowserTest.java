package hudson.scm.browsers;

import hudson.model.FreeStyleProject;
import hudson.scm.CVSChangeLogSet;
import hudson.scm.CVSSCM;
import hudson.scm.CvsModule;
import hudson.scm.CvsRepository;
import hudson.scm.CvsRepositoryItem;
import hudson.scm.CvsRepositoryLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@WithJenkins
class CvsFacadeRepositoryBrowserTest {

    private JenkinsRule jenkinsRule;

    private CvsFacadeRepositoryBrowser testCase;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jenkinsRule = rule;
        testCase = new CvsFacadeRepositoryBrowser();
    }

    @Test
    void testResolveBrowser() throws Exception {
        ViewCVS browser = new ViewCVS(new URL("http://localhost/viewcvs/viewcvs.cgi?cvsroot=repo"));
        CvsRepository repository = new CvsRepository(":pserver:host:port/path/to/repo", false, null, new ArrayList<>(), new ArrayList<>(), -1, browser);

        CVSChangeLogSet.CVSChangeLog changelog = new CVSChangeLogSet.CVSChangeLog();
        changelog.setRepository(repository);
        assertSame(browser, testCase.resolveRepositoryBrowser(changelog));
    }

    @Test
    void testResolveOldFile() {
        CVSChangeLogSet.CVSChangeLog changelog = new CVSChangeLogSet.CVSChangeLog();
        assertNull(testCase.resolveRepositoryBrowser(changelog));
    }

    @Test
    void testResolveParameterisedRepository() throws Exception {
        ViewCVS browser = new ViewCVS(new URL("http://localhost/viewcvs/viewcvs.cgi?cvsroot=repo"));
        FreeStyleProject p = jenkinsRule.createFreeStyleProject();
        p = jenkinsRule.createFreeStyleProject("useful project");
        p.setScm(new CVSSCM(List.of(new CvsRepository(":pserver:user@host:${otherPort}/path/to/repo", false, null, new ArrayList<>(), new ArrayList<>(), -1, browser)), false, false, false, false, false, false, false));


        CvsRepository repository = new CvsRepository(":pserver:host:${port}/path/to/repo", false, null, new ArrayList<>(), new ArrayList<>(), -1, null);


        CVSChangeLogSet.CVSChangeLog changelog = new CVSChangeLogSet.CVSChangeLog();
        changelog.setRepository(repository);

        //we don't expect a browser match, and don't expect an error
        assertNull(testCase.resolveRepositoryBrowser(changelog));
    }

    @Test
    void testResolveBrowserFromAlternativeJob() throws Exception {
        // create a few projects with browsers
        for (int k = 0; k < 10; k++) {
            FreeStyleProject p = jenkinsRule.createFreeStyleProject("job " + k);
            List<CvsRepository> repositoryList = new ArrayList<>();
            for (int j = 0; j < 5; j++) {
                CvsModule[] modules = new CvsModule[10];
                for (int i = 0; i < modules.length; i++) {
                    modules[i] = new CvsModule("remote" + i, "remote" + i);
                }
                CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), modules);
                repositoryList.add(new CvsRepository(":pserver:user@host:" + j + "/path/to/repo", false, null, List.of(item), new ArrayList<>(), -1, new ViewCVS(new URL("http://host:" + j + "/viewcvs/viewcvs.cgi?cvsroot=root"))));
            }
            p.setScm(new CVSSCM(repositoryList, false, false, false, false, false, false, false));
        }

        FreeStyleProject p = jenkinsRule.createFreeStyleProject("null browser job");

        // now add a job with no browser
        List<CvsRepository> repositoryList = new ArrayList<>();
        for (int j = 0; j < 5; j++) {
            CvsModule[] modules = new CvsModule[10];
            for (int i = 0; i < modules.length; i++) {
                modules[i] = new CvsModule("remote" + i, "remote" + i);
            }
            CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), modules);
            repositoryList.add(new CvsRepository(":pserver:user@host:" + j + "/path/to/repo", false, null, List.of(item), new ArrayList<>(), -1, null));
        }
        p.setScm(new CVSSCM(repositoryList, false, false, false, false, false, false, false));

        // add a job with the browser we expect to get, and a cvs root that matches our test root
        ViewCVS browser = new ViewCVS(new URL("http://localhost/viewcvs/viewcvs.cgi?cvsroot=repo"));
        p = jenkinsRule.createFreeStyleProject("useful project");
        p.setScm(new CVSSCM(List.of(new CvsRepository(":pserver:host:10/path/to/repo", false, null, new ArrayList<>(), new ArrayList<>(), -1, browser)), false, false, false, false, false, false, false));


        CvsRepository repository = new CvsRepository(":pserver:host:10/path/to/repo", false, null, new ArrayList<>(), new ArrayList<>(), -1, null);

        CVSChangeLogSet.CVSChangeLog changelog = new CVSChangeLogSet.CVSChangeLog();
        changelog.setRepository(repository);
        assertSame(browser, testCase.resolveRepositoryBrowser(changelog));

        //cal again to check caching hasn't broken anything
        assertSame(browser, testCase.resolveRepositoryBrowser(changelog));
    }

}
