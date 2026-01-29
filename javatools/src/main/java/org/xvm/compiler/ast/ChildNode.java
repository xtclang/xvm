package org.xvm.compiler.ast;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Marks a field as containing a child AST node (or list of child nodes).
 *
 * <p>Fields annotated with {@code @ChildNode} represent structural children of an AST node
 * that are:
 * <ul>
 *   <li>Deep-copied during {@link AstNode#copy()} operations</li>
 *   <li>Adopted by the parent node (parent reference set)</li>
 *   <li>Visited during tree traversal operations</li>
 * </ul>
 *
 * <h2>Copy Constructor Semantics</h2>
 * <p>When implementing copy constructors, {@code @ChildNode} fields must be
 * <strong>deep-copied</strong> by calling {@code copy()} on each child node,
 * and the copied children must be adopted via {@code adopt()}.
 *
 * <h2>Historical Note</h2>
 * <p>This annotation replaces the reflection-based {@code CHILD_FIELDS} arrays that were
 * previously used to track child node fields for cloning and iteration purposes.
 *
 * @see ComputedState
 * @see AstNode#copy()
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface ChildNode {
    /**
     * Index of this child in the parent's child list (for ordering).
     * Children are visited in ascending index order.
     *
     * @return the ordinal index of this child field
     */
    int index();

    /**
     * Optional description of this child's role in the AST structure.
     *
     * @return description of the child node
     */
    String description() default "";
}
