package jenkins.scm.cvs;

import hudson.Extension;
import hudson.model.Cause;
import hudson.model.InvisibleAction;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import hudson.triggers.SCMTrigger;
import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Tracks when the scheduled task has left the quiet period, so that we can use that timestamp when
 * checking out source tree.
 *
 * @author Stephen Connolly
 */
public class QuietPeriodCompleted extends InvisibleAction {

    /**
     * A comma separated list of host names or IP addresses against which remote triggers will be assumed to be
     * SCM triggers from the perspective of recording the quiet time completed. This is a mutable constant to
     * allow changing via the Groovy console in order to change the setting without having to restart.
     *
     * @see #REMOTE_NOTE
     */
    public static String REMOTE_HOSTS = System.getProperty(QuietPeriodCompleted.class.getName() + ".REMOTE_HOSTS");

    /**
     * The note which remote triggers must match in order to be assumed as SCM triggers from the perspective of
     * recording the quiet time completed. This is a mutable constant to
     * allow changing via the Groovy console in order to change the setting without having to restart.
     *
     * @see #REMOTE_HOSTS
     */
    public static String REMOTE_NOTE = System.getProperty(QuietPeriodCompleted.class.getName() + ".REMOTE_NOTE");

    private final long timestamp;

    public QuietPeriodCompleted() {
        timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Date getTimestampDate() {
        return new Date(timestamp);
    }

    @Extension
    public static class SCMTriggerTimeObserver extends QueueListener {

        @Override
        public void onLeaveWaiting(Queue.WaitingItem wi) {
            SCMTrigger.SCMTriggerCause scmCause = null;
            Cause.RemoteCause remoteCause = null;
            Set<String> remoteHosts;
            if (REMOTE_HOSTS == null) {
                remoteHosts = Collections.emptySet();
            } else {
                remoteHosts = new HashSet<String>();
                for (String host: StringUtils.split(REMOTE_HOSTS, ", ")) {
                    final String h = host.trim();
                    if (!StringUtils.isBlank(h))
                    remoteHosts.add(h);
                }
            }
            for (Cause c: wi.getCauses()) {
                if (c instanceof SCMTrigger.SCMTriggerCause) {
                    scmCause = (SCMTrigger.SCMTriggerCause) c;
                    break;
                }
                if (c instanceof Cause.RemoteCause) {
                    Cause.RemoteCause r = (Cause.RemoteCause) c;
                    for (String h: remoteHosts) {
                        // TODO when Core exposes the addr and note fields, access them directly and provide a regex
                        // for the note, i.e. see
                        // https://github.com/jenkinsci/jenkins/commit/bfa9c503d6c8e213a683e1f9643f511d67f2ead5
                        if (new Cause.RemoteCause(h, REMOTE_NOTE).equals(r)) {
                            remoteCause = r;
                            break;
                        }
                    }
                    if (remoteCause != null) {
                        break;
                    }
                }
            }
            if ((scmCause != null || remoteCause != null) && wi.getAction(QuietPeriodCompleted.class) == null) {
                wi.addAction(new QuietPeriodCompleted());
            }

        }
    }
}
