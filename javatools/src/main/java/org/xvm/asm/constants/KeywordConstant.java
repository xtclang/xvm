package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a category of classes and/or types: IsImmutable, IsConst, IsEnum, IsModule, IsPackage,
 * IsService, and IsClass.
 */
public class KeywordConstant
        extends PseudoConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is the auto-narrowing identifier "this:class".
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the category format
     */
    public KeywordConstant(ConstantPool pool, Format format)
        {
        super(pool);
        validate(format);
        m_format = format;
        }

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public KeywordConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        this(pool, format);
        }

    private void validate(Format format)
        {
        if (!isValid(format))
            {
            throw new IllegalStateException("illegal format: " + format);
            }
        }

    private boolean isValid(Format format)
        {
        return format != null && switch (format)
            {
            case IsImmutable -> true;
            case IsConst     -> true;
            case IsEnum      -> true;
            case IsModule    -> true;
            case IsPackage   -> true;
            case IsService   -> true;
            case IsClass     -> true;
            default          -> false;
            };
        }

    // ----- Pseudo-constant methods --------------------------------------------------------------

    @Override
    public boolean isCongruentWith(PseudoConstant that)
        {
        return that instanceof KeywordConstant && this.getFormat() == that.getFormat();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return m_format;
        }

    @Override
    public TypeConstant getType()
        {
        return getConstantPool().ensureTerminalTypeConstant(this);
        }

    @Override
    public boolean isClass()
        {
        // REVIEW GG
        return true;
        }

    @Override
    protected Object getLocator()
        {
        // each format is a singleton
        return m_format;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof KeywordConstant))
            {
            return -1;
            }
        return this.m_format.compareTo(((KeywordConstant) that).m_format);
        }

    @Override
    public String getValueString()
        {
        return getDescription();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        return switch (m_format)
            {
            case IsImmutable -> "immutable";
            case IsConst     -> "const";
            case IsEnum      -> "enum";
            case IsModule    -> "module";
            case IsPackage   -> "package";
            case IsService   -> "service";
            case IsClass     -> "class";
            default          -> throw new IllegalStateException("format=" + m_format);
            };
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_format.ordinal();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The Constant Format that identifies the category of class/type for this constant.
     */
    private Format m_format;
    }
