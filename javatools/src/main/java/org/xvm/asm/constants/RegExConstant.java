package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.util.Hash;

import static org.xvm.util.Handy.quotedString;
import static org.xvm.util.Handy.readUtf8String;
import static org.xvm.util.Handy.writeUtf8String;


/**
 * Represent a regular expression constant.
 */
public class RegExConstant
        extends ValueConstant
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
    public RegExConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        this(pool, readUtf8String(in), in.readInt());
        }

    /**
     * Construct a constant whose value is a regular expression.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param regex  the regular expression pattern
     */
    public RegExConstant(ConstantPool pool, String regex, int nFlags)
        {
        super(pool);

        assert regex != null;
        f_regex  = regex;
        f_nFlags = nFlags;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the value of the regular expression
     */
    public String getValue()
        {
        return f_regex;
        }

    /**
     * @return the flags to create the regular expression pattern
     */
    public int getFlags()
        {
        return f_nFlags;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.RegEx;
        }

    @Override
    public TypeConstant getType()
        {
        return getConstantPool().typeRegEx();
        }

    @Override
    public Object getLocator()
        {
        return f_regex;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof RegExConstant))
            {
            return -1;
            }
        int n = this.f_regex.compareTo(((RegExConstant) that).f_regex);
        if (n == 0)
            {
            n = Integer.compare(f_nFlags, ((RegExConstant) that).f_nFlags);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return quotedString(f_regex);
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writeUtf8String(out, f_regex);
        out.writeInt(f_nFlags);
        }

    @Override
    public String getDescription()
        {
        return "regEx=" + getValueString() + "; flags=" + f_nFlags;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(f_regex,
               Hash.of(f_nFlags));
        }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant regular expression.
     */
    private final String f_regex;

    /**
     * The flags used to create the reg-ex pattern.
     */
    private final int f_nFlags;
    }