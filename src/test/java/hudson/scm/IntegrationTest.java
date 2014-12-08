package hudson.scm;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.internal.AssumptionViolatedException;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

public class IntegrationTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private String container;
    private File work;

    private static ProcessBuilder inheritIO(ProcessBuilder pb) throws Exception {
        try {
            ProcessBuilder.class.getMethod("inheritIO").invoke(pb);
        } catch (NoSuchMethodException x) {
            // Java 6, ignore
        }
        return pb;
    }

    private static void run(ProcessBuilder pb) throws Exception {
        int r = inheritIO(pb).start().waitFor();
        if (r != 0) {
            throw new IOException(pb.command() + " failed: " + r);
        }
    }

    private String cvsroot() {
        return ":pserver:nobody@localhost:/var/lib/cvs";
    }

    private ProcessBuilder command(String... args) {
        List<String> _args = new ArrayList<String>();
        _args.add("cvs");
        _args.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(_args);
        pb.environment().put("CVSROOT", cvsroot());
        return pb;
    }

    @SuppressWarnings("SleepWhileInLoop")
    @Before public void initRepo() throws Exception {
        try {
            Process server = new ProcessBuilder("docker", "run", "-d", "-p", "2401:2401", "jglick/cvs-demo").start();
            container = new BufferedReader(new InputStreamReader(server.getInputStream())).readLine();
            for (int i = 0; i < 1000; i++) {
                try {
                    run(command("rlog"));
                    System.err.println("CVS server started: " + container);
                    break;
                } catch (IOException x) {
                    System.err.println("so far failed to connect to CVS server: " + x);
                    Thread.sleep(250);
                }
            }
            work = tmp.newFolder();
            run(command("checkout", "-d", ".", ".").directory(work));
        } catch (IOException x) {
            throw new AssumptionViolatedException("could not initialize", x);
        }
    }

    @After public void killServer() throws Exception {
        if (container != null) {
            new ProcessBuilder("docker", "kill", container).start();
        }
    }

    @Test public void basics() throws Exception {
        File project = new File(work, "project");
        assertTrue(project.mkdir());
        run(command("add", "project").directory(work));
        FileUtils.touch(new File(project, "f1"));
        run(command("add", "f1").directory(project));
        run(command("commit", "-m", "start").directory(project));
        FreeStyleProject p = r.createFreeStyleProject();
        // TODO @DataBoundSetter would be really welcome here!
        p.setScm(new CVSSCM(Collections.singletonList(new CvsRepository(cvsroot(),false, null, Collections.singletonList(new CvsRepositoryItem(new CvsRepositoryLocation.HeadRepositoryLocation(), new CvsModule[] {new CvsModule("project", "project", null)})), Collections.<ExcludedRegion>emptyList(), 3, null)), true, false, false, true, false, true, false, false));
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        assertTrue(JenkinsRule.getLog(b1) + b1.getWorkspace().child("project").list(), b1.getWorkspace().child("project/f1").exists());
        FileUtils.touch(new File(project, "f2"));
        run(command("add", "f2").directory(project));
        run(command("commit", "-m", "more").directory(project));
        FreeStyleBuild b2 = r.buildAndAssertSuccess(p);
        assertTrue(JenkinsRule.getLog(b2) + b2.getWorkspace().child("project").list(), b2.getWorkspace().child("project/f2").exists());
        // TODO check changelog
    }

}
