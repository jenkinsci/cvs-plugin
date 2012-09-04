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
import hudson.model.User;
import hudson.scm.CVSChangeLogSet.CVSChangeLog;
import hudson.util.Digester2;
import hudson.util.IOException2;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import jenkins.model.Jenkins;
import org.apache.commons.digester.Digester;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.xml.sax.SAXException;

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
        Digester digester = new Digester2();
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

        return new CVSChangeLogSet(build, r);
    }

    /**
     * In-memory representation of CVS Changelog.
     */
    public static class CVSChangeLog extends ChangeLogSet.Entry {
        private static final DateFormat[] dateFormatters = new SimpleDateFormat[]{
            new SimpleDateFormat(CHANGE_DATE_FORMATTER_PATTERN),
            new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")};
        private static final DateFormat DATE_FORMATTER = new SimpleDateFormat(
                "yyyy-MM-dd");
        private static final DateFormat TIME_FORMATTER = new SimpleDateFormat(
                "HH:mm");

        private User author;
        private String msg;
        private final List<File> files = new ArrayList<File>();
        private Calendar changeDate;

        /**
         * Returns true if all the fields that are supposed to be non-null is
         * present. This is used to make sure the XML file was correct.
         */
        public boolean isComplete() {
            return changeDate != null && msg != null;
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
            if (!this.getAuthor().equals(that.getAuthor())) {
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

        /**
         * @deprecated use getChangeDate
         */
        @Deprecated
        public String getDate() {
            if (getChangeDate() == null) {
                return null;
            }
            synchronized (this) {
                return DATE_FORMATTER.format(getChangeDate());
            }
        }

        @Deprecated
        public void setDate(final String date) {
            if (null == date) {
                return;
            }
            Calendar changeDate = this.changeDate;
            if (changeDate == null) {
                changeDate = Calendar.getInstance();
                changeDate.set(Calendar.HOUR, 0);
                changeDate.set(Calendar.MINUTE, 0);
                changeDate.set(Calendar.SECOND, 0);
                changeDate.set(Calendar.MILLISECOND, 0);
                this.changeDate = changeDate;
            }
            synchronized (DATE_FORMATTER) {
                Calendar inputDate = Calendar.getInstance();
                try {
                    final Date parsedDate = DATE_FORMATTER.parse(date);
                    inputDate.setTime(parsedDate);
                } catch (ParseException e) {
                    throw new RuntimeException("Invalid date", e);
                }
                changeDate.set(Calendar.DAY_OF_MONTH,
                        inputDate.get(Calendar.DAY_OF_MONTH));
                changeDate.set(Calendar.MONTH, inputDate.get(Calendar.MONTH));
                changeDate.set(Calendar.YEAR, inputDate.get(Calendar.YEAR));
            }
        }

        /**
         * @deprecated use getChangeDate
         */
        @Deprecated
        public String getTime() {
            if (getChangeDate() == null) {
                return null;
            }
            synchronized (this) {
                return TIME_FORMATTER.format(getChangeDate());
            }
        }

        @Deprecated
        public void setTime(final String time) {
            if (null == time) {
                return;
            }
            Calendar changeDate = this.changeDate;
            if (changeDate == null) {
                changeDate = Calendar.getInstance();
                changeDate.set(Calendar.DAY_OF_MONTH, 0);
                changeDate.set(Calendar.MONTH, 0);
                changeDate.set(Calendar.YEAR, 0);
                this.changeDate = changeDate;
            }
            synchronized (DATE_FORMATTER) {
                Calendar inputDate = Calendar.getInstance();
                try {
                    final Date parsedDate = TIME_FORMATTER.parse(time);
                    inputDate.setTime(parsedDate);
                } catch (ParseException e) {
                    throw new RuntimeException("Invalid time", e);
                }
                changeDate.set(Calendar.HOUR, inputDate.get(Calendar.HOUR));
                changeDate.set(Calendar.MINUTE, inputDate.get(Calendar.MINUTE));
                changeDate.set(Calendar.SECOND, inputDate.get(Calendar.SECOND));
                changeDate.set(Calendar.MILLISECOND, 0);
            }
        }

        @Exported
        public Date getChangeDate() {
            if (changeDate == null) {
                return null;
            }
            return changeDate.getTime();
        }

        public void setChangeDate(final Date newChangeDate) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(newChangeDate);
            this.changeDate = calendar;
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

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(parsedDate);
            this.changeDate = calendar;
        }

        @Override
        @Exported
        public User getAuthor() {
            if (author == null) {
                return User.getUnknown();
            }
            return author;
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
            this.author = User.get(author);
        }

        @Exported
        // digester wants read/write property, even
        // though it never reads. Duh.
        public String getUser() {
            return author.getDisplayName();
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
                    + ((author == null) ? 0 : author.hashCode());
            result = prime
                    * result
                    + ((changeDate == null) ? 0 : changeDate.hashCode());
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
            if (author == null) {
                if (other.author != null) {
                    return false;
                }
            } else if (!author.equals(other.author)) {
                return false;
            }
            if (changeDate == null) {
                if (other.changeDate != null) {
                    return false;
                }
            } else if (!changeDate.equals(other.changeDate)) {
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
    public static class File implements AffectedFile {

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
         * For example, "1.5"->"1.4", "1.5.2.13"->"1.5.2.12", "1.5.2.1"->"1.5"
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
        String encoding = ((CVSSCM.DescriptorImpl)Jenkins.getInstance().getDescriptorOrDie(CVSSCM.class)).getChangelogEncoding();
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
}
