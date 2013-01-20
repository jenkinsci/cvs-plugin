package hudson.scm;

import hudson.scm.CVSChangeLogSet.CVSChangeLog;
import org.netbeans.lib.cvsclient.CVSRoot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents raw data produced by "cvs log"/"cvs rlog" and the parse logic to convert it into {@link CVSChangeLogSet}
 *
 * @author Michael Clarke
 * @author Kohsuke Kawaguchi
 */
public abstract class CvsLog {

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final Pattern DOT_PATTERN = Pattern.compile("(([0-9]+\\.)+)0\\.([0-9]+)");
    private static final String CHANGE_DIVIDER = "----------------------------";
    private static final String FILE_DIVIDER = "===========================";

    private static enum Status {
        FILE_NAME,
        FILE_NAME_PREVIOUS_LINE,
        CHANGE_HEADER,
        CHANGE_COMMENT,
        FILE_VERSION,
        FILE_BRANCH_NAMES
    }

    /**
     * Reads the "cvs log" output.
     * @return the Reader to pull output from.
     * @throws IOException on failure reading stored output
     */
    protected abstract Reader read() throws IOException;

    /**
     * Deletes any data stored by this object.
     */
    protected abstract void dispose();

    public CvsChangeSet mapCvsLog(final String cvsRoot, final CvsRepositoryLocation location) throws IOException {
        final List<CVSChangeLog> changes = new ArrayList<CVSChangeLog>();
        final Map<String, CvsFile> files = new HashMap<String, CvsFile>();
        CVSChangeLogSet.File file = null;
        CVSChangeLog change = null;
        final Map<String,String> branches = new HashMap<String,String>();
        final Set<String> tagNames = new TreeSet<String>();
        final Set<String> branchNames = new TreeSet<String>();
        final BufferedReader reader = new BufferedReader(read());
        Status status = Status.FILE_NAME;
        String line;
        String previousLine = null;
        String prePreviousLine = null;
        while ((line = reader.readLine()) != null) {
            switch (status) {
                case FILE_NAME:
                    branches.clear();
                    file = new CVSChangeLogSet.File();
                    status = parseFileName(line, file, status, cvsRoot);
                    break;
                case FILE_NAME_PREVIOUS_LINE:
                    branches.clear();
                    file = new CVSChangeLogSet.File();
                    status = parseFileName(previousLine, file, status, cvsRoot);
                    //we don't break here because we now want to continue parsing 'line'.
                    //we should be safe having prePrevious line skipped since we know it contained ====
                case FILE_BRANCH_NAMES:
                    status = parseBranchNames(line, status, branches, branchNames, tagNames);
                    break;

                case FILE_VERSION:
                    status = parseChangeVersion(line, file, status);
                    break;

                case CHANGE_HEADER:
                    change = new CVSChangeLog();
                    status = parseChangeHeader(line, file, change, status);
                    break;

                case CHANGE_COMMENT:
                    status = processComment(line, file, change, status, branches, previousLine, changes, files, location, prePreviousLine);
                    break;

            }
            prePreviousLine = previousLine;
            previousLine = line;
        }

        // if we've reached the end of the RLOG output then we may still have comment lines to parse (the last 2 lines)
        // given the way the comments are parsed (initially skip what looks like a divider line, then re-parse it if it
        // the following lines don't aren't empty and contain 'RCS file:' (or are null) respectively
        if (status == Status.CHANGE_COMMENT) {
            status = processComment(null, file, change, Status.CHANGE_COMMENT, branches, line, changes, files, location, previousLine);
        }

        if (status == Status.CHANGE_COMMENT) {
            //we don't care about the return status now - so don't save it
            processComment(null, file, change, Status.CHANGE_COMMENT, branches, null, changes, files, location, line);
        }
        reader.close();
        dispose();
        return new CvsChangeSet(new ArrayList<CvsFile>(files.values()), changes, branchNames, tagNames);

    }

