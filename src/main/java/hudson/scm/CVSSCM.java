/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jene Jasper, Stephen Connolly, CloudBees, Inc., Michael Clarke
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

import static hudson.Util.fixEmpty;
import static hudson.Util.fixEmptyAndTrim;
import static hudson.Util.fixNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.ModelObject;
import hudson.model.TaskListener;
import hudson.model.TaskThread;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.scm.CVSChangeLogSet.CVSChangeLog;
import hudson.scm.cvs.Messages;
import hudson.security.Permission;
import hudson.util.ArgumentListBuilder;
import hudson.util.AtomicFileWriter;
import hudson.util.FormValidation;
import hudson.util.IOUtils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.taskdefs.Expand;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.framework.io.ByteBuffer;

/**
 * CVS.
 *
 * <p>
 * I couldn't call this class "CVS" because that would cause the view folder name
 * to collide with CVS control files.
 *
 * <p>
 * This object gets shipped to the remote machine to perform some of the work,
 * so it implements {@link Serializable}.
 *
 * @author Kohsuke Kawaguchi
 * @author Michael Clarke
 */
public class CVSSCM extends SCM implements Serializable {

    private static final DateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
    
    /**
     * True to avoid creating a sub-directory inside the workspace.
     * (Works only when there's just one module.)
     */
    private boolean flatten;

    private CVSRepositoryBrowser repositoryBrowser;

    private CvsRepository[] repositories;

    private String cvsRsh;

    private boolean canUseUpdate;
    
    
    // start legacy fields
    private transient String module;
    private transient String cvsroot;
    private transient String branch;
    private transient boolean useHeadIfNotFound;
    private transient boolean isTag;
    private transient String excludedRegions;
    // end legacy fields

  

    /**
     * @deprecated as of V1-0-7
     */
    @Deprecated
    public CVSSCM(String cvsRoot, String allModules,String branch,String cvsRsh,boolean canUseUpdate, boolean useHeadIfNotFound, boolean legacy, boolean isTag, String excludedRegions) {
        this(cvsRoot, allModules, branch, cvsRsh, canUseUpdate, useHeadIfNotFound, legacy, isTag, excludedRegions, null);
    }
    
    @Deprecated
    public CVSSCM(String cvsRoot, String allModules,String branch,String cvsRsh,boolean canUseUpdate, boolean useHeadIfNotFound, boolean legacy, boolean isTag, String excludedRegions, CVSRepositoryBrowser browser) {
        this(convertLegacyConfigToRepositoryStructure(cvsRoot, allModules, branch, isTag, excludedRegions, useHeadIfNotFound), cvsRsh, canUseUpdate, legacy, browser);
    }
    
    @DataBoundConstructor
    public CVSSCM(final List<CvsRepository> repositories, final String cvsRsh, final boolean canUseUpdate, final boolean legacy, final CVSRepositoryBrowser browser) {
        this.repositories = repositories.toArray(new CvsRepository[repositories.size()]);
        this.canUseUpdate = canUseUpdate;
        this.flatten = !legacy && this.repositories.length == 1 && this.repositories[0].getModules().length == 1;
        this.cvsRsh = cvsRsh;
        this.repositoryBrowser = browser;
    }
    
    
    private static List<CvsRepository> convertLegacyConfigToRepositoryStructure(final String cvsRoot, final String allModules, final String branch, final boolean isBranchActuallyTag, final String excludedRegions, final boolean useHeadIfNotFound) {
        List<CvsModule> modules = new ArrayList<CvsModule>();
        String nodeName = fixNull(branch);
        boolean isBranch = !isBranchActuallyTag && !nodeName.equals("");
        boolean isTag = isBranchActuallyTag && !nodeName.equals("");
        CvsModuleLocationType locationType = isTag ? CvsModuleLocationType.TAG : isBranch ? CvsModuleLocationType.BRANCH : CvsModuleLocationType.HEAD;
        CvsModuleLocation location = new CvsModuleLocation(locationType.getName(), isTag ? nodeName : null, isTag ? useHeadIfNotFound : false, isBranch ? nodeName : null, isBranch ? useHeadIfNotFound : false);
        
        for (final String moduleName : convertModulesToList(allModules)) {
            modules.add(new CvsModule(moduleName, "", location));
        }
        
        List<CvsRepository> repositories = new ArrayList<CvsRepository>();
        repositories.add(new CvsRepository(cvsRoot, modules, convertExcludedRegionsToList(excludedRegions), -1));
        return repositories;
    }
    
    private static String[] convertModulesToList(final String modules) {
        // split by whitespace, except "\ "
        String[] moduleNames = modules.split("(?<!\\\\)[ \\r\\n]+");
        // now replace "\ " to " ".
        for (int i = 0; i < moduleNames.length; i++) {
            moduleNames[i] = moduleNames[i].replaceAll("\\\\ "," ");
        }
        return moduleNames;
    }
    
    private static List<ExcludedRegion> convertExcludedRegionsToList(final String excludedRegions) {
        final String[] parts = excludedRegions == null ? new String[]{} : excludedRegions.split("[\\r\\n]+");
        final List<ExcludedRegion> regions = new ArrayList<ExcludedRegion>();
        for (String part : parts) {
            regions.add(new ExcludedRegion(part));
        }
        return regions;
    }
    
    public Object readResolve() {
        /* check if we're running a version of this class that uses multiple repositories.
         * if we are then we can return it as it is
         */
        if (null != repositories) {
            return this;
        }
        
        /* if we've got to this point then we have to do some conversion.
         * Do this by using the deprecated constructor to  upgrade the model through constructor chaining
         */
        return new CVSSCM(cvsroot, module, branch, cvsRsh, canUseUpdate, useHeadIfNotFound, !flatten, isTag, excludedRegions);
    }
    
