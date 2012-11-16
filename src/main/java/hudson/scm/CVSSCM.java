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
package hudson.scm;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.scm.cvstagging.LegacyTagAction;
import hudson.util.FormValidation;
import hudson.util.Secret;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.netbeans.lib.cvsclient.CVSRoot;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;

import static hudson.Util.fixEmptyAndTrim;
import static hudson.Util.fixNull;

/**
 * CVS.
 *
 * <p>
 * I couldn't call this class "CVS" because that would cause the view folder
 * name to collide with CVS control files.
 *
 * <p>
 * This object gets shipped to the remote machine to perform some of the work,
 * so it implements {@link Serializable}.
 *
 * @author Kohsuke Kawaguchi
 * @author Michael Clarke
 */
public class CVSSCM extends AbstractCvs implements Serializable {

    private static final long serialVersionUID = -2175193493227149541L;



    /**
     * True to avoid creating a sub-directory inside the workspace. (Works only
     * when there's just one repository containing one module.)
     */
    private final boolean flatten;

    private CVSRepositoryBrowser repositoryBrowser;

    private final CvsRepository[] repositories;

    private final boolean canUseUpdate;

    private final boolean skipChangeLog;

    private boolean pruneEmptyDirectories;

    private boolean disableCvsQuiet;

    private boolean cleanOnFailedUpdate;

    private boolean forceCleanCopy;

    // start legacy fields
    @Deprecated
    private transient String module;
    @Deprecated
    private transient String cvsroot;
    @Deprecated
    private transient String branch;
    @Deprecated
    private transient boolean useHeadIfNotFound;
    @Deprecated
    private transient boolean isTag;
    @Deprecated
    private transient String excludedRegions;
    @Deprecated
    private transient String cvsRsh;

    // end legacy fields

    /**
     * @deprecated we now use multiple repositories and don't use cvsRsh
     */
    @Deprecated
    public CVSSCM(final String cvsRoot, final String allModules, final String branch, final String cvsRsh,
                  final boolean canUseUpdate, final boolean useHeadIfNotFound, final boolean legacy,
                  final boolean isTag, final String excludedRegions) {
        this(LegacyConvertor.getInstance().convertLegacyConfigToRepositoryStructure(cvsRoot, allModules, branch, isTag, excludedRegions,
                useHeadIfNotFound), canUseUpdate, legacy, null, Boolean.getBoolean(CVSSCM.class.getName() + ".skipChangeLog"), true, false, false, true);
    }

    @DataBoundConstructor
    public CVSSCM(final List<CvsRepository> repositories, final boolean canUseUpdate, final boolean legacy,
                  final CVSRepositoryBrowser browser, final boolean skipChangeLog, final boolean pruneEmptyDirectories,
                  final boolean disableCvsQuiet, final boolean cleanOnFailedUpdate, final boolean forceCleanCopy) {
        this.repositories = repositories.toArray(new CvsRepository[repositories.size()]);
        this.canUseUpdate = canUseUpdate;
        this.skipChangeLog = skipChangeLog;
        flatten = !legacy && this.repositories.length == 1 && this.repositories[0].getRepositoryItems().length == 1 && this.repositories[0].getRepositoryItems()[0].getModules().length == 1;
        repositoryBrowser = browser;
        this.pruneEmptyDirectories = pruneEmptyDirectories;
        this.disableCvsQuiet = disableCvsQuiet;
        this.cleanOnFailedUpdate = cleanOnFailedUpdate;
        this.forceCleanCopy = forceCleanCopy;
    }


    /**
     * Convert legacy configuration into the new class structure.
     *
     * @return an instance of this class with all the new fields transferred
     *         from the old structure to the new one
     */
    public final Object readResolve() {
        /*
         * check if we're running a version of this class that uses multiple
         * repositories. if we are then we can return it as it is
         */
        if (null != repositories) {
            return this;
        }

        /*
         * if we've got to this point then we have to do some conversion. Do
         * this by using the deprecated constructor to upgrade the model through
         * constructor chaining
         */
        return new CVSSCM(cvsroot, module, branch, cvsRsh, isCanUseUpdate(), useHeadIfNotFound, isLegacy(), isTag,
                excludedRegions);
    }



    /**
     * Gets the repositories currently configured for this job.
     *
     * @return an array of {@link CvsRepository}
     */
    @Exported
    public CvsRepository[] getRepositories() {
        return repositories;
    }

    @Override
    public CVSRepositoryBrowser getBrowser() {
        return repositoryBrowser;
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        return false;
    }

    /**
     * Checks for differences between the current workspace and the remote
     * repository.
     *
     * @see {@link SCM#compareRemoteRevisionWith(AbstractProject, Launcher, FilePath, TaskListener, SCMRevisionState)}
     */
    @Override
    protected PollingResult compareRemoteRevisionWith(final AbstractProject<?, ?> project, final Launcher launcher,
                                                      final FilePath workspace, final TaskListener listener, final SCMRevisionState baseline)
            throws IOException, InterruptedException {

        return super.compareRemoteRevisionWith(project, launcher, listener, baseline, getRepositories());
    }









