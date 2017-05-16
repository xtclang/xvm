package org.xvm.asm;


import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedTypeConstant;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents a method.
 *
 * @author cp 2016.04.25
 */
public class MethodStructure
        extends ClassContainer
    {
    /**
     * Construct a MethodStructure with the specified identity.
     *
     * @param structParent  the XvmStructure (probably a FileStructure, a
     *                      ModuleStructure, a PackageStructure, a
     *                      ClassStructure, a PropertyStructure, or a
     *                      MethodStructure) that contains this MethodStructure
     * @param constmethod   the constant that specifies the identity of the
     *                      method
     */
    MethodStructure(XvmStructure structParent, MethodConstant constmethod)
        {
        super(structParent, constmethod);
        }


    // ----- accessors -----------------------------------------------------------------------------

    // TODO


    // ----- Component methods ---------------------------------------------------------------------

    @Override
    public String getName()
        {
        return getIdentityConstant().getName();
        }

    @Override
    protected Component getEldestSibling()
        {
        Component parent = getParent();
        assert parent != null;

        Component sibling = parent.getMethodByConstantMap().get(getIdentityConstant());
        assert sibling != null;

        return sibling;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public MethodConstant getIdentityConstant()
        {
        return (MethodConstant) super.getIdentityConstant();
        }

    // TODO review section

    @Override
    protected void disassemble(DataInput in)
    throws IOException
        {
        m_type = (TypeConstant) getConstantPool().getConstant(readIndex(in));

        super.disassemble(in);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        ((Constant) m_type).registerConstants(pool);

        super.registerConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out)
    throws IOException
        {
        writePackedLong(out, m_type.getPosition());

        super.assemble(out);
        }


    @Override
    public String getDescription()
        {
        return new StringBuilder()
                .append("type=")
                .append(m_type)
                .append(", ")
                .append(super.getDescription())
                .toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * TODO this seems wrong like it was copied from Property
     */
    private TypeConstant m_type;
    }
