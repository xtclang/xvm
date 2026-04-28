package org.xtclang.ecstasy;

/**
 * Native representation of `ecstasy.Comparable`.
 *
 * We hit a bug in the Java compiler/runtime: Object extends Comparable, but Java reflection does
 * not show it. As a result, nothing on this interface is "reachable".
 */
public interface Comparable {
}
