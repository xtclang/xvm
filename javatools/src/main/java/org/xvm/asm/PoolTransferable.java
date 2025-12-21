package org.xvm.asm;


import org.xvm.util.Copyable;


/**
 * Extended interface for Constants that can be transferred between ConstantPools.
 * <p>
 * This interface replaces the old {@code adoptedBy(ConstantPool)} pattern which used
 * Java's {@code clone()} internally. The new {@code transferTo()} method:
 * <ul>
 *   <li>Has explicit, type-safe semantics</li>
 *   <li>Optimizes same-pool transfers (returns {@code this})</li>
 *   <li>Recursively transfers child constants</li>
 *   <li>Registers the result in the target pool</li>
 * </ul>
 * <p>
 * <b>Why Constants need pool transfer:</b>
 * <ul>
 *   <li>Each module has its own {@link ConstantPool}</li>
 *   <li>When module B references a type from module A, it needs its own constant</li>
 *   <li>Pools are separate for serialization, incremental compilation, and interning</li>
 * </ul>
 * <p>
 * <b>Example implementation:</b>
 * <pre>{@code
 * public class IntConstant extends Constant implements PoolTransferable<IntConstant> {
 *     private final PackedInteger value;
 *
 *     @Override
 *     public IntConstant transferTo(ConstantPool pool) {
 *         if (pool == getConstantPool()) {
 *             return this;  // Already in target pool
 *         }
 *         return pool.register(new IntConstant(pool, getFormat(), value));
 *     }
 * }
 *
 * public class ParameterizedTypeConstant extends TypeConstant
 *         implements PoolTransferable<ParameterizedTypeConstant> {
 *
 *     @Override
 *     public ParameterizedTypeConstant transferTo(ConstantPool pool) {
 *         if (pool == getConstantPool()) {
 *             return this;
 *         }
 *         // Transfer child constants first
 *         TypeConstant baseTransferred = baseType.transferTo(pool);
 *         TypeConstant[] paramsTransferred = transferArray(pool, typeParams);
 *
 *         return pool.register(new ParameterizedTypeConstant(
 *             pool, baseTransferred, paramsTransferred));
 *     }
 * }
 * }</pre>
 *
 * @param <T>  the concrete constant type (for type-safe returns)
 *
 * @see Copyable
 * @see ConstantPool#register(Constant)
 */
public interface PoolTransferable<T extends Constant> extends Copyable<T> {

    /**
     * Transfer this constant to a different {@link ConstantPool}.
     * <p>
     * If this constant already belongs to the target pool, returns {@code this}.
     * Otherwise, creates a new constant in the target pool with the same value,
     * recursively transferring any child constants.
     * <p>
     * The returned constant will be registered in the target pool.
     *
     * @param pool  the target pool
     *
     * @return this constant if already in target pool, otherwise a new constant
     *         registered in the target pool
     */
    T transferTo(ConstantPool pool);

    /**
     * Check if this constant can be used directly in the given pool without transfer.
     * <p>
     * Default implementation checks pool identity. Subclasses may override to
     * handle shared pools or other special cases.
     *
     * @param pool  the pool to check
     *
     * @return true if this constant belongs to (or is shared with) the given pool
     */
    default boolean isInPool(ConstantPool pool) {
        return getConstantPool() == pool;
    }

    /**
     * Get the pool this constant belongs to.
     *
     * @return the owning ConstantPool
     */
    ConstantPool getConstantPool();

    /**
     * Helper method to transfer an array of constants to a target pool.
     * <p>
     * Returns the same array if all constants are already in the target pool.
     * Otherwise, creates a new array with transferred constants.
     *
     * @param pool   the target pool
     * @param array  the array of constants to transfer (may be null)
     * @param <C>    the constant type
     *
     * @return the original array if no transfer needed, otherwise a new array
     */
    @SuppressWarnings("unchecked")
    static <C extends Constant> C[] transferArray(ConstantPool pool, C[] array) {
        if (array == null || array.length == 0) {
            return array;
        }

        C[] result = null;
        for (int i = 0; i < array.length; i++) {
            C original = array[i];
            C transferred = (C) original.transferTo(pool);

            if (transferred != original) {
                if (result == null) {
                    // First difference - create copy of array
                    result = array.clone();
                }
                result[i] = transferred;
            }
        }

        return result != null ? result : array;
    }
}