    /**
     * If there are multiple modules, return the module directory of the first
     * one.
     *
     * @param workspace
     */
    @Override
    public FilePath getModuleRoot(final FilePath workspace, @SuppressWarnings("rawtypes") final AbstractBuild build) {
        if (flatten) {
            return workspace;
        }
        return workspace.child(getRepositories()[0].getRepositoryItems()[0].getModules()[0].getCheckoutName());
    }

    @Override
    public FilePath[] getModuleRoots(final FilePath workspace, @SuppressWarnings("rawtypes") final AbstractBuild build) {
        if (!flatten) {
            List<FilePath> moduleRoots = new ArrayList<FilePath>();
            for (CvsRepository repository : getRepositories()) {
                for (CvsRepositoryItem item : repository.getRepositoryItems()) {
                    for (CvsModule module : item.getModules()) {
                        moduleRoots.add(workspace.child(module.getCheckoutName()));
                    }
                }
            }
            return moduleRoots.toArray(new FilePath[moduleRoots.size()]);
        }
        return new FilePath[] {getModuleRoot(workspace, build)};
    }

    @Exported
    public boolean isCanUseUpdate() {
        return canUseUpdate;
    }

    @Exported
    public boolean isSkipChangeLog() {
        return skipChangeLog;
    }

    @Exported
    public boolean isPruneEmptyDirectories() {
        return pruneEmptyDirectories;
    }

    @Exported
    public boolean isFlatten() {
        return flatten;
    }

    @Exported
    public boolean isDisableCvsQuiet() {
        return disableCvsQuiet;
    }

    @Exported
    public boolean isCleanOnFailedUpdate() {
        return cleanOnFailedUpdate;
    }

    @Exported
    public boolean isForceCleanCopy() {
        return forceCleanCopy;
    }

    public boolean isLegacy() {
        return !flatten;
    }

    @Override
    public void buildEnvVars(AbstractBuild<?,?> build, Map<String, String> env) {
        String branchName = getBranchName();

        if (branchName != null) {
            env.put("CVS_BRANCH", branchName);
        }
    }

    /**
     * Used to support legacy setup - if we have one repository with all modules on the
     * same tag/branch then this will return that tag/branch name, otherwise it will return null.
     * @return a name of a tag or branch, or null if we have more than one repository or modules on different branches
     */
    private String getBranchName() {

        if (getRepositories()[0].getRepositoryItems()[0].getLocation().getLocationType() == CvsRepositoryLocationType.HEAD) {
            return null;
        }

        String locationName = getRepositories()[0].getRepositoryItems()[0].getLocation().getLocationName();

        if (null == locationName) {
            return null;
        }

        for (CvsRepository repository : getRepositories()) {
            for (CvsRepositoryItem item : repository.getRepositoryItems()) {
                if (!locationName.equals(item.getLocation().getLocationName())) {
                    return null;
                }
            }
        }

        return locationName;
    }

