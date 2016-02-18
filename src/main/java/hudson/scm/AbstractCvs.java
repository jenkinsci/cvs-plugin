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


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.io.output.DeferredFileOutputStream;
import org.jenkinsci.remoting.RoleChecker;
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
import org.netbeans.lib.cvsclient.connection.ConnectionIdentity;
import org.netbeans.lib.cvsclient.event.CVSListener;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.cvstagging.CvsTagAction;
import hudson.util.Secret;
import jenkins.scm.cvs.QuietPeriodCompleted;

public abstract class AbstractCvs extends SCM implements ICvs {

    protected static final DateFormat DATE_FORMATTER = new SimpleDateFormat("dd MMM yyyy HH:mm:ss Z", Locale.UK);

    @Override
    public AbstractCvsDescriptor getDescriptor() {
        return (AbstractCvsDescriptor) super.getDescriptor();
    }

    public boolean isCheckoutCurrentTimestamp() {
        return false;
    }

    protected boolean checkout(CvsRepository[] repositories, boolean isFlatten, FilePath workspace, boolean canUseUpdate,
                               Run<?, ?> build, String dateStamp, boolean pruneEmptyDirectories,
                               boolean cleanOnFailedUpdate, TaskListener listener) throws IOException, InterruptedException {

        final EnvVars envVars = build.getEnvironment(listener);

        for (CvsRepository repository : repositories) {

            for (CvsRepositoryItem item : repository.getRepositoryItems()) {

                for (CvsModule cvsModule : item.getModules()) {
                    final String checkoutName = envVars.expand(cvsModule.getCheckoutName());
                    boolean localSubModule = checkoutName.contains("/") && cvsModule.isAlternativeCheckoutName();
                    int lastSlash = checkoutName.lastIndexOf("/");

                    final boolean flatten = isFlatten && !cvsModule.isAlternativeCheckoutName();

                    final FilePath targetWorkspace = flatten ? workspace.getParent() :
                            localSubModule ? workspace.child(checkoutName.substring(0, lastSlash)) : workspace;

                    final String moduleName = flatten ? workspace.getName() :
                            localSubModule ? checkoutName.substring(lastSlash + 1) : checkoutName;

                    final FilePath module = targetWorkspace.child(moduleName);

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

                    CvsRepositoryLocation repositoryLocation = item.getLocation();
                    CvsRepositoryLocationType locationType = repositoryLocation.getLocationType();
                    String locationName = repositoryLocation.getLocationName();
                    String expandedLocationName = envVars.expand(locationName);
                    // we're doing an update
                    if (update) {
                        // we're doing a CVS update
                        UpdateCommand updateCommand = new UpdateCommand();

                        // force it to recurse into directories
                        updateCommand.setBuildDirectories(true);
                        updateCommand.setRecursive(true);

                        // set directory pruning
                        updateCommand.setPruneDirectories(pruneEmptyDirectories);

                        // set overwrite policy
                        updateCommand.setCleanCopy(isForceCleanCopy());

                        // point to head, branch or tag
                        if (locationType == CvsRepositoryLocationType.BRANCH) {
                            updateCommand.setUpdateByRevision(expandedLocationName);
                            if (repositoryLocation.isUseHeadIfNotFound()) {
                                updateCommand.setUseHeadIfNotFound(true);
                                updateCommand.setUpdateByDate(dateStamp);
                            }
                        } else if (locationType == CvsRepositoryLocationType.TAG) {
                            updateCommand.setUpdateByRevision(expandedLocationName);
                            updateCommand.setUseHeadIfNotFound(repositoryLocation.isUseHeadIfNotFound());
                        } else {
                            updateCommand.setUpdateByRevision(CvsRepositoryLocationType.HEAD.getName().toUpperCase());
                            updateCommand.setUpdateByDate(dateStamp);
                        }

                        if (!perform(updateCommand, targetWorkspace, listener, repository, moduleName, envVars, pruneEmptyDirectories)) {
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
                        if (locationType == CvsRepositoryLocationType.BRANCH) {
                            checkoutCommand.setCheckoutByRevision(expandedLocationName);
                            if (repositoryLocation.isUseHeadIfNotFound()) {
                                checkoutCommand.setUseHeadIfNotFound(true);
                                checkoutCommand.setCheckoutByDate(dateStamp);
                            }
                        } else if (locationType == CvsRepositoryLocationType.TAG) {
                            checkoutCommand.setCheckoutByRevision(expandedLocationName);
                            if (repositoryLocation.isUseHeadIfNotFound()) {
                                checkoutCommand.setUseHeadIfNotFound(true);
                            }
                        } else if (locationType == CvsRepositoryLocationType.HEAD) {
                            checkoutCommand.setCheckoutByDate(dateStamp);
                        }

                        // set directory pruning
                        checkoutCommand.setPruneDirectories(pruneEmptyDirectories);

                        // set where we're checking out to
                        if (cvsModule.isAlternativeCheckoutName() || flatten) {
                            checkoutCommand.setCheckoutDirectory(moduleName);
                        }

                        // and specify which module to load
                        checkoutCommand.setModule(envVars.expand(cvsModule.getRemoteName()));

                        if (!perform(checkoutCommand, targetWorkspace, listener, repository, moduleName, envVars, pruneEmptyDirectories)) {
                            return false;
                        }

                    }

                }
            }

        }

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
                            final CvsRepository repository, final String moduleName, final EnvVars envVars, final boolean pruneEmptyDirectories)
            throws IOException, InterruptedException {

        final Client cvsClient = getCvsClient(repository, envVars, listener);
        final GlobalOptions globalOptions = getGlobalOptions(repository, envVars);


        if (!workspace.act(new FilePath.FileCallable<Boolean>() {

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
                    if (!cvsClient.executeCommand(cvsCommand, globalOptions)) {
                        return false;
                    }
                    if (pruneEmptyDirectories && !isDisableCvsQuiet()) {
                        try {
                            File moduleDir = new File(workspace, moduleName);
                            if (moduleDir.isDirectory()) {
                                pruneEmptyDirectories(moduleDir,listener);
                            }
                        } catch (IOException e) {
                            e.printStackTrace(listener.error("CVS empty directory cleanup failed: " + e.getMessage()));
                            return false;
                        }
                    }
                    return true;
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

			@Override
			public void checkRoles(RoleChecker checker)
					throws SecurityException {
				// Do nothing
			}
        })) {
            listener.error("Cvs task failed");
            return false;
        }

        return true;
    }

    /**
     * JENKINS-18390: work around buggy client.
     * Cannot copy similarly-named method from {@link UpdateCommand} due to license mismatch.
     * Cannot easily call the method reflectively on the {@link Command} since {@link CheckoutCommand} has a different signature for it.
     * Pending a fix in the client library, do it ourselves when necessary.
     */
    private static void pruneEmptyDirectories(File d, final TaskListener listener) throws IOException {
        File[] kids = d.listFiles();
        if (kids == null) {
            throw new IOException("could not examine " + d);
        }
        for (File kid : kids) {
            if (!kid.isDirectory()) {
                continue;
            }
            if (!new File(kid, "CVS").isDirectory()) {
                // not CVS-controlled, ignore
                continue;
            }

            if (isSymLink(kid,listener)) {
                listener.getLogger().println("pruneEmptyDirectories. prevent potential infinate loop, ignoring symlink:" + kid);
                continue;
            }

            pruneEmptyDirectories(kid,listener);
            File[] subkids = kid.listFiles();
            if (subkids != null && subkids.length == 1) {
                // Just CVS.
                Util.deleteRecursive(kid);
            }
        }
    }

    /**
     * Gets an instance of the CVS client that can be used for connection to a repository. If the
     * repository specifies a password then the client's connection will be set with this password.
     * @param repository the repository to connect to
     * @param envVars variables to use for macro expansion
     * @return a CVS client capable of connecting to the specified repository
     */
    public Client getCvsClient(final CvsRepository repository, final EnvVars envVars, final TaskListener listener) {
        return getCvsClient(repository, envVars, listener, true);
    }

    /**
     * Gets an instance of the CVS client that can be used for connection to a repository. If the
     * repository specifies a password then the client's connection will be set with this password.
     * @param repository the repository to connect to
     * @param envVars variables to use for macro expansion
     * @param showAuthenticationInfo whether to log where the authentication details are being obtanied from
     * @return a CVS client capable of connecting to the specified repository
     */
    public Client getCvsClient(final CvsRepository repository, final EnvVars envVars,
                               final TaskListener listener, boolean showAuthenticationInfo) {
        CVSRoot cvsRoot = CVSRoot.parse(envVars.expand(repository.getCvsRoot()));

        if (repository.isPasswordRequired()) {
            if (showAuthenticationInfo) {
                listener.getLogger().println("Using locally configured password for connection to " + cvsRoot.toString());
            }
            cvsRoot.setPassword(Secret.toString(repository.getPassword()));
        }
        else {
            String hostName = cvsRoot.getHostName();
            String partialRoot = (hostName != null ? hostName.toLowerCase(Locale.ENGLISH) : "") + ":" + ConnectionFactory.getConnection
                    (cvsRoot).getPort() + cvsRoot.getRepository();
            String sanitisedRoot = ":" + cvsRoot.getMethod() + ":" + partialRoot;
            for (CvsAuthentication authentication : getDescriptor().getAuthentication()) {
                CVSRoot authenticationRoot = CVSRoot.parse(authentication.getCvsRoot());
                String partialAuthenticationRoot = authenticationRoot.getHostName().toLowerCase(Locale.ENGLISH) + ":"
                        + ConnectionFactory.getConnection(authenticationRoot).getPort() + authenticationRoot.getRepository();
                String sanitisedAuthenticationRoot = ":" + cvsRoot.getMethod() + ":" + partialAuthenticationRoot;
                if (sanitisedAuthenticationRoot.equals(sanitisedRoot)
                        && (cvsRoot.getUserName() == null || authentication.getUsername().equals(cvsRoot.getUserName()))) {
                    cvsRoot = CVSRoot.parse(":" + cvsRoot.getMethod() + ":" + (authentication.getUsername() != null ? authentication.getUsername() + "@" :"") + partialRoot);
                    cvsRoot.setPassword(authentication.getPassword().getPlainText());
                    if (showAuthenticationInfo) {
                        listener.getLogger().println("Using globally configured password for connection to '"
                                + sanitisedRoot + "' with username '" + authentication.getUsername() + "'");
                    }
                    break;
                }
            }
        }

        ConnectionIdentity connectionIdentity = ConnectionFactory.getConnectionIdentity();
        connectionIdentity.setKnownHostsFile(envVars.expand(getDescriptor().getKnownHostsLocation()));
        connectionIdentity.setPrivateKeyPath(envVars.expand(getDescriptor().getPrivateKeyLocation()));
        if (getDescriptor().getPrivateKeyPassword() != null) {
            connectionIdentity.setPrivateKeyPassword(getDescriptor().getPrivateKeyPassword().getPlainText());
        }

        final Connection cvsConnection = ConnectionFactory.getConnection(cvsRoot);

        return new Client(cvsConnection, new StandardAdminHandler());
    }

    public GlobalOptions getGlobalOptions(CvsRepository repository, EnvVars envVars) {
        final GlobalOptions globalOptions = new GlobalOptions();
        globalOptions.setVeryQuiet(!isDisableCvsQuiet());
        globalOptions.setModeratelyQuiet(!isDisableCvsQuiet());
        globalOptions.setCompressionLevel(getCompressionLevel(repository, envVars));
        globalOptions.setCVSRoot(envVars.expand(repository.getCvsRoot()));
        return globalOptions;
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
     * @param envVars
     *            the environmental variables to expand any parameters from
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

    public boolean isDisableCvsQuiet() {
        return true;
    }

    /**
     * Since we add the current SCMRevisionState as an action during the build
     * (so we can get the current workspace state), this method should never be
     * called. Just for safety, we get the action and return it.
     *
     * @see {@link SCM#calcRevisionsFromBuild(hudson.model.AbstractBuild, hudson.Launcher, TaskListener)}
     */
    @Override
    public SCMRevisionState calcRevisionsFromBuild(final AbstractBuild<?, ?> build, final Launcher launcher,
                                                   final TaskListener listener) throws IOException, InterruptedException {
        return build.getAction(CvsRevisionState.class);
    }

    @Override
    public @CheckForNull SCMRevisionState calcRevisionsFromBuild(@Nonnull Run<?,?> build, @Nullable FilePath workspace,
    		                                                     @Nullable Launcher launcher, @Nonnull TaskListener listener)
    		                                                    		 throws IOException, InterruptedException {
        return build.getAction(CvsRevisionState.class);
    }

    protected PollingResult compareRemoteRevisionWith(final Job<?, ?> project, final Launcher launcher,
                                                      final FilePath workspace, final TaskListener listener,
                                                      final SCMRevisionState baseline, final CvsRepository[] repositories)
            throws IOException, InterruptedException {

        Run<?, ?> build = project.getLastBuild();

        // No previous build? everything has changed
        if (null == build) {
            listener.getLogger().println("No previous build found, scheduling build");
            return PollingResult.BUILD_NOW;
        }

        // Disable this check while the JENKINS-24141 is not resolved
//        if (!build.hasChangeSetComputed() && build.isBuilding()) {
//            listener.getLogger().println("Previous build has not finished checkout."
//                    + " Not triggering build as no valid baseline comparison available.");
//            return PollingResult.NO_CHANGES;
//        }

        final EnvVars envVars = build.getEnvironment(listener);

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
            final List<CvsFile> changes = calculateRepositoryState(build.getTime(),
                    currentPollDate, repository, listener, envVars, workspace);

            final List<CvsFile> remoteFiles = remoteState.get(repository);

            // update the remote state with the changes we've just retrieved
            for (CvsFile changedFile : changes) {
                boolean changed = false;
                for (ListIterator<CvsFile> itr = remoteFiles.listIterator(); itr.hasNext();) {
                    CvsFile existingFile = itr.next();
                    if (!changedFile.getName().equals(existingFile.getName())) {
                        continue;
                    }

                    itr.remove();
                    if (!changedFile.isDead()) {
                        itr.add(changedFile);
                    }
                    changed = true;
                }
                if (!changed) {
                    // file was not in old remote state, add it in
                    remoteFiles.add(changedFile);
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
                    listener.getLogger().println("Pattern could not be compiled: " + pattern);
                    throw new RuntimeException("Polling could not completed since pattern could not be compiled", ex);
                }
            }

            // create a list of changes we can use to filter out excluded
            // regions
            final List<CvsFile> filteredChanges = new ArrayList<CvsFile>(changes);

            // filter out all changes in the exclude regions
            for (final Pattern excludePattern : excludePatterns) {
                for (Iterator<CvsFile> itr = filteredChanges.iterator(); itr.hasNext(); ) {
                    CvsFile change = itr.next();
                    if (excludePattern.matcher(change.getName()).matches()) {
                        listener.getLogger().println("Skipping file '" + change.getName() + "' since it matches exclude pattern " + excludePattern.pattern());
                        itr.remove();
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
     * @param listener
     *            where to send log output
     * @param envVars the environmental variables to perform parameter expansion from
     * @return a list of changed files, including their versions and change
     *         comments
     * @throws IOException
     *             on communication failure (e.g. communication with slaves)
     */
    protected List<CvsFile> calculateRepositoryState(final Date startTime, final Date endTime,
                                                     final CvsRepository repository, final TaskListener listener,
                                                     final EnvVars envVars, final FilePath workspace) throws IOException, InterruptedException {
        final List<CvsFile> files = new ArrayList<CvsFile>();

        for (final CvsRepositoryItem item : repository.getRepositoryItems()) {
            for (final CvsModule module : item.getModules()) {
                files.addAll(getRemoteLogForModule(repository, item, module, startTime, endTime, envVars, listener, workspace).getFiles());
            }
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
     * @param listener
     *            where to log any error messages to
     * @param startTime
     *            don't list any changes before this time
     * @param endTime
     *            don't list any changes after this time
     * @return the output of rlog with no modifications
     * @throws IOException
     *             on underlying communication failure
     */
    private CvsChangeSet getRemoteLogForModule(final CvsRepository repository, final CvsRepositoryItem item, final CvsModule module,
                                         final Date startTime, final Date endTime,
                                         final EnvVars envVars, final TaskListener listener, FilePath workspace) throws IOException, InterruptedException {
        final Client cvsClient = getCvsClient(repository, envVars, listener);

        final RlogCommand rlogCommand = new RlogCommand();

        // we have to synchronize since we're dealing with DateFormat.format()
        synchronized (DATE_FORMATTER) {
            final String lastBuildDate = DATE_FORMATTER.format(startTime);
            final String endDate = DATE_FORMATTER.format(endTime);

            rlogCommand.setDateFilter(lastBuildDate + "<" + endDate);
        }

        // tell CVS which module we're logging
        rlogCommand.setModule(envVars.expand(module.getRemoteName()));

        // ignore headers for files that aren't in the current change-set
        rlogCommand.setSuppressHeader(true);

        final String encoding = getDescriptor().getChangelogEncoding();
        final GlobalOptions globalOptions = getGlobalOptions(repository, envVars);

        if (workspace == null) {
            return executeRlog(cvsClient, rlogCommand, listener, encoding, globalOptions, repository, envVars, item.getLocation());
        }
        else {
            return workspace.act(new FilePath.FileCallable<CvsChangeSet>() {
                @Override
                public CvsChangeSet invoke(File file, VirtualChannel virtualChannel) throws IOException, InterruptedException {
                    return executeRlog(cvsClient, rlogCommand, listener, encoding, globalOptions, repository, envVars, item.getLocation());
                }

    			@Override
    			public void checkRoles(RoleChecker checker)
    					throws SecurityException {
    				// Do nothing
    			}
            });
        }


    }

    private CvsChangeSet executeRlog(Client cvsClient, RlogCommand rlogCommand,
                             TaskListener listener, final String encoding, GlobalOptions globalOptions,
                             CvsRepository repository, EnvVars envVars, CvsRepositoryLocation location) throws IOException {
        // create an output stream to send the output from CVS command to - we
        // can then parse it from here
        final File tmpRlogSpill = File.createTempFile("cvs","rlog");
        final DeferredFileOutputStream outputStream = new DeferredFileOutputStream(100*1024,tmpRlogSpill);
        final PrintStream logStream = new PrintStream(outputStream, true, encoding);

        // set a listener with our output stream that we parse the log from
        final CVSListener basicListener = new BasicListener(logStream, listener.getLogger());
        cvsClient.getEventManager().addCVSListener(basicListener);

        // log the command to the current run/polling log
        listener.getLogger().println("cvs " + rlogCommand.getCVSCommand());


        try {
            if (!cvsClient.executeCommand(rlogCommand, globalOptions)) {
                cleanupLog(logStream, tmpRlogSpill);
                throw new RuntimeException("Error while trying to run CVS rlog");
            }
        } catch (CommandAbortedException e) {
            cleanupLog(logStream, tmpRlogSpill);
            throw new RuntimeException("CVS rlog command aborted", e);
        } catch (CommandException e) {
            cleanupLog(logStream, tmpRlogSpill);
            throw new RuntimeException("CVS rlog command failed", e);
        } catch (AuthenticationException e) {
            cleanupLog(logStream, tmpRlogSpill);
            throw new RuntimeException("CVS authentication failure while running rlog command", e);
        } finally {
            try {
                cvsClient.getConnection().close();
            } catch (IOException ex) {
                listener.error("Could not close CVS connection");
                ex.printStackTrace(listener.getLogger());
            }
            // flush the output so we have it all available for parsing
            logStream.close();
        }



        // return the contents of the stream as the output of the command
        CvsLog log = new CvsLog() {
            @Override
            public Reader read() throws IOException {
                // note that master and slave can have different platform encoding
                if (outputStream.isInMemory())
                    return new InputStreamReader(new ByteArrayInputStream(outputStream.getData()), encoding);
                else
                    return new InputStreamReader(new FileInputStream(outputStream.getFile()), encoding);
            }

            @Override
            public void dispose() {
                if (!tmpRlogSpill.delete()) {
                    tmpRlogSpill.deleteOnExit();
                }
            }
        };

        return log.mapCvsLog(envVars.expand(repository.getCvsRoot()), location, repository, envVars);
    }

    private void cleanupLog(PrintStream logStream, File tmpRlogSpill)
    {
        logStream.close();
        if (!tmpRlogSpill.delete()) {
            tmpRlogSpill.deleteOnExit();
        }
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
     * @param listener
     *            where to send log output
     * @param envVars the environmental variables to perform parameter expansion from.
     * @return a list of changed files, including their versions and change
     *         comments
     * @throws IOException
     *             on communication failure (e.g. communication with slaves)
     * @throws InterruptedException
     *             on job cancellation
     */
    protected List<CVSChangeLogSet.CVSChangeLog> calculateChangeLog(final Date startTime, final Date endTime,
                                                                    final CvsRepository repository,
                                                                    final TaskListener listener, final EnvVars envVars, FilePath workspace)
            throws IOException, InterruptedException {

        final List<CVSChangeLogSet.CVSChangeLog> changes = new ArrayList<CVSChangeLogSet.CVSChangeLog>();

        for (final CvsRepositoryItem item : repository.getRepositoryItems()) {
            for (final CvsModule module : item.getModules()) {
                changes.addAll(getRemoteLogForModule(repository, item, module, startTime, endTime, envVars, listener, workspace).getChanges());
            }
        }
        return changes;
    }

    protected void postCheckout(Run<?, ?> build, File changelogFile, CvsRepository[] repositories,
                                FilePath workspace, final TaskListener listener, boolean flatten, EnvVars envVars)
            throws IOException, InterruptedException {
        // build change log
        final Run<?, ?> lastCompleteBuild = build.getPreviousBuiltBuild();

        if (lastCompleteBuild != null && !isSkipChangeLog()) {
            final Date lastCompleteTimestamp = getCheckoutDate(lastCompleteBuild);
            final Date checkoutDate = getCheckoutDate(build);

            final List<CVSChangeLogSet.CVSChangeLog> changes = new ArrayList<CVSChangeLogSet.CVSChangeLog>();
            for (CvsRepository location : repositories) {
                changes.addAll(calculateChangeLog(lastCompleteTimestamp, checkoutDate, location,
                        listener, build.getEnvironment(listener), workspace));
            }
            new CVSChangeLogSet(build, getBrowser(), changes).toFile(changelogFile);
        } else {
            createEmptyChangeLog(changelogFile, listener, "changelog");
        }

        // add the current workspace state as an action
        build.getActions().add(new CvsRevisionState(calculateWorkspaceState(workspace, repositories, flatten, envVars, listener)));

        // add the tag action to the build
        build.getActions().add(new CvsTagAction(build, this));

        // remove sticky date tags
        for (final CvsRepository repository : getRepositories()) {
            for (final CvsRepositoryItem repositoryItem : repository.getRepositoryItems()) {
                for (final CvsModule module : repositoryItem.getModules()) {
                    FilePath target = (flatten ? workspace : workspace.child(module.getCheckoutName()));
                    target.act(new FilePath.FileCallable<Void>() {
                        @Override
                        public Void invoke(File module, VirtualChannel virtualChannel) throws IOException, InterruptedException {
                            final AdminHandler adminHandler = new StandardAdminHandler();

                            cleanup(module, adminHandler);

                            return null;
                        }

            			@Override
            			public void checkRoles(RoleChecker checker)
            					throws SecurityException {
            				// Do nothing
            			}

                        private void cleanup(File directory, AdminHandler adminHandler) throws IOException {
                            for (File file : adminHandler.getAllFiles(directory)) {
                                Entry entry = adminHandler.getEntry(file);
                                entry.setTag(entry.getTag()); // re-setting the tag removes the date without altering tag info
                                adminHandler.setEntry(file, entry);
                            }

                            // we need to remove CVS/Tag as it contains a sticky reference for HEAD modules
                            if (repositoryItem.getLocation().getLocationType() == CvsRepositoryLocationType.HEAD) {
                                final File tagFile = new File(directory, "CVS/Tag");

                                if (tagFile.exists()) {
                                    if (!tagFile.delete()) {
                                        listener.getLogger().println("Could not delete the sticky tag file, workspace may be in an inconsistent state");
                                    }
                                }
                            }

                            File[] innerFiles = directory.listFiles();
                            if (null != innerFiles) {

                                for (File innerFile : innerFiles) {
                                    if (isSymLink(innerFile,listener)) {
                                        listener.getLogger().println("cleanup. prevent potential infinate loop, ignoring symlink:" + innerFile);
                                        continue;
                                    }
                                    if (innerFile.isDirectory() && !innerFile.getName().equals("CVS")) {
                                        cleanup(innerFile, adminHandler);
                                    }
                                }
                            }
                        }
                    });
                }
            }
        }
    }

    protected Date getCheckoutDate(Run<?, ?> build) {
        QuietPeriodCompleted quietPeriodCompleted;
        Date checkoutDate;
        quietPeriodCompleted = build.getAction(QuietPeriodCompleted.class);
        if (quietPeriodCompleted != null && !isCheckoutCurrentTimestamp()) {
            checkoutDate = quietPeriodCompleted.getTimestampDate();
        } else {
            checkoutDate = build.getTime();
        }
        return checkoutDate;
    }

    private Map<CvsRepository, List<CvsFile>> calculateWorkspaceState(final FilePath workspace,
                                                                      final CvsRepository[] repositories,
                                                                      final boolean flatten, final EnvVars envVars,
                                                                      final TaskListener listener)
            throws IOException, InterruptedException {
        Map<CvsRepository, List<CvsFile>> workspaceState = new HashMap<CvsRepository, List<CvsFile>>();

        for (CvsRepository repository : repositories) {
            List<CvsFile> cvsFiles = new ArrayList<CvsFile>();
            for (CvsRepositoryItem item : repository.getRepositoryItems()) {
                for (CvsModule module : item.getModules()) {
                    cvsFiles.addAll(getCvsFiles(workspace, module, flatten, envVars, listener));
                }
            }
            workspaceState.put(repository, cvsFiles);
        }

        return workspaceState;
    }

    /**
     * Check if the given file is a symbolic link. Useful for preventing CSV recursing into directories infinitely.
     * @param file name of the file to test
     * @return whether the file if believed to be a symlink or not
     */
    public static boolean isSymLink(File file, final TaskListener listener) {
        if (file == null) {
            return false;
        }
        try {
            File canon;
            if (file.getParent() == null) {
                canon = file;
            } else {
                File canonDir = file.getParentFile().getCanonicalFile();
                canon = new File(canonDir, file.getName());
            }
            return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
        } catch (IOException ex) { 
            ex.printStackTrace(listener.error("Ignoring exception when checking for symlink. file:" + 
                                              file + " exception:" + ex.getMessage()));
        }
        return false;
    }


    private List<CvsFile> getCvsFiles(final FilePath workspace, final CvsModule module, final boolean flatten,
                                      final EnvVars envVars, final TaskListener listener)
            throws IOException, InterruptedException {
        FilePath targetWorkspace;
        if (flatten) {
            targetWorkspace = workspace;
        } else {
            targetWorkspace = workspace.child(envVars.expand(module.getCheckoutName()));
        }

        return targetWorkspace.act(new FilePath.FileCallable<List<CvsFile>>() {

            private static final long serialVersionUID = 8158155902777163137L;

            @Override
            public List<CvsFile> invoke(final File moduleLocation, final VirtualChannel channel) throws IOException {
                /*
                 * we use the remote name because we're actually wanting the
                 * workspace represented as it would be in CVS. This then allows
                 * us to do a comparison against the file list returned by the
                 * rlog command (which wouldn't be possible if we use the local
                 * module name on a module that had been checked out as an alias
                 */
                return buildFileList(moduleLocation, envVars.expand(module.getRemoteName()));
            }

			@Override
			public void checkRoles(RoleChecker checker)
					throws SecurityException {
				// Do nothing
			}

            public List<CvsFile> buildFileList(final File moduleLocation, final String prefix) throws IOException {
                AdminHandler adminHandler = new StandardAdminHandler();
                List<CvsFile> fileList = new ArrayList<CvsFile>();

                if (moduleLocation.isFile()) {
                    Entry entry = adminHandler.getEntry(moduleLocation);
                    if (entry != null) {
                        fileList.add(CvsFile.make(entry.getName(), entry.getRevision()));
                    }
                } else {
                    for (File file : adminHandler.getAllFiles(moduleLocation)) {


                        if (file.isFile()) {
                            Entry entry = adminHandler.getEntry(file);
                            CvsFile currentFile = CvsFile.make(prefix + "/" + entry.getName(), entry.getRevision());
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
                                if (!isSymLink(file,listener)) {
                                    fileList.addAll(buildFileList(file, prefix + "/" + file.getName()));
                                } else {
                                    listener.getLogger().println("buildFileList. prevent potential infinate loop, ignoring symlink:" + file);
                                }
                            }
                        }
                    }
                }

                return fileList;
            }

        });
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new CVSChangeLogParser();
    }


}
