package org.xvm.compiler.ast;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Marks a field as containing computed/cached state rather than syntax-derived structure.
 *
 * <p>Fields annotated with {@code @ComputedState} represent values that are:
 * <ul>
 *   <li>Computed during compilation phases (e.g., type resolution, validation)</li>
 *   <li>Cached for performance (e.g., resolved constants, method structures)</li>
 *   <li>Derived from the AST structure rather than representing the AST itself</li>
 * </ul>
 *
 * <h2>Copy Constructor Semantics</h2>
 * <p>When implementing copy constructors, {@code @ComputedState} fields should be
 * <strong>shallow-copied</strong> (same reference as original). This matches the behavior
 * of {@code Object.clone()}, which shallow-copies all fields including those marked
 * {@code transient}.
 *
 * <h2>Future Architecture</h2>
 * <p>In the planned Roslyn-like architecture, these fields will be moved out of AST nodes
 * entirely into a separate semantic model. The annotation serves as documentation for which
 * fields are candidates for extraction.
 *
 * <h2>Historical Note</h2>
 * <p>This annotation replaces the use of the {@code transient} keyword for documentation
 * purposes. The {@code transient} keyword is a holdover from Java Serialization and has
 * no effect on {@code Object.clone()} or the compiler's operation (since AST nodes are
 * not {@code Serializable}).
 *
 * @see AstNode#copy()
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface ComputedState {
    /**
     * Optional description of what this computed state represents.
     *
     * @return description of the computed state
     */
    String value() default "";
}
