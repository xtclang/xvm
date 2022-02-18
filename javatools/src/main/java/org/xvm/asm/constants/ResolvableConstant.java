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
        Constant next = getResolvedConstant();
        if (next == null)
            {
            return (Constant) this;
            }

        Constant last;
        do
            {
            last = next;
            next = last.resolve();
            }
        while (next != last);

        return last;
        }
    }
