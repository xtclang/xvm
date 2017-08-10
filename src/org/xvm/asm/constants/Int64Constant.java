package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.ConstantPool;

import org.xvm.util.PackedInteger;


/**
 * Represent a 64-bit signed integer constant.
 */
public class Int64Constant
        extends IntConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public Int64Constant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        assert format == Format.Int64;
        }

    /**
     * Construct a constant whose value is a signed 64-bit PackedInteger.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param pint  the PackedInteger value
     */
    public Int64Constant(ConstantPool pool, PackedInteger pint)
        {
        super(pool, Format.Int64, pint);
        }
    }
