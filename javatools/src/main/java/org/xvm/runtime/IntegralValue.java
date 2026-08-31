package org.xvm.runtime;


/**
 * A handle that carries an integral value, whatever width it is represented in.
 *
 * <p>An Ecstasy {@code Int} argument does not have one Java representation: it arrives as a
 * {@link ObjectHandle.JavaLong} when it fits in 64 bits and as a {@code LongLongHandle} when it
 * does not. Bodies that accept one therefore could not be typed by the declared Ecstasy type
 * alone - {@code xRTRandom.invokeInt} tests {@code hArg instanceof JavaLong} and casts to
 * {@code LongLongHandle} in the else branch, which is precisely the runtime type-dispatch this
 * work exists to remove.</p>
 *
 * <p>This interface is the common type those two representations share, so a native taking an
 * {@code Int} can be declared against it and its body can ask the value a question instead of
 * asking which class it is.</p>
 *
 * <p><b>Why an interface rather than a sealed hierarchy.</b> A sealed type's permitted subclasses
 * must share its package when there is no named module, and there is none here;
 * {@code JavaLong} lives in this package while {@code LongLongHandle} lives in
 * {@code template.numbers}. An abstract class tier between them and {@link ObjectHandle} would
 * also be wrong, because {@code JavaLong} is not only an integer - it backs {@code Char},
 * {@code Bit} and {@code Boolean} too. Naming what the handle CARRIES rather than what it IS keeps
 * that honest.</p>
 */
public interface IntegralValue {
    /**
     * @param fSigned  true to interpret the value as signed
     *
     * @return true iff the value fits in a 64-bit long under that interpretation, and
     *         {@link #longValue()} is therefore the whole of it
     */
    boolean fitsLong(boolean fSigned);

    /**
     * @return the low 64 bits of the value, which is the entire value when
     *         {@link #fitsLong(boolean)} holds
     */
    long longValue();
}
