package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template._native.reflect.xRTFunction;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Constant whose purpose is to represent a run-time target binding.
 *
 * For now, it's only used for property annotation arguments.
 */
public class MethodBindingConstant
        extends FrameDependentConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param pool      the ConstantPool that will contain this Constant
     * @param idMethod  the method to bind to the frame's "this" parent
     */
    public MethodBindingConstant(ConstantPool pool, MethodConstant idMethod)
        {
        super(pool);

        m_idMethod = idMethod;
        }

    /**
     * Constructor used for deserialization.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param in    the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public MethodBindingConstant(ConstantPool pool, DataInput in)
            throws IOException
        {
        super(pool);

        m_iMethod = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        m_idMethod = (MethodConstant) getConstantPool().getConstant(m_iMethod);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the MethodConstant represented by this {@link MethodBindingConstant}.
     */
    public MethodConstant getMethodConstant()
        {
        return m_idMethod;
        }


    // ----- MethodBindingConstant methods ---------------------------------------------------------

    @Override
    public ObjectHandle getHandle(Frame frame)
        {
        // this logic is basically a re-arranged copy of MBind op functionality
        GenericHandle   hThis    = (GenericHandle) frame.getThis();
        ObjectHandle    hTarget  = hThis.getField(frame, GenericHandle.OUTER);
        MethodConstant  idMethod = m_idMethod;
        MethodStructure method   = (MethodStructure) idMethod.getComponent();

        CallChain chain;
        if (method != null && method.getAccess() == Access.PRIVATE)
            {
            chain = new CallChain(method);
            }
        else
            {
            Object nid = idMethod.resolveNestedIdentity(
                            frame.poolContext(), frame.getGenericsResolver(true));
            chain = hTarget.getComposition().getMethodCallChain(nid);
            }

        if (chain.getDepth() == 0)
            {
            return new DeferredCallHandle(xException.makeHandle(frame,
                    "Missing method \"" + idMethod.getValueString() +
                    "\" on " + hTarget.getType().getValueString()));
            }

        return xRTFunction.makeHandle(frame, chain, 0).bindTarget(frame, hTarget);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.BindTarget;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_idMethod.containsUnresolved();
        }

    @Override
    protected int compareDetails(Constant constant)
        {
        return constant instanceof MethodBindingConstant that
                ? this.getMethodConstant().compareDetails(that.getMethodConstant())
                : -1;
        }

    @Override
    public String getValueString()
        {
        return "BindTarget: " + m_idMethod.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_idMethod = (MethodConstant) pool.register(m_idMethod);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        writePackedLong(out, m_idMethod.getPosition());
        }

    @Override
    public String getDescription()
        {
        return getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_idMethod.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant for the underlying method.
     */
    private int m_iMethod;

    /**
     * The MethodConstant for the method to bind.
     */
    private transient MethodConstant m_idMethod;
    }