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
package hudson.scm.browsers;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.scm.CVSChangeLogSet;
import hudson.scm.CVSRepositoryBrowser;
import hudson.scm.RepositoryBrowser;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public final class OpenGrok extends CVSRepositoryBrowser {
    public final URL url;

    @DataBoundConstructor
    public OpenGrok(URL url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(url);
    }


    public URL getFileLink(CVSChangeLogSet.File file) throws IOException {
        return new URL(url,"xref/ "+ file.getName() + param().add("r = " + file.getRevision()));
    }

    public URL getDiffLink(CVSChangeLogSet.File file) throws IOException {
        CVSChangeLogSet.Revision r = new CVSChangeLogSet.Revision(file.getRevision());
        CVSChangeLogSet.Revision p = r.getPrevious();
        if(p==null) return null;

        return new URL(getFileLink(file), file.getSimpleName()+".diff"+param().add("r1="+p).add("r2="+r));
    }

    public URL getChangeSetLink(CVSChangeLogSet.CVSChangeLog changeSet) throws IOException {
        return null;
    }

    private QueryBuilder param() {
        return new QueryBuilder(url.getQuery());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "OpenGrok";
        }
    }
}
