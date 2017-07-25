package org.xvm.asm.constants;


import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * An IdentityConstant identifies a Module, Package, Class, Property, MultiMethod, or Method.
 *
 * @author cp 2017.05.18
 */
public class AmbiguousIdentityConstant
        extends IdentityConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param pool   the ConstantPool
     */
    protected AmbiguousIdentityConstant(ConstantPool pool, IdentityConstant... aconstId)
        {
        super(pool);

        m_aconstId = aconstId;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public int getIdentityConstantCount()
        {
        return m_aconstId.length;
        }

    public IdentityConstant getIdentityConstant(int i)
        {
        return m_aconstId[i];
        }


    // ----- IdentityConstant methods --------------------------------------------------------------

    /**
     * @return the IdentityConstant that identifies the structure that contains the structure
     *         identified by this identity constant, or null if this is a module constant
     */
    public IdentityConstant getParentConstant()
        {
        return getIdentityConstant(0).getParentConstant();
        }

    /**
     * Determine the name for this identity constant. In the case of the MethodConstant, the name
     * is the name of the MultiMethodConstant.
     *
     * @return the name for this identity constant
     */
    public String getName()
        {
        return getIdentityConstant(0).getName();
        }

    /**
     * @return the Component structure that is identified by this IdentityConstant
     */
    public Component getComponent()
        {
        // could do a composite, but for now just throw
        throw new UnsupportedOperationException();
        }


    // ----- constant methods ----------------------------------------------------------------------


    @Override
    public Format getFormat()
        {
        return Format.Unresolved;
        }

    @Override
    public String getValueString()
        {
        return getIdentityConstant(0).getValueString();
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("count=")
          .append(getIdentityConstantCount());

        for (int i = 0, c = m_aconstId.length; i < c; ++i)
            {
            sb.append(", [")
              .append(i)
              .append("]=")
              .append(getIdentityConstant(i));
            }

        return sb.toString();
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        throw new UnsupportedOperationException();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        throw new UnsupportedOperationException();
        }


    // ----- fields --------------------------------------------------------------------------------

    private IdentityConstant[] m_aconstId;
    }