    /**
     * Retrieves the file name from the CVS Rlog output line. Expects the line to be
     * <tt>RCS file: /path/to/file.ext</tt> and will skip any other lines. Sets the extracted name
     * in the provided file.
     * @param line the line to parse the filename from
     * @param file the file object being created in the current iteration.
     * @param currentStatus the current parsing status
     * @param cvsRoot the cvsRoot used for connecting during the RLOG collection
     * @return the parsing status to use for the next line, either the current parsing status or FILE_BRANCH_NAMES.
     */
    private Status parseFileName(final String line, final CVSChangeLogSet.File file, final Status currentStatus,
                                 final String cvsRoot) {
        // defensive check - we can only get the file path if the line starts with 'RCS file:'
        if (!line.startsWith("RCS file:")) {
            return currentStatus;
        }

        // drop the 'RCS file:' label from the line and strip extra whitespace
        final String fileName = line.substring(10).trim();

        // remove the ',v' tag from the end of the file
        final String filePath = fileName.substring(0, fileName.length()-2);
        file.setFullName(filePath);

        // get the root directory from cvs root (e.g :pserver:host/path/to/repo/
        // gives /path/to/repo/ and remove it from the file path
        final String rootName = CVSRoot.parse(cvsRoot).getRepository();
        file.setName(filePath.substring(rootName.length() + 1));

        return Status.FILE_BRANCH_NAMES;
    }

    /**
     * Attempts to extracts the branch name from the current line. Requires the line to start with
     * a tab character to be parsed.
     * @param line the current CVS Rlog line to parse
     * @param currentStatus the current processing status
     * @param branches the list of branch names to add the parsed branch to.
     * @param branchNames the set of tags names parsed from any files in the module being parsed.
     * @param tagNames the set of tags names parsed from any files in the module being parsed.
     * @return the type to parse the next line as, either the currentStatus or FILE_VERSION
     */
    private Status parseBranchNames(final String line, final Status currentStatus, final Map<String, String> branches,
                                    final Set<String> branchNames, final Set<String> tagNames) {

        if (line.startsWith("keyword substitution:")) {
            //we've passed the branch/tag list, move onto the next content type
            return Status.FILE_VERSION;
        }
        else if (!line.startsWith("\t")) {
            //not a valid branch/tag line, skip it
            return currentStatus;
        }

        final String trimmedLine = line.trim();
        final int colonLocation = trimmedLine.lastIndexOf(':');

        // no colon in this line - doesn't seem to be a valid branch line so skip it
        if(colonLocation == -1) {
            return currentStatus;
        }

        // get the name of the branch/tag from the current line
        final String name = trimmedLine.substring(0, colonLocation).trim();

        // check the format of the associated file version. Branch versions are
        // n.n.0.n, tags do not have the second last section as 0. Tags cannot have
        // changelog entries so can safely be skipped
        final Matcher versionMatcher = DOT_PATTERN.matcher(trimmedLine.substring(colonLocation + 2));

        if(!versionMatcher.matches()) {
            // doesn't match branch format (see above), so suspect it's a tag. Collect is and keep it for now
            tagNames.add(name);
            return currentStatus;
        }

        branchNames.add(name);
        
        // add the branch to to the list, skipping the second last item in the group
        // since it's 0 and isn't used in the changelog file versions
        branches.put(versionMatcher.group(1) +versionMatcher.group(3) + '.', name);

        //we're still in the branch/tag parsing stage
        return currentStatus;
    }


    /**
     * Attempts to parse the current file version from the first change listed against the file.
     * @param line the current line from CVS RLOG to try and parse
     * @param file the file object to set the parsed revision from
     * @param currentStatus the status used for parsing the current line
     * @return the status for parsing the next line, one of currentStatus, CHANGE_HEADER, FILE_NAME
     */
    private Status parseChangeVersion(final String line, final CVSChangeLogSet.File file, final Status currentStatus) {
        if (line.startsWith("revision")) {
            // we're on a revision line, get the version number and move onto the next section
            file.setRevision(line.substring(9));
            return Status.CHANGE_HEADER;
        } else if (line.startsWith(FILE_DIVIDER)) {
            //This file has no changes, so skip it and move onto the next file
            return Status.FILE_NAME;
        }

        return currentStatus;
    }

