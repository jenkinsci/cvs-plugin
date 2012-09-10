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

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.netbeans.lib.cvsclient.CVSRoot;
import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.admin.StandardAdminHandler;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.log.RlogCommand;
import org.netbeans.lib.cvsclient.commandLine.BasicListener;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.Connection;
import org.netbeans.lib.cvsclient.connection.ConnectionFactory;
import org.netbeans.lib.cvsclient.connection.ConnectionIdentity;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.util.logging.Logger;

import static hudson.Util.fixEmpty;

public class CvsTagsParamDefinition extends ParameterDefinition {

    private static final Logger logger = Logger.getLogger("CvsTagsParamDefinition");

    private final String cvsRoot;
    private final Secret password;
    private final String moduleName;
    private final boolean passwordRequired;
    
    @DataBoundConstructor
    public CvsTagsParamDefinition(String name, String cvsRoot, final boolean passwordRequired, String password, String moduleName) {
        super(name);
        this.cvsRoot = cvsRoot;
        this.password = Secret.fromString(password);
        this.moduleName = moduleName;
        this.passwordRequired = passwordRequired;
    }

    @Exported
    public String getCvsRoot() {
        return cvsRoot;
    }

    @Exported
    public Secret getPassword() {
        return password;
    }

    @Exported
    public String getModuleName() {
        return moduleName;
    }

    @Exported
    public boolean isPasswordRequired() {
        return passwordRequired;
    }


