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
 * 用于实现对象终结（finalization）的最终引用类。
 * 该类继承自 {@link Reference} 类，提供了一种用于对象终结的机制。
 * 通常用于管理需要在垃圾回收之前进行终结处理的对象。
 *
 * @param <T> 被引用对象的类型
 */
class FinalReference<T> extends Reference<T> {

    /**
     * 使用给定的被引用对象和引用队列构造一个新的 FinalReference 实例。
     *
     * @param referent 被引用的对象
     * @param q 引用队列，当被引用对象被终结时，该引用会被加入此队列
     */
    public FinalReference(T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
    }

    /**
     * 重写 enqueue 方法，防止该引用被加入队列。
     * 此方法抛出 {@link InternalError}，表示该方法永远不应被调用。
     *
     * @return 此方法永远不会正常返回，总是抛出 InternalError
     * @throws InternalError 总是抛出，表示该方法永远不应被调用
     */
    @Override
    public boolean enqueue() {
        throw new InternalError("should never reach here");
    }
}

