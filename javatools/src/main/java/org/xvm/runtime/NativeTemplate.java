package org.xvm.runtime;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Names the Ecstasy class that a native template implements.
 *
 * <p>Without this, the binding is derived from the FILE NAME: the scan strips a leading {@code x}
 * and the {@code .class} suffix, so {@code xRTDelegate.class} serves {@code RTDelegate}. That
 * convention has three costs. It is invisible - nothing in the source says which Ecstasy class a
 * template answers for. It is silent when broken - renaming a template unregisters it, with no
 * error, because a template whose Ecstasy class is missing is simply skipped. And it makes the
 * name load-bearing, so an ordinary refactor is blocked by it: splitting {@code xRTDelegate} into
 * an abstract base and a concrete object-array delegate cannot give the concrete one a clearer
 * name, because the name is the binding.</p>
 *
 * <p>Where present, this annotation is the binding and the file name means nothing. Where absent,
 * the existing convention still applies, so templates can be annotated a few at a time rather than
 * all at once.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NativeTemplate {
    /**
     * @return the Ecstasy class name this template implements, as the scan spells it - for example
     *         {@code "collections.arrays.RTDelegate"}
     */
    String value();
}
