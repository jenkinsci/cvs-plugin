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

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

public abstract class CvsModuleLocation implements Describable<CvsModuleLocation>, ExtensionPoint, Serializable {

    private static final long serialVersionUID = 7852253189793815601L;

    private final CvsModuleLocationType locationType;

    private final String locationName;

    private final boolean useHeadIfNotFound;

    private CvsModuleLocation(final CvsModuleLocationType locationType,
                    final String locationName,
                    final boolean useHeadIfNotFound) {
        this.locationType = locationType;
        this.locationName = locationName;
        this.useHeadIfNotFound = useHeadIfNotFound;
    }

    @Exported
    public CvsModuleLocationType getLocationType() {
        return locationType;
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
    public Descriptor<CvsModuleLocation> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }
    
    public static class CvsModuleLocationDescriptor extends Descriptor<CvsModuleLocation> {
        
        private String locationName;

        protected CvsModuleLocationDescriptor(final Class<? extends CvsModuleLocation> clazz, final String locationName) {
            super(clazz);
            this.locationName = locationName;
        }
        
        @Override
        public String getDisplayName() {
            return locationName;
        }
    }
    
    public static class HeadModuleLocation extends CvsModuleLocation {
        
        private static final long serialVersionUID = -8309924574620513326L;

        @DataBoundConstructor
        public HeadModuleLocation() {
            super(CvsModuleLocationType.HEAD, null, false);
        }
        
        @Extension
        public static class HeadModuleLocationDescriptor extends CvsModuleLocationDescriptor {
            public HeadModuleLocationDescriptor() {
                super(HeadModuleLocation.class, "Head");
            }
        }

    }
    
    public static class TagModuleLocation extends CvsModuleLocation {
        
        private static final long serialVersionUID = 1165226806285930149L;

        @DataBoundConstructor
        public TagModuleLocation(final String tagName, final boolean useHeadIfNotFound) {
            super(CvsModuleLocationType.TAG, tagName, useHeadIfNotFound);
        }
        
        @Extension
        public static class TagModuleLocationDescriptor extends CvsModuleLocationDescriptor {
            public TagModuleLocationDescriptor() {
                super(TagModuleLocation.class, "Tag");
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
    
    public static class BranchModuleLocation extends CvsModuleLocation {
        
        private static final long serialVersionUID = -3848435525964164564L;

        @DataBoundConstructor
        public BranchModuleLocation(final String branchName, final boolean useHeadIfNotFound) {
            super(CvsModuleLocationType.BRANCH, branchName, useHeadIfNotFound);
        }
        
        @Extension
        public static class BranchModuleLocationDescriptor extends CvsModuleLocationDescriptor {
            public BranchModuleLocationDescriptor() {
                super(BranchModuleLocation.class, "Branch");
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
        CvsModuleLocation other = (CvsModuleLocation) obj;
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
