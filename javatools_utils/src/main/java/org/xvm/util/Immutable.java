package org.xvm.util;


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Indicates that instances of the annotated class are immutable.
 * <p>
 * An immutable object cannot have its state modified after construction.
 * All fields should be final, and any mutable objects referenced must not
 * be exposed or modified.
 * <p>
 * This is a compile-time documentation annotation with no runtime effect.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Immutable {
}
