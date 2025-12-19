package org.xvm.compiler.ast;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Marks a field as derived/computed rather than part of the AST structure.
 * <p>
 * These fields are populated during compilation stages (resolution, validation, etc.)
 * and should NOT be copied when creating a new node with different children via
 * {@link AstNode#withChildren}.
 * <p>
 * Examples of derived fields:
 * <ul>
 *   <li>Resolved types ({@code m_type})</li>
 *   <li>Jump labels ({@code m_label})</li>
 *   <li>Cached computations</li>
 *   <li>Validation context</li>
 *   <li>Component references</li>
 * </ul>
 * <p>
 * This annotation replaces the use of Java's {@code transient} keyword, which is
 * tied to serialization semantics. Using a semantic annotation makes the intent
 * clear: "this field is computed, not structural."
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Derived {
}
