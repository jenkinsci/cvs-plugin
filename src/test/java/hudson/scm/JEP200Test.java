/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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

import com.google.common.base.Predicate;
import hudson.remoting.ClassFilter;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import jenkins.security.ClassFilterImpl;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.reflections.ReflectionUtils;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import static java.lang.reflect.Modifier.*;

/**
 * Tests compatibility of the plugin with JEP-200 in Jenkins 2.102+
 * @author Oleg Nenashev
 */
public class JEP200Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-49574") // Fails on Jenkins 2.102+
    public void checkClassesForDefaultRemotingBlacklist() {
        ClassFilter cf;
        try {
            Class<?> cfclazz = Class.forName("jenkins.security.ClassFilterImpl");
            Constructor c = cfclazz.getDeclaredConstructor();
            c.setAccessible(true);
            cf = (ClassFilter) c.newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException | InvocationTargetException | InstantiationException ex) {
            if (new VersionNumber(Jenkins.VERSION).isNewerThan(new VersionNumber("2.101"))) {
                throw new AssertionError("Jenkins version is newer than 2.101, jenkins.security.ClassFilterImpl should be creatable", ex);
            }
            cf = ClassFilter.DEFAULT;
        }

        //TODO: It checks abstract classes, but not implementation
        //TODO: Use ReflectionUtils to automatically determine Callable Structures, then move it to a generic test
        checkClasses(cf, CvsChangeSet.class, CvsFile.class, CVSChangeLogSet.CVSChangeLog.class);
    }

    private void checkClasses(ClassFilter cf, Class<?> ... c) throws AssertionError {
        for (Class<?> clazz : c) {
            System.out.println("Checking " + c);
            checkClass(cf, clazz);
        }
    }

    private void checkClass(ClassFilter cf, Class<?> c) throws AssertionError {
        try {
            checkClass(c, cf, "/", c);
        } catch (FieldAsserionError ex) {
            throw new AssertionError("ClassFilter rejected class " + c + ". The field " + ex.getFieldName() + " is not serializable", ex);
        }
    }

    private void checkClass(Class<?> c, ClassFilter cf, String path, Class<?> rootClass) throws AssertionError {
        // TODO: Use better APIs when core requirement is beyond 2.102
        if (c.isInterface()) {
            // Required to process List<FooBar> & Co
            //TODO: here I gave up and explicitly added classes to the check list above
            System.out.println("Skipping interface " + path + " in " + rootClass + ": type=" + c.getName());
        } else {
           // System.out.println("Checking " + path + ": type=" + c);
            try {
                cf.check(c);
            } catch (SecurityException ex) {
                throw new FieldAsserionError(path, "ClassFilter rejected the type " + c, ex);
            }
        }

        Set<Field> allFields = ReflectionUtils.getAllFields(c, SerializableFieldPredicate.INSTANCE);
        for (Field f : allFields) {
            checkClass(f.getType(), cf, path + "/" + f.getName(), rootClass);
        }
    }

    private static class SerializableFieldPredicate implements Predicate<Field> {

        private static final SerializableFieldPredicate INSTANCE = new SerializableFieldPredicate();

        @Override
        public boolean apply(@Nullable Field field) {
            int modifiers = field.getModifiers();
            if (isStatic(modifiers) || isTransient(modifiers)) { // field may be persisted
                return false;
            }

            // We ignore primitives and enums
            Class<?> fieldType = field.getType();
            if (fieldType.isPrimitive() || fieldType.isEnum()) {
                return false;
            }

            return true;
        }
    }

    private class FieldAsserionError extends AssertionError {
        private String fieldName;

        public FieldAsserionError(String fieldName, String message, Throwable cause) {
            super(message, cause);
            this.fieldName = fieldName;
        }

        @Override
        public String getMessage() {
            return "Field '" + fieldName + "': " + super.getMessage();
        }

        public String getFieldName() {
            return fieldName;
        }
    }
}
