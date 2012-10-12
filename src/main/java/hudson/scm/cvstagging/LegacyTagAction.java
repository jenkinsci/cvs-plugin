/*
 * The MIT License
 * 
 * Copyright (c) 2004-2012, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jene Jasper, Stephen Connolly, CloudBees, Inc., Michael Clarke
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.scm.cvstagging;

import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.scm.*;
import hudson.scm.cvs.Messages;
import hudson.security.Permission;
import hudson.util.FormValidation;
import org.apache.tools.ant.taskdefs.Expand;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.tag.TagCommand;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static hudson.Util.fixNull;

/**
 * Performs tagging on legacy CVS workspaces using a ZIP file of CVS Control
 * files and the CVS <tt>tag</tt> command. This code in this class has been
 * split off from the original implementation in the {@link CVSSCM} class.
 * 
 * @author Michael Clarke
 * 
 */
public class LegacyTagAction extends AbstractScmTagAction implements
                Describable<LegacyTagAction> {

    /**
     * If non-null, that means the build is already tagged. If multiple tags are
     * created, those are whitespace-separated.
     */
    private volatile String tagName;
    private CVSSCM parent;

    public LegacyTagAction(final AbstractBuild<?, ?> build,
                    final CVSSCM parent) {
        super(build);
        this.parent = parent;
    }

    @Override
    public String getIconFileName() {
        if (tagName == null
                        && !build.getParent().getACL().hasPermission(SCM.TAG)) {
            return null;
        }
        return "save.gif";
    }

    @Override
    public String getDisplayName() {
        if (tagName == null) {
            return Messages.CVSSCM_TagThisBuild();
        }
        if (tagName.indexOf(' ') >= 0) {
            return Messages.CVSSCM_DisplayName2();
        } else {
            return Messages.CVSSCM_DisplayName1();
        }
    }

    @Exported
    public String[] getTagNames() {
        if (tagName == null) {
            return new String[0];
        }
        return tagName.split(" ");
    }

    /**
     * Checks if the value is a valid CVS tag name.
     */
    public synchronized FormValidation doCheckTag(
                    @QueryParameter final String value) {
        String tag = fixNull(value).trim();
        if (tag.length() == 0) {
            return FormValidation.ok();
        }
        return FormValidation.error(isInvalidTag(tag));
    }

    @Override
    public Permission getPermission() {
        return SCM.TAG;
    }

    @Override
    public String getTooltip() {
        if (tagName != null) {
            return "Tag: " + tagName;
        } else {
            return null;
        }
    }

    @Override
    public boolean isTagged() {
        return tagName != null;
    }

    /**
     * Invoked to actually tag the workspace.
     */
    @SuppressWarnings("unchecked")
    public synchronized void doSubmit(final StaplerRequest req,
                    final StaplerResponse rsp) throws IOException,
                    ServletException {
        build.checkPermission(getPermission());

        Map<AbstractBuild<?, ?>, String> tagSet = new HashMap<AbstractBuild<?, ?>, String>();

        String name = fixNull(req.getParameter("name")).trim();
        String reason = isInvalidTag(name);
        if (reason != null) {
            sendError(reason, req, rsp);
            return;
        }

        tagSet.put(build, name);

        if (req.getParameter("upstream") != null) {
            // tag all upstream builds
            Enumeration<String> e = req.getParameterNames();
            Map<AbstractProject<?, ?>, Integer> upstreams = build
                            .getTransitiveUpstreamBuilds();

            while (e.hasMoreElements()) {
                String upName = e.nextElement();
                if (!upName.startsWith("upstream.")) {
                    continue;
                }

                String tag = fixNull(req.getParameter(upName)).trim();
                reason = isInvalidTag(tag);
                if (reason != null) {
                    sendError(Messages.CVSSCM_NoValidTagNameGivenFor(upName,
                                    reason), req, rsp);
                    return;
                }

                upName = upName.substring(9); // trim off 'upstream.'

                // Note the form submission uses the full name, so getItemByFullName is valid
                AbstractProject<?, ?> p = Hudson.getInstance()
                                .getItemByFullName(upName,
                                                AbstractProject.class);
                if (p == null) {
                    sendError(Messages.CVSSCM_NoSuchJobExists(upName), req, rsp);
                    return;
                }

                Integer buildNum = upstreams.get(p);
                if (buildNum == null) {
                    sendError(Messages.CVSSCM_NoUpstreamBuildFound(upName),
                                    req, rsp);
                    return;
                }

                Run<?, ?> build = p.getBuildByNumber(buildNum);
                tagSet.put((AbstractBuild<?, ?>) build, tag);
            }
        }

        new LegacyTagWorkerThread(this, tagSet).start();

        doIndex(req, rsp);
    }

    /**
     * Checks if the given value is a valid CVS tag.
     * 
     * If it's invalid, this method gives you the reason as string.
     */
    private String isInvalidTag(final String name) {
        // source code from CVS rcs.c
        // void
        // RCS_check_tag (tag)
        // const char *tag;
        // {
        // char *invalid = "$,.:;@"; /* invalid RCS tag characters */
        // const char *cp;
        //
        // /*
        // * The first character must be an alphabetic letter. The remaining
        // * characters cannot be non-visible graphic characters, and must not
        // be
        // * in the set of "invalid" RCS identifier characters.
        // */
        // if (isalpha ((unsigned char) *tag))
        // {
        // for (cp = tag; *cp; cp++)
        // {
        // if (!isgraph ((unsigned char) *cp))
        // error (1, 0, "tag `%s' has non-visible graphic characters",
        // tag);
        // if (strchr (invalid, *cp))
        // error (1, 0, "tag `%s' must not contain the characters `%s'",
        // tag, invalid);
        // }
        // }
        // else
        // error (1, 0, "tag `%s' must start with a letter", tag);
        // }
        if (name == null || name.length() == 0) {
            return Messages.CVSSCM_TagNameInvalid(Messages.CVSSCM_Tag());
        }

        char ch = name.charAt(0);
        if (!(('A' <= ch && ch <= 'Z') || ('a' <= ch && ch <= 'z'))) {
            return Messages.CVSSCM_TagNameInvalid(Messages.CVSSCM_Tag());
        }

        for (char invalid : "$,.:;@".toCharArray()) {
            if (name.indexOf(invalid) >= 0) {
                return Messages.CVSSCM_TagNameInvalid(Messages.CVSSCM_Tag());
            }
        }

        return null;
    }

    /**
     * Performs tagging.
     */
    public void perform(final String tagName, final boolean moveTag, final TaskListener listener) {
        File destdir = null;
        try {
            destdir = Util.createTempDir();

            // unzip the archive
            listener.getLogger().println(
                            Messages.CVSSCM_ExpandingWorkspaceArchive(destdir));
            Expand e = new Expand();
            e.setProject(new org.apache.tools.ant.Project());
            e.setDest(destdir);
            e.setSrc(getArchiveFile(build));
            e.setTaskType("unzip");
            e.execute();

            // run cvs tag command
            listener.getLogger().println(Messages.CVSSCM_TaggingWorkspace());
            for (CvsRepository repository : parent.getRepositories()) {
                for (CvsRepositoryItem item : repository.getRepositoryItems()) {
                    for (CvsModule module : item.getModules()) {

                        final Client cvsClient = parent.getCvsClient(repository, build.getEnvironment(listener), listener);
                        final GlobalOptions globalOptions = parent.getGlobalOptions(repository, build.getEnvironment(listener));

                        File path = new File(destdir, module.getCheckoutName());
                        boolean isDir = path.isDirectory();

                        TagCommand tagCommand = new TagCommand();

                        if (isDir) {
                            tagCommand.setRecursive(true);
                        }
                        tagCommand.setTag(tagName);
                        tagCommand.setOverrideExistingTag(moveTag);

                        if (!isDir) {
                            path = path.getParentFile();
                        }
                        cvsClient.setLocalPath(path.getAbsolutePath());
                        if (!cvsClient.executeCommand(tagCommand, globalOptions)) {
                            listener.getLogger().print(
                                            Messages.CVSSCM_TaggingFailed());
                            try {
                                cvsClient.getConnection().close();
                            } catch(IOException ex) {
                                listener.getLogger().println("Could not close client connection: " + ex.getMessage());
                            }

                            return;
                        }

                        try {
                            cvsClient.getConnection().close();
                        } catch(IOException ex) {
                            listener.getLogger().println("Could not close client connection: " + ex.getMessage());
                        }


                    }
                }
            }

            // completed successfully
            onTagCompleted(tagName);
            build.save();
        } catch (Throwable e) {
            e.printStackTrace(listener.fatalError(e.getMessage()));
        } finally {
            try {
                if (destdir != null) {
                    listener.getLogger().println("cleaning up " + destdir);
                    Util.deleteRecursive(destdir);
                }
            } catch (IOException e) {
                e.printStackTrace(listener.fatalError(e.getMessage()));
            }
        }
    }

    /**
     * Atomically set the tag name.
     */
    private synchronized void onTagCompleted(final String tagName) {
        if (this.tagName != null) {
            this.tagName += ' ' + tagName;
        } else {
            this.tagName = tagName;
        }
        workerThread = null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Descriptor<LegacyTagAction> getDescriptor() {
        return Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    public static final class LegacyTagWorkerThread extends TaskThread {
        private final Map<AbstractBuild<?, ?>, String> tagSet;

        @SuppressWarnings("deprecation") // use a deprecated method, so we can support as many versions of Jenkins as possible
        public LegacyTagWorkerThread(final LegacyTagAction owner,
                        final Map<AbstractBuild<?, ?>, String> tagSet) {
            super(owner, ListenerAndText.forMemory());
            this.tagSet = tagSet;
        }

        @Override
        public synchronized void start() {
            for (Entry<AbstractBuild<?, ?>, String> e : tagSet.entrySet()) {
                LegacyTagAction ta = e.getKey()
                                .getAction(LegacyTagAction.class);
                if (ta != null) {
                    associateWith(ta);
                }
            }

            super.start();
        }

        @Override
        protected void perform(final TaskListener listener) {
            for (Entry<AbstractBuild<?, ?>, String> e : tagSet.entrySet()) {
                LegacyTagAction ta = e.getKey()
                                .getAction(LegacyTagAction.class);
                if (ta == null) {
                    listener.error(e.getKey()
                                    + " doesn't have CVS tag associated with it. Skipping");
                    continue;
                }
                listener.getLogger().println(
                                Messages.CVSSCM_TagginXasY(e.getKey(),
                                                e.getValue()));
                // fixes JENKINS-8128
                if (e.getKey().hasPermission(Permission.UPDATE)) {
                     try {
                         e.getKey().keepLog();
                     } catch (IOException x) {
                         x.printStackTrace(listener.error(Messages.CVSSCM_FailedToMarkForKeep(e.getKey())));
                     }
                }
                ta.perform(e.getValue(), false, listener);
                listener.getLogger().println();
            }
        }
    }

    /**
     * Returns the file name used to archive the build.
     */
    private static File getArchiveFile(final AbstractBuild<?, ?> build) {
        return new File(build.getRootDir(), "workspace.zip");
    }

    @Extension
    public static final class LegacyTagActionDescriptor extends
                    Descriptor<LegacyTagAction> {
        public LegacyTagActionDescriptor() {
            super(LegacyTagAction.class);
        }

        @Override
        public String getDisplayName() {
            return "";
        }
    }

}
