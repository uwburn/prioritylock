package it.mgt.util.prioritylock;

import java.util.Collections;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

public class PriorityLock {

    public static final Prioritizable DEFAULT_LOCK_REQUEST_PRIORITY = new Prioritizable() {
        public int getPriority() {
            return 1;
        }
    };
    private boolean isLocked = false;
    private int lockCount = 0;
    private Thread lockingThread = null;
    private PriorityQueue<LockRequest> waitingThreads = new PriorityQueue<LockRequest>();

    public void lock() {
        boolean lockAcquired = false;

        Thread.interrupted();
        do {
            try {
                lockInterruptibly(DEFAULT_LOCK_REQUEST_PRIORITY);
                lockAcquired = true;
            } catch (InterruptedException localInterruptedException) {
            }
        }
        while (!lockAcquired);
    }

    public void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(DEFAULT_LOCK_REQUEST_PRIORITY);
    }

    public void lock(Prioritizable prioritizable) {
        boolean lockAcquired = false;

        Thread.interrupted();
        do {
            try {
                lockInterruptibly(prioritizable);
                lockAcquired = true;
            } catch (InterruptedException localInterruptedException) {
            }
        }
        while (!lockAcquired);
    }

    public void lock(final int priority) {
        lock(new Prioritizable() {
            public int getPriority() {
                return priority;
            }
        });
    }

    public void lockInterruptibly(Prioritizable prioritizable) throws InterruptedException {
        lock(prioritizable, false, 0L, null);
    }

    public void lockInterruptibly(final int priority) throws InterruptedException {
        lockInterruptibly(new Prioritizable() {
            public int getPriority() {
                return priority;
            }
        });
    }

    public boolean tryLock() {
        return tryLock(DEFAULT_LOCK_REQUEST_PRIORITY);
    }

    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return lock(DEFAULT_LOCK_REQUEST_PRIORITY, true, time, unit);
    }

    public boolean tryLock(Prioritizable prioritizable) {
        synchronized (this) {
            if (this.isLocked) {
                if (this.lockingThread == Thread.currentThread()) {
                    this.lockCount += 1;
                    return true;
                }

                return false;
            }

            LockRequest lockRequest = new LockRequest(prioritizable);
            if ((this.waitingThreads.size() > 0) && (lockRequest.compareTo((LockRequest) this.waitingThreads.peek()) > 0)) {
                return false;
            }

            this.isLocked = true;
            this.lockCount = 1;
            this.lockingThread = Thread.currentThread();
            return true;
        }
    }

    public boolean tryLock(final int priority) {
        return tryLock(new Prioritizable() {
            public int getPriority() {
                return priority;
            }
        });
    }

    public boolean tryLock(Prioritizable prioritizable, long time, TimeUnit unit) throws InterruptedException {
        return lock(prioritizable, true, time, unit);
    }

    public boolean tryLock(final int priority, long time, TimeUnit unit) throws InterruptedException {
        return tryLock(new Prioritizable() {
            public int getPriority() {
                return priority;
            }
        }, time, unit);
    }

    boolean lock(Prioritizable priority, boolean isFiniteTimed, long time, TimeUnit unit) throws InterruptedException {
        if (isFiniteTimed) {
            if (time < 0L) {
                throw new IllegalArgumentException("time cannot be negative");
            }
            if (unit == null) {
                throw new NullPointerException("time unit");
            }

        }

        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        long timeOut = isFiniteTimed ? unit.toMillis(time) : 0L;

        LockRequest request = null;
        synchronized (this) {
            if ((this.isLocked) && (this.lockingThread == Thread.currentThread())) {
                this.lockCount += 1;
                return true;
            }
            request = new LockRequest(priority);
            this.waitingThreads.add(request);
        }

        boolean isLockedForThisThread;
        do {
            synchronized (this) {
                isLockedForThisThread = (this.isLocked) ||
                        (this.waitingThreads.peek() != request);
                if (!isLockedForThisThread) {
                    this.isLocked = true;
                    this.lockCount = 1;
                    this.waitingThreads.remove(request);
                    this.lockingThread = Thread.currentThread();
                    return true;
                }
                if (request.isTimedWaitAttempted()) {
                    this.waitingThreads.remove(request);
                    return false;
                }
            }
            try {
                request.doWait(timeOut);
            } catch (InterruptedException e) {
                synchronized (this) {
                    this.waitingThreads.remove(request);
                }

                Thread.interrupted();
                throw e;
            }
        }
        while (isLockedForThisThread);

        return false;
    }

    public synchronized void unlock() {
        if (this.lockingThread != Thread.currentThread()) {
            throw new IllegalMonitorStateException(
                    "Calling thread has not locked this lock");
        }
        this.lockCount -= 1;
        if (this.lockCount == 0) {
            this.isLocked = false;
            this.lockingThread = null;
            if (this.waitingThreads.size() > 0)
                ((LockRequest) this.waitingThreads.peek()).doNotify();
        }
    }

    public synchronized int getHighestQueuedPriority() {
        if (waitingThreads.size() > 0)
            return Collections.min(waitingThreads).getPriority();
        else
            return Integer.MAX_VALUE;
    }
}
