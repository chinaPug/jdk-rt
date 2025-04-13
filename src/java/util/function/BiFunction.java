/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
package java.util.function;

import java.util.Objects;
/**
 * 表示一个接受两个参数并生成结果的函数。
 * 这是 {@link Function} 接口的双参数特化版本。
 *
 * <p>这是一个 <a href="package-summary.html">函数式接口</a>，
 * 其函数方法是 {@link #apply(Object, Object)}。
 *
 * @param <T> 函数的第一个参数类型
 * @param <U> 函数的第二个参数类型
 * @param <R> 函数的结果类型
 *
 * @see Function
 * @since 1.8
 */
@FunctionalInterface
public interface BiFunction<T, U, R> {

    /**
     * 将此函数应用于给定的参数。
     *
     * @param t 函数的第一个参数
     * @param u 函数的第二个参数
     * @return 函数的结果
     */
    R apply(T t, U u);

    /**
     * 返回一个组合函数，该函数首先将此函数应用于其输入，
     * 然后将 {@code after} 函数应用于结果。
     * 如果任一函数的求值抛出异常，则该异常会传递给组合函数的调用者。
     *
     * @param <V> {@code after} 函数的输出类型，以及组合函数的输出类型
     * @param after 在此函数之后应用的函数
     * @return 一个组合函数，首先应用此函数，然后应用 {@code after} 函数
     * @throws NullPointerException 如果 after 为 null
     */
    default <V> BiFunction<T, U, V> andThen(Function<? super R, ? extends V> after) {
        // 确保 after 函数不为 null
        Objects.requireNonNull(after);

        // 返回一个新的 BiFunction，首先应用此函数，然后应用 after 函数
        return (T t, U u) -> after.apply(apply(t, u));
    }
}
