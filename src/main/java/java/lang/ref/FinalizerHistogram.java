/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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


import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Comparator;
/**
 * FinalizerHistogram 类用于支持 GC.finalizer_info 诊断命令。
 * 该类由虚拟机调用。
 */
final class FinalizerHistogram {

    /**
     * Entry 类表示 finalizer 直方图中的一个条目。
     * 它包含类名和该类的实例数量。
     */
    private static final class Entry {
        private int instanceCount; // 该类的实例数量
        private final String className; // 类名

        /**
         * 返回该条目的实例数量。
         *
         * @return 实例数量
         */
        int getInstanceCount() {
            return instanceCount;
        }

        /**
         * 将实例数量增加 1。
         */
        void increment() {
            instanceCount += 1;
        }

        /**
         * 使用指定的类名构造一个新的 Entry 对象。
         *
         * @param className 类名
         */
        Entry(String className) {
            this.className = className;
        }
    }

    /**
     * 该方法由虚拟机调用，用于获取 finalizer 直方图。
     * 它收集并统计待终结的类的实例数量。
     *
     * @return 按实例数量降序排序的 Entry 对象数组
     */
    static Entry[] getFinalizerHistogram() {
        // 用于存储类名及其对应的 Entry 对象的映射
        Map<String, Entry> countMap = new HashMap<>();

        // 包含待终结对象的引用队列
        ReferenceQueue<Object> queue = Finalizer.getQueue();

        // 遍历队列中的每个引用
        queue.forEach(r -> {
            Object referent = r.get();
            if (referent != null) {
                // 增加该引用对象所属类的实例数量
                countMap.computeIfAbsent(
                    referent.getClass().getName(), Entry::new).increment();

                // 清除引用以避免保守式 GC 导致的误保留
                referent = null;
            }
        });

        // 将映射值转换为数组并按实例数量降序排序
        Entry fhe[] = countMap.values().toArray(new Entry[countMap.size()]);
        Arrays.sort(fhe,
                Comparator.comparingInt(Entry::getInstanceCount).reversed());

        return fhe;
    }
}
