package hudson.scm;

import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

public class VisibleJenkinsRule extends JenkinsRule {
    public FreeStyleProject createFreeStyleProject() throws IOException {
        return super.createFreeStyleProject();
    }

    public FreeStyleProject createFreeStyleProject(String name) throws IOException {
        return super.createFreeStyleProject(name);
    }
}