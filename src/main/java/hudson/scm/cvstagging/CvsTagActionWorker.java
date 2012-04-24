package hudson.scm.cvstagging;

import java.io.IOException;

import hudson.model.TaskListener;
import hudson.model.TaskThread;
import hudson.model.AbstractBuild;
import hudson.scm.CvsFile;
import hudson.scm.CvsRepository;
import hudson.scm.CvsRevisionState;

import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.tag.RtagCommand;
import org.netbeans.lib.cvsclient.commandLine.BasicListener;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

public class CvsTagActionWorker extends TaskThread {

    private final CvsRevisionState revisionState;
    private final String tagName;
    private final AbstractBuild<?, ?> build;
    private final CvsTagAction parent;

    @SuppressWarnings("deprecation") // use a deprecated method, so we can support as many versions of Jenkins as possible
    public CvsTagActionWorker(final CvsRevisionState revisionState,
                    final String tagName, final AbstractBuild<?, ?> build, final CvsTagAction parent) {
        super(parent, ListenerAndText.forMemory());
        this.revisionState = revisionState;
        this.tagName = tagName;
        this.build = build;
        this.parent = parent;
    }

    @Override
    protected void perform(final TaskListener listener) throws Exception {
        for (CvsRepository repository : revisionState.getModuleFiles().keySet()) {
            for (CvsFile file : revisionState.getModuleState(repository)) {
                final Client cvsClient = parent.getParent().getCvsClient(repository, build.getEnvironment(listener));
                final GlobalOptions globalOptions = parent.getParent().getGlobalOptions(repository, build.getEnvironment(listener));

                globalOptions.setCVSRoot(repository.getCvsRoot());

                RtagCommand rtagCommand = new RtagCommand();

                rtagCommand.setTag(tagName);
                rtagCommand.setTagByRevision(file.getRevision());
                rtagCommand.addModule(file.getName());
                cvsClient.getEventManager().addCVSListener(
                                new BasicListener(listener.getLogger(),
                                                listener.getLogger()));

                try {
                    cvsClient.executeCommand(rtagCommand, globalOptions);
                } catch (CommandAbortedException e) {
                    e.printStackTrace(listener
                                    .error("The CVS rtag command was aborted"));
                    throw e;
                } catch (CommandException e) {
                    e.printStackTrace(listener
                                    .error("Error while trying to run CVS rtag command"));
                    throw e;
                } catch (AuthenticationException e) {
                    e.printStackTrace(listener
                                    .error("Authentication error while trying to run CVS rtag command"));
                    throw e;
                }  finally {
                    try {
                        cvsClient.getConnection().close();
                    } catch(IOException ex) {
                        listener.error("Could not close client connection: " + ex.getMessage());
                    }
                }
            }
        }
    }

}