    /**
     * Calculates the level of compression that should be used for dealing with the given repository.
     * <p>
     * If we're using a local repository then we don't use compression (http://root.cern.ch/root/CVS.html#checkout),
     * if no compression level has been specifically set for this repository then we use the global setting, otherwise
     * we use the one set for this repository.
     * @param repository the repository we're wanting to connect to
     * @return the level of compression to use between 0 and 9 (inclusive),
     *         with 0 being no compression and 9 being maximum
     */
    private int getCompression(final CvsRepository repository) {
        final String cvsroot = repository.getCvsRoot();

        // CVS 1.11.22 manual:
        // If the access method is omitted, then if the repository starts with
        // `/', then `:local:' is assumed.  If it does not start with `/' then
        // either `:ext:' or `:server:' is assumed.
        boolean local = cvsroot.startsWith("/") || cvsroot.startsWith(":local:") || cvsroot.startsWith(":fork:");
        // For local access, compression is senseless. For remote, use whatever the user has specified as the system default:
        // http://root.cern.ch/root/CVS.html#checkout
        int compressionLevel = repository.getCompressionLevel() == -1 ? getDescriptor().getCompressionLevel() : repository.getCompressionLevel();
        return local ? 0 : compressionLevel;
    }
    
    /**
     * Gets the repositories currently configured for this job.
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

    /**
     * This isn't going to do quite what I think was intended, but it's nicer than trying to get info on all files
     * in the workspace as this method seems to expect.
     * <p>
     * The way we use this is that, since we always check if there's changes using rlog, we set the baseline as
     * containing no files (i.e. there's no remote changes). That way any remote changes are always listed and if
     * the remote state from {@link #compareRemoteRevisionWith(AbstractProject, Launcher, FilePath, TaskListener,
     * SCMRevisionState)} is passed as a baseline to itself, we can then check the repository has no further
     * changes since the last update.
     * 
     * @see {@link SCM#calcRevisionsFromBuild(AbstractBuild, Launcher, TaskListener)}
     */
    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        
        final Map<CvsRepository, List<CVSChangeLog>> cvsState = new HashMap<CvsRepository, List<CVSChangeLog>>();
        
        /* loop through each repository and set the remote changes as empty. This may not actually be true (a module
         * could have changed between checkout and and this method being called), but any modules that have actually
         * changed will be picked up in the next comparison against remote.
         */
        for (CvsRepository repository : repositories) {
            cvsState.put(repository, new ArrayList<CVSChangeLog>());                            
        }
        
