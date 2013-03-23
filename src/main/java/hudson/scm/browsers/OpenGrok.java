/*
 * The MIT License
 *
 * Copyright (c) 2012-2013, Michael Clarke
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
import hudson.Util;
import hudson.model.Descriptor;
import hudson.scm.CVSChangeLogSet;
import hudson.scm.CVSRepositoryBrowser;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpenGrok extends CVSRepositoryBrowser {
    private final URL url;

    @DataBoundConstructor
    public OpenGrok(URL url) throws MalformedURLException {
        this.url = normalizeToEndWithSlash(url);
    }


    public URL getFileLink(CVSChangeLogSet.File file) throws IOException {
       return new URL(url, file.getName() + param().add("r=" + file.getRevision()));
    }

    public URL getDiffLink(CVSChangeLogSet.File file) throws IOException {
        CVSChangeLogSet.Revision r = new CVSChangeLogSet.Revision(file.getRevision());
        CVSChangeLogSet.Revision p = r.getPrevious();

        if(p == null) {
            return null;
        }
        String path = url.getPath();
        Matcher matches = Pattern.compile("^(.*)/xref/([^/]+/)$").matcher(path);
        if (!matches.matches()) {
            return null;
        }
        String moduleName = matches.group(2);
        path = matches.replaceFirst("$1/diff/" + moduleName);
        path = path + file.getName() + param().add("r2=/"+ moduleName +file.getName() + "@" + r).add("r1=/" + moduleName + file.getName() + "@" + p);
        return new URL(url.getProtocol(), url.getHost(), url.getPort(), path);
    }

    public URL getChangeSetLink(CVSChangeLogSet.CVSChangeLog changeSet) throws IOException {
        return null;
    }

    private QueryBuilder param() {
        return new QueryBuilder(url.getQuery());
    }

    @Exported
    public URL getUrl() {
        return url;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {

        private static final Pattern URL_PATTERN = Pattern.compile("^.+/xref/[^/]+/$");

        public String getDisplayName() {
            return "OpenGrok";
        }

        public FormValidation doCheckUrl(@QueryParameter String value) {
            String url = Util.fixEmpty(value);

            if (url == null) {
                return FormValidation.ok();
            }

            if (!url.endsWith("/"))   {
                url += '/';
            }

            if (!URL_PATTERN.matcher(url).matches()) {
                return FormValidation.errorWithMarkup("The URL should end like <tt>.../xref/foobar/</tt>");
            }

             return FormValidation.ok();

        }
    }
}
