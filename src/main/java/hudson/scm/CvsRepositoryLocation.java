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

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.w3c.dom.stylesheets.LinkStyle;

import java.io.Serializable;
import java.util.List;

/**
 * @since 2.1
 */
public abstract class CvsRepositoryLocation implements Describable<CvsRepositoryLocation>, ExtensionPoint, Serializable {

    private static final long serialVersionUID = 7852253189793815601L;

    private final CvsRepositoryLocationType locationType;

    private final String locationName;

    private final boolean useHeadIfNotFound;
    
    private final CvsModule[] modules;

    private CvsRepositoryLocation(final CvsRepositoryLocationType locationType,
                                  final List<CvsModule> modules,
                                  final String locationName,
                                  final boolean useHeadIfNotFound) {
        this.locationType = locationType;
        this.locationName = locationName;
        this.useHeadIfNotFound = useHeadIfNotFound;

        if (null == modules) {
            this.modules = new CvsModule[]{};
        } else {
            this.modules = modules.toArray(new CvsModule[modules.size()]);
        }
    }

    @Exported
    public CvsRepositoryLocationType getLocationType() {
        return locationType;
    }
    
    @Exported
    public CvsModule[] getModules() {
        return modules;
    }
   
    @Exported
    public String getLocationName() {
        return locationName;
    }

    @Exported
    public boolean isUseHeadIfNotFound() {
        return useHeadIfNotFound;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Descriptor<CvsRepositoryLocation> getDescriptor() {
        return Hudson.getInstance().getDescriptorOrDie(getClass());
    }
    
    public static class CvsRepositoryLocationDescriptor extends Descriptor<CvsRepositoryLocation> {
        
        private String locationName;

        protected CvsRepositoryLocationDescriptor(final Class<? extends CvsRepositoryLocation> clazz, final String locationName) {
            super(clazz);
            this.locationName = locationName;
        }
        
        @Override
        public String getDisplayName() {
            return locationName;
        }

        public DescriptorExtensionList<CvsRepositoryLocation, CvsRepositoryLocationDescriptor> getRepositoryLocationDescriptors() {
            return Hudson.getInstance().<CvsRepositoryLocation, CvsRepositoryLocationDescriptor>getDescriptorList(CvsRepositoryLocation.class);
        }

    }
    
    public static class HeadRepositoryLocation extends CvsRepositoryLocation {
        
        private static final long serialVersionUID = -8309924574620513326L;

        @DataBoundConstructor
        public HeadRepositoryLocation(final List<CvsModule> modules) {
            super(CvsRepositoryLocationType.HEAD, modules, null, false);
        }
        
        @Extension
        public static class HeadRepositoryLocationDescriptor extends CvsRepositoryLocationDescriptor {
            public HeadRepositoryLocationDescriptor() {
                super(HeadRepositoryLocation.class, "Head");
            }
        }

    }
    
    public static class TagRepositoryLocation extends CvsRepositoryLocation {
        
        private static final long serialVersionUID = 1165226806285930149L;

        @DataBoundConstructor
        public TagRepositoryLocation(final String tagName, final List<CvsModule> modules, final boolean useHeadIfNotFound) {
            super(CvsRepositoryLocationType.TAG, modules, tagName, useHeadIfNotFound);
        }
        
        @Extension
        public static class TagRepositoryLocationDescriptor extends CvsRepositoryLocationDescriptor {
            public TagRepositoryLocationDescriptor() {
                super(TagRepositoryLocation.class, "Tag");
            }
        }
        
        @Exported
        public String getTagName() {
            return getLocationName();
        }

        @Exported
        public boolean isUseHeadIfTagNotFound() {
            return isUseHeadIfNotFound();
        }
    }
    
    public static class BranchRepositoryLocation extends CvsRepositoryLocation {
        
        private static final long serialVersionUID = -3848435525964164564L;

        @DataBoundConstructor
        public BranchRepositoryLocation(final String branchName, final List<CvsModule> modules, final boolean useHeadIfNotFound) {
            super(CvsRepositoryLocationType.BRANCH, modules, branchName, useHeadIfNotFound);
        }
        
        @Extension
        public static class BranchRepositoryLocationDescriptor extends CvsRepositoryLocationDescriptor {
            public BranchRepositoryLocationDescriptor() {
                super(BranchRepositoryLocation.class, "Branch");
            }
        }
        
        @Exported
        public String getBranchName() {
            return getLocationName();
        }
        
        @Exported
        public boolean isUseHeadIfBranchNotFound() {
            return isUseHeadIfNotFound();
        }
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((locationName == null) ? 0 : locationName.hashCode());
        result = prime * result + ((locationType == null) ? 0 : locationType.hashCode());
        result = prime * result + (useHeadIfNotFound ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CvsRepositoryLocation other = (CvsRepositoryLocation) obj;
        if (locationName == null) {
            if (other.locationName != null)
                return false;
        } else if (!locationName.equals(other.locationName))
            return false;
        if (locationType != other.locationType)
            return false;
        if (useHeadIfNotFound != other.useHeadIfNotFound)
            return false;
        return true;
    }

}
