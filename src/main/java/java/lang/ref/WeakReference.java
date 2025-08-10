/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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

/**
 * 弱引用对象，它不会阻止其引用的对象被垃圾回收器标记为可终结、终结并最终回收。弱引用通常用于实现规范化映射。
 *
 * <p> 假设垃圾回收器在某个时间点确定一个对象是<a href="package-summary.html#reachability">弱可达的</a>。
 * 此时，它会自动清除所有对该对象的弱引用，以及通过强引用和软引用链可达的其他弱可达对象的所有弱引用。
 * 同时，它会将所有之前弱可达的对象标记为可终结的。在同一时间或稍后，它会将那些新清除的弱引用（如果已注册到引用队列）加入队列。
 *
 * @author   Mark Reinhold
 * @since    1.2
 */
public class WeakReference<T> extends Reference<T> {

    /**
     * 创建一个新的弱引用，该引用指向给定的对象。该引用没有注册到任何队列。
     *
     * @param referent 新弱引用将指向的对象
     */
    public WeakReference(T referent) {
        super(referent);
    }

    /**
     * 创建一个新的弱引用，该引用指向给定的对象，并注册到给定的队列。
     *
     * @param referent 新弱引用将指向的对象
     * @param q 引用要注册到的队列，如果不需要注册，则为 <tt>null</tt>
     */
    public WeakReference(T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
    }

}
