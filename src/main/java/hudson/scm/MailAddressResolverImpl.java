package hudson.scm;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.User;
import hudson.tasks.MailAddressResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@link MailAddressResolver} implementation for major CVS hosting sites.
 * @author Kohsuke Kawaguchi
 */
@Extension
public class MailAddressResolverImpl extends MailAddressResolver {
    public String findMailAddressFor(User u) {
        for (AbstractProject<?,?> p : u.getProjects()) {
            SCM scm = p.getScm();
            if (scm instanceof CVSSCM) {
                CVSSCM cvsscm = (CVSSCM) scm;

                String s = findMailAddressFor(u,cvsscm.getCvsRoot());
                if(s!=null) return s;
            }
        }

        // didn't hit any known rules
        return null;
    }

    /**
     *
     * @param scm
     *      String that represents SCM connectivity.
     */
    protected String findMailAddressFor(User u, String scm) {
        for (Map.Entry<Pattern, String> e : RULE_TABLE.entrySet())
            if(e.getKey().matcher(scm).matches())
                return u.getId()+e.getValue();
        return null;
    }

    private static final Map<Pattern,String/*suffix*/> RULE_TABLE = new HashMap<Pattern, String>();

    static {
        {// java.net
            String username = "([A-Za-z0-9_\\-])+";
            String host = "(.*.dev.java.net|kohsuke.sfbay.*)";
            Pattern cvsUrl = Pattern.compile(":pserver:"+username+"@"+host+":/cvs");

            RULE_TABLE.put(cvsUrl,"@dev.java.net");
        }

        {// source forge
            Pattern cvsUrl = Pattern.compile(":(pserver|ext):([^@]+)@([^.]+).cvs.(sourceforge|sf).net:.+");

            RULE_TABLE.put(cvsUrl,"@users.sourceforge.net");
        }

        // TODO: read some file under $HUDSON_HOME?
    }
}