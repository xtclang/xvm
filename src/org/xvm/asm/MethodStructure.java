package org.xvm.asm;


import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.StructureContainer.ClassContainer;
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

    /**
     * Obtain the MethodConstant that holds the identity of this Method.
     *
     * @return the MethodConstant representing the identity of this
     *         MethodStructure
     */
    public MethodConstant getMethodConstant()
        {
        return (MethodConstant) getIdentityConstant();
        }

    /**
     * When the property is created, if it does not have a known type, one can be temporarily
     * created that represents an eventually-resolved type.
     *
     * @param sType  the type string to use for the time being
     */
    public void provideTemporaryType(String sType)
        {
        assert m_type == null;
        m_type = new UnresolvedTypeConstant(getConstantPool(), sType);
        }

    public void resolveType(TypeConstant type)
        {
        assert type != null;
        assert m_type instanceof UnresolvedTypeConstant;
        assert !((UnresolvedTypeConstant) m_type).isTypeResolved();

        ((UnresolvedTypeConstant) m_type).resolve(type);
        m_type = type;
        }

    /**
     * Obtain the PropertyConstant that holds the identity of this Property.
     *
     * @return the PropertyConstant representing the identity of this
     *         PropertyStructure
     */
    public PropertyConstant getPropertyConstant()
        {
        return (PropertyConstant) getIdentityConstant();
        }

    /**
     * @return the TypeConstant representing the data type of the property value
     */
    public TypeConstant getTypeConstant()
        {
        return m_type;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

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

    private TypeConstant m_type;
    }
