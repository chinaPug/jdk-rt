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

/**
 * 虚引用对象，当垃圾收集器确定其引用对象可以被回收时，虚引用会被放入队列中。
 * 虚引用通常用于安排对象回收后的清理操作。
 *
 * <p> 假设垃圾收集器在某个时间点确定一个对象是
 * <a href="package-summary.html#reachability">虚可达</a>的。此时，它会原子性地清除
 * 所有指向该对象的虚引用，以及所有指向从该对象可达的其他虚可达对象的虚引用。
 * 同时或在稍后的某个时间，它会将那些新清除的虚引用放入已注册的引用队列中。
 *
 * <p> 为了确保可回收对象保持可回收状态，虚引用的引用对象无法被获取：
 * 虚引用的 <code>get</code> 方法始终返回 <code>null</code>。
 *
 * @author   Mark Reinhold
 * @since    1.2
 */
public class PhantomReference<T> extends Reference<T> {

    /**
     * 返回此引用对象的引用目标。由于虚引用的引用目标始终不可访问，因此该方法始终返回
     * <code>null</code>。
     *
     * @return  <code>null</code>
     */
    public T get() {
        return null;
    }

    /**
     * 创建一个新的虚引用，引用给定的对象，并注册到给定的队列中。
     *
     * <p> 可以创建一个带有 <tt>null</tt> 队列的虚引用，但这样的引用完全无用：
     * 它的 <tt>get</tt> 方法始终返回 {@code null}，并且由于没有队列，它永远不会被放入队列中。
     *
     * @param referent 新虚引用将引用的对象
     * @param q 引用要注册到的队列，如果不需要注册，则为 <tt>null</tt>
     */
    public PhantomReference(T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
    }

}
