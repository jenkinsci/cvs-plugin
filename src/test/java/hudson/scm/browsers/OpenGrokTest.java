package hudson.scm.browsers;


import hudson.scm.CVSChangeLogSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenGrokTest {

    private OpenGrok testCase;
    private CVSChangeLogSet.File file;

    @BeforeEach
    void setUp() throws Exception {
        testCase = new OpenGrok(new URL("http://1.2.3.4/source/xref/branchv3/"));

        file = new CVSChangeLogSet.File();
        file.setName("src/example2.java");
        file.setRevision("1.19.2.1");
        file.setPrevrevision("1.7");
    }

    @Test
    void testGetDiffLink() throws Exception {
        assertEquals(new URL("http://1.2.3.4/source/diff/branchv3/src/example2.java?r2=/branchv3/src/example2.java@1.19.2.1&r1=/branchv3/src/example2.java@1.19"), testCase.getDiffLink(file));
    }

    @Test
    void testGetFile() throws Exception {
        assertEquals(new URL("http://1.2.3.4/source/xref/branchv3/src/example2.java?r=1.19.2.1"), testCase.getFileLink(file));
    }

    @Test
    void testGetChangeSetLink() throws Exception {
        assertNull(testCase.getChangeSetLink(new CVSChangeLogSet.CVSChangeLog()));
    }

}
