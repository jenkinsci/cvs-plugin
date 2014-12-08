package hudson.scm.browsers;

import hudson.model.FreeStyleProject;
import hudson.scm.CVSChangeLogSet;
import hudson.scm.CVSSCM;
import hudson.scm.CvsModule;
import hudson.scm.CvsRepository;
import hudson.scm.CvsRepositoryItem;
import hudson.scm.CvsRepositoryLocation;
import hudson.scm.ExcludedRegion;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import org.jvnet.hudson.test.JenkinsRule;

public class CvsFacadeRepositoryBrowserTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private CvsFacadeRepositoryBrowser testCase;

    @Before
    public void setUp() {
        testCase = new CvsFacadeRepositoryBrowser();
    }

    @Test
    public void testResolveBrowser() throws MalformedURLException {
        ViewCVS browser = new ViewCVS(new URL("http://localhost/viewcvs/viewcvs.cgi?cvsroot=repo"));
        CvsRepository repository = new CvsRepository(":pserver:host:port/path/to/repo", false, null, new ArrayList<CvsRepositoryItem>(), new ArrayList<ExcludedRegion>(), -1, browser);

        CVSChangeLogSet.CVSChangeLog changelog = new CVSChangeLogSet.CVSChangeLog();
        changelog.setRepository(repository);
        assertSame(browser, testCase.resolveRepositoryBrowser(changelog));
    }

    @Test
     public void testResolveOldFile() throws MalformedURLException {
        CVSChangeLogSet.CVSChangeLog changelog = new CVSChangeLogSet.CVSChangeLog();
        assertNull(testCase.resolveRepositoryBrowser(changelog));
    }

    @Test
    public void testResolveParameterisedRepository() throws IOException {
        ViewCVS browser = new ViewCVS(new URL("http://localhost/viewcvs/viewcvs.cgi?cvsroot=repo"));
        FreeStyleProject p = jenkinsRule.createFreeStyleProject();
        p = jenkinsRule.createFreeStyleProject("useful project");
        p.setScm(new CVSSCM(Arrays.asList(new CvsRepository(":pserver:user@host:${otherPort}/path/to/repo", false, null, new ArrayList<CvsRepositoryItem>(), new ArrayList<ExcludedRegion>(), -1, browser)), false, false, false, false, false, false, false));


        CvsRepository repository = new CvsRepository(":pserver:host:${port}/path/to/repo", false, null, new ArrayList<CvsRepositoryItem>(), new ArrayList<ExcludedRegion>(), -1, null);


        CVSChangeLogSet.CVSChangeLog changelog = new CVSChangeLogSet.CVSChangeLog();
        changelog.setRepository(repository);

        //we don't expect a browser match, and don't expect an error
        assertNull(testCase.resolveRepositoryBrowser(changelog));
    }

    @Test
    public void testResolveBrowserFromAlternativeJob() throws IOException {
        // create a few projects with browsers
        for (int k = 0; k < 10; k++) {
            FreeStyleProject p = jenkinsRule.createFreeStyleProject("job " + k);
            List<CvsRepository> repositoryList = new ArrayList<CvsRepository>();
            for (int j = 0; j < 5; j++) {
                CvsModule[] modules = new CvsModule[10];
                for (int i = 0; i < modules.length; i++) {
                    modules[i] = new CvsModule("remote" + i, "remote" + i);
                }
                CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), modules);
                repositoryList.add(new CvsRepository(":pserver:user@host:" + j + "/path/to/repo", false, null, Arrays.asList(item), new ArrayList<ExcludedRegion>(), -1, new ViewCVS(new URL("http://host:" + j + "/viewcvs/viewcvs.cgi?cvsroot=root"))));
            }
            p.setScm(new CVSSCM(repositoryList, false, false, false, false, false, false, false));
        }

        FreeStyleProject p = jenkinsRule.createFreeStyleProject("null browser job");

        // now add a job with no browser
        List<CvsRepository> repositoryList = new ArrayList<CvsRepository>();
        for (int j = 0; j < 5; j++) {
            CvsModule[] modules = new CvsModule[10];
            for (int i = 0; i < modules.length; i++) {
                modules[i] = new CvsModule("remote" + i, "remote" + i);
            }
            CvsRepositoryItem item = new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), modules);
            repositoryList.add(new CvsRepository(":pserver:user@host:" + j + "/path/to/repo", false, null, Arrays.asList(item), new ArrayList<ExcludedRegion>(), -1, null));
        }
        p.setScm(new CVSSCM(repositoryList, false, false, false, false, false, false, false));

        // add a job with the browser we expect to get, and a cvs root that matches our test root
        ViewCVS browser = new ViewCVS(new URL("http://localhost/viewcvs/viewcvs.cgi?cvsroot=repo"));
        p = jenkinsRule.createFreeStyleProject("useful project");
        p.setScm(new CVSSCM(Arrays.asList(new CvsRepository(":pserver:host:10/path/to/repo", false, null, new ArrayList<CvsRepositoryItem>(), new ArrayList<ExcludedRegion>(), -1, browser)), false, false, false, false, false, false, false));


        CvsRepository repository = new CvsRepository(":pserver:host:10/path/to/repo", false, null, new ArrayList<CvsRepositoryItem>(), new ArrayList<ExcludedRegion>(), -1, null);

        CVSChangeLogSet.CVSChangeLog changelog = new CVSChangeLogSet.CVSChangeLog();
        changelog.setRepository(repository);
        assertSame(browser, testCase.resolveRepositoryBrowser(changelog));

        //cal again to check caching hasn't broken anything
        assertSame(browser, testCase.resolveRepositoryBrowser(changelog));
    }



}
