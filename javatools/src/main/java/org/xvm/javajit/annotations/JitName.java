package org.xvm.javajit.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.xvm.asm.constants.MethodConstant;

/**
 * An annotation used to annotate methods in native code so to indicate to the JIT that these
 * methods should have a fixed name in the JIT generated code.
 * <p>
 * To find the JIT name for a method, the
 * {@link org.xvm.javajit.NativeTypeSystem#findReservedJitName(MethodConstant)} method will
 * match the name and {@link java.lang.constant.MethodTypeDesc} of a natural method to the
 * corresponding annotated native method with the same name and signature.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JitName {
    /**
     * @return the name of the natural Ecstasy method the annotated method represents, or an
     *         empty string if the natural name is the same as the annotated method's name.
     */
    String value() default "";
}
