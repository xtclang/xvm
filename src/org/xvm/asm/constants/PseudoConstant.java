package org.xvm.asm.constants;


import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a constant whose purpose is to represent a level of indirection.
 */
public abstract class PseudoConstant
        extends Constant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param pool the ConstantPool that will contain this Constant
     */
    protected PseudoConstant(ConstantPool pool)
        {
        super(pool);
        }


    // ----- constant-specific functionality -------------------------------------------------------

    /**
     * Determine if this PseudoConstant is equal to or structurally equivalent with another
     * PseudoConstant, because they differ only in irrelevant ways, such as the declaration level.
     *
     * @param that  another type
     *
     * @return true iff the two constants are structurally equivalent
     */
    public boolean isCongruentWith(PseudoConstant that)
        {
        return this.equals(that);
        }

    /**
     * Note: This only applies to {@link ThisClassConstant}, {@link ChildClassConstant}, and
     * {@link ParentClassConstant}.
     *
     * @return the IdentityConstant for the structure that corresponds to the "this:class" at the
     *         level at which the constant was declared for
     */
    public IdentityConstant getDeclarationLevelClass()
        {
        throw new UnsupportedOperationException();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    protected Object getLocator()
        {
        // this protected method must be present here to make it accessible to other classes in this
        // package
        return super.getLocator();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        // this protected method must be present here to make it accessible to other classes in this
        // package
        super.assemble(out);
        }
    }
