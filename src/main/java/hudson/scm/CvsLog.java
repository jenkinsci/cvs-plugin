package hudson.scm;

import hudson.EnvVars;
import hudson.scm.CVSChangeLogSet.CVSChangeLog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents raw data produced by "cvs log"/"cvs rlog" and the parse logic to convert it into {@link CVSChangeLogSet}
 *
 * @author Michael Clarke
 * @author Kohsuke Kawaguchi
 */
public abstract class CvsLog {
    /**
     * Reads the "cvs log" output.
     */
    abstract Reader read() throws IOException;

    /**
     * Deletes any data stored by this object.
     */
    abstract void dispose();

    /**
     * Splits cvs log by "============================================================================="
     * and return each "section" separated by it.
     *
     * If an {@link IOException} is encountered while reading data, iterator will throw {@link Error}.
     */
    public Iterable<String> getSections() {
        return new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                try {
                    return new Iterator<String>() {
                        String next;
                        BufferedReader in = new BufferedReader(read());

                        @Override
                        public boolean hasNext() {
                            fetch();
                            return next!=null;
                        }

                        @Override
                        public String next() {
                            fetch();
                            String r = next;
                            next = null;
                            if (r==null)
                                throw new NoSuchElementException();
                            return r;
                        }

                        private void fetch() {
                            if (next!=null)     return; // already have the data fetched
                            if (in==null)       return; // nothing more to read

                            try {
                                StringBuilder buf = new StringBuilder();
                                while (true) {
                                    String line = in.readLine();
                                    if (line==null) {
                                        in.close();
                                        in = null;
                                        break;
                                    }
                                    if (line.equals("============================================================================="))
                                        break;
                                    buf.append(line).append('\n');
                                }
                                next = buf.toString();
                            } catch (IOException e) {
                                throw new Error(e);
                            }
                        }

                        @Override
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                } catch (IOException e) {
                    throw new Error(e);
                }
            }
        };
    }


    /**
     * Parses this output and produces {@link CvsChangeSet}.
     *
     * @return a list of CVS files with changes in the log
     * @throws IOException
     *             on error parsing log
     */
    public CvsChangeSet mapCvsLog(final CvsRepository repository,
                final CvsRepositoryItem item, final CvsModule module, final EnvVars envVars) {

        final List<CVSChangeLog> changes = new ArrayList<CVSChangeLog>();
        final List<CvsFile> files = new ArrayList<CvsFile>();

        DateFormat[] formatters = new SimpleDateFormat[] {
            new SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z"), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z"),
            new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"), };

        for (String section : this.getSections()) {
            final Matcher mainMatcher = MAIN_REGEX.matcher(section);

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

                if (null == tipVersion) {
                    continue;
                }
            }

            final String[] cvsChanges = section.split(
                            "----------------------------[\\r|\\n]+revision");

            for (final String cvsChange : cvsChanges) {
                final Matcher innerMatcher = SECONDARY_REGEX.matcher(cvsChange);

                CvsFile cvsFile = null;
                while (innerMatcher.find()) {

                    Date changeDate = null;

                    final String inputDate = innerMatcher.group(2);

                    for (DateFormat dateFormat : formatters) {
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

        return (Integer.parseInt(fileParts[fileParts.length - 1]) <= Integer
                        .parseInt(changeParts[changeParts.length - 1]));
    }

    private String getCurrentFileVersion(final String tagName, final String versionAndTagList,
                    final String headVersion, final boolean useHeadIfNotFound) {
        final Pattern pattern = Pattern.compile(tagName + ": (([0-9]+\\.)+)0\\.([0-9]+)", Pattern.MULTILINE | Pattern.DOTALL);
        final Matcher matcher = pattern.matcher(versionAndTagList);
        if (!matcher.find()) {
            final Pattern innerPattern = Pattern.compile(tagName + ": ([0-9]+(\\.[0-9]+)+)", Pattern.MULTILINE | Pattern.DOTALL);
            final Matcher innerMatcher = innerPattern.matcher(versionAndTagList);
            if (innerMatcher.find()) {
              return innerMatcher.group(1);
            } else {
                if (useHeadIfNotFound) {
                  return headVersion;
                } else {
                    return null;
                }
            }
        }

        return matcher.group(1) + matcher.group(3) + ".0";
    }

    private static final Pattern MAIN_REGEX = Pattern.compile("[\\r|\\n]+RCS file:\\s(.+?),[a-z]+[\\r|\\n]+head:\\s+(.*?)"
                    + "[\\r|\\n]+branch:(.*?)[\\r|\\n]+locks:.*?[\\r|\\n]+access list:.*?[\\r|\\n]+symbolic names:(.*?)"
                    + "[\\r|\\n]+keyword substitution:.*?[\\r|\\n]+total revisions:.+?;\\s+selected revisions:\\s+[1-9]+[0-9]*\\s*[\\r|\\n]+"
                    + "description:.*?", Pattern.DOTALL | Pattern.MULTILINE);
    private static final Pattern SECONDARY_REGEX = Pattern.compile("\\s+(.+?)[\\r|\\n]+date:\\s+(.+?)\\;\\s+author:\\s+(.+?);\\s+state:\\s+(.+?);.*?[\\r|\\n]+(.*|\\r|\\n])[\\r|\\r\\n]",Pattern.MULTILINE | Pattern.DOTALL);
}
