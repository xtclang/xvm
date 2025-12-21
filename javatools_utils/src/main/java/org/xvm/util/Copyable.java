package org.xvm.util;


/**
 * Interface for structures that support explicit structural copying.
 * <p>
 * This interface replaces the use of Java's broken {@code Cloneable} interface
 * (see Effective Java, Item 13). Unlike {@code clone()}, the {@code copy()} method:
 * <ul>
 *   <li>Has an explicit return type (not Object)</li>
 *   <li>Is guaranteed to create a new instance (except for immutable objects)</li>
 *   <li>Does not throw checked exceptions</li>
 *   <li>Copies only structural fields, not derived/cached state</li>
 * </ul>
 * <p>
 * <b>Contract:</b>
 * <ol>
 *   <li>Only copy STRUCTURAL fields (not fields marked with {@code @Derived})</li>
 *   <li>Return a new instance, OR return {@code this} for immutable objects</li>
 *   <li>Handle children/nested structures explicitly, not via reflection</li>
 *   <li>The copy should be independent - modifications to the copy should not
 *       affect the original, and vice versa</li>
 * </ol>
 * <p>
 * <b>Immutable objects:</b> Classes annotated with {@link Immutable} may return
 * {@code this} from {@code copy()} since the object cannot be modified anyway.
 * This enables structural sharing in copy-on-write data structures.
 * <p>
 * <b>Example implementation:</b>
 * <pre>{@code
 * public class BiExpression extends Expression implements Copyable<BiExpression> {
 *     private final Expression left;
 *     private final Token operator;
 *     private final Expression right;
 *
 *     @Derived  // Not copied
 *     private TypeConstant resolvedType;
 *
 *     @Override
 *     public BiExpression copy() {
 *         // Deep copy children, share immutable Token
 *         return new BiExpression(left.copy(), operator, right.copy());
 *     }
 * }
 * }</pre>
 *
 * @param <T>  the type of this object (for type-safe returns)
 *
 * @see Immutable
 */
public interface Copyable<T> {

    /**
     * Create a structural copy of this object.
     * <p>
     * Fields marked with {@code @Derived} (cached/computed values) are NOT copied.
     * For immutable objects (annotated with {@link Immutable}), this method may
     * return {@code this} since the object cannot be modified.
     *
     * @return a structural copy, or {@code this} for immutable objects
     */
    T copy();
}
