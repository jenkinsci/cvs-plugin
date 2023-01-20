/*
 * The MIT License
 * 
 * Copyright (c) 2004-2012, Sun Microsystems, Inc., Kohsuke Kawaguchi, Michael Clarke
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

import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.User;
import hudson.scm.CVSChangeLogSet.CVSChangeLog;
import hudson.util.IOException2;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.digester3.Digester;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * {@link ChangeLogSet} for CVS.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CVSChangeLogSet extends ChangeLogSet<CVSChangeLog> {

    private static final String CHANGE_DATE_FORMATTER_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private final List<CVSChangeLog> logs;

    public CVSChangeLogSet(final AbstractBuild<?, ?> build,
            final List<CVSChangeLog> logs) {
        super(build);
        this.logs = Collections.unmodifiableList(logs);
        for (CVSChangeLog log : logs) {
            log.setParent(this);
        }
    }

    public CVSChangeLogSet(final Run<?, ?> build, RepositoryBrowser<?> browser,
            final List<CVSChangeLog> logs) {
        super(build, browser);
        this.logs = Collections.unmodifiableList(logs);
        for (CVSChangeLog log : logs) {
            log.setParent(this);
        }
    }

    /**
     * Returns the read-only list of changes.
     */
    public List<CVSChangeLog> getLogs() {
        return logs;
    }

    @Override
    public boolean isEmptySet() {
        return logs.isEmpty();
    }

    @Override
    public Iterator<CVSChangeLog> iterator() {
        return logs.iterator();
    }

    @Override
    public String getKind() {
        return "cvs";
    }

    public static CVSChangeLogSet parse(final AbstractBuild<?, ?> build,
            final java.io.File f) throws IOException, SAXException {
        ArrayList<CVSChangeLog> r = parseFile(f);

        return new CVSChangeLogSet(build, r);
    }

    public static CVSChangeLogSet parse(final Run<?, ?> build, RepositoryBrowser<?> browser,
            final java.io.File f) throws IOException, SAXException {
        ArrayList<CVSChangeLog> r = parseFile(f);

        return new CVSChangeLogSet(build, browser, r);
    }

	private static ArrayList<CVSChangeLog> parseFile(final java.io.File f)
			throws IOException2, SAXException {
	
        Digester digester = new Digester();

        digester.setXIncludeAware(false);

        if (!Boolean.getBoolean(CVSChangeLogParser.class.getName() + ".UNSAFE")) {
            try {
                digester.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                digester.setFeature("http://xml.org/sax/features/external-general-entities", false);
                digester.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                digester.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            }
            catch (ParserConfigurationException ex) {
                throw new SAXException("Failed to securely configure CVS changelog parser", ex);
            }
        }
        ArrayList<CVSChangeLog> r = new ArrayList<CVSChangeLog>();

        digester.push(r);

        digester.addObjectCreate("*/entry", CVSChangeLog.class);
        digester.addBeanPropertySetter("*/entry/changeDate", "changeDateString");
        digester.addBeanPropertySetter("*/entry/date");
        digester.addBeanPropertySetter("*/entry/time");
        digester.addBeanPropertySetter("*/entry/author", "user");
        digester.addBeanPropertySetter("*/entry/msg");
        digester.addSetNext("*/entry", "add");

        digester.addObjectCreate("*/entry/file", File.class);
        digester.addBeanPropertySetter("*/entry/file/name");
        digester.addBeanPropertySetter("*/entry/file/fullName");
        digester.addBeanPropertySetter("*/entry/file/revision");
        digester.addBeanPropertySetter("*/entry/file/prevrevision");
        digester.addCallMethod("*/entry/file/dead", "setDead");
        digester.addSetNext("*/entry/file", "addFile");

        try {
            digester.parse(f);
        } catch (IOException e) {
            throw new IOException2("Failed to parse " + f, e);
        } catch (SAXException e) {
            throw new IOException2("Failed to parse " + f, e);
        }

        // merge duplicate entries. Ant task somehow seems to report duplicate
        // entries.
        for (int i = r.size() - 1; i >= 0; i--) {
            CVSChangeLog log = r.get(i);
            boolean merged = false;
            if (!log.isComplete()) {
                r.remove(log);
                continue;
            }
            for (int j = 0; j < i; j++) {
                CVSChangeLog c = r.get(j);
                if (c.canBeMergedWith(log)) {
                    c.merge(log);
                    merged = true;
                    break;
                }
            }
            if (merged) {
                r.remove(log);
            }
        }
        
		return r;
	}

    /**
     * In-memory representation of CVS Changelog.
     */
    public static class CVSChangeLog extends ChangeLogSet.Entry implements Serializable {
        private static final DateFormat[] dateFormatters = new SimpleDateFormat[]{
            new SimpleDateFormat(CHANGE_DATE_FORMATTER_PATTERN),
            new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")};

        private String user;
        private String msg;
        private final List<File> files = new ArrayList<File>();
        private long changeDate;
        private CvsRepository repository;

        /**
         * Returns true if all the fields that are supposed to be non-null is
         * present. This is used to make sure the XML file was correct.
         */
        public boolean isComplete() {
            return changeDate != 0 && msg != null;
        }

        /**
         * Checks if two {@link CVSChangeLog} entries can be merged. This is to
         * work around the duplicate entry problems.
         */
        public boolean canBeMergedWith(final CVSChangeLog that) {
            // TODO: perhaps check the date loosely?
            if (!this.getChangeDate().equals(that.getChangeDate())) {
                return false;
            }
            if (this.user == null && that.user != null) {
                 return false;
            }
            if (that.user == null && this.user != null) {
                 return false;
            }
            if (this.user != null && !this.user.equals(that.user)) {
                 return false;
            }
            if (!this.getMsg().equals(that.getMsg())) {
                return false;
            }
            return true;
        }

        // this is necessary since core and CVS belong to different
        // classloaders.
        @Override
        protected void setParent(
                @SuppressWarnings("rawtypes") final ChangeLogSet parent) {
            super.setParent(parent);
        }

        public void merge(final CVSChangeLog that) {
            files.addAll(that.files);
            for (File f : that.files) {
                f.parent = this;
            }
        }

        @Override
        public long getTimestamp() {
            return changeDate;
        }

        @Deprecated
        public void setDate(final String date) {
            // ignore, only applies to very old changelogs
        }

        @Deprecated
        public void setTime(final String time) {
            // ditto
        }

        @Exported
        public Date getChangeDate() {
            if (changeDate == 0) {
                return null;
            }
            return new Date(changeDate);
        }

        @Exported
        public CvsRepository getRepository() {
            return repository;
        }

        public void setRepository(CvsRepository repository) {
            this.repository = repository;
        }

        public void setChangeDate(final Date newChangeDate) {
            changeDate = newChangeDate.getTime();
        }

        public void setChangeDateString(final String changeDate) {
            Date parsedDate = null;
            synchronized (dateFormatters) {
                for (DateFormat format : dateFormatters) {
                    try {
                        parsedDate = format.parse(changeDate);
                        break; //end loop if we get this far
                    } catch (ParseException ex) {
                        // intentionally ignored - complete failure handled later
                    }
                }
            }

            if (parsedDate == null) {
                throw new RuntimeException(changeDate + " could not be parsed using any recognised date formatter.");
            }

            setChangeDate(parsedDate);
        }

        @Override
        @Exported
        public User getAuthor() {
            if (user == null) {
                return User.getUnknown();
            }
            return User.get(user);
        }

        @Override
        public Collection<String> getAffectedPaths() {
            return new AbstractList<String>() {
                @Override
                public String get(final int index) {
                    return files.get(index).getName();
                }

                @Override
                public int size() {
                    return files.size();
                }
            };
        }

        public void setUser(final String author) {
            this.user = author;
        }

        @Override
        @Exported
        public String getMsg() {
            return msg;
        }

        public void setMsg(final String msg) {
            this.msg = msg;
        }

        public void addFile(final File f) {
            f.parent = this;
            files.add(f);
        }

        @Exported
        public List<File> getFiles() {
            return files;
        }

        @Override
        public Collection<File> getAffectedFiles() {
            return files;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((user == null) ? 0 : user.hashCode());
            result = prime
                    * result;
            result = prime * result + ((files == null) ? 0 : files.hashCode());
            result = prime * result + ((msg == null) ? 0 : msg.hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            CVSChangeLog other = (CVSChangeLog) obj;
            if (user == null) {
                if (other.user != null) {
                    return false;
                }
            } else if (!user.equals(other.user)) {
                return false;
            }
            if (changeDate != other.changeDate) {
                return false;
            }
            if (files == null) {
                if (other.files != null) {
                    return false;
                }
            } else if (!files.equals(other.files)) {
                return false;
            }
            if (msg == null) {
                if (other.msg != null) {
                    return false;
                }
            } else if (!msg.equals(other.msg)) {
                return false;
            }
            return true;
        }
    }

    @ExportedBean(defaultVisibility = 999)
    public static class File implements AffectedFile, Serializable {

        private String name;
        private String fullName;
        private String revision;
        private String prevrevision;
        private boolean dead;
        private CVSChangeLog parent;

        /**
         * Inherited from AffectedFile
         */
        @Override
        public String getPath() {
            return getName();
        }

        /**
         * Gets the path name in the CVS repository, like "foo/bar/zot.c"
         *
         * <p>
         * The path is relative to the workspace root.
         */
        @Exported
        public String getName() {
            return name;
        }

        /**
         * Gets the full path name in the CVS repository, like
         * "/module/foo/bar/zot.c"
         *
         * <p>
         * Unlike {@link #getName()}, this method returns a full name from the
         * root of the CVS repository.
         */
        @Exported
        public String getFullName() {
            if (fullName == null) {
                // Hudson < 1.91 doesn't record full path name for CVS,
                // so try to infer that from the current CVS setting.
                // This is an approximation since the config could have changed
                // since this build has done.
                SCM scm = parent.getParent().build.getProject().getScm();
                if (scm instanceof CVSSCM) {
                    CVSSCM cvsscm = (CVSSCM) scm;
                    if (cvsscm.isFlatten()) {
                        fullName = '/'
                                + cvsscm.getRepositories()[0]
                                .getRepositoryItems()[0]
                                .getModules()[0]
                                .getCheckoutName()
                                + '/' + name;
                    } else {
                        // multi-module set up.
                        fullName = '/' + name;
                    }
                } else {
                    // no way to infer.
                    fullName = '/' + name;
                }
            }
            return fullName;
        }

        public void setFullName(final String fullName) {
            this.fullName = fullName;
        }

        /**
         * Gets just the last component of the path, like "zot.c"
         */
        public String getSimpleName() {
            int idx = name.lastIndexOf('/');
            if (idx > 0) {
                return name.substring(idx + 1);
            }
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        @Exported
        public String getRevision() {
            return revision;
        }

        public void setRevision(final String revision) {
            this.revision = revision;
        }

        @Exported
        public String getPrevrevision() {
            return prevrevision;
        }

        public void setPrevrevision(final String prevrevision) {
            this.prevrevision = prevrevision;
        }

        @Exported
        public boolean isDead() {
            return dead;
        }

        public void setDead(boolean dead) {
            this.dead = dead;
        }

        public void setDead() {
            setDead(dead);
        }

        @Override
        @Exported
        public EditType getEditType() {
            // see issue #73. Can't do much better right now
            if (dead) {
                return EditType.DELETE;
            }
            if (revision.equals("1.1")) {
                return EditType.ADD;
            }
            return EditType.EDIT;
        }

        public CVSChangeLog getParent() {
            return parent;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (dead ? 1231 : 1237);
            result = prime * result
                    + ((fullName == null) ? 0 : fullName.hashCode());
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime
                    * result
                    + ((prevrevision == null) ? 0 : prevrevision
                    .hashCode());
            result = prime * result
                    + ((revision == null) ? 0 : revision.hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            File other = (File) obj;
            if (dead != other.dead) {
                return false;
            }
            if (fullName == null) {
                if (other.fullName != null) {
                    return false;
                }
            } else if (!fullName.equals(other.fullName)) {
                return false;
            }
            if (name == null) {
                if (other.name != null) {
                    return false;
                }
            } else if (!name.equals(other.name)) {
                return false;
            }
            /*
            We don't care about the 'parent' value as 2 copies of a file on
            the same version and same state are the same, and can only
            have one parent, so any difference here would be wrong.
            This also reflects the parent missing from hashcode() method

            if (parent == null) {
                if (other.parent != null) {
                    return false;
                }
            } else if (!parent.equals(other.parent)) {
                return false;
            } */
            if (prevrevision == null) {
                if (other.prevrevision != null) {
                    return false;
                }
            } else if (!prevrevision.equals(other.prevrevision)) {
                return false;
            }
            if (revision == null) {
                if (other.revision != null) {
                    return false;
                }
            } else if (!revision.equals(other.revision)) {
                return false;
            }
            return true;
        }
    }

    /**
     * Represents CVS revision number like "1.5.3.2". Immutable.
     */
    public static class Revision {
        public final int[] numbers;

        public Revision(final int[] numbers) {
            this.numbers = numbers;
            assert numbers.length % 2 == 0;
        }

        public Revision(final String s) {
            String[] tokens = s.split("\\.");
            numbers = new int[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                numbers[i] = Integer.parseInt(tokens[i]);
            }
            assert numbers.length % 2 == 0;
        }

        /**
         * Returns a new {@link Revision} that represents the previous revision.
         *
         * For example, {@code "1.5"->"1.4", "1.5.2.13"->"1.5.2.12", "1.5.2.1"->"1.5"}
         *
         * @return null if there's no previous version, meaning this is "1.1"
         */
        public Revision getPrevious() {
            if (numbers[numbers.length - 1] == 1) {
                // x.y.z.1 => x.y
                int[] p = new int[numbers.length - 2];
                System.arraycopy(numbers, 0, p, 0, p.length);
                if (p.length == 0) {
                    return null;
                }
                return new Revision(p);
            }

            int[] p = numbers.clone();
            p[p.length - 1]--;

            return new Revision(p);
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            for (int n : numbers) {
                if (buf.length() > 0) {
                    buf.append('.');
                }
                buf.append(n);
            }
            return buf.toString();
        }
    }

    public void toFile(final java.io.File changelogFile) throws IOException {
        final String encoding = CVSSCM.DescriptorImpl.getOrDie().getChangelogEncoding();
        PrintStream output = new PrintStream(new FileOutputStream(changelogFile), true, encoding);

        DateFormat format = new SimpleDateFormat(CHANGE_DATE_FORMATTER_PATTERN);

        output.println("<?xml version=\"1.0\" encoding=\"" + encoding + "\"?>");
        output.println("<changelog>");

        for (CVSChangeLog entry : this) {

            output.println("\t<entry>");
            output.println("\t\t<changeDate>" + format.format(entry.getChangeDate()) + "</changeDate>");
            output.println("\t\t<author><![CDATA[" + entry.getAuthor() + "]]></author>");

            for (CVSChangeLogSet.File file : entry.getFiles()) {

                output.println("\t\t<file>");
                output.println("\t\t\t<name><![CDATA[" + file.getName() + "]]></name>");

                output.println("\t\t\t<fullName><![CDATA[" + file.getFullName() + "]]></fullName>");

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
}
