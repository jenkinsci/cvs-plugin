package hudson.scm.browsers;


import hudson.scm.CVSChangeLogSet;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OpenGrokTest {

    private OpenGrok testCase;
    private CVSChangeLogSet.File file;

    @Before
    public void setUp() throws MalformedURLException {
        testCase = new OpenGrok(new URL("http://1.2.3.4/source/xref/branchv3/"));

        file = new CVSChangeLogSet.File();
        file.setName("src/example2.java");
        file.setRevision("1.19.2.1");
        file.setPrevrevision("1.7");
    }

    @Test
    public void testGetDiffLink() throws IOException {
        assertEquals(new URL("http://1.2.3.4/source/diff/branchv3/src/example2.java?r2=/branchv3/src/example2.java@1.19.2.1&r1=/branchv3/src/example2.java@1.19"), testCase.getDiffLink(file));
    }

    @Test
    public void testGetFile() throws IOException {
        assertEquals(new URL("http://1.2.3.4/source/xref/branchv3/src/example2.java?r=1.19.2.1"), testCase.getFileLink(file));
    }

    @Test
    public void testGetChangeSetLink() throws IOException {
        assertNull(testCase.getChangeSetLink(new CVSChangeLogSet.CVSChangeLog()));
    }
}
