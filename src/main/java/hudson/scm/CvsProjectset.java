/*
 * The MIT License
 *
 * Copyright (c) 2012, Michael Clarke
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
import hudson.Util;
import hudson.model.*;
import hudson.scm.cvs.Messages;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class CvsProjectset extends AbstractCvs {

    private static final Pattern PSF_PATTERN = Pattern.compile("<project reference=\"[^,]+,((:[a-z]+:)([a-z|A-Z|0-9|\\.]+)" +
            "(:([0-9]+)?)?([/|a-z|A-Z|_|0-9]+)),([/|A-Z|a-z|0-9|_|\\.|\\-]+),([A-Z|a-z|0-9|_|\\.|\\-]+)(,(.*?)){0,1}\"/>");

    private final CvsRepository[] repositories;
    private final boolean canUseUpdate;
    private final String username;
    private final Secret password;
    private final CVSRepositoryBrowser browser;
    private final boolean skipChangeLog;
    private final boolean pruneEmptyDirectories;
    private final boolean disableCvsQuiet;
    private final boolean cleanOnFailedUpdate;

    @DataBoundConstructor
    public CvsProjectset(final CvsRepository[] repositories, final boolean canUseUpdate, final String username, final String password,
                         final CVSRepositoryBrowser browser, final boolean skipChangeLog, final boolean pruneEmptyDirectories,
                         final boolean disableCvsQuiet, final boolean cleanOnFailedUpdate) {
        this.repositories = repositories;
        this.username = Util.fixEmpty(username);
        this.password = Secret.fromString(password);
        this.canUseUpdate = canUseUpdate;
        this.browser = browser;
        this.skipChangeLog = skipChangeLog;
        this.pruneEmptyDirectories = pruneEmptyDirectories;
        this.disableCvsQuiet = disableCvsQuiet;
        this.cleanOnFailedUpdate = cleanOnFailedUpdate;
    }

    @Override
    @Exported
    public CvsRepository[] getRepositories() {
        return repositories;
    }

    @Exported
    public String getUsername() {
        return username;
    }

    @Exported
    public Secret getPassword() {
        return password;
    }

    @Override
    @Exported
    public boolean isCanUseUpdate() {
        return canUseUpdate;
    }

    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?, ?> project, Launcher launcher,
                                                      FilePath workspace, TaskListener listener,
                                                      SCMRevisionState baseline)
            throws IOException, InterruptedException {
        return super.compareRemoteRevisionWith(project, launcher,
                listener, baseline, getAllRepositories(workspace));
    }

    @Override
    public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher, FilePath workspace, BuildListener listener,
                            File changelogFile) throws IOException, InterruptedException {
        if (!isCanUseUpdate()) {
            workspace.deleteContents();
        }

        final String dateStamp;

        synchronized (DATE_FORMATTER) {
            dateStamp = DATE_FORMATTER.format(new Date());
        }

        if (!checkout(getRepositories(), false, workspace, isCanUseUpdate(),
                build, dateStamp, isPruneEmptyDirectories(), isCleanOnFailedUpdate(), listener)) {
            return false;
        }

        if (!checkout(getInnerRepositories(workspace), false, workspace, isCanUseUpdate(),
                build, dateStamp, isPruneEmptyDirectories(), isCleanOnFailedUpdate(), listener)) {
            return false;
        }

        postCheckout(build, changelogFile, getAllRepositories(workspace), workspace, listener, isFlatten(), build.getEnvironment(listener));

        return true;
    }


    private CvsRepository[] getInnerRepositories(FilePath workspace) throws IOException, InterruptedException {
        List<CvsRepository> psfList = new ArrayList<CvsRepository>();
        for (CvsRepository repository : getRepositories()) {
            for (CvsRepositoryItem item : repository.getRepositoryItems()) {
                for (CvsModule module : item.getModules()) {
                    FilePath projectsetFile = workspace.child(module.getCheckoutName())
                            .child(module.getProjectsetFileName());
                    if (!projectsetFile.exists()) {
                        throw new Error(Messages.CVSSCM_InvalidProjectset(module.getProjectsetFileName(),
                                module.getRemoteName()));
                    }
                    String psfContents = projectsetFile.readToString();

                    for (Matcher matcher = PSF_PATTERN.matcher(psfContents); matcher.find();) {
                        CvsModule innerModule = new CvsModule(matcher.group(7), matcher.group(8));
                        CvsRepositoryLocation innerLocation;
                        if (matcher.group(10) == null) {
                            innerLocation = new CvsRepositoryLocation.HeadRepositoryLocation();
                        }
                        else {
                            innerLocation = new CvsRepositoryLocation.BranchRepositoryLocation(matcher.group(10), false);
                        }
                        CvsRepositoryItem innerItem = new CvsRepositoryItem(innerLocation,
                                new CvsModule[]{innerModule});

                        CvsAuthentication authentication = getAuthenticationForCvsRoot(matcher.group(1));

                        StringBuilder root = new StringBuilder();
                        root.append(matcher.group(2));

                        String password = null;
                        if (authentication == null) {
                            if (username != null) {
                                root.append(getUsername());
                                root.append("@");
                            }
                            
                            Secret secret = getPassword();
                            
                            if (null != secret) {
                                password = secret.getPlainText();
                            }
                        }
                        // we don't actually do anything with the authentication details if they're available just now
                        // as they're automatically configured in a later call

                        root.append(matcher.group(3));
                        if (matcher.group(5) != null) {
                            root.append(":").append(matcher.group(5));
                        }
                        root.append(matcher.group(6));

                        CvsRepository innerRepository = new CvsRepository(root.toString(), password != null, password,
                                Arrays.asList(innerItem), new ArrayList<ExcludedRegion>(), 0);

                        psfList.add(innerRepository);
                    }
                }
            }
        }
        return psfList.toArray(new CvsRepository[psfList.size()]);
    }

    private CvsAuthentication getAuthenticationForCvsRoot(final String cvsRoot) {
        for(CvsAuthentication authentication : getDescriptor().getAuthentication()) {
            if (authentication.getCvsRoot().equals(cvsRoot)) {
                return authentication;
            }
        }

        return null;
    }

    private CvsRepository[] getAllRepositories(FilePath workspace) throws IOException, InterruptedException {
        List<CvsRepository> returnList = new ArrayList<CvsRepository>();
        returnList.addAll(Arrays.asList(getRepositories()));
        returnList.addAll(Arrays.asList(getInnerRepositories(workspace)));

        return returnList.toArray(new CvsRepository[returnList.size()]);
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        //we need to have the project-set files checked out so we can discover the rest of the projects
        return true;
    }

    @Override
    @Exported
    public boolean isPruneEmptyDirectories() {
        return pruneEmptyDirectories;
    }

    @Override
    @Exported
    public boolean isCleanOnFailedUpdate() {
        return cleanOnFailedUpdate;
    }

    @Override
    @Exported
    public boolean isDisableCvsQuiet() {
        return disableCvsQuiet;
    }

    @Override
    @Exported
    public boolean isSkipChangeLog() {
        return skipChangeLog;
    }

    @Override
    public boolean isFlatten() {
        return false;
    }

    @Override
    public RepositoryBrowser getBrowser() {
        return browser;
    }

    @Override
    public CvsProjectsetDescriptor getDescriptor() {
        return (CvsProjectsetDescriptor) super.getDescriptor();
    }

    @Extension
    public static class CvsProjectsetDescriptor extends AbstractCvsDescriptor<CvsProjectset> {

        public CvsProjectsetDescriptor() {
            super(CVSRepositoryBrowser.class);
        }

        @Override
        public String getDisplayName() {
            return "CVS Projectset";
        }

        private CVSSCM.DescriptorImpl getCvsDescriptor() {
            return (CVSSCM.DescriptorImpl) Hudson.getInstance().getDescriptor(CVSSCM.class);
        }

        @Override
        public String getKnownHostsLocation() {
            return getCvsDescriptor().getKnownHostsLocation();
        }

        @Override
        public String getPrivateKeyLocation() {
            return getCvsDescriptor().getPrivateKeyLocation();
        }

        @Override
        public Secret getPrivateKeyPassword() {
            return getCvsDescriptor().getPrivateKeyPassword();
        }

        @Override
        public int getCompressionLevel() {
            return getCvsDescriptor().getCompressionLevel();
        }

        @Override
        public CvsAuthentication[] getAuthentication() {
            return getCvsDescriptor().getAuthentication();
        }


        @Override
        public String getChangelogEncoding() {
            return getCvsDescriptor().getChangelogEncoding();
        }

    }


}