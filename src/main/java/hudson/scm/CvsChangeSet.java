/*
 * The MIT License
 * 
 * Copyright (c) 2004-2012, Michael Clarke
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

import hudson.scm.CVSChangeLogSet.CVSChangeLog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Used to store a list of changes and a list of files parsed from the output of
 * the CVS <tt>rlog</tt> command.
 * 
 * @author Michael Clarke
 */
public class CvsChangeSet {

    private final List<CVSChangeLog> changes;
    private final List<CvsFile> files;
    private transient Set<String> tagNames = new HashSet<String>();
    private transient Set<String> branchNames = new HashSet<String>();


    public CvsChangeSet(final List<CvsFile> files,
                    final List<CVSChangeLog> changes) {
        this.files = files;
        this.changes = changes;
    }
    
    public CvsChangeSet(final List<CvsFile> files, final List<CVSChangeLog> changes, final Set<String> branchNames,
                        final Set<String> tagNames) {
        this(files, changes);
        this.branchNames = branchNames;
        this.tagNames = tagNames;
    }

    public List<CVSChangeLog> getChanges() {
        return changes;
    }

    public List<CvsFile> getFiles() {
        return files;
    }
    
    public Set<String> getTagNames(){
        return tagNames;
    }
    
    public Set<String> getBranchNames() {
        return branchNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CvsChangeSet that = (CvsChangeSet) o;

        if (branchNames != null ? !branchNames.equals(that.branchNames) : that.branchNames != null) return false;
        if (changes != null ? !changes.equals(that.changes) : that.changes != null) return false;
        if (files != null ? !files.equals(that.files) : that.files != null) return false;
        if (tagNames != null ? !tagNames.equals(that.tagNames) : that.tagNames != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = changes != null ? changes.hashCode() : 0;
        result = 31 * result + (files != null ? files.hashCode() : 0);
        result = 31 * result + (tagNames != null ? tagNames.hashCode() : 0);
        result = 31 * result + (branchNames != null ? branchNames.hashCode() : 0);
        return result;
    }
}
