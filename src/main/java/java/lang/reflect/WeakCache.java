/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.reflect;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 缓存映射对 {@code (key, sub-key) -> value}。键和值是弱引用，但子键是强引用。
 * 键直接传递给 {@link #get} 方法，该方法还接受一个 {@code parameter} 参数。
 * 子键通过构造函数传入的 {@code subKeyFactory} 函数从键和参数计算得出。
 * 值通过构造函数传入的 {@code valueFactory} 函数从键和参数计算得出。
 * 键可以为 {@code null}，并通过身份比较，而 {@code subKeyFactory} 返回的子键或
 * {@code valueFactory} 返回的值不能为 null。子键通过其 {@link #equals} 方法进行比较。
 * 每次调用 {@link #get}、{@link #containsValue} 或 {@link #size} 方法时，
 * 当键的弱引用被清除时，缓存中的条目会被惰性地清除。单个值的弱引用被清除不会导致清除，
 * 但此类条目在逻辑上被视为不存在，并在请求其键/子键时触发 {@code valueFactory} 的重新计算。
 *
 * @author Peter Levart
 * @param <K> 键的类型
 * @param <P> 参数的类型
 * @param <V> 值的类型
 */
final class WeakCache<K, P, V> {

    // todo 一个引用队列，但不知道是谁的
    private final ReferenceQueue<K> refQueue
        = new ReferenceQueue<>();
    // 键类型为 Object 以支持 null 键
    private final ConcurrentMap<Object, ConcurrentMap<Object, Supplier<V>>> map
        = new ConcurrentHashMap<>();
    // todo 暂时不知道这个反转map是来干什么的
    private final ConcurrentMap<Supplier<V>, Boolean> reverseMap
        = new ConcurrentHashMap<>();
    // BiFunction<A,B,C> 其中A、B为入参，C为返回值
    // todo 子键工厂，目前感觉是提供子键用的
    private final BiFunction<K, P, ?> subKeyFactory;
    // todo 值工厂，目前感觉是提供值用的
    private final BiFunction<K, P, V> valueFactory;

    /**
     * 构造一个 {@code WeakCache} 实例
     *
     * @param subKeyFactory 一个函数，将 {@code (key, parameter)} 映射为子键
     * @param valueFactory  一个函数，将 {@code (key, parameter)} 映射为值
     * @throws NullPointerException 如果 {@code subKeyFactory} 或
     *                              {@code valueFactory} 为 null
     */
    public WeakCache(BiFunction<K, P, ?> subKeyFactory,
                     BiFunction<K, P, V> valueFactory) {
        // 从使用层面来说，new WeakCache<>(new KeyFactory(), new ProxyClassFactory());
        // subKeyFactory->KeyFactory
        // valueFactory->ProxyClassFactory
        this.subKeyFactory = Objects.requireNonNull(subKeyFactory);
        this.valueFactory = Objects.requireNonNull(valueFactory);
    }

    /**
     * 通过缓存查找值。此方法始终会计算 {@code subKeyFactory} 函数，
     * 如果缓存中没有给定 (key, subKey) 对的条目或条目已被清除，
     * 则会可选地计算 {@code valueFactory} 函数。
     *
     * @param key       可能为 null 的键
     * @param parameter 与键一起用于创建子键和值的参数（不应为 null）
     * @return 缓存的值（永不为 null）
     * @throws NullPointerException 如果传入的 {@code parameter} 或
     *                              {@code subKeyFactory} 计算的 {@code sub-key} 或
     *                              {@code valueFactory} 计算的 {@code value} 为 null
     */
    public V get(K key, P parameter) {
        Objects.requireNonNull(parameter);
        //todo 暂时不懂
        expungeStaleEntries();
        //如果key是空，则是一个代表NULL的强引用Object对象，否则是一个虚引用的子类对象
        Object cacheKey = CacheKey.valueOf(key, refQueue);

        // 获取二级缓存
        ConcurrentMap<Object, Supplier<V>> valuesMap = map.get(cacheKey);
        // 下面这个 if (valuesMap == null) {...}代码块是符合多线程的写法
        if (valuesMap == null) {
            // map.putIfAbsent返回值是map中上一个value，所以这里用oldValuesMap
            ConcurrentMap<Object, Supplier<V>> oldValuesMap
                = map.putIfAbsent(cacheKey,
                                  valuesMap = new ConcurrentHashMap<>());
            //如果有旧值，说明其他线程在当前线程走到这里的时候已经创建了二级缓存，所以直接赋值给valuesMap
            if (oldValuesMap != null) {
                valuesMap = oldValuesMap;
            }
        }
        // 到这里为止，我们已经拿到了二级缓存，一级缓存的key是该方法的入参
        // 那么二级缓存需要通过一级缓存和方法的另一个入参来计算

        // 这个类的构造函数的入参其中之一就是subKeyFactory，提供了如何获取二级缓存key的方法
        Object subKey = Objects.requireNonNull(subKeyFactory.apply(key, parameter));
        //这里获取了最终的value，是个Supplier
        Supplier<V> supplier = valuesMap.get(subKey);
        // todo 暂时不知道这个fatory是干什么
        Factory factory = null;

        // 直接while循环到todo 什么情况下break
        while (true) {
            //如果最终获取的值不是空，直接调用他的get方法就可以返回了
            if (supplier != null) {
                // supplier 可能是 Factory 或 CacheValue<V> 实例
                V value = supplier.get();
                if (value != null) {
                    return value;
                }
            }
            // 否则缓存中没有 supplier
            // 或者 supplier 返回了 null（可能是清除的 CacheValue
            // 或未成功安装 CacheValue 的 Factory）

            // 惰性地构造 Factory
            if (factory == null) {
                factory = new Factory(key, parameter, subKey, valuesMap);
            }

            if (supplier == null) {
                supplier = valuesMap.putIfAbsent(subKey, factory);
                if (supplier == null) {
                    // 成功安装 Factory
                    supplier = factory;
                }
                // 否则重试获胜的 supplier
            } else {
                if (valuesMap.replace(subKey, supplier, factory)) {
                    // 成功替换
                    // 清除的 CacheEntry / 不成功的 Factory
                    // 替换为我们的 Factory
                    supplier = factory;
                } else {
                    // 重试当前的 supplier
                    supplier = valuesMap.get(subKey);
                }
            }
        }
    }

    /**
     * 检查指定的非空值是否已在此 {@code WeakCache} 中。检查使用身份比较，
     * 无论值的类是否重写了 {@link Object#equals}。
     *
     * @param value 要检查的非空值
     * @return 如果给定的 {@code value} 已缓存，则返回 true
     * @throws NullPointerException 如果值为 null
     */
    public boolean containsValue(V value) {
        Objects.requireNonNull(value);

        expungeStaleEntries();
        return reverseMap.containsKey(new LookupValue<>(value));
    }

    /**
     * 返回当前缓存的条目数，当键/值被垃圾回收时，此数量会减少。
     */
    public int size() {
        expungeStaleEntries();
        return reverseMap.size();
    }

    /**
     * 通过轮询引用队列并移除与已清除键关联的条目来清除缓存中的陈旧条目。
     */
    private void expungeStaleEntries() {
        CacheKey<K> cacheKey;
        while ((cacheKey = (CacheKey<K>)refQueue.poll()) != null) {
            cacheKey.expungeFrom(map, reverseMap);
        }
    }

    /**
     * 一个工厂类，继承自 {@link Supplier}，todo 讲解该类
     */
    private final class Factory implements Supplier<V> {

        private final K key;
        private final P parameter;
        private final Object subKey;
        private final ConcurrentMap<Object, Supplier<V>> valuesMap;

        Factory(K key, P parameter, Object subKey,
                ConcurrentMap<Object, Supplier<V>> valuesMap) {
            this.key = key;
            this.parameter = parameter;
            this.subKey = subKey;
            this.valuesMap = valuesMap;
        }

        @Override
        public synchronized V get() { // 序列化访问
            // 重新检查
            Supplier<V> supplier = valuesMap.get(subKey);
            if (supplier != this) {
                // 在我们等待时发生了变化：
                // 可能是我们被 CacheValue 替换
                // 或者由于失败而被移除 ->
                // 返回 null 以通知 WeakCache.get() 重试循环
                return null;
            }
            // 否则仍然是我们（supplier == this）

            // 创建新值
            V value = null;
            try {
                value = Objects.requireNonNull(valueFactory.apply(key, parameter));
            } finally {
                if (value == null) { // 失败时移除我们
                    valuesMap.remove(subKey, this);
                }
            }
            // 到达此处的唯一路径是 value 不为 null
            assert value != null;

            // 用 CacheValue（弱引用）包装值
            CacheValue<V> cacheValue = new CacheValue<>(value);

            // 放入 reverseMap
            reverseMap.put(cacheValue, Boolean.TRUE);

            // 尝试用 CacheValue 替换我们（这应该总是成功）
            if (!valuesMap.replace(subKey, this, cacheValue)) {
                throw new AssertionError("不应到达此处");
            }

            // 成功用新的 CacheValue 替换我们 -> 返回其包装的值
            return value;
        }
    }

    /**
     * 持有引用的值供应商的通用类型。
     * 实现的 {@link #equals} 和 {@link #hashCode} 通过身份比较引用。
     */
    private interface Value<V> extends Supplier<V> {}

    /**
     * 一个优化的 {@link Value}，用于在 {@link WeakCache#containsValue} 方法中查找值，
     * 以便我们不必构造整个 {@link CacheValue} 来查找引用。
     */
    private static final class LookupValue<V> implements Value<V> {
        private final V value;

        LookupValue(V value) {
            this.value = value;
        }

        @Override
        public V get() {
            return value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value); // 通过身份比较
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this ||
                   obj instanceof Value &&
                   this.value == ((Value<?>) obj).get();  // 通过身份比较
        }
    }

    /**
     * 一个 {@link Value}，弱引用其引用。
     */
    private static final class CacheValue<V>
        extends WeakReference<V> implements Value<V>
    {
        private final int hash;

        CacheValue(V value) {
            super(value);
            this.hash = System.identityHashCode(value); // 通过身份比较
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            V value;
            return obj == this ||
                   obj instanceof Value &&
                   // 清除的 CacheValue 仅等于自身
                   (value = get()) != null &&
                   value == ((Value<?>) obj).get(); // 通过身份比较
        }
    }

    /**
     * 包含弱引用 {@code key} 的 CacheKey。它将自己注册到 {@code refQueue}，
     * 以便在 {@link WeakReference} 被清除时用于清除条目。
     */
    private static final class CacheKey<K> extends WeakReference<K> {

        // 用于 null 键的替代品
        private static final Object NULL_KEY = new Object();

        static <K> Object valueOf(K key, ReferenceQueue<K> refQueue) {
            return key == null
                   // null 键意味着我们无法弱引用它，
                   // 因此我们使用 NULL_KEY 单例作为缓存键
                   ? NULL_KEY
                   // 非 null 键需要用 WeakReference 包装
                   : new CacheKey<>(key, refQueue);
        }

        private final int hash;

        private CacheKey(K key, ReferenceQueue<K> refQueue) {
            super(key, refQueue);
            this.hash = System.identityHashCode(key);  // 通过身份比较
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            K key;
            return obj == this ||
                   obj != null &&
                   obj.getClass() == this.getClass() &&
                   // 清除的 CacheKey 仅等于自身
                   (key = this.get()) != null &&
                   // 通过身份比较键
                   key == ((CacheKey<K>) obj).get();
        }

        /**
         * 从 map 和 reverseMap 中清除与此 CacheKey 关联的缓存条目。
         *
         * @param map 主缓存 map
         * @param reverseMap 用于跟踪值的反向 map
         */
        void expungeFrom(ConcurrentMap<?, ? extends ConcurrentMap<?, ?>> map,
                         ConcurrentMap<?, Boolean> reverseMap) {
            // 仅通过键移除总是安全的，因为在 CacheKey 被清除并入队后，
            // 它仅等于自身（参见 equals 方法）...
            ConcurrentMap<?, ?> valuesMap = map.remove(this);
            // 如果需要，也从 reverseMap 中移除
            if (valuesMap != null) {
                for (Object cacheValue : valuesMap.values()) {
                    reverseMap.remove(cacheValue);
                }
            }
        }
    }
}