    @Override
    public ParameterValue createValue(StaplerRequest req) {
        String[] values = req.getParameterValues(getName());
        if(values == null || values.length != 1) {
            return new CvsTagsParamValue(getName(), "HEAD");
        }
        else {
            return new CvsTagsParamValue(getName(), values[0]);
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject formData) {
        return req.bindJSON(CvsTagsParamValue.class, formData);
    }

    @Exported
    public ListBoxModel getSymbolicNames()  {
        ListBoxModel model = new ListBoxModel();
        CvsChangeSet changeSet = null;

        RlogCommand statusCommand = new RlogCommand();
        statusCommand.setHeaderOnly(true);
        statusCommand.setModule(moduleName);
        statusCommand.setRecursive(true);

        try {
            final File tempRlogSpill = File.createTempFile("cvs","status );                                                                                                                                                                                                 `                                                                                                                                                                                                                                                                                                                                                                   ");
            final DeferredFileOutputStream outputStream = new DeferredFileOutputStream(100*1024,tempRlogSpill);
            final PrintStream logStream = new PrintStream(outputStream, true, getCvsDescriptor().getChangelogEncoding());

            final OutputStream errorOutputStream = new OutputStream() {
                final StringBuffer buffer = new StringBuffer();
                
                @Override
                public void write(int b) throws IOException {
                    if ((int)("\n".getBytes()[0]) == b) {
                        flush();
                    }
                    else {
                        buffer.append(new String(new byte[]{(byte)b}));
                    }
                }
                
                @Override
                public void flush() throws IOException {
                    logger.info(buffer.toString());
                    buffer.delete(0, buffer.length());
                    super.flush();
                }
                
                public void close() throws IOException {
                    flush();
                    super.close();
                }
            };
            final PrintStream errorPrintStream = new PrintStream(errorOutputStream);
            Client cvsClient = getCvsClient(cvsRoot, passwordRequired, password);
            cvsClient.getEventManager().addCVSListener(new BasicListener(logStream, errorPrintStream));
            cvsClient.executeCommand(statusCommand, getGlobalOptions(cvsRoot));

            logStream.close();
            errorPrintStream.flush();
            errorPrintStream.close();

            CvsLog parser  = new CvsLog() {
                @Override
                public Reader read() throws IOException {
                    if (outputStream.isInMemory())
                        return new InputStreamReader(new ByteArrayInputStream(outputStream.getData()), getCvsDescriptor().getChangelogEncoding());
                    else
                        return new InputStreamReader(new FileInputStream(outputStream.getFile()), getCvsDescriptor().getChangelogEncoding());
                }

                @Override
                public void dispose() {
                    tempRlogSpill.delete();
                }
            };
        
            changeSet = parser.mapCvsLog(cvsRoot, new CvsRepositoryLocation.HeadRepositoryLocation());
    
        }
        catch(IOException ex) {
            model.add(new ListBoxModel.Option("Could not load symbolic names - " + ex.getLocalizedMessage()));
            return model;
        } catch (CommandAbortedException ex) {
            model.add(new ListBoxModel.Option("Could not load symbolic names - " + ex.getLocalizedMessage()));
            return model;
        } catch (CommandException ex) {
            model.add(new ListBoxModel.Option("Could not load symbolic names - " + ex.getLocalizedMessage()));
            return model;
        } catch (AuthenticationException ex) {
            model.add(new ListBoxModel.Option("Could not load symbolic names - " + ex.getLocalizedMessage()));
            return model;
        }

        model.add(new ListBoxModel.Option("Head", "HEAD"));
        
        for (String branchName : changeSet.getBranchNames()) {
            model.add(new ListBoxModel.Option(branchName + " (Branch)", branchName));
        }

        for (String tagName : changeSet.getTagNames()) {
            model.add(new ListBoxModel.Option(tagName + " (Tag)", tagName));
        }

        return model;
    }

    public Client getCvsClient(final String cvsRootString, final boolean passwordRequired, final Secret password) {
        CVSRoot cvsRoot = CVSRoot.parse(cvsRootString);
        EnvVars envVars = new EnvVars();
        try {
            envVars = Computer.currentComputer().getEnvironment();
        } catch (IOException e) {
            //ignored, can't do much
        } catch (InterruptedException e) {
            //ignored, can't do much
        }

        CVSSCM.DescriptorImpl cvsDescriptor = getCvsDescriptor();

        if (passwordRequired) {
            cvsRoot.setPassword(Secret.toString(password));
        }
        else {
            String partialRoot = cvsRoot.getHostName() + ":" + cvsRoot.getPort() + cvsRoot.getRepository();
            String sanitisedRoot = ":" + cvsRoot.getMethod() + ":" + partialRoot;
            for (CvsAuthentication authentication : cvsDescriptor.getAuthentication()) {
                if (authentication.getCvsRoot().equals(sanitisedRoot) && (cvsRoot.getUserName() == null || authentication.getUsername().equals(cvsRoot.getUserName()))) {
                    cvsRoot = CVSRoot.parse(":" + cvsRoot.getMethod() + ":" + (authentication.getUsername() != null ? authentication.getUsername() + "@" :"") + partialRoot);
                    cvsRoot.setPassword(authentication.getPassword().getPlainText());

                    break;
                }
            }
        }
        
        ConnectionIdentity connectionIdentity = ConnectionFactory.getConnectionIdentity();
        connectionIdentity.setKnownHostsFile(envVars.expand(cvsDescriptor.getKnownHostsLocation()));
        connectionIdentity.setPrivateKeyPath(envVars.expand(cvsDescriptor.getPrivateKeyLocation()));
        if (cvsDescriptor.getPrivateKeyPassword() != null) {
            connectionIdentity.setPrivateKeyPassword(cvsDescriptor.getPrivateKeyPassword().getPlainText());
        }

        final Connection cvsConnection = ConnectionFactory.getConnection(cvsRoot);

        return new Client(cvsConnection, new StandardAdminHandler());
    }

    private GlobalOptions getGlobalOptions(String cvsRoot) {
        final GlobalOptions globalOptions = new GlobalOptions();
        globalOptions.setVeryQuiet(true);
        globalOptions.setCVSRoot(cvsRoot);
        return globalOptions;
    }
    
    private static CVSSCM.DescriptorImpl getCvsDescriptor() {
        return (CVSSCM.DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(CVSSCM.class);
    }

    @Extension
    public static class ListCvsTagsParameterDefinitionDescriptor extends ParameterDescriptor {

        public String getDisplayName() {
            return "CVS Symbolic Name Parameter";
        }

        public String getHelpFile() {
            return super.getHelpFile("CvsTagsParam");
        }

        public FormValidation doCheckCvsRoot(@QueryParameter String value) throws IOException {
            String v = fixEmpty(value);
            if(v==null) {
                return FormValidation.error(hudson.scm.cvs.Messages.CVSSCM_MissingCvsroot());
            }

            try {
                CVSRoot.parse(v);
            } catch(IllegalArgumentException ex) {
                return FormValidation.error(hudson.scm.cvs.Messages.CVSSCM_InvalidCvsroot());
            }


            return FormValidation.ok();
        }

        public FormValidation doCheckModuleName(@QueryParameter String value) throws IOException {
           if (null == fixEmpty(value)) {
               return FormValidation.error(hudson.scm.cvs.Messages.CVSSCM_MissingModuleName());
           }
            
            return FormValidation.ok();
        }

        public FormValidation doCheckName(@QueryParameter String value) throws IOException {
            if (null == fixEmpty(value)) {
                return FormValidation.error(hudson.scm.cvs.Messages.CVSSCM_MissingParameterName());
            }

            return FormValidation.ok();
        }
    }

}