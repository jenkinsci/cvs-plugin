/*
 * The MIT License
 * 
 * Copyright (c) 2011, Michael Clarke
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
import java.util.Arrays;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class CvsRepository implements Serializable {

    private static final long serialVersionUID = -5137002480695525335L;

    private final String cvsRoot;

    private final CvsModule[] modules;

    private final int compressionLevel;

    private final ExcludedRegion[] excludedRegions;

    @DataBoundConstructor
    public CvsRepository(final String cvsRoot, final List<CvsModule> modules, final List<ExcludedRegion> excludedRegions, final int compressionLevel) {
        this.cvsRoot = cvsRoot;
        this.modules = modules.toArray(new CvsModule[modules.size()]);
        this.compressionLevel = compressionLevel;
        this.excludedRegions = excludedRegions.toArray(new ExcludedRegion[excludedRegions.size()]);
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

}
