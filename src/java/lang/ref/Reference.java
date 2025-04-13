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

import sun.misc.Cleaner;
import sun.misc.JavaLangRefAccess;
import sun.misc.SharedSecrets;
/**
 * 引用对象的抽象基类。该类定义了所有引用对象共有的操作。由于引用对象是与垃圾回收器紧密合作实现的，
 * 因此不能直接子类化该类。
 *
 * @author   Mark Reinhold
 * @since    1.2
 */
public abstract class Reference<T> {

    /**
     * 该引用对象所引用的目标对象。该字段由垃圾回收器特殊处理。
     */
    private T referent;         /* 由GC特殊处理 */

    /**
     * 该引用对象注册的队列，如果未注册任何队列，则为NULL。
     */
    volatile ReferenceQueue<? super T> queue;

    /**
     * 队列或待处理列表中的下一个引用，具体取决于该引用的状态。该字段用于链接队列或待处理列表中的引用。
     */
    @SuppressWarnings("rawtypes")
    volatile Reference next;

    /**
     * discovered字段由垃圾回收器用于链接发现的引用。它也用于链接待处理列表中的引用。
     */
    transient private Reference<T> discovered;  /* 由VM使用 */

    /**
     * 用于与垃圾回收器同步的锁对象。垃圾回收器必须在每个回收周期开始时获取该锁。
     */
    static private class Lock { }
    private static Lock lock = new Lock();

    /**
     * 等待入队的引用列表。垃圾回收器将引用添加到该列表，而Reference-handler线程从中移除引用。
     */
    private static Reference<Object> pending = null;

    /**
     * 一个高优先级的线程，用于将待处理的引用入队。
     */
    private static class ReferenceHandler extends Thread {

        /**
         * 确保指定的类已初始化。
         *
         * @param clazz 要初始化的类
         */
        private static void ensureClassInitialized(Class<?> clazz) {
            try {
                Class.forName(clazz.getName(), true, clazz.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw (Error) new NoClassDefFoundError(e.getMessage()).initCause(e);
            }
        }

        static {
            // 预加载并初始化InterruptedException和Cleaner类，以避免在运行循环中出现问题。
            ensureClassInitialized(InterruptedException.class);
            ensureClassInitialized(Cleaner.class);
        }

        /**
         * 构造一个新的ReferenceHandler线程。
         *
         * @param g 线程组
         * @param name 线程名称
         */
        ReferenceHandler(ThreadGroup g, String name) {
            super(g, name);
        }

        /**
         * ReferenceHandler线程的主运行循环，持续处理待处理的引用。
         */
        public void run() {
            while (true) {
                tryHandlePending(true);
            }
        }
    }

    /**
     * 尝试处理一个待处理的引用。如果有待处理的引用，则处理并将其入队。如果没有待处理的引用且waitForNotify为true，
     * 则该方法等待直到被通知或中断。
     *
     * @param waitForNotify 如果为true，在没有待处理引用时等待通知
     * @return 如果处理了引用或线程等待了通知，则返回true；
     *         如果没有待处理引用且waitForNotify为false，则返回false
     */
    static boolean tryHandlePending(boolean waitForNotify) {
        Reference<Object> r;
        Cleaner c;
        try {
            synchronized (lock) {
                if (pending != null) {
                    r = pending;
                    // 检查引用是否为Cleaner实例
                    c = r instanceof Cleaner ? (Cleaner) r : null;
                    // 从待处理列表中取消链接该引用
                    pending = r.discovered;
                    r.discovered = null;
                } else {
                    // 如果没有待处理引用，则等待通知
                    if (waitForNotify) {
                        lock.wait();
                    }
                    return waitForNotify;
                }
            }
        } catch (OutOfMemoryError x) {
            // 让出CPU给其他线程，以便GC回收内存
            Thread.yield();
            return true;
        } catch (InterruptedException x) {
            return true;
        }

        // 如果引用是Cleaner，则调用其clean方法
        if (c != null) {
            c.clean();
            return true;
        }

        // 如果引用注册了队列，则将其入队
        ReferenceQueue<? super Object> q = r.queue;
        if (q != ReferenceQueue.NULL) q.enqueue(r);
        return true;
    }

    static {
        // 初始化ReferenceHandler线程
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        for (ThreadGroup tgn = tg;
             tgn != null;
             tg = tgn, tgn = tg.getParent());
        Thread handler = new ReferenceHandler(tg, "Reference Handler");
        handler.setPriority(Thread.MAX_PRIORITY);
        handler.setDaemon(true);
        handler.start();

        // 在SharedSecrets中提供对tryHandlePending的访问
        SharedSecrets.setJavaLangRefAccess(new JavaLangRefAccess() {
            @Override
            public boolean tryHandlePendingReference() {
                return tryHandlePending(false);
            }
        });
    }

    /**
     * 返回该引用对象的引用目标。如果引用已被清除，则返回null。
     *
     * @return 引用目标对象，如果引用已被清除，则返回null
     */
    public T get() {
        return this.referent;
    }

    /**
     * 清除该引用对象。该方法不会导致引用入队。
     */
    public void clear() {
        this.referent = null;
    }

    /**
     * 检查该引用对象是否已入队。
     *
     * @return 如果引用已入队，则返回true；否则返回false
     */
    public boolean isEnqueued() {
        return (this.queue == ReferenceQueue.ENQUEUED);
    }

    /**
     * 清除该引用对象并将其添加到其注册的队列中（如果有）。
     *
     * @return 如果引用成功入队，则返回true；否则返回false
     */
    public boolean enqueue() {
        this.referent = null;
        return this.queue.enqueue(this);
    }

    /**
     * 抛出CloneNotSupportedException。引用对象不能被有意义地克隆。
     *
     * @return 永远不会正常返回
     * @throws CloneNotSupportedException 总是抛出
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    /**
     * 使用给定的引用目标构造一个新的引用对象，不注册任何队列。
     *
     * @param referent 引用目标对象
     */
    Reference(T referent) {
        this(referent, null);
    }

    /**
     * 使用给定的引用目标和队列构造一个新的引用对象。
     *
     * @param referent 引用目标对象
     * @param queue 引用队列，如果不需要队列，则为null
     */
    Reference(T referent, ReferenceQueue<? super T> queue) {
        this.referent = referent;
        this.queue = (queue == null) ? ReferenceQueue.NULL : queue;
    }
}
