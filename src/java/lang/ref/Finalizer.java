/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.lang.ref;

import java.security.PrivilegedAction;
import java.security.AccessController;
import sun.misc.JavaLangAccess;
import sun.misc.SharedSecrets;
import sun.misc.VM;
/**
 * Finalizer类是一个用于管理对象终结的类。它继承自FinalReference<Object>，用于在对象被垃圾回收时执行终结操作。
 * 该类通过维护一个未终结对象的链表和一个引用队列来实现终结器的管理。
 */
final class Finalizer extends FinalReference<Object> { /* Package-private; must be in
                                                          same package as the Reference
                                                          class */

    private static ReferenceQueue<Object> queue = new ReferenceQueue<>();
    private static Finalizer unfinalized = null;
    private static final Object lock = new Object();

    private Finalizer
        next = null,
        prev = null;

    /**
     * 检查当前Finalizer是否已经被终结。
     * @return 如果当前Finalizer已经被终结，则返回true，否则返回false。
     */
    private boolean hasBeenFinalized() {
        return (next == this);
    }

    /**
     * 将当前Finalizer添加到未终结对象的链表中。
     */
    private void add() {
        synchronized (lock) {
            if (unfinalized != null) {
                this.next = unfinalized;
                unfinalized.prev = this;
            }
            unfinalized = this;
        }
    }

    /**
     * 将当前Finalizer从未终结对象的链表中移除。
     */
    private void remove() {
        synchronized (lock) {
            if (unfinalized == this) {
                if (this.next != null) {
                    unfinalized = this.next;
                } else {
                    unfinalized = this.prev;
                }
            }
            if (this.next != null) {
                this.next.prev = this.prev;
            }
            if (this.prev != null) {
                this.prev.next = this.next;
            }
            this.next = this;   /* Indicates that this has been finalized */
            this.prev = this;
        }
    }

    /**
     * 构造函数，创建一个新的Finalizer实例并将其添加到未终结对象的链表中。
     * @param finalizee 需要终结的对象。
     */
    private Finalizer(Object finalizee) {
        super(finalizee, queue);
        add();
    }

    /**
     * 获取引用队列。
     * @return 返回引用队列。
     */
    static ReferenceQueue<Object> getQueue() {
        return queue;
    }

    /**
     * 注册一个对象，使其在垃圾回收时执行终结操作。
     * @param finalizee 需要注册的对象。
     */
    static void register(Object finalizee) {
        new Finalizer(finalizee);
    }

    /**
     * 执行终结操作。如果对象已经被终结，则直接返回；否则从链表中移除并调用对象的finalize方法。
     * @param jla JavaLangAccess实例，用于调用对象的finalize方法。
     */
    private void runFinalizer(JavaLangAccess jla) {
        synchronized (this) {
            if (hasBeenFinalized()) return;
            remove();
        }
        try {
            Object finalizee = this.get();
            if (finalizee != null && !(finalizee instanceof java.lang.Enum)) {
                jla.invokeFinalize(finalizee);

                /* Clear stack slot containing this variable, to decrease
                   the chances of false retention with a conservative GC */
                finalizee = null;
            }
        } catch (Throwable x) { }
        super.clear();
    }

    /**
     * 创建一个特权线程来执行终结操作，并等待其完成。
     * 该方法用于在runFinalization中创建一个独立的线程来执行终结操作，以避免主线程被阻塞。
     * @param proc 需要执行的Runnable任务。
     */
    private static void forkSecondaryFinalizer(final Runnable proc) {
        AccessController.doPrivileged(
            new PrivilegedAction<Void>() {
                public Void run() {
                    ThreadGroup tg = Thread.currentThread().getThreadGroup();
                    for (ThreadGroup tgn = tg;
                         tgn != null;
                         tg = tgn, tgn = tg.getParent());
                    Thread sft = new Thread(tg, proc, "Secondary finalizer");
                    sft.start();
                    try {
                        sft.join();
                    } catch (InterruptedException x) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }});
    }

    /**
     * 执行所有未终结对象的终结操作。该方法由Runtime.runFinalization()调用。
     */
    static void runFinalization() {
        if (!VM.isBooted()) {
            return;
        }

        forkSecondaryFinalizer(new Runnable() {
            private volatile boolean running;
            public void run() {
                // in case of recursive call to run()
                if (running)
                    return;
                final JavaLangAccess jla = SharedSecrets.getJavaLangAccess();
                running = true;
                for (;;) {
                    Finalizer f = (Finalizer)queue.poll();
                    if (f == null) break;
                    f.runFinalizer(jla);
                }
            }
        });
    }

    /**
     * FinalizerThread是一个用于执行终结操作的线程。它会在后台运行，不断从引用队列中取出Finalizer并执行其终结操作。
     */
    private static class FinalizerThread extends Thread {
        private volatile boolean running;
        FinalizerThread(ThreadGroup g) {
            super(g, "Finalizer");
        }
        public void run() {
            // in case of recursive call to run()
            if (running)
                return;

            // Finalizer thread starts before System.initializeSystemClass
            // is called.  Wait until JavaLangAccess is available
            while (!VM.isBooted()) {
                // delay until VM completes initialization
                try {
                    VM.awaitBooted();
                } catch (InterruptedException x) {
                    // ignore and continue
                }
            }
            final JavaLangAccess jla = SharedSecrets.getJavaLangAccess();
            running = true;
            for (;;) {
                try {
                    Finalizer f = (Finalizer)queue.remove();
                    f.runFinalizer(jla);
                } catch (InterruptedException x) {
                    // ignore and continue
                }
            }
        }
    }

    static {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        for (ThreadGroup tgn = tg;
             tgn != null;
             tg = tgn, tgn = tg.getParent());
        Thread finalizer = new FinalizerThread(tg);
        finalizer.setPriority(Thread.MAX_PRIORITY - 2);
        finalizer.setDaemon(true);
        finalizer.start();
    }

}