    /**
     * Attempts to parse the change details (author, change date and file status) from a change status line.
     * If the line does not start with 'date:' then it will be skipped.
     * @param line the current line from CVS RLOG to parse the change details from
     * @param file the file to set the dead status on if indicated in the current line
     * @param change the change to set the parsed details (date and author) in
     * @param currentStatus the status being used for parsing the current line
     * @return the status for parsing the next line, one of currentStatus, or CHANGE_COMMENT
     */
    private Status parseChangeHeader(final String line, final CVSChangeLogSet.File file, final CVSChangeLog change,
                                     final Status currentStatus) {

        if (!line.startsWith("date:")) {
            // we're only interested in a line starting with 'date:'. Skip this line otherwise.
            return currentStatus;
        }


        int semiColonIndex = line.indexOf(";");

        // the date is between the end of 'date' and the first semi-colon
        change.setChangeDateString(line.substring(6, semiColonIndex));

        // get the rest of the line content for parsing
        final String remainingLineContent = line.substring(semiColonIndex + 1);

        semiColonIndex = remainingLineContent.indexOf(";");

        // username is between 'author' and the next semi-colon
        change.setUser(remainingLineContent.substring(10, semiColonIndex));

        // file is deleted if line contains 'state: dead'
        file.setDead(remainingLineContent.contains("state: dead;"));

        change.setMsg("");

        return Status.CHANGE_COMMENT;

    }

    /**
     * Parses the version number from the previous change for this file from a line in the CVS RLOG output.
     * Performs a defensive check to check for valid RLOG output, throws an IllegalArgumentException if format
     * is not parseable. Saves the current change after parsing the version number.
     * @param line the current line from CVS RLOG to try and parse
     * @param file the file to set the parsed revision from
     * @param change the change to save following parsing
     * @param branches the list of branches with file version numbers to use when saving the changes
     * @param changes the list of changes to save the current change to
     * @param files the list of files to save the current file to
     * @param location the CVS location (head/branch/tag) the CVS RLOG was collected from
     */
    private void parsePreviousChangeVersion(final String line, final CVSChangeLogSet.File file, final CVSChangeLog change,
                                            final Map<String, String> branches, final List<CVSChangeLog> changes,
                                            final Map<String, CvsFile> files, final CvsRepositoryLocation location) {
        if (!line.startsWith("revision")) {
            throw new IllegalStateException("Unexpected line from CVS log: " + line);
        }

        final String revision = line.substring(9);
        file.setPrevrevision(revision);

        saveChange(file, change, branches, changes, files, location);

        file.setRevision(revision);
    }

    /**
     * Parses the change comment line from the current CVS RLOG line. Checks we're not on a change divide line (-------)
     * or a file divide line (=======).
     * @param line the current line from CVS RLOG to parse
     * @param file the file we're building the change for
     * @param change the change to add the comment to
     * @param currentStatus the current parsing status
     * @param branches the list of branches for the current file
     * @param previousLine the previous line from the CVS RLOG output
     * @param changes the list of previously saved changes
     * @param files the list of previously parsed files
     * @param location the location (head/branch/tag) the CVS RLOG was retrieved from
     * @param prePreviousLine the line before the preiovus line from the CVS RLOG output
     * @return what the next line should be parsed as, one of currentStatus, FILE_NAME, CHANGE_HEADER
     */
    private Status processComment(final String line, final CVSChangeLogSet.File file, final CVSChangeLog change,
                                  final Status currentStatus, final Map<String, String> branches,
                                  final String previousLine, final List<CVSChangeLog> changes, final Map<String, CvsFile> files,
                                  final CvsRepositoryLocation location, final String prePreviousLine) {
        if (line != null && line.startsWith(FILE_DIVIDER)) {
            if (previousLine.equals(CHANGE_DIVIDER)) {
                updateChangeMessage(change, previousLine);
            }
            return currentStatus;
        } else if (previousLine != null && previousLine.startsWith(FILE_DIVIDER)) {
            if (line != null  && line.isEmpty()) {
                //we could be on a line between files
                return currentStatus;
            } else {
                updateChangeMessage(change, previousLine);
            }
        } else if (prePreviousLine != null && prePreviousLine.startsWith(FILE_DIVIDER)) {
            // we've reached the end of the changes for the current file. Save the current change
            // and start processing the next file
            if ((previousLine == null || previousLine.isEmpty())
                    && (line  == null || line.startsWith("RCS file:"))) {
                saveChange(file, change, branches, changes, files, location);
                return Status.FILE_NAME_PREVIOUS_LINE;
            } else {
                updateChangeMessage(change, prePreviousLine);
                updateChangeMessage(change, previousLine);
                return currentStatus;
            }
        } else if (previousLine != null && previousLine.startsWith(CHANGE_DIVIDER)) {
            if (line != null && line.startsWith("revision")) {
                // the previous commit line has ended and we're now in a new commit.
                // Add the current change to our changeset and start processing the next commit
                parsePreviousChangeVersion(line, file, change, branches, changes, files, location);
                return Status.CHANGE_HEADER;
            } else {
                // see next else if line - we may have skipped a line that contains '-------'.
                // if we don't now have a 'revision' line then the line we skipped was actually
                // part of a comment so we need to include it in the current change
                updateChangeMessage(change, previousLine);
                updateChangeMessage(change, line);
            }
        } else if (line != null && line.startsWith(CHANGE_DIVIDER)) {
            // don't do anything yet, this could be either a part of the current comment
            // or a dividing line
            return currentStatus;
        } else {
            // nothing special on this line, add it to the current change comment
            updateChangeMessage(change, line);
        }
        return currentStatus;
    }

