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

import java.util.List;

/**
 * Used to store a list of changes and a list of files parsed from the output of
 * the CVS <tt>rlog</tt> command.
 * 
 * @author Michael Clarke
 */
public class CvsChangeSet {

    private final List<CVSChangeLog> changes;
    private final List<CvsFile> files;

    public CvsChangeSet(final List<CvsFile> files,
                    final List<CVSChangeLog> changes) {
        this.files = files;
        this.changes = changes;
    }

    public List<CVSChangeLog> getChanges() {
        return changes;
    }

    public List<CvsFile> getFiles() {
        return files;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((changes == null) ? 0 : changes.hashCode());
        result = prime * result + ((files == null) ? 0 : files.hashCode());
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
        CvsChangeSet other = (CvsChangeSet) obj;
        if (changes == null) {
            if (other.changes != null) {
                return false;
            }
        } else if (!changes.equals(other.changes)) {
            return false;
        }
        if (files == null) {
            if (other.files != null) {
                return false;
            }
        } else if (!files.equals(other.files)) {
            return false;
        }
        return true;
    }
}
