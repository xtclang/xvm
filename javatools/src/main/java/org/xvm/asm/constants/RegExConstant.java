package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.regex.Pattern;
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
        m_regex  = regex;
        m_nFlags = nFlags;
        }

    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Get the value of the regular expression.
     *
     * @return the constant's char string value as a {@code String}
     */
    public String getValue()
        {
        return m_regex;
        }

    /**
     * Get the compiled regular expression {@link Pattern}.
     *
     * @return the compiled regular expression {@link Pattern}
     */
    public Pattern ensurePattern()
        {
        if (m_pattern == null)
            {
            m_pattern = Pattern.compile(m_regex, m_nFlags);
            }
        return m_pattern;
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
        return m_regex;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof RegExConstant))
            {
            return -1;
            }
        int n = this.m_regex.compareTo(((RegExConstant) that).m_regex);
        if (n == 0)
            {
            n = Integer.compare(m_nFlags, ((RegExConstant) that).m_nFlags);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return quotedString(m_regex);
        }

    public int getFlags()
        {
        return m_nFlags;
        }

    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writeUtf8String(out, m_regex);
        out.writeInt(m_nFlags);
        }

    @Override
    public String getDescription()
        {
        return "regEx=" + getValueString() + " flags=" + m_nFlags;
        }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_regex, Hash.of(m_nFlags));
        }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant regular expression.
     */
    private final String m_regex;

    /**
     * The flags used to create the reg-ex pattern.
     */
    private final int m_nFlags;

    /**
     * The compiled regular expression {@link Pattern}.
     */
    private Pattern m_pattern;
    }
