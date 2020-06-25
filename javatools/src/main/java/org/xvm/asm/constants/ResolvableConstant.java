package org.xvm.asm.constants;


import org.xvm.asm.Constant;


/**
 * A constant that starts as an unresolved constant, but is eventually resolved.
 */
public interface ResolvableConstant
    {
    /**
     * @return the constant, iff it has been resolved; otherwise null
     */
    Constant getResolvedConstant();

    /**
     * Use the specified constant to resolve the ResolvableConstant.
     *
     * @param constant  the actual, underlying constant
     */
    void resolve(Constant constant);

    /**
     * @return the resolved constant, if one exists, otherwise the ResolvableConstant that needs to
     *         be resolved
     */
    default Constant unwrap()
        {
        ResolvableConstant constWrapper = this;
        while (true)
            {
            Constant constUnwrapped = constWrapper.getResolvedConstant();
            if (constUnwrapped instanceof ResolvableConstant)
                {
                constWrapper = (ResolvableConstant) constUnwrapped;
                }
            else
                {
                return constUnwrapped == null
                        ? (Constant) constWrapper
                        : constUnwrapped;
                }
            }
        }
    }
