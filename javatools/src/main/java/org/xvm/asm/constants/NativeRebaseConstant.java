package org.xvm.asm.constants;


import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;


/**
 * NativeRebaseConstant is a transient, pseudo constant that represents a native type that does
 * not exist outside of (previous to) the runtime, and could not have been naturally created.
 * Its purpose is to provide a native class representation where there is only an interface known.
 *
 * This TypeConstant is *never* registered with the ConstantPool and is intended to be used only
 * by the runtime.
 */
public class NativeRebaseConstant
        extends ClassConstant
    {
    /**
     * Construct a {@link NativeRebaseConstant} representing the specified interface.
     */
    public NativeRebaseConstant(ClassConstant constIface)
        {
        super(constIface.getConstantPool(), constIface.getParentConstant(), constIface.getName());

        assert constIface.getComponent().getFormat() == Component.Format.INTERFACE;

        m_constIface = constIface;
        }


    // ----- type specific methods  ----------------------------------------------------------------

    /**
     * @return the underlying ClassConstant
     */
    public ClassConstant getClassConstant()
        {
        return m_constIface;
        }


    // ----- Constant methods ----------------------------------------------------------------------


    @Override
    public boolean containsUnresolved()
        {
        return super.containsUnresolved() || m_constIface.containsUnresolved();
        }

    @Override
    public boolean validate(ErrorListener errs)
        {
        return true;
        }

    @Override
    public Format getFormat()
        {
        return Format.NativeClass;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof NativeRebaseConstant))
            {
            return -1;
            }
        return m_constIface.compareDetails(((NativeRebaseConstant) that).m_constIface);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        throw new IllegalStateException();
        }

    @Override
    public int hashCode()
        {
        return -m_constIface.hashCode();
        }

    @Override
    public String toString()
        {
        return getValueString();
        }

    @Override
    public String getValueString()
        {
        return "Native(" + m_constIface.getValueString() + ')';
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The underlying type.
     */
    private final ClassConstant m_constIface;
    }