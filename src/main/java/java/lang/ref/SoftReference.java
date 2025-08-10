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
 * 软引用对象，由垃圾收集器根据内存需求决定是否清除。软引用通常用于实现内存敏感的缓存。
 *
 * <p> 假设垃圾收集器在某个时间点确定一个对象是<a href="package-summary.html#reachability">软可达</a>的。
 * 此时，它可以选择原子性地清除所有指向该对象的软引用，以及通过强引用链可达的其他软可达对象的软引用。
 * 同时或在稍后的时间，它会将那些已注册到引用队列的新清除的软引用入队。
 *
 * <p> 在虚拟机抛出<code>OutOfMemoryError</code>之前，所有指向软可达对象的软引用都保证会被清除。
 * 除此之外，对于软引用何时被清除或不同对象的软引用清除顺序没有任何限制。
 * 然而，鼓励虚拟机实现偏向不清除最近创建或最近使用的软引用。
 *
 * <p> 该类的直接实例可用于实现简单的缓存；该类或其派生子类也可用于更复杂的数据结构中，以实现更高级的缓存。
 * 只要软引用的引用对象是强可达的（即实际在使用中），软引用就不会被清除。
 * 因此，例如，一个高级缓存可以通过保持对最近使用条目的强引用来防止这些条目被丢弃，而将剩余条目交由垃圾收集器决定是否清除。
 *
 * @author   Mark Reinhold
 * @since    1.2
 */
public class SoftReference<T> extends Reference<T> {

    /**
     * 时间戳时钟，由垃圾收集器更新。该时钟用于跟踪软引用的最后访问时间，可能影响垃圾收集器清除引用的决策。
     */
    static private long clock;

    /**
     * 每次调用get方法时更新的时间戳。虚拟机在选择要清除的软引用时可能会使用该字段，但并非必须。
     * 该时间戳有助于判断引用的使用频率。
     */
    private long timestamp;

    /**
     * 创建一个新的软引用，引用给定对象。该引用未注册到任何队列。
     *
     * @param referent 新软引用将引用的对象
     */
    public SoftReference(T referent) {
        super(referent);
        this.timestamp = clock;
    }

    /**
     * 创建一个新的软引用，引用给定对象，并注册到指定队列。
     *
     * @param referent 新软引用将引用的对象
     * @param q 引用要注册到的队列，如果不需要注册，则为<tt>null</tt>
     */
    public SoftReference(T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
        this.timestamp = clock;
    }

    /**
     * 返回该引用对象所引用的对象。如果该引用对象已被程序或垃圾收集器清除，则返回<code>null</code>。
     *
     * @return 该引用所引用的对象，如果该引用对象已被清除，则返回<code>null</code>
     */
    public T get() {
        T o = super.get();
        if (o != null && this.timestamp != clock)
            this.timestamp = clock;
        return o;
    }

}
