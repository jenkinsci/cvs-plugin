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

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class CvsRepositoryItem implements Describable<CvsRepositoryItem>, Serializable {

    private CvsModule[] modules;
    private CvsRepositoryLocation location;

    @DataBoundConstructor
    public CvsRepositoryItem(CvsRepositoryLocation location, CvsModule[] modules) {
        this.location = location;
        this.modules = modules;
    }
    
    @Exported
    public CvsModule[] getModules() {
        return modules;
    }

    @Exported
    public CvsRepositoryLocation getLocation() {
        return location;
    }

    @Override
    public CvsRepositoryItemDescriptor getDescriptor() {
        return (CvsRepositoryItemDescriptor) Hudson.getInstance().getDescriptorOrDie(CvsRepositoryItem.class);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CvsRepositoryItem that = (CvsRepositoryItem) o;

        if (location != null ? !location.equals(that.location) : that.location != null) return false;
        if (!Arrays.equals(modules, that.modules)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = modules != null ? Arrays.hashCode(modules) : 0;
        result = 31 * result + (location != null ? location.hashCode() : 0);
        return result;
    }

    @Extension
    public static class CvsRepositoryItemDescriptor extends Descriptor<CvsRepositoryItem> {

        @Override
        public String getDisplayName() {
            return "CVS Repository Item";
        }

        public DescriptorExtensionList<CvsRepositoryLocation, CvsRepositoryLocation.CvsRepositoryLocationDescriptor> getRepositoryLocationDescriptorList() {
            return Hudson.getInstance().getDescriptorList(CvsRepositoryLocation.class);
        }
    }
}
