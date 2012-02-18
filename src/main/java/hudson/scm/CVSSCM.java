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

import static hudson.Util.fixEmptyAndTrim;
import static hudson.Util.fixNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.ModelObject;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.remoting.VirtualChannel;
import hudson.scm.CVSChangeLogSet.CVSChangeLog;
import hudson.scm.cvstagging.CvsTagAction;
import hudson.scm.cvstagging.LegacyTagAction;
import hudson.util.Secret;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.netbeans.lib.cvsclient.CVSRoot;
import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.admin.AdminHandler;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.StandardAdminHandler;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;
import org.netbeans.lib.cvsclient.command.log.RlogCommand;
import org.netbeans.lib.cvsclient.command.update.UpdateCommand;
import org.netbeans.lib.cvsclient.commandLine.BasicListener;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.Connection;
import org.netbeans.lib.cvsclient.connection.ConnectionFactory;
import org.netbeans.lib.cvsclient.event.CVSListener;

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
public class CVSSCM extends SCM implements Serializable {

    private static final long serialVersionUID = -2175193493227149541L;

    private static final DateFormat DATE_FORMATTER = new SimpleDateFormat("dd MMM yyyy HH:mm:ss z", Locale.UK);

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
                useHeadIfNotFound), canUseUpdate, legacy, null, Boolean.getBoolean(CVSSCM.class.getName() + ".skipChangeLog"), true, false, false);
    }

    @DataBoundConstructor
    public CVSSCM(final List<CvsRepository> repositories, final boolean canUseUpdate, final boolean legacy,
                    final CVSRepositoryBrowser browser, final boolean skipChangeLog, final boolean pruneEmptyDirectories,
                    final boolean disableCvsQuiet, final boolean cleanOnFailedUpdate) {
        this.repositories = repositories.toArray(new CvsRepository[repositories.size()]);
        this.canUseUpdate = canUseUpdate;
        this.skipChangeLog = skipChangeLog;
        flatten = !legacy && this.repositories.length == 1 && this.repositories[0].getModules().length == 1 && "".equals(fixNull(this.repositories[0].getModules()[0].getLocalName()));
        repositoryBrowser = browser;
        this.pruneEmptyDirectories = pruneEmptyDirectories;
        this.disableCvsQuiet = disableCvsQuiet;
        this.cleanOnFailedUpdate = cleanOnFailedUpdate;
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
     * Calculates the level of compression that should be used for dealing with
     * the given repository.
     * <p>
     * If we're using a local repository then we don't use compression
     * (http://root.cern.ch/root/CVS.html#checkout), if no compression level has
     * been specifically set for this repository then we use the global setting,
     * otherwise we use the one set for this repository.
     * 
     * @param repository
     *            the repository we're wanting to connect to
     * @return the level of compression to use between 0 and 9 (inclusive), with
     *         0 being no compression and 9 being maximum
     */
    private int getCompressionLevel(final CvsRepository repository, final EnvVars envVars) {
        final String cvsroot = envVars.expand(repository.getCvsRoot());

        /*
         * CVS 1.11.22 manual: If the access method is omitted, then if the
         * repository starts with `/', then `:local:' is assumed. If it does not
         * start with `/' then either `:ext:' or `:server:' is assumed.
         */
        boolean local = cvsroot.startsWith("/") || cvsroot.startsWith(":local:") || cvsroot.startsWith(":fork:");

        // Use whatever the user has specified as the system default if the
        // repository doesn't specifically set one
        int compressionLevel = repository.getCompressionLevel() == -1 ? getDescriptor().getCompressionLevel()
                        : repository.getCompressionLevel();

        // For local access, compression is senseless (always return 0),
        // otherwise return the calculated value
        return local ? 0 : compressionLevel;
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
     * Since we add the current SCMRevisionState as an action during the build
     * (so we can get the current workspace state), this method should never be
     * called. Just for safety, we get the action and return it.
     * 
     * @see {@link SCM#calcRevisionsFromBuild(AbstractBuild, Launcher, TaskListener)}
     */
    @Override
    public SCMRevisionState calcRevisionsFromBuild(final AbstractBuild<?, ?> build, final Launcher launcher,
                    final TaskListener listener) throws IOException, InterruptedException {
        return build.getAction(CvsRevisionState.class);
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

        // No previous build? everything has changed
        if (null == project.getLastBuild()) {
            listener.getLogger().println("No previous build found, scheduling build");
            return PollingResult.BUILD_NOW;
        }

        final EnvVars envVars = project.getLastBuild().getEnvironment(listener);

        final Date currentPollDate = Calendar.getInstance().getTime();

        /*
         * this flag will be used to check whether a build is needed (assuming
         * the local and remote states are comparable and no configuration has
         * changed between the last build and the current one)
         */
        boolean changesPresent = false;

        // Schedule a new build if the baseline isn't valid
        if ((baseline == null || !(baseline instanceof CvsRevisionState))) {
            listener.getLogger().println("Invalid baseline detected, scheduling build");
            return new PollingResult(baseline, new CvsRevisionState(new HashMap<CvsRepository, List<CvsFile>>()),
                            PollingResult.Change.INCOMPARABLE);
        }

        // convert the baseline into a use-able form
        final Map<CvsRepository, List<CvsFile>> remoteState = new HashMap<CvsRepository, List<CvsFile>>(
                        ((CvsRevisionState) baseline).getModuleFiles());

        // Loops through every module and check if it has changed
        for (CvsRepository repository : repositories) {

            /*
             * this repository setting either didn't exist or has changed since
             * the last build so we'll force a new build now.
             */
            if (!remoteState.containsKey(repository)) {
                listener.getLogger().println("Repository not found in workspace state, scheduling build");
                return new PollingResult(baseline, new CvsRevisionState(new HashMap<CvsRepository, List<CvsFile>>()),
                                PollingResult.Change.INCOMPARABLE);
            }

            // get the list of current changed files in this repository
            final List<CvsFile> changes = calculateRepositoryState(project.getLastCompletedBuild().getTime(),
                            currentPollDate, repository, launcher, workspace, listener, envVars);

            final List<CvsFile> remoteFiles = remoteState.get(repository);

            // update the remote state with the changes we've just retrieved
            for (CvsFile changedFile : changes) {
                for (CvsFile existingFile : remoteFiles) {
                    if (!changedFile.getName().equals(existingFile.getName())) {
                        continue;
                    }

                    remoteFiles.remove(existingFile);
                    if (!changedFile.isDead()) {
                        remoteFiles.add(changedFile);
                    }
                }
            }

            // set the updated files list back into the remote state
            remoteState.put(repository, remoteFiles);

            // convert the excluded regions into patterns so we can use them as
            // regular expressions
            final List<Pattern> excludePatterns = new ArrayList<Pattern>();

            for (ExcludedRegion pattern : repository.getExcludedRegions()) {
                try {
                    excludePatterns.add(Pattern.compile(pattern.getPattern()));
                } catch (PatternSyntaxException ex) {
                    launcher.getListener().getLogger().println("Pattern could not be compiled: " + ex.getMessage());
                }
            }

            // create a list of changes we can use to filter out excluded
            // regions
            final List<CvsFile> filteredChanges = new ArrayList<CvsFile>(changes);

            // filter out all changes in the exclude regions
            for (final Pattern excludePattern : excludePatterns) {
                for (final CvsFile change : filteredChanges) {
                    if (excludePattern.matcher(change.getName()).matches()) {
                        filteredChanges.remove(change);
                    }
                }
            }

            // if our list of changes isn't empty then we want to note this as
            // we need a build
            changesPresent = changesPresent || !filteredChanges.isEmpty();
        }

        // Return the new repository state and whether we require a new build
        return new PollingResult(baseline, new CvsRevisionState(remoteState),
                        changesPresent ? PollingResult.Change.SIGNIFICANT : PollingResult.Change.NONE);
    }

    /**
     * Builds a list of changes that have occurred in the given repository
     * between any 2 time-stamps. This does not require the workspace to be
     * checked out and does not change the state of any checked out workspace.
     * The list returned does not have any filters set for exclude regions (i.e.
     * it's every file that's changed for the modules being watched).
     * 
     * @param startTime
     *            the time-stamp to start filtering changes from
     * @param endTime
     *            the time-stamp to end filtering changes from
     * @param repository
     *            the repository to search for changes in. All modules under
     *            this repository will be checked
     * @param launcher
     *            the executor to use when running the CVS command
     * @param workspace
     *            the folder to use as the root directory when running the
     *            command
     * @param listener
     *            where to send log output
     * @return a list of changed files, including their versions and change
     *         comments
     * @throws IOException
     *             on communication failure (e.g. communication with slaves)
     * @throws InterruptedException
     *             on job cancellation
     */
    private List<CVSChangeLog> calculateChangeLog(final Date startTime, final Date endTime,
                    final CvsRepository repository, final Launcher launcher, final FilePath workspace,
                    final TaskListener listener, final EnvVars envVars) throws IOException, InterruptedException {

        final List<CVSChangeLog> changes = new ArrayList<CVSChangeLog>();

        for (final CvsModule module : repository.getModules()) {

            String logContents = getRemoteLogForModule(repository, module, listener.getLogger(), startTime, endTime, envVars);

            // use the parser to build up a list of changes and add it to the
            // list we've been creating
            changes.addAll(CvsChangeLogHelper.getInstance().mapCvsLog(logContents, repository, module, envVars).getChanges());

        }
        return changes;
    }

    /**
     * Builds a list of files that have changed in the given repository between
     * any 2 time-stamps. This does not require the workspace to be checked out
     * and does not change the state of any checked out workspace. The list
     * returned does not have any filters set for exclude regions (i.e. it's
     * every file that's changed for the modules being watched).
     * 
     * @param startTime
     *            the time-stamp to start filtering changes from
     * @param endTime
     *            the time-stamp to end filtering changes from
     * @param repository
     *            the repository to search for changes in. All modules under
     *            this repository will be checked
     * @param launcher
     *            the executor to use when running the CVS command
     * @param workspace
     *            the folder to use as the root directory when running the
     *            command
     * @param listener
     *            where to send log output
     * @return a list of changed files, including their versions and change
     *         comments
     * @throws IOException
     *             on communication failure (e.g. communication with slaves)
     * @throws InterruptedException
     *             on job cancellation
     */
    private List<CvsFile> calculateRepositoryState(final Date startTime, final Date endTime,
                    final CvsRepository repository, final Launcher launcher, final FilePath workspace,
                    final TaskListener listener, final EnvVars envVars) throws IOException {
        final List<CvsFile> files = new ArrayList<CvsFile>();

        for (final CvsModule module : repository.getModules()) {

            String logContents = getRemoteLogForModule(repository, module, listener.getLogger(), startTime, endTime, envVars);

            // use the parser to build up a list of changed files and add it to
            // the list we've been creating
            files.addAll(CvsChangeLogHelper.getInstance().mapCvsLog(logContents, repository, module, envVars).getFiles());

        }
        return files;
    }

    /**
     * Gets the output for the CVS <tt>rlog</tt> command for the given module
     * between the specified dates.
     * 
     * @param repository
     *            the repository to connect to for running rlog against
     * @param module
     *            the module to check for changes against
     * @param errorStream
     *            where to log any error messages to
     * @param startTime
     *            don't list any changes before this time
     * @param endTime
     *            don't list any changes after this time
     * @return the output of rlog with no modifications
     * @throws IOException
     *             on underlying communication failure
     */
    private String getRemoteLogForModule(final CvsRepository repository, final CvsModule module,
                    final PrintStream errorStream, final Date startTime, final Date endTime, final EnvVars envVars) throws IOException {
        final Client cvsClient = getCvsClient(repository, envVars);

        RlogCommand rlogCommand = new RlogCommand();

        // we have to synchronize since we're dealing with DateFormat.format()
        synchronized (DATE_FORMATTER) {
            final String lastBuildDate = DATE_FORMATTER.format(startTime);
            final String endDate = DATE_FORMATTER.format(endTime);

            rlogCommand.setDateFilter(lastBuildDate + "<" + endDate);
            rlogCommand.setSuppressHeader(true);
        }

        // set branch name if selected
        if (module.getModuleLocation().getLocationType().equals(CvsModuleLocationType.BRANCH)) {
            rlogCommand.setRevisionFilter(envVars.expand(module.getModuleLocation().getLocationName()));
        }

        // set tag name if selected
        if (module.getModuleLocation().getLocationType().equals(CvsModuleLocationType.TAG)) {
            rlogCommand.setRevisionFilter(envVars.expand(module.getModuleLocation().getLocationName()));
        }

        // tell CVS which module we're logging
        rlogCommand.setModule(module.getRemoteName());

        // create an output stream to send the output from CVS command to - we
        // can then parse it from here
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final PrintStream logStream = new PrintStream(outputStream);

        // set a listener with our output stream that we parse the log from
        final CVSListener basicListener = new BasicListener(logStream, errorStream);
        cvsClient.getEventManager().addCVSListener(basicListener);

        // send the command to be run, we can't continue of the task fails
        try {
            if (!cvsClient.executeCommand(rlogCommand, getGlobalOptions(repository, envVars))) {
                throw new RuntimeException("Error while trying to run CVS rlog");
            }
        } catch (CommandAbortedException e) {
            throw new RuntimeException("CVS rlog command aborted", e);
        } catch (CommandException e) {
            throw new RuntimeException("CVS rlog command failed", e);
        } catch (AuthenticationException e) {
            throw new RuntimeException("CVS authentication failure while running rlog command", e);
        } finally {
            try {
                cvsClient.getConnection().close();
            } catch(IOException ex) {
                errorStream.println("Could not close client connection: " + ex.getMessage());
            }
        }
        
        // flush the output so we have it all available for parsing
        logStream.flush();
        outputStream.flush();

        // return the contents of the stream as the output of the command
        return outputStream.toString();
    }

    /**
     * Gets an instance of the CVS client that can be used for connection to a repository. If the
     * repository specifies a password then the client's connection will be set with this password.
     * @param repository the repository to connect to
     * @param envVars variables to use for macro expansion
     * @return a CVS client capable of connecting to the specified repository
     */
    public Client getCvsClient(final CvsRepository repository, final EnvVars envVars) {
        final CVSRoot cvsRoot = CVSRoot.parse(envVars.expand(repository.getCvsRoot()));
        
        if (repository.isPasswordRequired()) {
            cvsRoot.setPassword(Secret.toString(repository.getPassword()));  
        }
        
        ConnectionFactory.getConnectionIdentity().setKnownHostsFile(getDescriptor().getKnownHostsLocation());
        ConnectionFactory.getConnectionIdentity().setPrivateKeyPath(getDescriptor().getPrivateKeyLocation());
        if (getDescriptor().getPrivateKeyPassword() != null) {
            ConnectionFactory.getConnectionIdentity().setPrivateKeyPassword(getDescriptor().getPrivateKeyPassword().getPlainText());
        }
        
        final Connection cvsConnection = ConnectionFactory.getConnection(cvsRoot);
        
        return new Client(cvsConnection, new StandardAdminHandler());
    }
    
    public GlobalOptions getGlobalOptions(CvsRepository repository, EnvVars envVars) {
        final GlobalOptions globalOptions = new GlobalOptions();
        globalOptions.setVeryQuiet(!disableCvsQuiet);
        globalOptions.setCompressionLevel(getCompressionLevel(repository, envVars));
        globalOptions.setCVSRoot(envVars.expand(repository.getCvsRoot()));
        return globalOptions;
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
        return workspace.child(getRepositories()[0].getModules()[0].getCheckoutName());
    }

    @Override
    public FilePath[] getModuleRoots(final FilePath workspace, @SuppressWarnings("rawtypes") final AbstractBuild build) {
        if (!flatten) {
            List<FilePath> moduleRoots = new ArrayList<FilePath>();
            for (CvsRepository repository : getRepositories()) {
                for (CvsModule module : repository.getModules()) {
                    moduleRoots.add(workspace.child(module.getCheckoutName()));
                }
            }
            return moduleRoots.toArray(new FilePath[moduleRoots.size()]);
        }
        return new FilePath[] {getModuleRoot(workspace, build)};
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new CVSChangeLogParser();
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

        String locationName = getRepositories()[0].getModules()[0].getModuleLocation().getLocationName();
        
        if (null == locationName) {
            return null;
        }
        
        for (CvsRepository repository : getRepositories()) {
            for (CvsModule module : repository.getModules()) {
                if (!locationName.equals(module.getModuleLocation().getLocationName())) {
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
        
        final EnvVars envVars = build.getEnvironment(listener);

        final String dateStamp;
        final Date buildDate = new Date();

        synchronized (DATE_FORMATTER) {
            dateStamp = DATE_FORMATTER.format(buildDate);
        }

        for (CvsRepository repository : repositories) {

            for (CvsModule cvsModule : repository.getModules()) {

                final FilePath module = workspace.child(cvsModule.getCheckoutName());

                boolean updateFailed = false;
                boolean update = false;

                if (flatten) {
                    if (workspace.child("CVS/Entries").exists()) {
                        update = true;
                    }
                } else {
                    if (canUseUpdate && module.exists()) {
                        update = true;
                    }
                }
                
                final FilePath targetWorkspace = flatten ? workspace.getParent() : workspace;

                final String moduleName= flatten ?workspace.getName() : cvsModule.getCheckoutName();
                
                
                // we're doing an update
                if (update) {
                    // we're doing a CVS update
                    UpdateCommand updateCommand = new UpdateCommand();

                    // force it to recurse into directories
                    updateCommand.setBuildDirectories(true);
                    updateCommand.setRecursive(true);
                    
                    // set directory pruning
                    updateCommand.setPruneDirectories(isPruneEmptyDirectories());

                    // point to head, branch or tag
                    if (cvsModule.getModuleLocation().getLocationType() == CvsModuleLocationType.BRANCH) {
                        updateCommand.setUpdateByRevision(envVars.expand(cvsModule.getModuleLocation().getLocationName()));
                        if (cvsModule.getModuleLocation().isUseHeadIfNotFound()) {
                            updateCommand.setUseHeadIfNotFound(true);
                        } else {
                            updateCommand.setUpdateByDate(dateStamp);
                        }
                    } else if (cvsModule.getModuleLocation().getLocationType() == CvsModuleLocationType.TAG) {
                        updateCommand.setUpdateByRevision(envVars.expand(cvsModule.getModuleLocation().getLocationName()));
                        updateCommand.setUseHeadIfNotFound(cvsModule.getModuleLocation().isUseHeadIfNotFound());
                    } else {
                        updateCommand.setUpdateByRevision(CvsModuleLocationType.HEAD.getName().toUpperCase());
                        updateCommand.setUpdateByDate(dateStamp);
                    }
                    
                    if (!perform(updateCommand, targetWorkspace, listener, repository, moduleName, envVars)) {
                        if (cleanOnFailedUpdate) {
                            updateFailed = true;
                        } else {
                            return false;
                        }
                    }

                }
                
                
                // we're doing a checkout
                if (!update || (updateFailed && cleanOnFailedUpdate)) {
                    
                    if (updateFailed) {
                        listener.getLogger().println("Update failed. Cleaning workspace and performing full checkout");
                        workspace.deleteContents();
                    }
                    
                    // we're doing a CVS checkout
                    CheckoutCommand checkoutCommand = new CheckoutCommand();

                    // point to branch or tag if specified
                    if (cvsModule.getModuleLocation().getLocationType() == CvsModuleLocationType.BRANCH) {
                        checkoutCommand.setCheckoutByRevision(envVars.expand(cvsModule.getModuleLocation().getLocationName()));
                        if (cvsModule.getModuleLocation().isUseHeadIfNotFound()) {
                            checkoutCommand.setUseHeadIfNotFound(true);
                        } else {
                            checkoutCommand.setCheckoutByDate(dateStamp);
                        }
                    } else if (cvsModule.getModuleLocation().getLocationType() == CvsModuleLocationType.TAG) {
                        checkoutCommand.setCheckoutByRevision(envVars.expand(cvsModule.getModuleLocation().getLocationName()));
                        if (cvsModule.getModuleLocation().isUseHeadIfNotFound()) {
                            checkoutCommand.setUseHeadIfNotFound(true);
                        }
                    } else if (cvsModule.getModuleLocation().getLocationType() == CvsModuleLocationType.HEAD) {
                        checkoutCommand.setCheckoutByDate(dateStamp);
                    }
                    
                    // set directory pruning
                    checkoutCommand.setPruneDirectories(isPruneEmptyDirectories());

                    // set where we're checking out to
                    checkoutCommand.setCheckoutDirectory(moduleName);

                    // and specify which module to load
                    checkoutCommand.setModule(cvsModule.getRemoteName());

                    if (!perform(checkoutCommand, workspace, listener, repository, moduleName, envVars)) {
                        return false;
                    }

                }

            }

        }

        // build change log
        final AbstractBuild<?, ?> lastCompleteBuild = build.getPreviousBuiltBuild();

        if (lastCompleteBuild != null && !isSkipChangeLog()) {
            final List<CVSChangeLog> changes = new ArrayList<CVSChangeLog>();
            for (CvsRepository location : repositories) {
                changes.addAll(calculateChangeLog(lastCompleteBuild.getTime(), build.getTime(), location, launcher,
                                workspace, listener, build.getEnvironment(listener)));
            }
            CvsChangeLogHelper.getInstance().toFile(changes, changelogFile);
        }

        // add the current workspace state as an action
        build.getActions().add(new CvsRevisionState(calculateWorkspaceState(workspace)));

        // add the tag action to the build
        build.getActions().add(new CvsTagAction(build, this));

        // remove sticky date tags
        workspace.act(new FileCallable<Void>() {

            private static final long serialVersionUID = -6867861913158282961L;

            @Override
            public Void invoke(final File f, final VirtualChannel channel) throws IOException {
                AdminHandler adminHandler = new StandardAdminHandler();
                for (File file : adminHandler.getAllFiles(f)) {
                    Entry entry = adminHandler.getEntry(file);
                    entry.setDate(null);
                    adminHandler.setEntry(file, entry);
                }

                return null;
            }
        });

        return true;
    }
    
    /**
     * Runs a cvs command in the given workspace.
     * @param cvsCommand the command to run (checkout, update etc)
     * @param workspace the workspace to run the command in
     * @param listener where to log output to
     * @param repository the repository to connect to
     * @param moduleName the name of the directory within the workspace that will have work performed on it
     * @param envVars the environmental variables to expand
     * @return true if the action succeeds, false otherwise
     * @throws IOException on failure handling files or server actions
     * @throws InterruptedException if the user cancels the action
     */
    private boolean perform(final Command cvsCommand, final FilePath workspace, final TaskListener listener,
                    final CvsRepository repository, final String moduleName, final EnvVars envVars) throws IOException, InterruptedException {
        
        final Client cvsClient = getCvsClient(repository, envVars);
        final GlobalOptions globalOptions = getGlobalOptions(repository, envVars);
       
        
        if (!workspace.act(new FileCallable<Boolean>() {

            private static final long serialVersionUID = -7517978923721181408L;
            
            @Override
            public Boolean invoke(final File workspace, final VirtualChannel channel) throws RuntimeException {
                
                
                if (cvsCommand instanceof UpdateCommand) {
                    ((UpdateCommand) cvsCommand).setFiles(new File[]{new File(workspace, moduleName)});
                }
                    
                listener.getLogger().println("cvs " + cvsCommand.getCVSCommand());
                    
                
                cvsClient.setLocalPath(workspace.getAbsolutePath());
                final BasicListener basicListener = new BasicListener(listener.getLogger(), listener.getLogger());
                cvsClient.getEventManager().addCVSListener(basicListener);

                try {
                    return cvsClient.executeCommand(cvsCommand, globalOptions);
                } catch (CommandAbortedException e) {
                    e.printStackTrace(listener.error("CVS Command aborted: " + e.getMessage()));
                    return false;
                } catch (CommandException e) {
                    e.printStackTrace(listener.error("CVS Command failed: " + e.getMessage()));
                    return false;
                } catch (AuthenticationException e) {
                    e.printStackTrace(listener.error("CVS Authentication failed: " + e.getMessage()));
                    return false;
                }  finally {
                    try {
                        cvsClient.getConnection().close();
                    } catch(IOException ex) {
                        listener.error("Could not close client connection: " + ex.getMessage());
                    }
                }
            }
        })) {
            listener.error("Cvs task failed");
            return false;
        }
        
        return true;
    }

    private Map<CvsRepository, List<CvsFile>> calculateWorkspaceState(final FilePath workspace) throws IOException,
                    InterruptedException {
        Map<CvsRepository, List<CvsFile>> workspaceState = new HashMap<CvsRepository, List<CvsFile>>();

        for (CvsRepository repository : getRepositories()) {
            List<CvsFile> cvsFiles = new ArrayList<CvsFile>();
            for (CvsModule module : repository.getModules()) {
                cvsFiles.addAll(getCvsFiles(workspace, module, flatten));
            }
            workspaceState.put(repository, cvsFiles);
        }

        return workspaceState;
    }

    private List<CvsFile> getCvsFiles(final FilePath workspace, final CvsModule module, final boolean flatten)
                    throws IOException, InterruptedException {
        FilePath targetWorkspace;
        if (flatten) {
            targetWorkspace = workspace;
        } else {
            targetWorkspace = workspace.child(module.getCheckoutName());
        }

        return targetWorkspace.act(new FileCallable<List<CvsFile>>() {

            private static final long serialVersionUID = 8158155902777163137L;

            @Override
            public List<CvsFile> invoke(final File moduleLocation, final VirtualChannel channel) throws IOException,
                            InterruptedException {
                /*
                 * we use the remote name because we're actually wanting the
                 * workspace represented as it would be in CVS. This then allows
                 * us to do a comparison against the file list returned by the
                 * rlog command (which wouldn't be possible if we use the local
                 * module name on a module that had been checked out as an alias
                 */
                return buildFileList(moduleLocation, module.getRemoteName());
            }

            public List<CvsFile> buildFileList(final File moduleLocation, final String prefix) throws IOException {
                AdminHandler adminHandler = new StandardAdminHandler();
                List<CvsFile> fileList = new ArrayList<CvsFile>();

                if (moduleLocation.isFile()) {
                    Entry entry = adminHandler.getEntry(moduleLocation);
                    if (entry != null) {
                        fileList.add(new CvsFile(entry.getName(), entry.getRevision()));
                    }
                } else {
                    for (File file : adminHandler.getAllFiles(moduleLocation)) {

                        
                        if (file.isFile()) {
                            Entry entry = adminHandler.getEntry(file);
                            CvsFile currentFile = new CvsFile(prefix + "/" + entry.getName(), entry.getRevision());
                            fileList.add(currentFile);
                        }
                    }
                    
                    // JENKINS-12807: we get a NPE here which shouldn't be possible given we know
                    // the file we're getting children of is a directory, but we'll do a null check
                    // for safety
                    File[] directoryFiles = moduleLocation.listFiles();
                    if (directoryFiles != null) {
                        for (File file : directoryFiles) {
                            if (file.isDirectory()) {
                                fileList.addAll(buildFileList(file, prefix + "/" + file.getName()));
                            }
                        }
                    }
                }

                return fileList;
            }

        });
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<CVSSCM> implements ModelObject {

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

        public void setPrivateKeyLocation(String privateKeyLocation) {
            this.privateKeyLocation = privateKeyLocation;
        }

        @Exported
        public Secret getPrivateKeyPassword() {
            return privateKeyPassword;
        }

        public void setPrivateKeyPassword(String privateKeyPassword) {
            this.privateKeyPassword = Secret.fromString(privateKeyPassword);
        }

        @Exported
        public String getKnownHostsLocation() {
            return knownHostsLocation;
        }

        public void setKnownHostsLocation(String knownHostsLocation) {
            this.knownHostsLocation = knownHostsLocation;
        }

        public int getCompressionLevel() {
            return compressionLevel;
        }

        public void setCompressionLevel(final int compressionLevel) {
            this.compressionLevel = compressionLevel;
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
            
            if (knownHostsLocation == null) {
                this.privateKeyLocation = System.getProperty("user.home") + "/.ssh/id_rsa";
            } else {
                this.privateKeyLocation = privateKeyLocation;
            }

            privateKeyPassword = Secret.fromString(fixEmptyAndTrim(o.getString("privateKeyPassword")));
            
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
