package org.xvm.asm;


import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

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
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a MethodStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure (probably a FileStructure) that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module
     * @param condition  the optional condition for this ModuleStructure
     */
    protected MethodStructure(XvmStructure xsParent, int nFlags, MethodConstant constId, ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        m_atypeParams  = constId.getRawParams();
        m_atypeReturns = constId.getRawReturns();
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

    @Override
    public boolean isClassContainer()
        {
        return true;
        }

    @Override
    public boolean isMethodContainer()
        {
        return true;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public MethodConstant getIdentityConstant()
        {
        return (MethodConstant) super.getIdentityConstant();
        }

    @Override
    protected void disassemble(DataInput in)
    throws IOException
        {
        super.disassemble(in);
        // TODO params & returns .. m_type = (TypeConstant) getConstantPool().getConstant(readIndex(in));
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        // TODO params & returns .. ((Constant) m_type).registerConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out)
    throws IOException
        {
        super.assemble(out);

        // TODO params & returns .. writePackedLong(out, m_type.getPosition());
        }


    @Override
    public String getDescription()
        {
        return new StringBuilder()
// TODO
//                .append("type=")
//                .append(m_type)
//                .append(", ")
                .append(super.getDescription())
                .toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The return value types.
     */
    private TypeConstant[] m_atypeReturns;

    /**
     * The parameter types.
     */
    private TypeConstant[] m_atypeParams;
    }
