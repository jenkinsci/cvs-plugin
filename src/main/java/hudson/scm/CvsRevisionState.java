/*
 * The MIT License
 * 
 * Copyright (c) 2011, Michael Clarke
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

import hudson.scm.CVSChangeLogSet.CVSChangeLog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CvsRevisionState extends SCMRevisionState {

    private Map<CvsRepository, List<CVSChangeLog>> moduleFiles = new HashMap<CvsRepository, List<CVSChangeLog>>();

    public CvsRevisionState(final Map<CvsRepository, List<CVSChangeLog>> moduleStates) {
        super();
        moduleFiles = new HashMap<CvsRepository, List<CVSChangeLog>>(moduleStates);
    }

    public List<CVSChangeLog> getModuleState(final CvsRepository module) {
        return moduleFiles.get(module);
    }

    public Map<CvsRepository, List<CVSChangeLog>> getModuleFiles() {
        return new HashMap<CvsRepository, List<CVSChangeLog>>(moduleFiles);
    }


}
