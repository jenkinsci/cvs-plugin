package hudson.scm;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.LogTaskListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

public class IntegrationTest {

    private static final Logger LOGGER = Logger.getLogger(IntegrationTest.class.getName());

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File repo;
    private ServerSocket sock;
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
        return ":pserver:" + sock.getInetAddress().getHostAddress() + ":" + sock.getLocalPort() + repo.getAbsolutePath().replace('\\', '/');
    }

    private ProcessBuilder command(String... args) {
        List<String> _args = new ArrayList<String>();
        _args.add("cvs");
        _args.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(_args);
        pb.environment().put("CVSROOT", cvsroot());
        return pb;
    }

    @Before public void initRepo() throws Exception {
        Assume.assumeTrue("CVS must be installed to run this test", new File("/usr/bin/cvs").canExecute());
        repo = tmp.newFolder();
        run(new ProcessBuilder("cvs", "-d", repo.getAbsolutePath(), "init"));
        // TODO is there a simpler way to ask pserver to run without trying to setuid?
        FileUtils.writeStringToFile(new File(repo, "CVSROOT/passwd"), System.getProperty("user.name") + ":\n");
        sock = new ServerSocket();
        sock.bind(new InetSocketAddress(0));
        LOGGER.log(Level.INFO, "listening at {0}", cvsroot());
        new Thread("listen") {
            private void copy(InputStream is, OutputStream os) throws IOException {
                int b;
                while ((b = is.read()) != -1) {
                    os.write(b);
                    os.flush();
                }
            }
            @Override public void run() {
                try {
                    while (true) {
                        final Socket s;
                        try {
                            s = sock.accept();
                        } catch (SocketException x) {
                            // Socket closed?
                            break;
                        }
                        LOGGER.info("accepted client connection");
                        ProcessBuilder pb = new ProcessBuilder("cvs", "-f", "--allow-root=" + repo, "pserver");
                        final Process server = pb.start();
                        new Thread("printing errors") {
                            @Override public void run() {
                                try {
                                    copy(server.getErrorStream(), new LogTaskListener(LOGGER, Level.INFO).getLogger());
                                } catch (IOException x) {
                                    LOGGER.log(Level.WARNING, "failed to copy errors", x);
                                }
                            }
                        }.start();
                        new Thread("sending output") {
                            @Override public void run() {
                                try {
                                    copy(server.getInputStream(), /*new TeeOutputStream(*/s.getOutputStream()/*, System.err)*/);
                                } catch (SocketException x) {
                                    // Broken pipe? Ignore.
                                } catch (IOException x) {
                                    LOGGER.log(Level.WARNING, "failed to copy output", x);
                                }
                            }
                        }.start();
                        new Thread("accepting input") {
                            @Override public void run() {
                                try {
                                    InputStream is = s.getInputStream();
                                    OutputStream os = server.getOutputStream();
                                    copy(is, /*new TeeOutputStream(*/os/*, System.err)*/);
                                } catch (IOException x) {
                                    LOGGER.log(Level.WARNING, "failed to copy input", x);
                                }
                            }
                        }.start();
                        new Thread("wait for exit") {
                            @Override public void run() {
                                try {
                                    int r = server.waitFor();
                                    if (r != 0) {
                                        LOGGER.log(Level.INFO, "server exited with status {0}", r);
                                    }
                                } catch (InterruptedException x) {
                                    LOGGER.log(Level.WARNING, "failed to failed for server to exit", x);
                                }
                            }
                        };
                    }
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, "failed to start server or open socket", x);
                }
            }
        }.start();
        work = tmp.newFolder();
        run(command("checkout", "-d", ".", ".").directory(work));
    }

    @After public void killServer() throws Exception {
        if (sock != null) {
            sock.close();
        }
        // TODO if necessary, kill all threads & destroy() any server processes still running
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
