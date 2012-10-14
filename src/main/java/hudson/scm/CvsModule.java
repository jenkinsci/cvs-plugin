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
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.scm.cvs.Messages;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;

import java.io.Serializable;

import static hudson.Util.fixNull;

public class CvsModule extends AbstractDescribableImpl<CvsModule> implements Serializable {

    private static final long serialVersionUID = 8450427422042269152L;

    private final String localName;

    private final String remoteName;

    private final String projectsetFileName;

    @SuppressWarnings("unused")
    @Deprecated
    private transient CvsModuleLocation moduleLocation;

    public CvsModule(final String remoteName, final String localName) {
        this(remoteName, localName, null);
    }

    @DataBoundConstructor
    public CvsModule(final String remoteName, final String localName, final String projectsetFileName) {
        this.remoteName = remoteName;
        this.localName = localName;
        this.projectsetFileName = projectsetFileName;
    }

    @Exported
    public String getLocalName() {
        return localName;
    }

    @Exported
    public String getRemoteName() {
        return remoteName;
    }

    @Exported
    public String getProjectsetFileName() {
        return projectsetFileName;
    }

    @Exported
    @Deprecated
    public CvsModuleLocation getModuleLocation() {
        return moduleLocation;
    }

    /**
     * Gives a useable form of the local module name. Where the local name has
     * been configured, the defined value will be returned, otherwise the value
     * configured for remote will be returned.
     *
     * @return the name to be used when referring to the module in the local
     *         file-system
     */
    public String getCheckoutName() {
        return isAlternativeCheckoutName()? localName : remoteName;
    }

    public boolean isAlternativeCheckoutName() {
          return !"".equals(Util.fixNull(localName));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((localName == null) ? 0 : localName.hashCode());
        result = prime * result
                + ((remoteName == null) ? 0 : remoteName.hashCode());
        result = prime * result
                + ((projectsetFileName == null) ? 0 : projectsetFileName.hashCode());
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
        final CvsModule other = (CvsModule) obj;
        if (localName == null) {
            if (other.localName != null) {
                return false;
            }
        } else if (!localName.equals(other.localName)) {
            return false;
        }
        if (remoteName == null) {
            if (other.remoteName != null) {
                return false;
            }
        } else if (!remoteName.equals(other.remoteName)) {
            return false;
        }
        if (projectsetFileName == null) {
            if (other.projectsetFileName != null) {
                return false;
            }
        } else if (!projectsetFileName.equals(other.projectsetFileName)) {
            return false;
        }
        return true;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CvsModule> {
        @Override
        public String getDisplayName() {
            return "CVS Module";
        }

        /**
         * Checks the modules remote name has been defined
         */
        public FormValidation doCheckRemoteName(@QueryParameter final String value) {
            String v = fixNull(value);

            if ("".equals(v)) {
                return FormValidation.error(hudson.scm.cvs.Messages.CVSSCM_MissingRemoteName());
            }

            return FormValidation.ok();

        }

        public FormValidation doCheckProjectsetFileName(@QueryParameter final String value) {
            String v = fixNull(value);

            if ("".equals(v)) {
                return FormValidation.error(hudson.scm.cvs.Messages.CVSSCM_MissingProjectsetName());
            }

            return FormValidation.ok();
        }

        /**
         * Checks the correctness of the branch/tag name.
         */
        private FormValidation doCheckLocationName(final String value, final String location) {
            String v = fixNull(value);

            if (v.equals("HEAD")) {
                return FormValidation.error(Messages.CVSSCM_HeadIsNotTag(location));
            }

            if (!v.equals(v.trim())) {
                return FormValidation.error(Messages.CVSSCM_TagNameInvalid(location));
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckBranchName(@QueryParameter final String branchName) {
            return doCheckLocationName(branchName, Messages.CVSSCM_Branch());
        }

        public FormValidation doCheckTagName(@QueryParameter final String tagName) {
            return doCheckLocationName(tagName, Messages.CVSSCM_Tag());
        }
    }
}