        return new CvsRevisionState(cvsState);
    }

    /**
     * Checks for differences between the current workspace and the remote repository.
     * 
     * @see {@link SCM#compareRemoteRevisionWith(AbstractProject, Launcher, FilePath, TaskListener, SCMRevisionState)}
     */
    @Override
    protected PollingResult compareRemoteRevisionWith(final AbstractProject<?, ?> project, final Launcher launcher,
                                                      final FilePath workspace, final TaskListener listener,
                                                      final SCMRevisionState baseline)
            throws IOException, InterruptedException {        
        
        // No previous build? everything has changed
        if (project.getLastBuild() == null) {
            return PollingResult.BUILD_NOW;
        }
        
        final Date currentPollDate = Calendar.getInstance().getTime();
        
        boolean changesPresent = false;
        
        Map<CvsRepository, List<CVSChangeLog>> remoteState = null;

         // Loops through every module and check if its changed
         for (CvsRepository repository : repositories) {
            
            final List<Pattern> excludePatterns = new ArrayList<Pattern>();
            
            // check if all the modules currently exist in the workspace. If they don't then we need a build now so we can get them
            for (CvsModule module : repository.getModules()) {
                if (!flatten && (workspace.child(module.getCheckoutName()) == null ||  !workspace.child(module.getCheckoutName()).exists())) {
                    return PollingResult.BUILD_NOW;
                }
            }
            
            for (ExcludedRegion pattern : repository.getExcludedRegions()) {
                try {
                    excludePatterns.add(Pattern.compile(pattern.getPattern()));
                } catch (PatternSyntaxException ex) {
                    launcher.getListener().getLogger().println("Pattern could not be compiled: " + ex.getMessage());
                }
            }

            // get the list of current changed files in this repository
            final List<CVSChangeLog> changes = calculateRepositoryState(project.getLastCompletedBuild().getTime(), currentPollDate, repository, launcher,
                            workspace, listener);
            
            /* Check we have a valid (non-null) baseline. If we don't and the change list isn't empty then we must need a build
             * (the value returned is equivalent to PollingResults.BUILD_NOW)
             */
            if ((baseline == null || !(baseline instanceof CvsRevisionState)) && !changes.isEmpty()) {
                return new PollingResult(new CvsRevisionState(new HashMap<CvsRepository, List<CVSChangeLog>>()), new CvsRevisionState(Collections.singletonMap(repository, changes)), PollingResult.Change.INCOMPARABLE);
            }

            // cast the baseline into a format we can use, and copy the changes list so we can filter the copy
            final CvsRevisionState cvsBaseline = (CvsRevisionState)baseline;
            final List<CVSChangeLog> filteredChanges = new ArrayList<CVSChangeLog>(changes);
            
            // filter out the changes we already know about (the ones in the baseline)
            if (null != cvsBaseline.getModuleState(repository)) {
                filteredChanges.removeAll(cvsBaseline.getModuleState(repository));
            }
            
            // filter out all changes in the exclude regions
            for (final Pattern excludePattern : excludePatterns) {
                for (final CVSChangeLog change : filteredChanges) {
                    for (final String path : change.getAffectedPaths()) {
                        if (excludePattern.matcher(path).matches()) {
                            filteredChanges.remove(change);
                        }
                    }
                }
            }
            
            /* since the remoteState couldn't be retrieved above (since we couldn't check the baseline was valid
             * and prove we had changes) we need to set it now. This will only happen once through the repository
             * loop.
             */
            if (remoteState == null) {
                remoteState = cvsBaseline.getModuleFiles();
            }
            
            /* If we have items after filtering then the current repository has changes in it.
             * To make sure we pick up all changes, we record the new baseline and let processing
             * continue on the rest of the repositories.
             */
            if (!filteredChanges.isEmpty()) {
                changesPresent = true;
                remoteState.put(repository, changes);
            }
        }

        // If we have changes then we want to return the new repository state
        if (changesPresent) {
            return new PollingResult(baseline, new CvsRevisionState(remoteState), PollingResult.Change.SIGNIFICANT);
        }
        
        // If we got this far then no changes were detected
        return PollingResult.NO_CHANGES;
    }
    
   
    /**
     * Builds a list of files that have changed in the given repository between any 2 time-stamps. This does not require
     * the workspace to be checked out and does not change the state of any checked out workspace. The list returned does
     * not have any filters set for exclude regions (i.e. it's every file that's changed for the modules being watched).
     * @param startTime the time-stamp to start filtering changes from
     * @param endTime the time-stamp to end filtering changes from
     * @param repository the repository to search for changes in. All modules under this repository will be checked
     * @param launcher the executor to use when running the CVS command
     * @param workspace the folder to use as the root directory when running the command
     * @param listener where to send log output
     * @return a list of changed files, including their versions and change comments
     * @throws IOException on communication failure (e.g. communication with slaves)
     * @throws InterruptedException on job cancellation
     */
    private List<CVSChangeLog> calculateRepositoryState(final Date startTime, final Date endTime, final CvsRepository repository,
                    final Launcher launcher, final FilePath workspace, final TaskListener listener) throws IOException, InterruptedException {

        final List<CVSChangeLog> files = new ArrayList<CVSChangeLog>();
        final ArgumentListBuilder arguments = new ArgumentListBuilder();
        
        // create an output stream to send the output from CVS command to - we can then parse it from here
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final PrintStream logStream = new PrintStream(outputStream);

        for (final CvsModule module : repository.getModules()) {


            // we have to synchronize since we're dealing with DateFormat.format()
            synchronized (this) {
                final String lastBuildDate = DATE_FORMATTER.format(startTime);
                final String endDate = DATE_FORMATTER.format(endTime);
            

                arguments.add(getDescriptor().getCvsExeOrDefault(), "-d", repository.getCvsRoot(), "rlog", "-S", "-d",  lastBuildDate+"<"+endDate);
            }
            
            // set branch name if selected
            if (module.getModuleLocation().getLocationType().equals(CvsModuleLocationType.BRANCH)) {
                arguments.add("-r" + module.getModuleLocation().getBranchName());
            }

            // set tag name if selected
            if (module.getModuleLocation().getLocationType().equals(CvsModuleLocationType.TAG)) {
                arguments.add("-r" + module.getModuleLocation().getTagName());
            }

            // tell CVS which module we're checking out
            arguments.add(module.getRemoteName());

            // send the command to be run, we can't continue of the task fails
            if (!invoke(launcher, workspace, listener, arguments, logStream)) {
                listener.fatalError("CVS task failed");
                throw new RuntimeException("Error while trying to use CVS");
            }
            
            // flush the output so we have it all available for parsing
            logStream.flush();
            outputStream.flush();

            //  use the parser to build up a list of changed files and add it to the list we've been creating
            files.addAll(CvsChangeLogHelper.getInstance().mapCvsLog(outputStream.toString(), repository, module));
            
            //clear the CVS command so we can rebuild it and reset the output for the next run
            arguments.clear();
            outputStream.reset();
        }
        
        // tidy up - close the output stream
        outputStream.close();

        return files;
    }

    /**
     * Executes a pre-created command, returning whether the command was a success or not.
     * 
     * @param launcher  the launcher to launch the command with
     * @param workspace the workspace the command is being run under
     * @param listener  the listener to pass logging events to
     * @param arguments the command to run
     * @param outputStream  where to log to
     * @return true if command runs fine, false otherwise
     * @throws IOException on communication error during the command
     * @throws InterruptedException is the command is canceled while running
     */
    private boolean invoke(final Launcher launcher, final FilePath workspace, final TaskListener listener, final ArgumentListBuilder arguments,
                    final PrintStream outputStream) throws IOException, InterruptedException {

        final int returnCode = launcher.launch().cmds(arguments).envs(EnvVars.masterEnvVars).stdout(outputStream).pwd(workspace).join();

        if (returnCode != 0) {
            listener.fatalError(getDescriptor().getDisplayName() + " failed. Exit code=" + returnCode);
        }

        return returnCode == 0;
    }

    @Deprecated
    public String getCvsRoot() {
        return null;
    }

    /**
     * Returns true if {@link #getBranch()} represents a tag.
     * <p>
     * This causes Jenkins to stop using "-D" option while check out and update.
     * @deprecated check all modules in each repository to check if it's a tag
     */
    @Deprecated
    public boolean isTag() {
        return false;
    }

    /**
     * If there are multiple modules, return the module directory of the first one.
     * @param workspace
     */
    public FilePath getModuleRoot(FilePath workspace, @SuppressWarnings("rawtypes") AbstractBuild build) {
        if(flatten) {
            return workspace;
        }
        return workspace.child(getRepositories()[0].getModules()[0].getCheckoutName());
    }

    @Override
    public FilePath[] getModuleRoots(FilePath workspace, @SuppressWarnings("rawtypes") AbstractBuild build) {
        if (!flatten) {
            List<FilePath> moduleRoots = new ArrayList<FilePath>();
            for (CvsRepository repository : getRepositories()) {
                for (CvsModule module : repository.getModules()) {
                    moduleRoots.add(workspace.child(module.getCheckoutName()));
                }
            }
            return moduleRoots.toArray(new FilePath[moduleRoots.size()]);
        }
        return new FilePath[]{getModuleRoot(workspace, build)};
    }

    public ChangeLogParser createChangeLogParser() {
        return new CVSChangeLogParser();
    }

    @Deprecated
    public String getAllModules() {
        return null;
    }

    @Deprecated
    public String getExcludedRegions() {
        return null;
    }

    @Deprecated
    public String[] getExcludedRegionsNormalized() {
        return null;
    }

    
    /**
     * List up all modules to check out.
     * @deprecated get module names by looping through all CVS repositories
     */
    @Deprecated
    public String[] getAllModulesNormalized() {
        return null;
    }

    /**
     * Branch to build. Null to indicate the trunk.
     * @deprecated check all modules in all repositories to get branch.
     */
    @Deprecated
    public String getBranch() {
        return null;
    }

    
    @Exported
    public String getCvsRsh() {
        return cvsRsh;
    }

    @Exported
    public boolean getCanUseUpdate() {
        return canUseUpdate;
    }

    @Deprecated
    public boolean getUseHeadIfNotFound() {
        return false;
    }

    @Exported
    public boolean isFlatten() {
        return flatten;
    }

    public boolean isLegacy() {
        return !flatten;
    }


    public boolean checkout(final AbstractBuild<?, ?> build, final Launcher launcher, final FilePath workspace, final BuildListener listener, final File changelogFile) throws IOException, InterruptedException {
        if (!canUseUpdate) {
            workspace.deleteContents();
        }
        
        final String dateStamp;
        
        synchronized(DATE_FORMATTER) {
            dateStamp = DATE_FORMATTER.format(new Date());
        }
        
        final ArgumentListBuilder arguments = new ArgumentListBuilder();

        for (CvsRepository repository : repositories) {
            
            final String compressionLevel = this.getCompression(repository) > 0 ? "-z" + this.getCompression(repository) : null;
            
            for (CvsModule cvsModule : repository.getModules()) {

                final FilePath module = workspace.child(cvsModule.getCheckoutName());

                // create cvs command specifying CVSROOT
                arguments.add(getDescriptor().getCvsExeOrDefault(), "-d", repository.getCvsRoot());

                //compress communication to the requested level
                arguments.add(compressionLevel);
                
                if (canUseUpdate && module.exists()) {
                    // we're doing a CVS update
                    arguments.add("update");
                    
                    //force it to recurse into directories
                    arguments.add("-d", "-R");
                    
                    
                    // point to head, branch or tag
                    if (cvsModule.getModuleLocation().getLocationType() == CvsModuleLocationType.BRANCH) {
                        arguments.add("-r" + cvsModule.getModuleLocation().getBranchName());
                        if (cvsModule.getModuleLocation().isUseHeadIfBranchNotFound()) {
                            arguments.add("-f");
                        }
                    } else if (cvsModule.getModuleLocation().getLocationType() == CvsModuleLocationType.TAG) {
                        arguments.add("-r" + cvsModule.getModuleLocation().getTagName());
                        if (cvsModule.getModuleLocation().isUseHeadIfTagNotFound()) {
                            arguments.add("-f");
                        }
                    } else {
                        arguments.add("-r" + CvsModuleLocationType.HEAD.getName().toUpperCase());
                    }
                    
                    //tell it which module to update - if we're flattening then we ignore any user provided name
                    if (flatten && repositories.length == 1 && repositories[0].getModules().length == 1) {
                        arguments.add(workspace.getName());
                    }
                    else {
                        arguments.add(cvsModule.getCheckoutName());
                    }
                    
                }
                else {
                    // we're doing a CVS checkout
                    arguments.add("co");

                    // point to branch or tag if specified
                    if (cvsModule.getModuleLocation().getLocationType() == CvsModuleLocationType.BRANCH) {
                        arguments.add("-r" + cvsModule.getModuleLocation().getBranchName());
                        if (cvsModule.getModuleLocation().isUseHeadIfBranchNotFound()) {
                            arguments.add("-f");
                        }
                        else {
                            arguments.add("-D", dateStamp);
                        }
                    } else if (cvsModule.getModuleLocation().getLocationType() == CvsModuleLocationType.TAG) {
                        arguments.add("-r" + cvsModule.getModuleLocation().getTagName());
                        if (cvsModule.getModuleLocation().isUseHeadIfTagNotFound()) {
                            arguments.add("-f");
                        }
                    } else if (cvsModule.getModuleLocation().getLocationType() == CvsModuleLocationType.HEAD) {
                        arguments.add("-D", dateStamp);
                    }
    
                    
                    //tell it what to checkout the module as - if we're flattening then we ignore any user set name
                    arguments.add("-d");
                    
                    if (flatten) {
                        arguments.add(workspace.getName());
                    }
                    else {
                        arguments.add(cvsModule.getCheckoutName());
                    }
                    
                    // and specify which module to load
                    arguments.add(cvsModule.getRemoteName());
                }

                final FilePath targetWorkspace = flatten ? workspace.getParent() : workspace;
                
                // invoke the command
                if (!invoke(launcher, targetWorkspace, listener, arguments, listener.getLogger())) {
                    return false;
                }
                
                arguments.clear();
                
                if (flatten) {
                    workspace.act(new StickyDateCleanUpTask());
                } else {
                    module.act(new StickyDateCleanUpTask());
                }

            }

        }


        // build change log
        final Build<?, ?> lastCompleteBuild = (Build<?, ?>) build.getPreviousBuiltBuild();

        if (lastCompleteBuild != null && !skipChangeLog) {
            final List<CVSChangeLog> changes = new ArrayList<CVSChangeLog>();
            for (CvsRepository location : repositories) {
                changes.addAll(calculateRepositoryState(lastCompleteBuild.getTime(), build.getTime(), location, launcher, workspace, listener));
            }
            CvsChangeLogHelper.getInstance().toFile(changes, changelogFile);
        }
        
        
        archiveWorkspace(build, workspace);
        
        build.getActions().add(new TagAction(build));

        return true;
    }
    
    private void archiveWorkspace(final AbstractBuild<?, ?> build, final FilePath ws) throws IOException, InterruptedException {
     // archive the workspace to support later tagging
        File archiveFile = getArchiveFile(build);
        final OutputStream os = new RemoteOutputStream(new FileOutputStream(archiveFile));

        ws.act(new FileCallable<Void>() {

            private static final long serialVersionUID = -7147841423716821535L;

            public Void invoke(File ws, VirtualChannel channel) throws IOException {
                ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os));

                if(flatten) {
                    archive(ws, repositories[0].getModules()[0].getCheckoutName(), zos,true);
                } else {
                    for (CvsRepository repository : getRepositories()) {
                        
                    for (CvsModule module : repository.getModules()) {
                        String m = module.getCheckoutName();
                        File mf = new File(ws, m);

                        if(!mf.exists()) {
                            // directory doesn't exist. This happens if a directory that was checked out
                            // didn't include any file.
                            continue;
                        }

                        if(!mf.isDirectory()) {
                            // this module is just a file, say "foo/bar.txt".
                            // to record "foo/CVS/*", we need to start by archiving "foo".
                            int idx = m.lastIndexOf('/');
                            if(idx == -1) {
                                throw new Error("Kohsuke probe: m="+m);
                            }
                            m = m.substring(0, idx);
                            mf = mf.getParentFile();
                        }
                        archive(mf,m,zos,true);
                    }
                    }
                }
                zos.close();
                return null;
            }
        });
    }
    
    
    /**
     * Returns the file name used to archive the build.
     */
    private static File getArchiveFile(AbstractBuild<?, ?> build) {
        return new File(build.getRootDir(),"workspace.zip");
    }

    /**
     * Archives all the CVS-controlled files in {@code dir}.
     *
     * @param relPath
     *      The path name in ZIP to store this directory with.
     */
    private void archive(File dir,String relPath,ZipOutputStream zos, boolean isRoot) throws IOException {
        Set<String> knownFiles = new HashSet<String>();
        // see http://www.monkey.org/openbsd/archive/misc/9607/msg00056.html for what Entries.Log is for
        parseCVSEntries(new File(dir,"CVS/Entries"),knownFiles);
        parseCVSEntries(new File(dir,"CVS/Entries.Log"),knownFiles);
        parseCVSEntries(new File(dir,"CVS/Entries.Extra"),knownFiles);
        boolean hasCVSdirs = !knownFiles.isEmpty();
        knownFiles.add("CVS");

        File[] files = dir.listFiles();
        if(files==null) {
            if(isRoot)
                throw new IOException("No such directory exists. Did you specify the correct branch? Perhaps you specified a tag: "+dir);
            else
                throw new IOException("No such directory exists. Looks like someone is modifying the workspace concurrently: "+dir);
        }
        for( File f : files ) {
            String name = relPath+'/'+f.getName();
            if(f.isDirectory()) {
                if(hasCVSdirs && !knownFiles.contains(f.getName())) {
                    // not controlled in CVS. Skip.
                    // but also make sure that we archive CVS/*, which doesn't have CVS/CVS
                    continue;
                }
                archive(f,name,zos,false);
            } else {
                if(!dir.getName().equals("CVS"))
                    // we only need to archive CVS control files, not the actual workspace files
                    continue;
                zos.putNextEntry(new ZipEntry(name));
                FileInputStream fis = new FileInputStream(f);
                Util.copyStream(fis,zos);
                fis.close();
                zos.closeEntry();
            }
        }
    }

    /**
     * Parses the CVS/Entries file and adds file/directory names to the list.
     */
    private void parseCVSEntries(File entries, Set<String> knownFiles) throws IOException {
        if(!entries.exists()) {
            return;
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(entries)));
        try {
            String line;
            while((line=in.readLine())!=null) {
                String[] tokens = line.split("/+");
                if(tokens==null || tokens.length<2)    continue;   // invalid format
                knownFiles.add(tokens[1]);
            }
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Override
    public void buildEnvVars(AbstractBuild<?,?> build, Map<String, String> env) {
        if(cvsRsh!=null)
            env.put("CVS_RSH",cvsRsh);
        String cvspass = getDescriptor().getCvspassFile();
        if(cvspass.length()!=0)
            env.put("CVS_PASSFILE",cvspass);
    }

    /**
     * Recursively visits directories and get rid of the sticky date in <tt>CVS/Entries</tt> folder.
     */
    private static final class StickyDateCleanUpTask implements FileCallable<Void> {
        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public Void invoke(File f, VirtualChannel channel) throws IOException {
            process(f);
            return null;
        }

        private void process(File f) throws IOException {
            File entries = new File(f,"CVS/Entries");
            if(!entries.exists())
                return; // not a CVS-controlled directory. No point in recursing

            boolean modified = false;
            String contents;
            try {
                contents = FileUtils.readFileToString(entries);
            } catch (IOException e) {
                // reports like http://www.nabble.com/Exception-while-checking-out-from-CVS-td24256117.html
                // indicates that CVS/Entries may contain something more than we know of. leave them as is
                //LOGGER.log(INFO, "Failed to parse "+entries,e);
                return;
            }
            StringBuilder newContents = new StringBuilder(contents.length());
            String[] lines = contents.split("\n");
            
            for (String line : lines) {
                int idx = line.lastIndexOf('/');
                if(idx==-1) continue;       // something is seriously wrong with this line. just skip.

                String date = line.substring(idx+1);
                if(STICKY_DATE.matcher(date.trim()).matches()) {
                    // the format is like "D2008.01.21.23.30.44"
                    line = line.substring(0,idx+1);
                    modified = true;
                }

                newContents.append(line).append('\n');
            }

            if(modified) {
                // write it back
                AtomicFileWriter w = new AtomicFileWriter(entries,null);
                try {
                    w.write(newContents.toString());
                    w.commit();
                } finally {
                    w.abort();
                }
            }

            // recursively process children
            File[] children = f.listFiles();
            if(children!=null) {
                for (File child : children)
                    process(child);
            }
        }

        private static final Pattern STICKY_DATE = Pattern.compile("D\\d\\d\\d\\d\\.\\d\\d\\.\\d\\d\\.\\d\\d\\.\\d\\d\\.\\d\\d");
    }

    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<CVSSCM> implements ModelObject {
        /**
         * Path to <tt>.cvspass</tt>. Null to default.
         */
        private String cvsPassFile;

        /**
         * Path to cvs executable. Null to just use "cvs".
         */
        private String cvsExe;

        @Deprecated
        private transient boolean noCompression;
        
        /**
         * CVS compression level if individual repositories don't specifically set it.
         */
        private int compressionLevel = 3;
        
        // compatibility only
        @SuppressWarnings("unused")
        private transient Map<String,RepositoryBrowser> browsers;

        // compatibility only
        private class RepositoryBrowser {
            @SuppressWarnings("unused")
            String diffURL;
            @SuppressWarnings("unused")
            String browseURL;
        }

        public DescriptorImpl() {
            super(CVSRepositoryBrowser.class);
            load();
        }

        public String getDisplayName() {
            return "CVS";
        }

        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            CVSSCM scm = req.bindJSON(CVSSCM.class,formData);
            scm.repositoryBrowser = RepositoryBrowsers.createInstance(CVSRepositoryBrowser.class,req,formData,"browser");
            return scm;
        }

        public String getCvspassFile() {
            String value = cvsPassFile;
            if(value==null)
                value = "";
            return value;
        }

        public String getCvsExe() {
            return cvsExe;
        }

        public void setCvsExe(String value) {
            this.cvsExe = value;
            save();
        }

        public String getCvsExeOrDefault() {
            if(Util.fixEmpty(cvsExe)==null)     return "cvs";
            else                                return cvsExe;
        }

        public void setCvspassFile(String value) {
            cvsPassFile = value;
            save();
        }

        @Deprecated
        public boolean isNoCompression() {
            return compressionLevel == 0;
        }
        
        public int getCompressionLevel() {
            return compressionLevel;
        }
        
        public void setCompressionLevel(final int compressionLevel) {
            this.compressionLevel = compressionLevel;
        }
        

        public void load() {
            super.load();
            //used to move to the new data structure
            if (noCompression) {
                compressionLevel = 0;
            }
        }

        @Override
        public boolean configure( StaplerRequest req, JSONObject o ) {
            cvsPassFile = fixEmptyAndTrim(o.getString("cvspassFile"));
            cvsExe = fixEmptyAndTrim(o.getString("cvsExe"));
            String compressionLevel = fixEmptyAndTrim(o.getString("cvsCompression"));
            
            try {
                this.compressionLevel = Integer.parseInt(compressionLevel);
            }
            catch (final NumberFormatException ex) {
                this.compressionLevel = 0;
            }
            save();

            return true;
        }

        @Override
        public boolean isBrowserReusable(CVSSCM x, CVSSCM y) {
            return false;
        }

        /**
         * Returns all {@code CVSROOT} strings used in the current Jenkins installation.
         */
        public Set<String> getAllCvsRoots() {
            Set<String> r = new TreeSet<String>();
            for( AbstractProject<?, ?> p : Hudson.getInstance().getAllItems(AbstractProject.class) ) {
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

    //
    // web methods
    //

        public FormValidation doCheckCvspassFile(@QueryParameter String value) {
            // this method can be used to check if a file exists anywhere in the file system,
            // so it should be protected.
            if(!Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
                return FormValidation.ok();

            value = fixEmpty(value);
            if(value==null) // not entered
                return FormValidation.ok();

            File cvsPassFile = new File(value);

            if(cvsPassFile.exists()) {
                if(cvsPassFile.isDirectory())
                    return FormValidation.error(cvsPassFile+" is a directory");
                else
                    return FormValidation.ok();
            }

            return FormValidation.error("No such file exists");
        }

        /**
         * Checks if cvs executable exists.
         */
        public FormValidation doCheckCvsExe(@QueryParameter String value) {
            return FormValidation.validateExecutable(value);
        }

        /**
         * Displays "cvs --version" for trouble shooting.
         */
        public void doVersion(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
            ByteBuffer baos = new ByteBuffer();
            try {
                Hudson.getInstance().createLauncher(TaskListener.NULL).launch()
                        .cmds(getCvsExeOrDefault(), "--version").stdout(baos).join();
                rsp.setContentType("text/plain");
                baos.writeTo(rsp.getOutputStream());
            } catch (IOException e) {
                req.setAttribute("error",e);
                rsp.forward(this,"versionCheckError",req);
            }
        }

        /**
         * Checks the correctness of the branch name.
         */
        public FormValidation doCheckBranchName(@QueryParameter String value) {
            String v = fixNull(value);

            if(v.equals("HEAD"))
                return FormValidation.error(Messages.CVSSCM_HeadIsNotBranch());

            return FormValidation.ok();
        }
        
        /**
         * Checks the correctness of the branch name.
         */
        public FormValidation doCheckTagName(@QueryParameter String value) {
            String v = fixNull(value);

            if(v.equals("HEAD"))
                return FormValidation.error(Messages.CVSSCM_HeadIsNotTag());

            return FormValidation.ok();
        }
        
        /**
         * Checks the modules remote name has been defined
         */
        public FormValidation doCheckRemoteName(@QueryParameter String value) {
            String v = fixNull(value);
            
            if ("".equals(v)) {
                return FormValidation.error(Messages.CVSSCM_MissingRemoteName());
            }
            
            return FormValidation.ok();
            
            
        }

        /**
         * Checks the entry to the CVSROOT field.
         * <p>
         * Also checks if .cvspass file contains the entry for this.
         */
        public FormValidation doCheckCvsRoot(@QueryParameter String value) throws IOException {
            String v = fixEmpty(value);
            if(v==null)
                return FormValidation.error(Messages.CVSSCM_MissingCvsroot());

            Matcher m = CVSROOT_PSERVER_PATTERN.matcher(v);

            // CVSROOT format isn't really that well defined. So it's hard to check this rigorously.
            if(v.startsWith(":pserver") || v.startsWith(":ext")) {
                if(!m.matches())
                    return FormValidation.error(Messages.CVSSCM_InvalidCvsroot());
                // I can't really test if the machine name exists, either.
                // some cvs, such as SOCKS-enabled cvs can resolve host names that Jenkins might not
                // be able to. If :ext is used, all bets are off anyway.
            }

            // check .cvspass file to see if it has entry.
            // CVS handles authentication only if it's pserver.
            if(v.startsWith(":pserver")) {
                if(m.group(2)==null) {// if password is not specified in CVSROOT
                    String cvspass = getCvspassFile();
                    File passfile;
                    if(cvspass.equals("")) {
                        passfile = new File(new File(System.getProperty("user.home")),".cvspass");
                    } else {
                        passfile = new File(cvspass);
                    }

                    if(passfile.exists()) {
                        // It's possible that we failed to locate the correct .cvspass file location,
                        // so don't report an error if we couldn't locate this file.
                        //
                        // if this is explicitly specified, then our system config page should have
                        // reported an error.
                        if(!scanCvsPassFile(passfile, v))
                            return FormValidation.error(Messages.CVSSCM_PasswordNotSet());
                    }
                }
            }
            return FormValidation.ok();
        }

        /**
         * Validates the excludeRegions Regex
         */
        public FormValidation doCheckPattern(@QueryParameter String value) {
            String v = fixNull(value).trim();

            for (String region : v.split("[\\r\\n]+"))
                try {
                    Pattern.compile(region);
                } catch (PatternSyntaxException e) {
                    return FormValidation.error("Invalid regular expression. " + e.getMessage());
                }
            return FormValidation.ok();
        }

        /**
         * Checks if the given pserver CVSROOT value exists in the pass file.
         */
        private boolean scanCvsPassFile(File passfile, String cvsroot) throws IOException {
            cvsroot += ' ';
            String cvsroot2 = "/1 "+cvsroot; // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5006835
            BufferedReader in = new BufferedReader(new FileReader(passfile));
            try {
                String line;
                while((line=in.readLine())!=null) {
                    // "/1 " version always have the port number in it, so examine a much with
                    // default port 2401 left out
                    int portIndex = line.indexOf(":2401/");
                    String line2 = "";
                    if(portIndex>=0)
                        line2 = line.substring(0,portIndex+1)+line.substring(portIndex+5); // leave '/'

                    if(line.startsWith(cvsroot) || line.startsWith(cvsroot2) || line2.startsWith(cvsroot2))
                        return true;
                }
                return false;
            } finally {
                in.close();
            }
        }

        private static final Pattern CVSROOT_PSERVER_PATTERN =
            Pattern.compile(":(ext|extssh|pserver):[^@:]+(:[^@:]+)?@[^:]+:(\\d+:)?.+");

        /**
         * Runs cvs login command.
         *
         * TODO: this apparently doesn't work. Probably related to the fact that
         * cvs does some tty magic to disable echo back or whatever.
         */
        public void doPostPassword(StaplerRequest req, StaplerResponse rsp) throws IOException, InterruptedException {
            Hudson.getInstance().checkPermission(Hudson.ADMINISTER);

            String cvsroot = req.getParameter("cvsroot");
            String password = req.getParameter("password");

            if(cvsroot==null || password==null) {
                rsp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            rsp.setContentType("text/plain");
            Proc proc = Hudson.getInstance().createLauncher(TaskListener.NULL).launch()
                    .cmds(getCvsExeOrDefault(), "-d",cvsroot,"login")
                    .stdin(new ByteArrayInputStream((password+"\n").getBytes()))
                    .stdout(rsp.getOutputStream()).start();
            proc.join();
        }
    }

    /**
     * Action for a build that performs the tagging.
     */
    @ExportedBean
    public final class TagAction extends AbstractScmTagAction implements Describable<TagAction> {

        /**
         * If non-null, that means the build is already tagged.
         * If multiple tags are created, those are whitespace-separated.
         */
        private volatile String tagName;

        public TagAction(AbstractBuild<?, ?> build) {
            super(build);
        }

        public String getIconFileName() {
            if(tagName==null && !build.getParent().getACL().hasPermission(TAG))
                return null;
            return "save.gif";
        }

        public String getDisplayName() {
            if(tagName==null)
                return Messages.CVSSCM_TagThisBuild();
            if(tagName.indexOf(' ')>=0)
                return Messages.CVSSCM_DisplayName2();
            else
                return Messages.CVSSCM_DisplayName1();
        }

        @Exported
        public String[] getTagNames() {
            if(tagName==null)   return new String[0];
            return tagName.split(" ");
        }

        /**
         * Checks if the value is a valid CVS tag name.
         */
        public synchronized FormValidation doCheckTag(@QueryParameter String value) {
            String tag = fixNull(value).trim();
            if(tag.length()==0) // nothing entered yet
                return FormValidation.ok();
            return FormValidation.error(isInvalidTag(tag));
        }

        @Override
        public Permission getPermission() {
            return TAG;
        }

        @Override
        public String getTooltip() {
            if(tagName!=null)   return "Tag: "+tagName;
            else                return null;
        }

        @Override
        public boolean isTagged() {
            return tagName!=null;
        }

        /**
         * Invoked to actually tag the workspace.
         */
        @SuppressWarnings("unchecked")
        public synchronized void doSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            build.checkPermission(getPermission());

            Map<AbstractBuild<?,?>,String> tagSet = new HashMap<AbstractBuild<?,?>,String>();

            String name = fixNull(req.getParameter("name")).trim();
            String reason = isInvalidTag(name);
            if(reason!=null) {
                sendError(reason,req,rsp);
                return;
            }

            tagSet.put(build,name);

            if(req.getParameter("upstream")!=null) {
                // tag all upstream builds
                Enumeration<String> e = req.getParameterNames();
                Map<AbstractProject<?,?>, Integer> upstreams = build.getTransitiveUpstreamBuilds(); // TODO: define them at AbstractBuild level

                while(e.hasMoreElements()) {
                    String upName = e.nextElement();
                    if(!upName.startsWith("upstream."))
                        continue;

                    String tag = fixNull(req.getParameter(upName)).trim();
                    reason = isInvalidTag(tag);
                    if(reason!=null) {
                        sendError(Messages.CVSSCM_NoValidTagNameGivenFor(upName,reason),req,rsp);
                        return;
                    }

                    upName = upName.substring(9);   // trim off 'upstream.'
                    AbstractProject<?,?> p = Hudson.getInstance().getItemByFullName(upName,AbstractProject.class);
                    if(p==null) {
                        sendError(Messages.CVSSCM_NoSuchJobExists(upName),req,rsp);
                        return;
                    }

                    Integer buildNum = upstreams.get(p);
                    if(buildNum==null) {
                        sendError(Messages.CVSSCM_NoUpstreamBuildFound(upName),req,rsp);
                        return;
                    }

                    Run<?,?> build = p.getBuildByNumber(buildNum);
                    tagSet.put((AbstractBuild<?,?>) build,tag);
                }
            }

            new TagWorkerThread(this,tagSet).start();

            doIndex(req,rsp);
        }

        /**
         * Checks if the given value is a valid CVS tag.
         *
         * If it's invalid, this method gives you the reason as string.
         */
        private String isInvalidTag(String name) {
            // source code from CVS rcs.c
            //void
            //RCS_check_tag (tag)
            //    const char *tag;
            //{
            //    char *invalid = "$,.:;@";		/* invalid RCS tag characters */
            //    const char *cp;
            //
            //    /*
            //     * The first character must be an alphabetic letter. The remaining
            //     * characters cannot be non-visible graphic characters, and must not be
            //     * in the set of "invalid" RCS identifier characters.
            //     */
            //    if (isalpha ((unsigned char) *tag))
            //    {
            //    for (cp = tag; *cp; cp++)
            //    {
            //        if (!isgraph ((unsigned char) *cp))
            //        error (1, 0, "tag `%s' has non-visible graphic characters",
            //               tag);
            //        if (strchr (invalid, *cp))
            //        error (1, 0, "tag `%s' must not contain the characters `%s'",
            //               tag, invalid);
            //    }
            //    }
            //    else
            //    error (1, 0, "tag `%s' must start with a letter", tag);
            //}
            if(name==null || name.length()==0)
                return Messages.CVSSCM_TagIsEmpty();

            char ch = name.charAt(0);
            if(!(('A'<=ch && ch<='Z') || ('a'<=ch && ch<='z')))
                return Messages.CVSSCM_TagNeedsToStartWithAlphabet();

            for( char invalid : "$,.:;@".toCharArray() ) {
                if(name.indexOf(invalid)>=0)
                    return Messages.CVSSCM_TagContainsIllegalChar(invalid);
            }

            return null;
        }

        /**
         * Performs tagging.
         */
        public void perform(String tagName, TaskListener listener) {
            File destdir = null;
            try {
                destdir = Util.createTempDir();

                // unzip the archive
                listener.getLogger().println(Messages.CVSSCM_ExpandingWorkspaceArchive(destdir));
                Expand e = new Expand();
                e.setProject(new org.apache.tools.ant.Project());
                e.setDest(destdir);
                e.setSrc(getArchiveFile(build));
                e.setTaskType("unzip");
                e.execute();

                // run cvs tag command
                listener.getLogger().println(Messages.CVSSCM_TaggingWorkspace());
                for (CvsRepository repository : getRepositories()) {
                    for (CvsModule module : repository.getModules()) {
                        FilePath path = new FilePath(destdir).child(module.getCheckoutName());
                        boolean isDir = path.isDirectory();
    
                        ArgumentListBuilder cmd = new ArgumentListBuilder();
                        cmd.add(CVSSCM.this.getDescriptor().getCvsExeOrDefault(),"tag");
                        if(isDir) {
                            cmd.add("-R");
                        }
                        cmd.add(tagName);
                        if(!isDir) {
                            cmd.add(path.getName());
                            path = path.getParent();
                        }
                        if(!invoke(new Launcher.LocalLauncher(listener), path, listener, cmd, listener.getLogger())) {
                            listener.getLogger().println(Messages.CVSSCM_TaggingFailed());
                            return;
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
                    if(destdir!=null) {
                        listener.getLogger().println("cleaning up "+destdir);
                        Util.deleteRecursive(destdir);
                    }
                } catch (IOException e) {
                    e.printStackTrace(listener.fatalError(e.getMessage()));
                }
            }
        }

        /**
         * Atomically set the tag name and then be done with {@link TagWorkerThread}.
         */
        private synchronized void onTagCompleted(String tagName) {
            if(this.tagName!=null)
                this.tagName += ' '+tagName;
            else
                this.tagName = tagName;
            this.workerThread = null;
        }

        @SuppressWarnings("unchecked")
        public Descriptor<TagAction> getDescriptor() {
            return Hudson.getInstance().getDescriptorOrDie(getClass());
        }
    }

    @Extension
    public static final class TagActionDescriptor extends Descriptor<TagAction> {
        public TagActionDescriptor() {
            super(CVSSCM.TagAction.class);
        }

        public String getDisplayName() {
            return "";
        }
    }

    public static final class TagWorkerThread extends TaskThread {
        private final Map<AbstractBuild<?,?>,String> tagSet;

        public TagWorkerThread(TagAction owner,Map<AbstractBuild<?,?>,String> tagSet) {
            super(owner,ListenerAndText.forMemory(null));
            this.tagSet = tagSet;
        }

        @Override
        public synchronized void start() {
            for (Entry<AbstractBuild<?,?>, String> e : tagSet.entrySet()) {
                TagAction ta = e.getKey().getAction(TagAction.class);
                if(ta!=null)
                    associateWith(ta);
            }

            super.start();
        }

        protected void perform(TaskListener listener) {
            for (Entry<AbstractBuild<?,?>, String> e : tagSet.entrySet()) {
                TagAction ta = e.getKey().getAction(TagAction.class);
                if(ta==null) {
                    listener.error(e.getKey()+" doesn't have CVS tag associated with it. Skipping");
                    continue;
                }
                listener.getLogger().println(Messages.CVSSCM_TagginXasY(e.getKey(),e.getValue()));
                // Removed for JENKINS-8128
                //try {
                //    e.getKey().keepLog();
                //} catch (IOException x) {
                //    x.printStackTrace(listener.error(Messages.CVSSCM_FailedToMarkForKeep(e.getKey())));
                //}
                ta.perform(e.getValue(), listener);
                listener.getLogger().println();
            }
        }
    }

   
    private static final long serialVersionUID = 1L;

    /**
     * True to avoid computing the changelog. Useful with ancient versions of CVS that doesn't support
     * the -d option in the log command. See #1346.
     */
    private static boolean skipChangeLog = Boolean.getBoolean(CVSSCM.class.getName()+".skipChangeLog");

}
