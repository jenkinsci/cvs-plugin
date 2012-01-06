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

import static hudson.Util.fixNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;

public class CvsModule extends AbstractDescribableImpl<CvsModule> implements Serializable {

    private static final long serialVersionUID = 8450427422042269152L;

    private final String localName;

    private final String remoteName;

    private final CvsModuleLocation moduleLocation;

    @DataBoundConstructor
    public CvsModule(final String remoteName, final String localName,
                    final CvsModuleLocation moduleLocation) {
        this.remoteName = remoteName;
        this.localName = localName;
        this.moduleLocation = moduleLocation;
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
        return "".equals(Util.fixNull(localName)) ? remoteName : localName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                        + ((localName == null) ? 0 : localName.hashCode());
        result = prime
                        * result
                        + ((moduleLocation == null) ? 0 : moduleLocation
                                        .hashCode());
        result = prime * result
                        + ((remoteName == null) ? 0 : remoteName.hashCode());
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
        if (moduleLocation == null) {
            if (other.moduleLocation != null) {
                return false;
            }
        } else if (!moduleLocation.equals(other.moduleLocation)) {
            return false;
        }
        if (remoteName == null) {
            if (other.remoteName != null) {
                return false;
            }
        } else if (!remoteName.equals(other.remoteName)) {
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
    }
}