    /**
     * Adds the current line onto the current change message, with a line break if
     * this is not the first line of the comment.
     * @param change the change with the comment to add the current line to
     * @param line the line to add to the comment
     */
    private void updateChangeMessage(final CVSChangeLog change, final String line) {
        String message = change.getMsg();
        if (message.isEmpty()) {
            message += line;
        } else {
            message += LINE_SEPARATOR + line;
        }
        change.setMsg(message);
    }


    /**
     * Checks the current change is valid for the selected branch/tag/head and saves it to the current list
     * of changes and adds the file to the list of changed files
     * @param file the file that's changed
     * @param change the change for the current file
     * @param branches the list of branches for the current file
     * @param changes the list of changes to add the current change to
     * @param files the list of files to add the current file to
     * @param location the CVS Repository location (head/branch/tag) the CVS RLOG was retrieved from
     */
    private void saveChange(final CVSChangeLogSet.File file, final CVSChangeLog change, final Map<String, String> branches,
                            final List<CVSChangeLog> changes, final Map<String, CvsFile> files, final CvsRepositoryLocation location) {

        final String branch = getBranchNameForRevision(file.getRevision(), branches);

        // check we're on head if the branch name is null
        if (branch == null && !(location instanceof CvsRepositoryLocation.HeadRepositoryLocation)) {
            return;
        }

        if (branch != null && location instanceof CvsRepositoryLocation.HeadRepositoryLocation) {
            return;
        }

        // Check the branch/tag name matches the retrieved branch name
        if (!(location instanceof CvsRepositoryLocation.HeadRepositoryLocation)
                && !location.getLocationName().equals(branch)) {
            return;
        }

        CVSChangeLog currentChange = change;
        boolean addChange = true;

        // check the change isn't the same as any other change
        for (final CVSChangeLog existingChange : changes) {
            if (change.canBeMergedWith(existingChange)) {
                // equivalent to merging the new and existing change
                currentChange = existingChange;
                addChange = false;
                break;
            }
        }

        // we only want the first listing of this file since changes are
        // sorted in reverse order of when they were made
        if (!files.containsKey(file.getFullName())) {
            final CvsFile cvsFile = new CvsFile(file.getSimpleName(), file.getRevision(), file.isDead());
            files.put(file.getFullName(), cvsFile);
        }

        if (addChange) {
            changes.add(currentChange);
        }

        // we have to copy the file and save the copy since the passed file gets internally
        // modified during following calls
        final CVSChangeLogSet.File localFile = new CVSChangeLogSet.File();
        localFile.setRevision(file.getRevision());
        localFile.setDead(file.isDead());
        localFile.setFullName(file.getFullName());
        localFile.setName(file.getName());
        localFile.setPrevrevision(file.getPrevrevision());

        currentChange.addFile(localFile);

    }

    /**
     * Finds the name of the branch for the requested file revision.
     * @param revision the file revision to lookup the branch name for
     * @param branches the list of branches to search through
     * @return either null if revision is null or no branch match, or the name of the matching branch.
     */
    private String getBranchNameForRevision(final String revision, final Map<String, String> branches) {
        if(null == revision) {
            // prevent a NPE later if we failed to parse a revision line
            return null;
        }

        for (final Map.Entry<String,String> e : branches.entrySet()) {
            if(revision.startsWith(e.getKey()) && revision.substring(e.getKey().length()).indexOf('.')==-1)
                return e.getValue();
        }

        //no match
        return null;
    }


}