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
package hudson.scm;


import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

public class CvsAuthentication extends AbstractDescribableImpl<CvsAuthentication> {

    private String cvsRoot;
    private String username;
    private Secret password;

    @DataBoundConstructor
    public CvsAuthentication(final String cvsRoot, final String username, final String password) {
        this.cvsRoot = cvsRoot;
        this.username = Util.fixNull(username);
        this.password = Secret.fromString(password);
    }

    @Exported
    public String getCvsRoot() {
        return cvsRoot;
    }

    @Exported
    public String getUsername() {
        return username;
    }

    @Exported
    public Secret getPassword() {
        return password;
    }

    @Extension
    public static class CvsAuthenticationDescriptor extends Descriptor<CvsAuthentication> {

        @Override
        public String getDisplayName() {
            return "CVS Authentication";
        }
    }
}