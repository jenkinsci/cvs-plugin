package jenkins.scm.cvs;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import hudson.triggers.SCMTrigger;

import java.util.Date;

/**
* @author Stephen Connolly
*/
public class QuietPeriodCompleted implements Action {

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

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }

    @Extension
    public static class SCMTriggerTimeObserver extends QueueListener {

        @Override
        public void onLeaveWaiting(Queue.WaitingItem wi) {
            SCMTrigger.SCMTriggerCause scmCause = null;
            for (Cause c: wi.getCauses()) {
                if (c instanceof SCMTrigger.SCMTriggerCause) {
                    scmCause = (SCMTrigger.SCMTriggerCause) c;
                    break;
                }
            }
            if (scmCause != null && wi.getAction(QuietPeriodCompleted.class) == null) {
                wi.addAction(new QuietPeriodCompleted());
            }
        }
    }


}
