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

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

public class CvsModuleLocation implements Serializable {

    private static final long serialVersionUID = 7852253189793815601L;

    private static final int PRIME = 31;

    private final CvsModuleLocationType locationType;

    private final String tagName;

    private final String branchName;

    private final boolean useHeadIfTagNotFound;

    private final boolean useHeadIfBranchNotFound;

    @DataBoundConstructor
    public CvsModuleLocation(final String value, final String tagName,
                    final boolean useHeadIfTagNotFound,
                    final String branchName,
                    final boolean useHeadIfBranchNotFound) {
        locationType = CvsModuleLocationType.getType(value);
        this.tagName = tagName;
        this.branchName = branchName;
        this.useHeadIfBranchNotFound = useHeadIfBranchNotFound;
        this.useHeadIfTagNotFound = useHeadIfTagNotFound;
    }

    @Exported
    public CvsModuleLocationType getLocationType() {
        return locationType;
    }

    @Exported
    public String getTagName() {
        return tagName;
    }

    @Exported
    public String getBranchName() {
        return branchName;
    }

    @Exported
    public boolean isUseHeadIfTagNotFound() {
        return useHeadIfTagNotFound;
    }

    @Exported
    public boolean isUseHeadIfBranchNotFound() {
        return useHeadIfBranchNotFound;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = PRIME * result
                        + ((branchName == null) ? 0 : branchName.hashCode());
        result = PRIME
                        * result
                        + ((locationType == null) ? 0 : locationType.hashCode());
        result = PRIME * result + ((tagName == null) ? 0 : tagName.hashCode());
        result = PRIME * result + (useHeadIfBranchNotFound ? 1231 : 1237);
        result = PRIME * result + (useHeadIfTagNotFound ? 1231 : 1237);
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
        final CvsModuleLocation other = (CvsModuleLocation) obj;
        if (branchName == null) {
            if (other.branchName != null) {
                return false;
            }
        } else if (!branchName.equals(other.branchName)) {
            return false;
        }
        if (locationType != other.locationType) {
            return false;
        }
        if (tagName == null) {
            if (other.tagName != null) {
                return false;
            }
        } else if (!tagName.equals(other.tagName)) {
            return false;
        }
        if (useHeadIfBranchNotFound != other.useHeadIfBranchNotFound) {
            return false;
        }
        if (useHeadIfTagNotFound != other.useHeadIfTagNotFound) {
            return false;
        }
        return true;
    }

}
