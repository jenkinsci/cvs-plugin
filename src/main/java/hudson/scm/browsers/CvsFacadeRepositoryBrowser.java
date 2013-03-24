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
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.scm.AbstractCvs;
import hudson.scm.CVSChangeLogSet;
import hudson.scm.CVSRepositoryBrowser;
import hudson.scm.CvsRepository;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import org.netbeans.lib.cvsclient.CVSRoot;
import org.netbeans.lib.cvsclient.connection.Connection;
import org.netbeans.lib.cvsclient.connection.ConnectionFactory;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class CvsFacadeRepositoryBrowser extends CVSRepositoryBrowser {

    private final CVSRepositoryBrowser legacyBrowser;
    private final transient Map<CVSChangeLogSet.CVSChangeLog, CVSRepositoryBrowser> changeToBrowserMap = new HashMap<CVSChangeLogSet.CVSChangeLog, CVSRepositoryBrowser>();

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

    protected CVSRepositoryBrowser resolveRepositoryBrowser(CVSChangeLogSet.CVSChangeLog changelog) {
        synchronized (changeToBrowserMap) {
           if (!changeToBrowserMap.containsKey(changelog)) {
                changeToBrowserMap.put(changelog, calculateRepositoryBrowser(changelog));
            }
        }
        return  changeToBrowserMap.get(changelog);
    }

    private CVSRepositoryBrowser calculateRepositoryBrowser(CVSChangeLogSet.CVSChangeLog changelog) {

        if (changelog.getRepository() == null) {
            return legacyBrowser;
        }

        CVSRepositoryBrowser browser = changelog.getRepository().getRepositoryBrowser();

        if (browser != null) {
            return browser;
        }

        for (AbstractProject<?, ?> p : Hudson.getInstance().getAllItems(AbstractProject.class)) {
            SCM scm = p.getScm();
            if (scm instanceof AbstractCvs) {
                AbstractCvs cvs = (AbstractCvs) scm;
                for (CvsRepository repository : cvs.getRepositories()) {
                    if (repository.getRepositoryBrowser() != null
                            && equals(CVSRoot.parse(repository.getCvsRoot()), CVSRoot.parse(changelog.getRepository().getCvsRoot()))) {
                        return repository.getRepositoryBrowser();
                    }
                }
            }
        }

        return null;
    }

    private static boolean equals(CVSRoot root1, CVSRoot root2) {
        return toString(root1).equals(toString(root2));
    }

    private static String toString(CVSRoot root) {

        if (root.getHostName() == null) {
            if (root.getMethod() == null) {
                return root.getRepository();
            }

            return ":" + root.getMethod() + ":" + root.getRepository();
        } else {

            final StringBuilder buf = new StringBuilder();

            if (root.getMethod() != null) {
                buf.append(':');
                buf.append(root.getMethod());
                buf.append(':');
            }

            // hostname
            buf.append(root.getHostName());
            buf.append(':');

            // port
            Connection connection = ConnectionFactory.getConnection(root);
            if (connection.getPort() > 0) {
                buf.append(connection.getPort());
            }

            // repository
            buf.append(root.getRepository());

            return buf.toString();
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
