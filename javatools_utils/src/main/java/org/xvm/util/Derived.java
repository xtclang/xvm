package org.xvm.util;


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Marks a field as derived (computed/cached) rather than structural.
 * <p>
 * Fields marked with this annotation are:
 * <ul>
 *   <li>NOT copied by {@link Copyable#copy()}</li>
 *   <li>NOT transferred by {@code PoolTransferable.transferTo()}</li>
 *   <li>Recomputed lazily after copying if needed</li>
 * </ul>
 * <p>
 * This annotation replaces the use of {@code transient} for marking non-structural
 * fields. Unlike {@code transient} (which is tied to Java serialization semantics),
 * {@code @Derived} clearly expresses the intent: "this field is computed from
 * structural data and should not be copied."
 * <p>
 * <b>Common uses:</b>
 * <ul>
 *   <li>Cached type information on AST nodes</li>
 *   <li>Resolved references after name resolution</li>
 *   <li>Computed hash codes</li>
 *   <li>Lazy-initialized lookup maps</li>
 *   <li>Validation state and error flags</li>
 * </ul>
 * <p>
 * <b>Example:</b>
 * <pre>{@code
 * public class TypeConstant extends Constant {
 *     // Structural - must be copied
 *     private final ClassConstant classRef;
 *     private final TypeConstant[] typeParams;
 *
 *     // Derived - NOT copied, recomputed after transfer
 *     @Derived
 *     private volatile TypeInfo cachedTypeInfo;
 *
 *     @Derived
 *     private Map<String, PropertyInfo> propertyCache;
 * }
 * }</pre>
 * <p>
 * <b>Migration from transient:</b>
 * <pre>{@code
 * // Before (ambiguous - is this for serialization or for copying?)
 * private transient TypeInfo m_typeInfo;
 *
 * // After (clear intent)
 * @Derived
 * private TypeInfo typeInfo;
 * }</pre>
 *
 * @see Copyable
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Derived {
}
