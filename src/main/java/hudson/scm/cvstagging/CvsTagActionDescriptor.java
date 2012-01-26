package hudson.scm.cvstagging;

import hudson.Extension;
import hudson.model.Descriptor;

@Extension
public final class CvsTagActionDescriptor extends Descriptor<CvsTagAction> {
    public CvsTagActionDescriptor() {
        super(CvsTagAction.class);
    }

    @Override
    public String getDisplayName() {
        return "";
    }
}