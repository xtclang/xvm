package org.xvm.compiler.ast;


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Indicates that a field should not be copied by the {@link AstNode#copy()} method.
 * <p>
 * Fields marked with this annotation are typically:
 * <ul>
 *   <li>Computed values that are derived during validation</li>
 *   <li>Cached values that will be recomputed</li>
 *   <li>Runtime state such as registers, labels, or context</li>
 *   <li>Temporary state used during compilation phases</li>
 * </ul>
 * <p>
 * When an AST node is copied, these fields should start with their default values
 * (null, false, 0, etc.) in the copy, and will be populated fresh during validation.
 * <p>
 * Note: This annotation replaces the previous convention of using the {@code transient}
 * keyword, which had no semantic effect since AstNode is not serializable.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface NotCopied {
}
