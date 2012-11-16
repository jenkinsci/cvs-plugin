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


import java.io.Serializable;

public interface ICvs extends Serializable {

    /**
     * Gets the descriptor for the current plugin.
     * @return the current descriptor
     * @see hudson.scm.SCM#getDescriptor()
     */
    public ICvsDescriptor getDescriptor();

    /**
     * Gets a list of all repositories configured for this job. This list does not include any added through discovery
     * e.g. parsing projectsets or ant scripts, purely those configured through the config page.
     * @return a list of configured repositories.
     */
    public CvsRepository[] getRepositories();

    /**
     * Whether to checkout an individual module's files directly into the workspace root
     * rather than into a subdirectory of the module name. Some scripts expect this functionality
     * so this switch allows users to enabled or disable this. Some plugins may not have the option
     * of supporting this if they automatically checkout multiple module (e.g. the CVS projectset plugin)
     * @return true if the files should be checkout directly into the workspace root, false if they should be
     *          put in a subdirectory.
     */
    public boolean isFlatten();

    /**
     * Allows cleaning of the workspace with a fresh checkout if CVS update fails.
     * @return whether to do a workspace wipe-out followed be CVS checkout if CVS update fails
     */
    public boolean isCleanOnFailedUpdate();

    /**
     * Whether CVS update can be used in place of CVS checkout if a workspace is already checked out.
     * @return true is the <tt>cvs up</tt> can be used in place of <tt>cvs co</tt> under the correct circumstances.
     */
    public boolean isCanUseUpdate();

    /**
     * Allows the option of skipping the changelog generation after checkout. Primarily used
     * to save time as part of the build process . Has no effect on the polling configuration which
     * uses a similar method to calculate changes.
     * @return whether changelog generation should be skipped or not.
     */
    public boolean isSkipChangeLog();

    /**
     * Whether CVS should be instructed to remove empty directories as part of checkout/update.
     * @return if CVS should remove empty directories from the workspace on checkout
     */
    public boolean isPruneEmptyDirectories();

    /**
     * CVS is normally run in quiet mode to reduce un-needed log output. This option allows plugins
     * to stop quiet mode being enabled.
     * @return whether to disable quiet mode or not.
     */
    public boolean isDisableCvsQuiet();

    /**
     * Controls whether CVS should apply the 'C' option to update commands to overwrite local changes.
     * @return whether CVS should over-write modifications to controlled files int he workspace during update
     */
    public boolean isForceCleanCopy();

}