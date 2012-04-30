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

import static hudson.Util.fixNull;
import hudson.model.Hudson;
import hudson.scm.CVSSCM.DescriptorImpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.netbeans.lib.cvsclient.connection.StandardScrambler;

/**
 * Used to convert legacy configuration into the new repository structure. These methods should only
 * need to be used by the main CVSSCM class, but have been separated out from there to reduce the
 * volume of legacy code in there
 * 
 * @author Michael Clarke
 *
 */
public class LegacyConvertor {
    private static LegacyConvertor instance;
    
    private LegacyConvertor() {}

    public List<CvsRepository> convertLegacyConfigToRepositoryStructure(final String cvsRoot,
                    final String allModules, final String branch, final boolean isBranchActuallyTag,
                    final String excludedRegions, final boolean useHeadIfNotFound) {
        List<CvsModule> modules = new ArrayList<CvsModule>();
        String nodeName = fixNull(branch);
        boolean isBranch = !isBranchActuallyTag && !nodeName.equals("");
        boolean isTag = isBranchActuallyTag && !nodeName.equals("");
       
        CvsRepositoryLocation location;
        
        if (isBranch) {
            location = new CvsRepositoryLocation.BranchRepositoryLocation(nodeName, useHeadIfNotFound);
        } else if (isTag) {
            location = new CvsRepositoryLocation.TagRepositoryLocation(nodeName, useHeadIfNotFound);
        } else {
            location = new CvsRepositoryLocation.HeadRepositoryLocation();
        }
        

        for (final String moduleName : convertModulesToList(allModules)) {
            modules.add(new CvsModule(moduleName, ""));
        }

        List<CvsRepository> repositories = new ArrayList<CvsRepository>();
        final String password = getPassword(cvsRoot);
        final List<CvsRepositoryItem> items = new ArrayList<CvsRepositoryItem>();
        items.add(new CvsRepositoryItem(location, modules.toArray(new CvsModule[modules.size()])));
        repositories.add(new CvsRepository(cvsRoot, password != null, password, items, convertExcludedRegionsToList(excludedRegions), -1));
        return repositories;
    }
    
    /**
     * Tries to retrieve the current password from the CVS pass file (if it's been set)
     * @param cvsRoot the CVS Root to look for a password for
     * @return the decrypted password if found or null on no match or problems reading file
     */
    public String getPassword(final String cvsRoot) {
        @SuppressWarnings("deprecation")
        String customPassfileLocation = fixNull(((DescriptorImpl)Hudson.getInstance().getDescriptorOrDie(CVSSCM.class)).getCvsPassFile());
        File passFile;
        if(customPassfileLocation.equals("")) {
            passFile = new File(new File(System.getProperty("user.home")),".cvspass");
        } else {
            passFile = new File(customPassfileLocation);
        }
        
        if (!passFile.exists()) {
            return null;
        }

        
        String password;
        
        try {
            password = findPassword(cvsRoot, passFile);
        } catch (IOException e) {
            return null;
        }
        
        if (null == password) {
            return null;
        }
        
        return StandardScrambler.getInstance().scramble(password.substring(1)).substring(1);
    }
    
    /**
     * Treats the given file as a .cvspass file and retrieves the encoded password for
     * the requested CVS root the file.
     * @param cvsRoot the CVS root to search file file for
     * @param passFile the file to treat as a CVS pass file
     * @return the encoded password is found, null otherwise
     * @throws IOException on failure reading file
     */
    public String findPassword(String cvsRoot, File passFile) throws IOException {
        BufferedReader reader = null;
        String password = null;

        try {
            reader = new BufferedReader(new FileReader(passFile));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("/1 ")) {
                    line = line.substring("/1 ".length());
                }
                int portIndex = line.indexOf(":2401/");
                if (line.startsWith(cvsRoot + " ")) {
                    password = line.substring(cvsRoot.length() + 1);
                    break;
                }
                if (portIndex > 0) {
                    if ((line.substring(0, portIndex) + line.substring(portIndex + 5)).startsWith(cvsRoot + " ")) {
                        password = line.substring(cvsRoot.length() + 5 + 1);
                        break;
                    }
                    if ((line.substring(0, portIndex) + ":" + line.substring(portIndex + 5)).startsWith(cvsRoot + " ")) {
                        password = line.substring(cvsRoot.length() + 4 + 1);
                        break;
                    }
                }
            }
        } finally {
            if (reader != null) {
               reader.close();
            }
        }
        
        return password;

    }

    public String[] convertModulesToList(final String modules) {
        // split by whitespace, except "\ "
        String[] moduleNames = modules.split("(?<!\\\\)[ \\r\\n]+");
        // now replace "\ " to " ".
        for (int i = 0; i < moduleNames.length; i++) {
            moduleNames[i] = moduleNames[i].replaceAll("\\\\ ", " ");
        }
        return moduleNames;
    }

    public List<ExcludedRegion> convertExcludedRegionsToList(final String excludedRegions) {
        final String[] parts = excludedRegions == null ? new String[] {} : excludedRegions.split("[\\r\\n]+");
        final List<ExcludedRegion> regions = new ArrayList<ExcludedRegion>();
        for (String part : parts) {
            regions.add(new ExcludedRegion(part));
        }
        return regions;
    }
    
    
    public static LegacyConvertor getInstance() {
        synchronized(LegacyConvertor.class) {
            if (null == instance) {
                instance = new LegacyConvertor();
            }
        }
        
        return instance;
    }
}
