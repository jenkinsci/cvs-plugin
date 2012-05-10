/*
 * The MIT License
 * 
 * Copyright (c) 2011-2012, Michael Clarke
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

import hudson.EnvVars;
import hudson.scm.CVSChangeLogSet.CVSChangeLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CvsChangeLogHelper {

    private static CvsChangeLogHelper instance;
    private static final String MAIN_REGEX = "[\\r|\\n]+RCS file:\\s(.+?),[a-z]+[\\r|\\n]+head:\\s+(.*?)"
                    + "[\\r|\\n]+branch:(.*?)[\\r|\\n]+locks:.*?[\\r|\\n]+access list:.*?[\\r|\\n]+symbolic names:(.*?)"
                    + "[\\r|\\n]+keyword substitution:.*?[\\r|\\n]+total revisions:.+?;\\s+selected revisions:\\s+[1-9]+[0-9]*\\s*[\\r|\\n]+"
                    + "description:.*?(([\\r|\\n]+----------------------------[\\r|\\n]+revision\\s+.+?[\\r|\\n]"
                    + "date:\\s+.+?\\;\\s+author:\\s+.+?;.*?[\\r|\\n]+.*?)+)[\\r|\\n]+";
    private static final String SECONDARY_REGEX = "\\s+(.+?)[\\r|\\n]+date:\\s+(.+?)\\;\\s+author:\\s+(.+?);\\s+state:\\s+(.+?);.*?[\\r|\\n]+(.*)";

    private static final DateFormat[] DATE_FORMATTER = new SimpleDateFormat[] {
                    new SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z"), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z"),
                    new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"), };

    private CvsChangeLogHelper() {
    }

    public static CvsChangeLogHelper getInstance() {
        synchronized (CvsChangeLogHelper.class) {
            if (null == instance) {
                instance = new CvsChangeLogHelper();
            }
        }

        return instance;
    }

    public void toFile(final List<CVSChangeLog> repositoryState, final File changelogFile) throws IOException {
        PrintStream output = new PrintStream(new FileOutputStream(changelogFile));

        output.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        output.println("<changelog>");

        for (CVSChangeLog entry : repositoryState) {

            synchronized (DATE_FORMATTER) {
                output.println("\t<entry>");
                output.println("\t\t<changeDate>" + DATE_FORMATTER[3].format(entry.getChangeDate()) + "</changeDate>");
                output.println("\t\t<author><![CDATA[" + entry.getAuthor() + "]]></author>");
            }

            for (CVSChangeLogSet.File file : entry.getFiles()) {

                output.println("\t\t<file>");
                output.println("\t\t\t<name><![CDATA[" + file.getName() + "]]></name>");

                if (file.getFullName() != null) {
                    output.println("\t\t\t<fullName><![CDATA[" + file.getFullName() + "]]></fullName>");
                }

                output.println("\t\t\t<revision>" + file.getRevision() + "</revision>");

                final String previousRevision = file.getPrevrevision();

                if (previousRevision != null) {
                    output.println("\t\t\t<prevrevision>" + previousRevision + "</prevrevision>");
                }

                if (file.isDead()) {
                    output.println("\t\t\t<dead />");
                }

                output.println("\t\t</file>");
            }

            output.println("\t\t<msg><![CDATA[" + entry.getMsg() + "]]></msg>");
            output.println("\t</entry>");
        }
        output.println("</changelog>");
        output.flush();
        output.close();
    }

    /**
     * Converts the output of the cvs log command into a java structure.
     * 
     * @param logContents
     *            output of 'cvs log -S' (with any date filters)
     * @return a list of CVS files with changes in the log
     * @throws IOException
     *             on error parsing log
     */
    public CvsChangeSet mapCvsLog(final String logContents,final CvsRepository repository,
                final CvsRepositoryItem item, final CvsModule module, final EnvVars envVars) {
        final List<CVSChangeLog> changes = new ArrayList<CVSChangeLog>();
        final List<CvsFile> files = new ArrayList<CvsFile>();

        final Pattern mainPattern = Pattern.compile(MAIN_REGEX, Pattern.DOTALL | Pattern.MULTILINE);
        final Pattern innerPattern = Pattern.compile(SECONDARY_REGEX, Pattern.MULTILINE | Pattern.DOTALL);
        for (String section : logContents.split("=============================================================================")) {
            final Matcher mainMatcher = mainPattern.matcher(section);

            if (!mainMatcher.find()) {
               continue;
            }

            /*
             * this is a bit of a hack - we get the root of the module in the
             * remote repository by splitting the CVS URL on a forward slash
             * which should give us the folder path in the second position of
             * the array
             */
            final String rootName = envVars.expand(repository.getCvsRoot()).split("/", 2)[1];
            final String fullName = mainMatcher.group(1);
            final String tipVersion;

            if (CvsRepositoryLocationType.HEAD == item.getLocation().getLocationType()) {
                tipVersion = mainMatcher.group(2);
            } else {
                CvsRepositoryLocation repositoryLocation = item.getLocation();
                tipVersion = getCurrentFileVersion(
                                repositoryLocation.getLocationName(),
                                mainMatcher.group(4),
                                mainMatcher.group(2),
                                repositoryLocation.isUseHeadIfNotFound());
            }

            final String[] cvsChanges = mainMatcher.group(5).split(
                            "[\\r|\\n]+----------------------------[\\r|\\n]+revision");

            for (final String cvsChange : cvsChanges) {
                final Matcher innerMatcher = innerPattern.matcher(cvsChange);

                CvsFile cvsFile = null;
                while (innerMatcher.find()) {

                    Date changeDate = null;

                    synchronized (DATE_FORMATTER) {

                        final String inputDate = innerMatcher.group(2);

                        for (DateFormat dateFormat : DATE_FORMATTER) {
                            try {
                                changeDate = dateFormat.parse(inputDate);
                            } catch (final ParseException ex) {
                                /*
                                 * we can ignore the exception (for now), if
                                 * date is null after exiting the loop then we
                                 * throw an exception then.
                                 */
                            }
                        }

                        if (null == changeDate) {
                            throw new RuntimeException("Date could not be parsed into any recognised format - "
                                            + inputDate);
                        }
                    }

                    final String changeVersion = innerMatcher.group(1);
                    final String changeAuthor = innerMatcher.group(3);
                    final String changeDescription = innerMatcher.group(5);
                    final boolean isDead = innerMatcher.group(4).equals("dead");

                    if (!isChangeValidForFileVersion(changeVersion, tipVersion)) {
                        continue;
                    }

                    final CVSChangeLog change = getCvsChangeLog(changes, changeDescription, changeDate, changeAuthor);

                    final CVSChangeLogSet.File file = new CVSChangeLogSet.File();
                    file.setFullName(fullName);
                    file.setName(fullName.substring(rootName.length() + 2));
                    file.setRevision(changeVersion);
                    if (isDead) {
                        file.setDead();
                    }

                    if (null == cvsFile) {
                        cvsFile = new CvsFile(file.getFullName(), file.getRevision(), isDead);
                        files.add(cvsFile);
                    }

                    change.addFile(file);

                }

            }

        }

        return new CvsChangeSet(files, changes);
    }

    private CVSChangeLog getCvsChangeLog(final List<CVSChangeLog> changes, final String changeDescription,
                    final Date changeDate, final String changeAuthor) {
        final CVSChangeLog changeLogEntry = new CVSChangeLog();
        changeLogEntry.setChangeDate(changeDate);
        changeLogEntry.setMsg(changeDescription);
        changeLogEntry.setUser(changeAuthor);
        for (CVSChangeLog change : changes) {
            if (change.canBeMergedWith(changeLogEntry)) {
                return change;
            }
        }

        changes.add(changeLogEntry);
        return changeLogEntry;
    }

    private boolean isChangeValidForFileVersion(final String changeRevision, final String fileRevision) {
        String[] changeParts = changeRevision.split("\\.");
        String[] fileParts = fileRevision.split("\\.");

        if (fileParts.length != changeParts.length) {
            return false;
        }

        for (int i = 0; i < fileParts.length - 1; i++) {
            if (!changeParts[i].equals(fileParts[i])) {
                return false;
            }
        }

        return (Integer.parseInt(fileParts[fileParts.length - 1]) >= Integer
                        .parseInt(changeParts[changeParts.length - 1]));
    }

    private String getCurrentFileVersion(final String tagName, final String versionAndTagList,
                    final String headVersion, final boolean useHeadIfNotFound) {
        final Pattern pattern = Pattern.compile(tagName + ": ([0-9]+(\\.[0-9]+)+)", Pattern.MULTILINE | Pattern.DOTALL);
        final Matcher matcher = pattern.matcher(versionAndTagList);
        if (!matcher.find()) {
            if (useHeadIfNotFound) {
                return headVersion;
            } else {
                throw new RuntimeException("No file version found for the specified tag - " + versionAndTagList);
            }
        }

        return matcher.group(1);
    }

}
