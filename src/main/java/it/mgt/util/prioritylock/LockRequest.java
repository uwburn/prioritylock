package it.mgt.util.prioritylock;

class LockRequest implements Comparable<LockRequest> {

    private Prioritizable prioritizable;
    private long createdOn;
    private boolean isNotified = false;

    private boolean timedWaitAttempted = false;

    LockRequest(Prioritizable prioritizable) {
        this.prioritizable = prioritizable;
        this.createdOn = System.currentTimeMillis();
    }

    synchronized void doWait(long timeout) throws InterruptedException {
        if (timeout < 0L) {
            throw new IllegalArgumentException("timeout cannot be nagative");
        }
        if (timeout != 0L) {
            if (!this.isNotified) {
                wait(timeout);
            }
            this.timedWaitAttempted = true;
        } else {
            while (!this.isNotified) {
                wait(0L);
            }

            this.timedWaitAttempted = false;
        }
        this.isNotified = false;
    }

    synchronized void doNotify() {
        this.isNotified = true;
        notify();
    }

    int getPriority() {
        return prioritizable.getPriority();
    }

    public boolean isTimedWaitAttempted() {
        return timedWaitAttempted;
    }

    public int compareTo(LockRequest otherRequest) {
        if (otherRequest != null) {
            int priorityDiff = this.prioritizable.getPriority() - otherRequest.prioritizable.getPriority();
            if (priorityDiff != 0) {
                return -priorityDiff;
            }

            if (this.createdOn < otherRequest.createdOn) {
                return -1;
            }

            return 1;
        }

        return -1;
    }

}
