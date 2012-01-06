package hudson.scm.cvstagging;

import hudson.model.TaskListener;
import hudson.model.TaskThread;
import hudson.model.AbstractBuild;
import hudson.scm.CvsFile;
import hudson.scm.CvsRepository;
import hudson.scm.CvsRevisionState;

import org.netbeans.lib.cvsclient.CVSRoot;
import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.admin.StandardAdminHandler;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.tag.RtagCommand;
import org.netbeans.lib.cvsclient.commandLine.BasicListener;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.Connection;
import org.netbeans.lib.cvsclient.connection.ConnectionFactory;

public class CvsTagActionWorker extends TaskThread {

    private final CvsRevisionState revisionState;
    private final String tagName;
    private final AbstractBuild<?, ?> build;

    public CvsTagActionWorker(final CvsRevisionState revisionState,
                    final String tagName, final AbstractBuild<?, ?> build, final CvsTagAction parent) {
        super(parent, ListenerAndText.forMemory(null));
        this.revisionState = revisionState;
        this.tagName = tagName;
        this.build = build;
    }

    @Override
    protected void perform(final TaskListener listener) throws Exception {
        for (CvsRepository repository : revisionState.getModuleFiles().keySet()) {
            for (CvsFile file : revisionState.getModuleState(repository)) {
                final CVSRoot cvsRoot = CVSRoot.parse(build.getEnvironment(listener).expand(repository.getCvsRoot()));
                final Connection cvsConnection = ConnectionFactory
                                .getConnection(cvsRoot);
                final Client cvsClient = new Client(cvsConnection,
                                new StandardAdminHandler());
                final GlobalOptions globalOptions = new GlobalOptions();

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
                                    .error("Authentication error while trying to run CVS rtag command"));
                    e.printStackTrace();
                    throw e;
                } catch (CommandException e) {
                    e.printStackTrace(listener
                                    .error("Authentication error while trying to run CVS rtag command"));
                    e.printStackTrace();
                    throw e;
                } catch (AuthenticationException e) {
                    e.printStackTrace(listener
                                    .error("Authentication error while trying to run CVS rtag command"));
                    e.printStackTrace();
                    throw e;
                }
            }
        }
    }

}
