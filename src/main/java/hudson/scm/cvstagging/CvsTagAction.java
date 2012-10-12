/*
 * The MIT License
 * 
 * Copyright (c) 2004-2012, Michael Clarke
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
package hudson.scm.cvstagging;

import hudson.model.*;
import hudson.scm.AbstractCvs;
import hudson.scm.AbstractScmTagAction;
import hudson.scm.CvsRevisionState;
import hudson.scm.SCM;
import hudson.scm.cvs.Messages;
import hudson.security.Permission;
import hudson.util.FormValidation;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static hudson.Util.fixNull;

@ExportedBean
public class CvsTagAction extends AbstractScmTagAction implements Describable<CvsTagAction> {

    private final List<String> tagNames = new ArrayList<String>();
    private final AbstractCvs parent;

    public CvsTagAction(final AbstractBuild<?, ?> build, final AbstractCvs parent) {
        super(build);
        this.parent = parent;
    }

    @Override
    public String getIconFileName() {
        if (!build.getParent().getACL().hasPermission(SCM.TAG)) {
            return null;
        }
        return "save.gif";
    }

    @Override
    public String getDisplayName() {
        if (tagNames.isEmpty()) {
            return Messages.CVSSCM_TagThisBuild();
        }
        if (tagNames.size() > 1) {
            return Messages.CVSSCM_DisplayName2();
        } else {
            return Messages.CVSSCM_DisplayName1();
        }
    }

    @Override
    public boolean isTagged() {
        return !tagNames.isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Descriptor<CvsTagAction> getDescriptor() {
        return Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    @Exported
    public String[] getTagNames() {
        return tagNames.toArray(new String[tagNames.size()]);
    }
    
    public AbstractCvs getParent() {
        return parent;
    }

    public synchronized void doSubmit(final StaplerRequest request, final StaplerResponse response) throws IOException,
                    ServletException {
        // check the user is allowed to tag
        getBuild().checkPermission(getPermission());

        // get the user supplied tag name and check it's ok
        final String tagName = fixNull(request.getParameter("name")).trim();
        final boolean createBranch = Boolean.parseBoolean(request.getParameter("createBranch"));
        if (!checkTagName(tagName)) {
            sendError(Messages.CVSSCM_TagNameInvalid(Messages.CVSSCM_Tag()));
        }
        final boolean moveTag = Boolean.parseBoolean(request.getParameter("moveTag"));

        // handle upstream tagging
        if (null != request.getParameter("upstream")) {
            @SuppressWarnings("unchecked")
            Map<AbstractProject<?, ?>, Integer> upstream = getBuild().getTransitiveUpstreamBuilds();
            for (AbstractProject<?, ?> project : upstream.keySet()) {
                String upstreamTagName = fixNull(request.getParameter("upstream." + project.getName())).trim();
                boolean upstreamCreateBranch = Boolean.parseBoolean(request.getParameter("upstream-createBranch." + project.getName()));
                if (!checkTagName(upstreamTagName)) {
                    sendError(Messages.CVSSCM_TagNameInvalid(Messages.CVSSCM_Tag()));
                }
                boolean upstreamMoveTag = Boolean.parseBoolean(request.getParameter("upstream-moveTag." + project.getName()));
                CvsTagAction action = project.getBuildByNumber(upstream.get(project)).getAction(CvsTagAction.class);
                if (null != action) {
                    action.perform(upstreamTagName, upstreamCreateBranch, upstreamMoveTag);
                }
            }
        }

        perform(tagName, createBranch, moveTag);

        doIndex(request, response);
    }

    public void perform(final String tagName, boolean createTag, boolean moveTag) throws IOException {
        if (getBuild().hasPermission(Permission.UPDATE)) {
            getBuild().keepLog(true);
        }
        CvsRevisionState state = getBuild().getAction(CvsRevisionState.class);

        if (state == null) {
            return;
        }

        new CvsTagActionWorker(state, tagName, createTag, getBuild(), this, moveTag).start();

        synchronized (this) {
            tagNames.add(tagName);
        }
    }

    public FormValidation doCheckTag(@QueryParameter final String value) {
        if (checkTagName(value)) {
            return FormValidation.ok();
        }

        return FormValidation.error(Messages.CVSSCM_TagNameInvalid(Messages.CVSSCM_Tag()));
    }

    public boolean checkTagName(final String tagName) {
        /*
         * we can improve this:
         * You've probably noticed that no periods or spaces were used in the
         * tag names. CVS is rather strict about what constitutes a valid tag
         * name. The rules are that it must start with a letter and contain
         * letters, digits, hyphens ("-"), and underscores ("_"). No spaces,
         * periods, colons, commas, or any other symbols may be used.
         */
        if (fixNull(tagName).length() == 0) {
            return false;
        }

        char ch = tagName.charAt(0);
        if (!(('A' <= ch && ch <= 'Z') || ('a' <= ch && ch <= 'z'))) {
            return false;
        }

        for (char invalid : "$,.:;@".toCharArray()) {
            if (tagName.indexOf(invalid) >= 0) {
                return false;
            }
        }

        return true;
    }

}