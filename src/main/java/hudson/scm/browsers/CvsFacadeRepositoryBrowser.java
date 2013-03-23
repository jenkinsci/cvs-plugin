/*
 * The MIT License
 *
 * Copyright (c) 2013, Michael Clarke
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

import java.io.IOException;
import java.net.URL;

public class CvsFacadeRepositoryBrowser extends CVSRepositoryBrowser {

    private final CVSRepositoryBrowser legacyBrowser;

    public CvsFacadeRepositoryBrowser(CVSRepositoryBrowser legacyBrowser) {
        super();
        this.legacyBrowser = legacyBrowser;
    }

    public CvsFacadeRepositoryBrowser() {
        this(null);
    }



    @Override
    public URL getDiffLink(CVSChangeLogSet.File file) throws IOException {
        CVSRepositoryBrowser browser = resolveRepositoryBrowser(file.getParent());

        if (null ==  browser) {
            return null;
        }

        return resolveRepositoryBrowser(file.getParent()).getDiffLink(file);
    }

    @Override
    public URL getFileLink(CVSChangeLogSet.File file) throws IOException {
        CVSRepositoryBrowser browser = resolveRepositoryBrowser(file.getParent());
        if (null == browser) {
            return null;
        }
        return resolveRepositoryBrowser(file.getParent()).getFileLink(file);
    }

    @Override
    public URL getChangeSetLink(CVSChangeLogSet.CVSChangeLog changeSet) throws IOException {
        CVSRepositoryBrowser browser = resolveRepositoryBrowser(changeSet);
        if (browser == null) {
            return null;
        }
        return browser.getChangeSetLink(changeSet);
    }

    private CVSRepositoryBrowser resolveRepositoryBrowser(CVSChangeLogSet.CVSChangeLog changelog) {
        if (changelog.getRepository() == null) {
            return legacyBrowser;
        } else {
            return changelog.getRepository().getRepositoryBrowser();
        }
    }


    @Extension
    public static class CvsFacadeRepositoryBrowserDescriptor extends Descriptor<RepositoryBrowser<?>> {

        @Override
        public String getDisplayName() {
            return "Cvs Facade Repository Browser";
        }
    }

}