    @Override
    public boolean checkout(final AbstractBuild<?, ?> build, final Launcher launcher, final FilePath workspace,
                            final BuildListener listener, final File changelogFile) throws IOException, InterruptedException {
        if (!canUseUpdate) {
            workspace.deleteContents();
        }

        final String dateStamp;

        synchronized (DATE_FORMATTER) {
            dateStamp = DATE_FORMATTER.format(new Date());
        }

        if (!checkout(repositories, flatten, workspace, canUseUpdate,
                build, dateStamp, pruneEmptyDirectories, cleanOnFailedUpdate, listener)) {
            return false;
        }

        postCheckout(build, changelogFile, getRepositories(), workspace, listener, isFlatten(), build.getEnvironment(listener));

        return true;
    }





    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends AbstractCvsDescriptor<CVSSCM> implements ModelObject {

        // start legacy fields
        private transient String cvsPassFile;
        @SuppressWarnings("unused")
        private transient String cvsExe;
        private transient boolean noCompression;

        private class RepositoryBrowser {
            @SuppressWarnings("unused")
            transient String diffURL;
            @SuppressWarnings("unused")
            transient String browseURL;
        }

        @SuppressWarnings("unused")
        private transient Map<String, RepositoryBrowser> browsers;
        // end legacy fields

        /**
         * CVS compression level if individual repositories don't specifically
         * set it.
         */
        private int compressionLevel = 3;

        private String privateKeyLocation = System.getProperty("user.home") + "/.ssh/id_rsa";
        private Secret privateKeyPassword = null;
        private String knownHostsLocation = System.getProperty("user.home") + "/.ssh/known_hosts";
        private CvsAuthentication[] authTokens = new CvsAuthentication[]{};
        // we don't provide a way for users to edit this, other than by manually editing their XML config
        private String changelogEncoding = "UTF-8";
        
        public DescriptorImpl() {
            super(CVSRepositoryBrowser.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return "CVS";
        }

        @Override
        public SCM newInstance(final StaplerRequest req, final JSONObject formData) throws FormException {
            CVSSCM scm = req.bindJSON(CVSSCM.class, formData);
            scm.repositoryBrowser = RepositoryBrowsers.createInstance(CVSRepositoryBrowser.class, req, formData,
                    "browser");
            return scm;
        }

        @Exported
        public String getPrivateKeyLocation() {
            return privateKeyLocation;
        }

        @Exported
        public Secret getPrivateKeyPassword() {
            return privateKeyPassword;
        }

        @Exported
        public String getKnownHostsLocation() {
            return knownHostsLocation;
        }

        @Exported
        public int getCompressionLevel() {
            return compressionLevel;
        }

        @Exported
        public CvsAuthentication[] getAuthentication() {
            return authTokens;
        }
        
        @Override
        @Exported
        public String getChangelogEncoding() {
            return changelogEncoding;
        }

        @Override
        public void load() {
            super.load();
            // used to move to the new data structure
            if (noCompression) {
                compressionLevel = 0;
            }
        }

        @Override
        public boolean configure(final StaplerRequest req, final JSONObject o) {
            String compressionLevel = fixEmptyAndTrim(o.getString("cvsCompression"));

            try {
                this.compressionLevel = Integer.parseInt(compressionLevel);
            } catch (final NumberFormatException ex) {
                this.compressionLevel = 0;
            }

            final String knownHostsLocation = fixEmptyAndTrim(o.getString("knownHostsLocation"));

            if (knownHostsLocation == null) {
                this.knownHostsLocation = System.getProperty("user.home") + "/.ssh/known_hosts";
            } else {
                this.knownHostsLocation = knownHostsLocation;
            }

            final String privateKeyLocation = fixEmptyAndTrim(o.getString("privateKeyLocation"));

            if (privateKeyLocation == null) {
                this.privateKeyLocation = System.getProperty("user.home") + "/.ssh/id_rsa";
            } else {
                this.privateKeyLocation = privateKeyLocation;
            }

            privateKeyPassword = Secret.fromString(fixEmptyAndTrim(o.getString("privateKeyPassword")));

            List<CvsAuthentication> authTokens = req.bindParametersToList(CvsAuthentication.class, "cvsAuthentication.");
            this.authTokens = authTokens.toArray(new CvsAuthentication[authTokens.size()]);
            save();

            return true;
        }

        @Override
        public boolean isBrowserReusable(final CVSSCM x, final CVSSCM y) {
            return false;
        }

        /**
         * Returns all {@code CVSROOT} strings used in the current Jenkins
         * installation.
         */
        public Set<String> getAllCvsRoots() {
            Set<String> r = new TreeSet<String>();
            for (AbstractProject<?, ?> p : Hudson.getInstance().getAllItems(AbstractProject.class)) {
                SCM scm = p.getScm();
                if (scm instanceof CVSSCM) {
                    CVSSCM cvsscm = (CVSSCM) scm;
                    for (CvsRepository repository : cvsscm.getRepositories()) {
                        r.add(repository.getCvsRoot());
                    }
                }
            }

            return r;
        }

        @Deprecated
        public String getCvsPassFile() {
            return cvsPassFile;
        }

        public FormValidation doCheckAuthenticationCvsRoot(@QueryParameter final String value) {
            try {
                CVSRoot cvsRoot = CVSRoot.parse(value);

                if (cvsRoot.getUserName() != null) {
                    return FormValidation.error("Do not specify a username in the CVS root; use the username field.");
                }

                if (cvsRoot.getPassword() != null) {
                    return FormValidation.error("Do not specify a password in the CVS root; use the password field.");
                }

                if (cvsRoot.getMethod().equals(CVSRoot.METHOD_FORK)
                        || cvsRoot.getMethod().equals(CVSRoot.METHOD_LOCAL)) {
                    return FormValidation.error(cvsRoot.getMethod() +
                            " does not support authentication so should not be specified");
                }

                return FormValidation.ok();
            } catch(IllegalArgumentException ex) {
                return FormValidation.error(value +
                        " does not seem to be a valid CVS root so would not match any repositories.");
            }

        }

        //
        // web methods
        //


    }

    /**
     * Action for a build that performs the tagging.
     *
     * @deprecated we now use CvsTagAction but have to keep this class around
     *             for old builds that have a serialized version of this class
     *             and use the old archive method of tagging a build
     */
    @Deprecated
    public final class TagAction extends AbstractScmTagAction implements Describable<TagAction> {

        private String tagName;

        public TagAction(final AbstractBuild<?, ?> build) {
            super(build);
        }

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public Descriptor<TagAction> getDescriptor() {
            return null;
        }

        @Override
        public boolean isTagged() {
            return false;
        }

        /**
         * Convert the old TagAction structure into the new (legacy) structure
         *
         * @return an instance of LegacyTagAction
         * @throws NoSuchFieldException
         * @throws IllegalAccessException
         * @throws SecurityException
         * @throws IllegalArgumentException
         */
        public Object readResolve() throws IllegalArgumentException, SecurityException, IllegalAccessException, NoSuchFieldException {
            LegacyTagAction legacyTagAction = new LegacyTagAction(super.build, CVSSCM.this);
            Field tagNameField = legacyTagAction.getClass().getDeclaredField("tagName");
            tagNameField.setAccessible(true);
            tagNameField.set(legacyTagAction, tagName);
            return legacyTagAction;
        }

    }

}