/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Consumer;
/**
 * 引用队列，垃圾收集器在检测到适当的可达性变化后，将已注册的引用对象附加到该队列中。
 *
 * @author   Mark Reinhold
 * @since    1.2
 */
public class ReferenceQueue<T> {

    /**
     * 构造一个新的引用对象队列。
     */
    public ReferenceQueue() { }

    /**
     * 一个特殊的空实现，不允许将引用对象入队。
     */
    private static class Null<S> extends ReferenceQueue<S> {
        /**
         * 尝试将引用对象入队，但始终返回 false。
         *
         * @param r 要入队的引用对象
         * @return 始终返回 false
         */
        boolean enqueue(Reference<? extends S> r) {
            return false;
        }
    }

    /**
     * 表示空和已入队状态的静态实例。
     */
    static ReferenceQueue<Object> NULL = new Null<>();
    static ReferenceQueue<Object> ENQUEUED = new Null<>();

    /**
     * 用于同步的锁对象。
     */
    static private class Lock { };
    private Lock lock = new Lock();

    /**
     * 引用队列的头部。
     */
    private volatile Reference<? extends T> head = null;

    /**
     * 引用队列的长度。
     */
    private long queueLength = 0;

    /**
     * 将引用对象入队到队列中。
     *
     * @param r 要入队的引用对象
     * @return 如果引用成功入队则返回 true，否则返回 false
     */
    boolean enqueue(Reference<? extends T> r) { /* 仅由 Reference 类调用 */
        synchronized (lock) {
            // 检查引用是否已经入队或为空
            ReferenceQueue<?> queue = r.queue;
            if ((queue == NULL) || (queue == ENQUEUED)) {
                return false;
            }
            assert queue == this;
            r.next = (head == null) ? r : head;
            head = r;
            queueLength++;
            // 在将引用添加到列表后标记为已入队
            r.queue = ENQUEUED;
            if (r instanceof FinalReference) {
                sun.misc.VM.addFinalRefCount(1);
            }
            lock.notifyAll();
            return true;
        }
    }

    /**
     * 从队列中移除并返回头部引用。
     *
     * @return 如果头部引用存在则返回，否则返回 null
     */
    private Reference<? extends T> reallyPoll() {       /* 必须持有锁 */
        Reference<? extends T> r = head;
        if (r != null) {
            r.queue = NULL;
            // 在移除引用之前更新队列状态
            @SuppressWarnings("unchecked")
            Reference<? extends T> rn = r.next;
            head = (rn == r) ? null : rn;
            r.next = r;
            queueLength--;
            if (r instanceof FinalReference) {
                sun.misc.VM.addFinalRefCount(-1);
            }
            return r;
        }
        return null;
    }

    /**
     * 轮询队列以查看是否有可用的引用对象。如果立即有可用的引用对象，
     * 则将其从队列中移除并返回。否则，此方法立即返回 <tt>null</tt>。
     *
     * @return 如果立即有可用的引用对象则返回，否则返回 <code>null</code>
     */
    public Reference<? extends T> poll() {
        if (head == null)
            return null;
        synchronized (lock) {
            return reallyPoll();
        }
    }

    /**
     * 移除队列中的下一个引用对象，阻塞直到有引用对象可用或给定的超时时间到期。
     *
     * <p> 此方法不提供实时保证：它通过调用 {@link Object#wait(long)} 方法来调度超时。
     *
     * @param timeout 如果为正数，则在等待引用对象添加到队列时最多阻塞 <code>timeout</code>
     *                毫秒。如果为零，则无限期阻塞。
     *
     * @return 如果在指定的超时时间内有可用的引用对象则返回，否则返回 <code>null</code>
     *
     * @throws IllegalArgumentException 如果超时参数为负数
     * @throws InterruptedException 如果超时等待被中断
     */
    public Reference<? extends T> remove(long timeout)
        throws IllegalArgumentException, InterruptedException
    {
        if (timeout < 0) {
            throw new IllegalArgumentException("超时时间为负数");
        }
        synchronized (lock) {
            Reference<? extends T> r = reallyPoll();
            if (r != null) return r;
            long start = (timeout == 0) ? 0 : System.nanoTime();
            for (;;) {
                lock.wait(timeout);
                r = reallyPoll();
                if (r != null) return r;
                if (timeout != 0) {
                    long end = System.nanoTime();
                    timeout -= (end - start) / 1000_000;
                    if (timeout <= 0) return null;
                    start = end;
                }
            }
        }
    }

    /**
     * 移除队列中的下一个引用对象，阻塞直到有引用对象可用。
     *
     * @return 阻塞直到有引用对象可用时返回
     * @throws InterruptedException 如果等待被中断
     */
    public Reference<? extends T> remove() throws InterruptedException {
        return remove(0);
    }

    /**
     * 遍历队列并对每个引用执行给定的操作。适用于诊断目的。
     *
     * 警告：使用此方法时应确保不要保留迭代引用的引用对象（在 FinalReference 的情况下），
     * 以免不必要地延长它们的生命周期。
     *
     * @param action 对每个引用执行的操作
     */
    void forEach(Consumer<? super Reference<? extends T>> action) {
        for (Reference<? extends T> r = head; r != null;) {
            action.accept(r);
            @SuppressWarnings("unchecked")
            Reference<? extends T> rn = r.next;
            if (rn == r) {
                if (r.queue == ENQUEUED) {
                    // 仍然在队列中 -> 到达链的末尾
                    r = null;
                } else {
                    // 已经出队: r.queue == NULL; ->
                    // 当被队列轮询器超越时，从头重新开始
                    r = head;
                }
            } else {
                // 链中的下一个
                r = rn;
            }
        }
    }
}
