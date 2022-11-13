package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.util.Hash;


/**
 * Represent a category of keyword classes and/or types: "const", "enum", "module", "package",
 * "service", and "class".
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

        if (!isValid(format))
            {
            throw new IllegalStateException("illegal format: " + format);
            }
        f_format = format;
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

    private boolean isValid(Format format)
        {
        return format != null && switch (format)
            {
            case IsConst   -> true;
            case IsEnum    -> true;
            case IsModule  -> true;
            case IsPackage -> true;
            case IsClass   -> true;
            default        -> false;
            };
        }

    /**
     * @return an equivalent base type
     */
    public TypeConstant getBaseType()
        {
        ConstantPool pool = getConstantPool();
        return switch (f_format)
            {
            case IsConst    -> pool.typeConst();
            case IsEnum     -> pool.typeEnumValue();
            case IsModule   -> pool.typeModule();
            case IsPackage  -> pool.typePackage();
            case IsClass    -> pool.typeObject();
            default         -> throw new IllegalStateException();
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
        return f_format;
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
        return f_format;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof KeywordConstant))
            {
            return -1;
            }
        return this.f_format.compareTo(((KeywordConstant) that).f_format);
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
        return switch (f_format)
            {
            case IsConst   -> "const";
            case IsEnum    -> "enum";
            case IsModule  -> "module";
            case IsPackage -> "package";
            case IsClass   -> "class";
            default        -> throw new IllegalStateException("format=" + f_format);
            };
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(f_format);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The Constant Format that identifies the category of class/type for this constant.
     */
    private final Format f_format;
    }
