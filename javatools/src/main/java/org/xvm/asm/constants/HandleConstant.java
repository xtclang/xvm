package org.xvm.asm.constants;


import org.xvm.asm.Constant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.util.Hash;


/**
 * Constant whose purpose is to represent an object handle (run-time only).
 */
public class HandleConstant
        extends FrameDependentConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param hValue  the handle
     */
    public HandleConstant(ObjectHandle hValue)
        {
        super(null);

        m_hValue = hValue;
        }


    // ----- FrameDependentConstant methods --------------------------------------------------------

    @Override
    public ObjectHandle getHandle(Frame frame)
        {
        return m_hValue;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        // no need to introduce a new format; reuse Register
        return Format.Register;
        }

    @Override
    protected int compareDetails(Constant constant)
        {
        return -1;
        }

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_hValue);
        }

    @Override
    public String getValueString()
        {
        return m_hValue.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        return getValueString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The handle.
     */
    private final ObjectHandle m_hValue;
    }