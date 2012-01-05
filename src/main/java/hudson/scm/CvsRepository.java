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

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.scm.cvs.*;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.netbeans.lib.cvsclient.CVSRoot;

import static hudson.Util.fixEmpty;

@ExportedBean
public class CvsRepository extends AbstractDescribableImpl<CvsRepository> implements Serializable {

    private static final long serialVersionUID = -5137002480695525335L;

    private final String cvsRoot;

    private final CvsModule[] modules;

    private final int compressionLevel;

    private final ExcludedRegion[] excludedRegions;

    @DataBoundConstructor
    public CvsRepository(final String cvsRoot, final List<CvsModule> modules,
                    final List<ExcludedRegion> excludedRegions,
                    final int compressionLevel) {
        this.cvsRoot = cvsRoot;
        this.modules = modules.toArray(new CvsModule[modules.size()]);
        this.compressionLevel = compressionLevel;
        this.excludedRegions = excludedRegions
                        .toArray(new ExcludedRegion[excludedRegions.size()]);
    }

    @Exported
    public String getCvsRoot() {
        return cvsRoot;
    }

    @Exported
    public CvsModule[] getModules() {
        return modules.clone();
    }

    @Exported
    public int getCompressionLevel() {
        return compressionLevel;
    }

    @Exported
    public ExcludedRegion[] getExcludedRegions() {
        return excludedRegions;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + compressionLevel;
        result = prime * result + ((cvsRoot == null) ? 0 : cvsRoot.hashCode());
        result = prime * result + Arrays.hashCode(excludedRegions);
        result = prime * result + Arrays.hashCode(modules);
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
        CvsRepository other = (CvsRepository) obj;
        if (compressionLevel != other.compressionLevel) {
            return false;
        }
        if (cvsRoot == null) {
            if (other.cvsRoot != null) {
                return false;
            }
        } else if (!cvsRoot.equals(other.cvsRoot)) {
            return false;
        }
        if (!Arrays.equals(excludedRegions, other.excludedRegions)) {
            return false;
        }
        if (!Arrays.equals(modules, other.modules)) {
            return false;
        }
        return true;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CvsRepository> {
        @Override
        public String getDisplayName() {
            return "CVS Repository";
        }

        public FormValidation doCheckCvsRoot(@QueryParameter String value) throws IOException {
            String v = fixEmpty(value);
            if(v==null) {
                return FormValidation.error(hudson.scm.cvs.Messages.CVSSCM_MissingCvsroot());
            }

            try {
                CVSRoot.parse(v);
            } catch(IllegalArgumentException ex) {
                return FormValidation.error(hudson.scm.cvs.Messages.CVSSCM_InvalidCvsroot());
            }


            return FormValidation.ok();
        }
        
        private static Option option(String i) {
            return new Option(i,i);
        }

        public ListBoxModel doFillCompressionLevelItems() {
            return COMPRESSION_LEVELS;
        }

        private static final ListBoxModel COMPRESSION_LEVELS = new ListBoxModel(
            new Option("System Default", "-1"),
            new Option("None", "-1"),
            option("1"),
            option("2"),
            new Option("3 (Recommended)", "3"),
            option("4"),
            option("5"),
            option("6"),
            option("7"),
            option("8"),
            option("9")
        );
    }
}
