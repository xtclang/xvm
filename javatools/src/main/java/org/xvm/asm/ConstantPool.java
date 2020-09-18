package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.attribute.FileTime;

import java.time.Instant;
import java.time.ZoneOffset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Vector;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.Constant.Format;

import org.xvm.asm.constants.*;
import org.xvm.asm.constants.TypeConstant.Relation;
import org.xvm.asm.constants.TypeInfo.Progress;

import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.OpSupport;

import org.xvm.type.Decimal;

import org.xvm.util.Auto;
import org.xvm.util.ListMap;
import org.xvm.util.PackedInteger;

import static org.xvm.compiler.Lexer.isValidIdentifier;
import static org.xvm.compiler.Lexer.isValidQualifiedModule;

import static org.xvm.util.Handy.checkElementsNonNull;
import static org.xvm.util.Handy.quotedString;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A shared pool of all Constant objects used in a particular FileStructure.
 */
public class ConstantPool
        extends XvmStructure
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ConstantPool.
     *
     * @param fileStructure  the FileStructure that contains this ConstantPool
     */
    public ConstantPool(FileStructure fileStructure)
        {
        super(fileStructure);
        }


    // ----- public API ----------------------------------------------------------------------------

    /**
     * Obtain the Constant that is currently stored at the specified index. A runtime exception will
     * occur if the index is invalid.
     *
     * @param i  the index, for example obtained during the disassembly process
     *
     * @return the Constant at that index
     */
    public Constant getConstant(int i)
        {
        return i == -1 ? null : m_listConst.get(i);
        }

    /**
     * Obtain the corresponding Constant that is currently stored in this pool.
     *
     * @param constant  the Constant to find in the pool
     *
     * @return the corresponding constant from the pool, it was already present in this pool
     */
    public Constant getConstant(Constant constant)
        {
        return constant == null
                ? null
                : ensureConstantLookup(constant.getFormat()).get(constant);
        }

    /**
     * Register a Constant. This is used when a new Constant is created by the ConstantPool, but it
     * can also be used directly by a consumer, and it's used during the bulk (re-)registration of
     * Constants by the {@link XvmStructure#registerConstants} method of all of the various parts
     * of the FileStructure.
     * <p/>
     * The caller should use the returned constant in lieu of the constant that the caller passed
     * in.
     *
     * @param constant  the Constant to register
     *
     * @return if the passed Constant was not previously registered, then it is returned; otherwise,
     *         the previously registered Constant (which should be used in lieu of the passed
     *         Constant) is returned
     */
    public Constant register(Constant constant)
        {
        // to allow this method to be used blindly, i.e. for constants that may be optional within a
        // given structure, simply pass back null refs
        if (constant == null)
            {
            return null;
            }

        // before registering the constant, see if there is a simpler alternative to use; for
        // example, this allows a type constant that refers to a typedef constant to be replaced
        // with the type constant that the typedef refers to, removing a level of indirection;
        // also it's imperative to avoid a recursive call from TypeConstant.equals() implementation,
        // which has a possibility of locking up the ConcurrentHashMap.get()
        constant = constant.resolveTypedefs();

        // check if the Constant is already registered
        Map<Constant, Constant> mapConstants = ensureConstantLookup(constant.getFormat());
        Constant                constantOld  = mapConstants.get(constant);

        boolean fRegisterRecursively = false;
        if (constantOld == null)
            {
            // constants that contain unresolved information need to be resolved before they are
            // registered; note that if m_fRecurseReg is true, we could be in a compile phase that has already
            // experienced one or more name resolution errors, and so there could still be unresolved
            // constants (just ignore them here; they should have been logged as errors already)
            if (constant.containsUnresolved())
                {
                return constant;
                }

            if (constant.getContaining() != this)
                {
                constant = constant.adoptedBy(this);
                }

            synchronized (this)
                {
                if (mapConstants.containsKey(constant))
                    {
                    // it was concurrently inserted
                    return mapConstants.get(constant);
                    }

                constant.setPosition(m_listConst.size());
                m_listConst.add(constant);
                mapConstants.put(constant, constant);

                // also allow the constant to be looked up by a locator
                Object oLocator = constant.getLocator();
                if (oLocator != null)
                    {
                    Constant constOld = ensureLocatorLookup(constant.getFormat()).put(oLocator, constant);
                    if (constOld != null && !constOld.equals(constant))
                        {
                        throw new IllegalStateException("locator collision: old=" + constOld + ", new=" + constant);
                        }
                    }
                }

            // make sure that the recursively referenced constants are all
            // registered (and that they are aware of their being referenced)
            fRegisterRecursively = true;
            }
        else
            {
            constant = constantOld;
            }

        if (m_fRecurseReg)
            {
            // this can happen only in a single-thread scenario at the end of the compilation;
            // the first time that this constant is registered, the constant has to recursively
            // register any constants that it refers to
            // .. and each time the constant is registered, we tally that registration so that we
            // can later order the constants from most to least referenced
            fRegisterRecursively = constant.addRef();
            }

        if (fRegisterRecursively)
            {
            constant.registerConstants(this);

            // once all of the modules are linked together, we know all of the valid upstream
            // constant pools that we are allowed to refer to from this constant pool, so this
            // is an assertion to make sure that we don't accidentally refer to a constant pool
            // that isn't in that set of valid pools
            constant.checkValidPools(m_setValidPools, new int[] {0});
            }

        return constant;
        }

    /**
     * Create a set or ConstantPools (upstream) that this ConstantPool is allowed to depend on.
     */
    public void buildValidPoolSet()
        {
        Set<ConstantPool> set = m_setValidPools;
        if (set.isEmpty())
            {
            contributeToValidPoolSet(set);
            }
        }

    /**
     * Add ConstantPool references that this one is allowed to depend on to the specified set.
     */
    private void contributeToValidPoolSet(Set<ConstantPool> set)
        {
        if (set.add(this))
            {
            FileStructure file = getFileStructure();
            for (String sModule : file.moduleNames())
                {
                ModuleStructure moduleFingerprint = file.getModule(sModule);
                if (moduleFingerprint.isFingerprint())
                    {
                    ModuleStructure moduleUpstream = moduleFingerprint.getFingerprintOrigin();
                    // if the module is not there, TypeCompositionStatement will report it
                    if (moduleUpstream != null)
                        {
                        moduleUpstream.getConstantPool().contributeToValidPoolSet(set);
                        }
                    }
                }
            }
        }

    /**
     * Given the specified byte value, obtain a ByteConstant that represents it.
     *
     * @param b  the byte value
     *
     * @return a ByteConstant for the passed byte value
     */
    public UInt8Constant ensureByteConstant(int b)
        {
        // check the pre-existing constants first
        UInt8Constant constant = (UInt8Constant) ensureLocatorLookup(Format.UInt8).get(Byte.valueOf((byte) b));
        if (constant == null)
            {
            constant = (UInt8Constant) register(new UInt8Constant(this, b));
            }
        return constant;
        }

    /**
     * Given the specified byte array value, obtain a ByteStringConstant that represents it.
     *
     * @param ab  the byte array value
     *
     * @return a ByteStringConstant for the passed byte array value
     */
    public UInt8ArrayConstant ensureByteStringConstant(byte[] ab)
        {
        UInt8ArrayConstant constant = new UInt8ArrayConstant(this, ab.clone());
        return (UInt8ArrayConstant) register(constant);
        }

    /**
     * Given the specified character value, obtain a CharConstant that represents it.
     *
     * @param ch  the character value
     *
     * @return a CharConstant for the passed character value
     */
    public CharConstant ensureCharConstant(int ch)
        {
        // check the cache
        if (ch <= 0x7F)
            {
            CharConstant constant = (CharConstant) ensureLocatorLookup(Format.Char).get(Character.valueOf((char) ch));
            if (constant != null)
                {
                return constant;
                }
            }

        return (CharConstant) register(new CharConstant(this, ch));
        }

    /**
     * Given the specified String value, obtain a CharStringConstant that represents it.
     *
     * @param s  the String value
     *
     * @return a CharStringConstant for the passed String value
     */
    public StringConstant ensureStringConstant(String s)
        {
        // check the pre-existing constants first
        StringConstant constant = (StringConstant) ensureLocatorLookup(Format.String).get(s);
        if (constant == null)
            {
            constant = (StringConstant) register(new StringConstant(this, s));
            }
        return constant;
        }

    public LiteralConstant ensureLiteralConstant(Format format, String s)
        {
        return (LiteralConstant) ensureLiteralConstant(format, s, null);
        }

    public Constant ensureLiteralConstant(Format format, String s, Object oValue)
        {
        switch (format)
            {
            case IntLiteral:
            case FPLiteral:
            case Date:
            case Time:
            case DateTime:
            case Duration:
            case Path:
                LiteralConstant constant = (LiteralConstant) ensureLocatorLookup(format).get(s);
                if (constant == null)
                    {
                    constant = (LiteralConstant) register(new LiteralConstant(this, format, s, oValue));
                    }
                return constant;

            case Int16:
            case Int32:
            case Int64:
            case Int128:
            case UInt16:
            case UInt32:
            case UInt64:
            case UInt128:
                return ensureIntConstant((PackedInteger) oValue, format);

            case Int8:
                return ensureInt8Constant(((PackedInteger) oValue).getInt());

            case UInt8:
                return ensureUInt8Constant(((PackedInteger) oValue).getInt());

            default:
                throw new IllegalStateException("unsupported format: " + format);
            }
        }

    public Int8Constant ensureInt8Constant(int n)
        {
        Int8Constant constant = (Int8Constant) ensureLocatorLookup(Format.Int8).get(n);
        if (constant == null)
            {
            constant = (Int8Constant) register(new Int8Constant(this, n));
            }
        return constant;
        }

    public UInt8Constant ensureBitConstant(int n)
        {
        return ensureUInt8Constant(Format.Bit, n);
        }

    public UInt8Constant ensureNibbleConstant(int n)
        {
        return ensureUInt8Constant(Format.Nibble, n);
        }

    public UInt8Constant ensureUInt8Constant(int n)
        {
        return ensureUInt8Constant(Format.UInt8, n);
        }

    public UInt8Constant ensureUInt8Constant(Format format, int n)
        {
        switch (format)
            {
            case Bit:
            case Nibble:
            case UInt8:
                UInt8Constant constant = (UInt8Constant) ensureLocatorLookup(format).get(n);
                if (constant == null)
                    {
                    constant = (UInt8Constant) register(new UInt8Constant(this, format, n));
                    }
                return constant;

            default:
                throw new IllegalArgumentException("format=" + format);
            }
        }

    /**
     * Given the specified {@code long} value, obtain a IntConstant that represents it.
     *
     * @param n  the {@code long} value of the integer
     *
     * @return an IntConstant for the passed {@code long} value
     */
    public IntConstant ensureIntConstant(long n)
        {
        return ensureIntConstant(PackedInteger.valueOf(n));
        }

    /**
     * Given the specified PackedInteger value, obtain a IntConstant that represents it.
     *
     * @param pint  the PackedInteger value
     *
     * @return an IntConstant for the passed PackedInteger value
     */
    public IntConstant ensureIntConstant(PackedInteger pint)
        {
        return ensureIntConstant(pint, Format.Int64);
        }


    /**
     * Given the specified PackedInteger value and a format, obtain a IntConstant that represents it.
     *
     * @param pint    the PackedInteger value
     * @param format  the format of the integer constant, one of {@link Format#Int16},
     *                {@link Format#Int32}, {@link Format#Int64}, {@link Format#Int128},
     *                {@link Format#VarInt}, {@link Format#UInt16}, {@link Format#UInt32},
     *                {@link Format#UInt64}, {@link Format#UInt128}, or {@link Format#VarUInt}
     *
     * @return an IntConstant for the passed PackedInteger value
     */
    public IntConstant ensureIntConstant(PackedInteger pint, Format format)
        {
        switch (format)
            {
            case Int16:
            case Int32:
            case Int64:
            case Int128:
            case VarInt:
            case UInt16:
            case UInt32:
            case UInt64:
            case UInt128:
            case VarUInt:
                // check the pre-existing constants first
                IntConstant constant = (IntConstant) ensureLocatorLookup(format).get(pint);
                if (constant == null)
                    {
                    constant = (IntConstant) register(new IntConstant(this, format, pint));
                    }
                return constant;

            default:
                throw new IllegalStateException("unsupported format: " + format);
            }
        }

    /**
     * Given the specified decimal value, obtain a DecimalConstant that represents it.
     *
     * @param dec  the decimal value
     *
     * @return a DecimalConstant for the passed decimal value
     */
    public DecimalConstant ensureDecimalConstant(Decimal dec)
        {
        Format format;
        switch (dec.getBitLength())
            {
            case 32:
                format = Format.Dec32;
                break;
            case 64:
                format = Format.Dec64;
                break;
            case 128:
                format = Format.Dec128;
                break;
            default:
                throw new IllegalArgumentException("unsupported decimal type: " + dec.getClass().getSimpleName());
            }

        DecimalConstant constant = (DecimalConstant) ensureLocatorLookup(format).get(dec);
        if (constant == null)
            {
            constant = (DecimalConstant) register(new DecimalConstant(this, dec));
            }
        return constant;
        }

    /**
     * Given the specified decimal floating point value (stored in a byte array), obtain a
     * VarFPConstant that represents it.
     *
     * @param abVal  the floating point value encoded in 2, 4, 8, 16, 32 (and so on) bytes
     *
     * @return a VarFPConstant for the passed floating point value
     */
    public VarFPConstant ensureVarDecimalConstant(byte[] abVal)
        {
        return (VarFPConstant) register(new VarFPConstant(this, Format.VarDec, abVal));
        }

    /**
     * Given the specified floating point value, obtain a BFloat16Constant that represents it.
     *
     * @param flVal  the floating point value
     *
     * @return a BFloat16Constant for the passed floating point value
     */
    public BFloat16Constant ensureBFloat16Constant(float flVal)
        {
        BFloat16Constant constant = (BFloat16Constant) ensureLocatorLookup(Format.BFloat16).get(flVal);
        if (constant == null)
            {
            constant = (BFloat16Constant) register(new BFloat16Constant(this, flVal));
            }
        return constant;
        }

    /**
     * Given the specified floating point value, obtain a Float16Constant that represents it.
     *
     * @param flVal  the floating point value
     *
     * @return a Float16Constant for the passed floating point value
     */
    public Float16Constant ensureFloat16Constant(float flVal)
        {
        Float16Constant constant = (Float16Constant) ensureLocatorLookup(Format.Float16).get(flVal);
        if (constant == null)
            {
            constant = (Float16Constant) register(new Float16Constant(this, flVal));
            }
        return constant;
        }

    /**
     * Given the specified floating point value, obtain a Float32Constant that represents it.
     *
     * @param flVal  the floating point value
     *
     * @return a Float32Constant for the passed floating point value
     */
    public Float32Constant ensureFloat32Constant(float flVal)
        {
        Float32Constant constant = (Float32Constant) ensureLocatorLookup(Format.Float32).get(flVal);
        if (constant == null)
            {
            constant = (Float32Constant) register(new Float32Constant(this, flVal));
            }
        return constant;
        }

    /**
     * Given the specified floating point value, obtain a Float64Constant that represents it.
     *
     * @param flVal  the floating point value
     *
     * @return a Float64Constant for the passed floating point value
     */
    public Float64Constant ensureFloat64Constant(double flVal)
        {
        Float64Constant constant = (Float64Constant) ensureLocatorLookup(Format.Float64).get(flVal);
        if (constant == null)
            {
            constant = (Float64Constant) register(new Float64Constant(this, flVal));
            }
        return constant;
        }

    /**
     * Given the specified floating point value (stored in a byte array), obtain a Float128Constant
     * that represents it.
     *
     * @param abVal  the floating point value encoded in 16 bytes (128 bits)
     *
     * @return a Float128Constant for the passed floating point value
     */
    public Float128Constant ensureFloat128Constant(byte[] abVal)
        {
        return (Float128Constant) register(new Float128Constant(this, abVal));
        }

    /**
     * Given the specified binary floating point value (stored in a byte array), obtain a
     * VarFPConstant that represents it.
     *
     * @param abVal  the floating point value encoded in 2, 4, 8, 16, 32 (and so on) bytes
     *
     * @return a VarFPConstant for the passed floating point value
     */
    public VarFPConstant ensureVarFloatConstant(byte[] abVal)
        {
        return (VarFPConstant) register(new VarFPConstant(this, Format.VarFloat, abVal));
        }

    /**
     * @param ft  the FileTime value or null
     *
     * @return the DateTime constant for the specified FileTime (or the epoch if the FileTime is
     *         null)
     */
    public LiteralConstant ensureDateTimeConstant(FileTime ft)
        {
        if (ft == null)
            {
            return defaultDateTimeConstant();
            }

        String sDT = ft.toInstant().atOffset(ZoneOffset.UTC).toString();
        return ensureLiteralConstant(Format.DateTime, sDT);
        }

    /**
     * @param instant  the Instant value or null
     *
     * @return the DateTime constant for the specified instant (or the epoch if the Instant is null)
     */
    public LiteralConstant ensureDateTimeConstant(Instant instant)
        {
        return instant == null
                ? defaultDateTimeConstant()
                : ensureLiteralConstant(Format.DateTime, instant.toString());
        }

    /**
     * @return the DateTime constant for the epoch (zero) value
     */
    public LiteralConstant defaultDateTimeConstant()
        {
        return ensureLiteralConstant(Format.DateTime, Instant.EPOCH.toString());
        }

    /**
     * Given the specified Version value, obtain a VersionConstant that represents it.
     *
     * @param ver  a version
     *
     * @return a VersionConstant for the passed version value
     */
    public VersionConstant ensureVersionConstant(Version ver)
        {
        VersionConstant constant = (VersionConstant) ensureLocatorLookup(Format.Version).get(ver.toString());
        if (constant == null)
            {
            constant = (VersionConstant) register(new VersionConstant(this, ver));
            }
        return constant;
        }

    /**
     * Create an array constant value.
     *
     * @param constType  the type of the array
     * @param aconst     an array of constant values
     *
     * @return a constant representing the Array value
     */
    public ArrayConstant ensureArrayConstant(TypeConstant constType, Constant[] aconst)
        {
        checkElementsNonNull(aconst);

        return (ArrayConstant) register(new ArrayConstant(this, Format.Array, constType, aconst.clone()));
        }

    /**
     * Create a set constant value.
     *
     * @param constType  the type of the set
     * @param aconst     an array of constant values
     *
     * @return a constant representing the Set value
     */
    public ArrayConstant ensureSetConstant(TypeConstant constType, Constant[] aconst)
        {
        checkElementsNonNull(aconst);

        return (ArrayConstant) register(new ArrayConstant(this, Format.Set, constType, aconst.clone()));
        }

    /**
     * Create a tuple constant value.
     *
     * @param constType  the type of the tuple
     * @param aconst     an array of constant values
     *
     * @return a constant representing the tuple value
     */
    public ArrayConstant ensureTupleConstant(TypeConstant constType, Constant[] aconst)
        {
        checkElementsNonNull(aconst);

        return (ArrayConstant) register(new ArrayConstant(this, Format.Tuple, constType, aconst.clone()));
        }

    /**
     * Create a Map Entry constant.
     *
     * @param constType  the type of the Map Entry
     * @param constKey   the value of the key
     * @param constVal   the value of the entry
     *
     * @return the MapConstant representing the single Map Entry constant
     */
    public MapConstant ensureMapEntryConstant(TypeConstant constType, Constant constKey, Constant constVal)
        {
        // TODO validations
        return new MapConstant(this, constType, constKey, constVal);
        }


    /**
     * Create a Map constant.
     *
     * @param constType  the type of the Map
     * @param map        the contents of the Map constant
     *
     * @return the MapConstant representing the Map
     */
    public MapConstant ensureMapConstant(TypeConstant constType, Map<? extends Constant, ? extends Constant> map)
        {
        // TODO validations
        return new MapConstant(this, constType, map);
        }

    /**
     * Create a Range constant, which is also an Interval constant for Sequential types.
     *
     * @param const1  the start value for the Range
     * @param const2  the end value for the Range
     *
     * @return the RangeConstant representing the range or interval
     */
    public RangeConstant ensureRangeConstant(Constant const1, Constant const2)
        {
        return ensureRangeConstant(const1, false, const2, false);
        }

    /**
     * Create a Range constant, which is also an Interval constant for Sequential types.
     *
     * @param const1     the start value for the Range
     * @param fExclude1  true indicates that the start value is excluded from the Range
     * @param const2     the end value for the Range
     * @param fExclude2  true indicates that the end value is excluded from the Range
     *
     * @return the RangeConstant representing the range or interval
     */
    public RangeConstant ensureRangeConstant(Constant const1, boolean fExclude1,
                                             Constant const2, boolean fExclude2)
        {
        assert const1.getFormat() == const2.getFormat();

        return new RangeConstant(this, const1, fExclude1, const2,  fExclude2);
        }

    /**
     * Create a FileStore constant for the specified directory.
     *
     * @param sPath     the path used to specify the FileStore
     * @param constDir  the directory contents of the FileStore
     *
     * @return
     */
    public FileStoreConstant ensureFileStoreConstant(String sPath, FSNodeConstant constDir)
        {
        return new FileStoreConstant(this, sPath, constDir);
        }

    /**
     * Create a directory FSNodeConstant for the specified directory.
     *
     * @param sName       the (simple) name of the directory
     * @param ftCreated   the creation date/time of the directory, or null
     * @param ftModified  the last-modified date/time of the directory, or null
     * @param aFiles      the contents of the directory
     *
     * @return a directory FSNodeConstant
     */
    public FSNodeConstant ensureDirectoryConstant(
            String           sName,
            FileTime         ftCreated,
            FileTime         ftModified,
            FSNodeConstant[] aFiles)
        {
        return new FSNodeConstant(this, sName, ftCreated, ftModified, aFiles);
        }

    /**
     * Create a file FSNodeConstant for the specified bytes.
     *
     * @param sName       the name of the file within its directory
     * @param ftCreated   the creation date/time of the file, or null
     * @param ftModified  the last-modified date/time of the file, or null
     * @param ab          the contents of the file
     *
     * @return a file FSNodeConstant
     */
    public FSNodeConstant ensureFileConstant(
            String   sName,
            FileTime ftCreated,
            FileTime ftModified,
            byte[]   ab)
        {
        return new FSNodeConstant(this, sName, ftCreated, ftModified, ab);
        }

    /**
     * Create a match-any constant.
     *
     * @param type  the type to match
     *
     * @return the desired match-any constant
     */
    public MatchAnyConstant ensureMatchAnyConstant(TypeConstant type)
        {
        MatchAnyConstant constant = (MatchAnyConstant) ensureLocatorLookup(Format.Any).get(type);
        if (constant == null)
            {
            constant = (MatchAnyConstant) register(new MatchAnyConstant(this, type));
            }
        return constant;
        }

    /**
     * Given the specified name, obtain a NamedCondition that represents a test for the name being
     * specified.
     *
     * @param sName  a name
     *
     * @return a NamedCondition
     */
    public NamedCondition ensureNamedCondition(String sName)
        {
        NamedCondition cond = (NamedCondition) ensureLocatorLookup(Format.ConditionNamed).get(sName);
        if (cond == null)
            {
            cond = (NamedCondition) register(new NamedCondition(this, ensureStringConstant(sName)));
            }
        return cond;
        }

    /**
     * Given a constant for a particular XVM structure, obtain a PresentCondition that represents a
     * test for the structure's existence.
     *
     * @param constId   a constant specifying a particular XVM structure
     *
     * @return a PresentCondition
     */
    public PresentCondition ensurePresentCondition(Constant constId)
        {
        PresentCondition cond = (PresentCondition) ensureLocatorLookup(Format.ConditionPresent).get(constId);
        if (cond == null)
            {
            cond = (PresentCondition) register(new PresentCondition(this, constId));
            }
        return cond;
        }

    /**
     * Given a constant for a particular XVM structure and version, obtain a PresentCondition that
     * represents a test for the structure's existence of the specified version.
     *
     * @param constModule  the constant specifying the module
     * @param constVer     the version of that module to test for
     *
     * @return a VersionMatchesCondition
     */
    public VersionMatchesCondition ensureImportVersionCondition(ModuleConstant constModule, VersionConstant constVer)
        {
        return (VersionMatchesCondition) register(new VersionMatchesCondition(this, constModule, constVer));
        }

    /**
     * Given a version, obtain a VersionedCondition that represents a test for that version
     * of this module.
     *
     * @param ver  the version of this module to test for
     *
     * @return a VersionedCondition
     */
    public VersionedCondition ensureVersionedCondition(Version ver)
        {
        return ensureVersionedCondition(ensureVersionConstant(ver));
        }

    /**
     * Given a version constant, obtain a VersionedCondition that represents a test for that version
     * of this module.
     *
     * @param constVer  the version of this module to test for
     *
     * @return a VersionedCondition
     */
    public VersionedCondition ensureVersionedCondition(VersionConstant constVer)
        {
        VersionedCondition cond = (VersionedCondition) ensureLocatorLookup(Format.ConditionVersioned).get(constVer);
        if (cond == null)
            {
            cond = (VersionedCondition) register(new VersionedCondition(this, constVer));
            }
        return cond;
        }

    /**
     * Given a condition, obtain the inverse of that condition.
     *
     * @param cond   a condition
     *
     * @return a Condition representing the inverse
     */
    public ConditionalConstant ensureNotCondition(ConditionalConstant cond)
        {
        // avoid double-negatives
        if (cond instanceof NotCondition)
            {
            return ((NotCondition) cond).getUnderlyingCondition();
            }

        NotCondition condNot = (NotCondition) ensureLocatorLookup(Format.ConditionNot).get(cond);
        if (condNot == null)
            {
            condNot = (NotCondition) register(new NotCondition(this, cond));
            }
        return condNot;
        }


    /**
     * Given the multiple conditions, obtain an AnyCondition that represents them.
     *
     * @param aCondition  an array of conditions
     *
     * @return an AnyCondition
     */
    public AnyCondition ensureAnyCondition(ConditionalConstant... aCondition)
        {
        checkElementsNonNull(aCondition);
        if (aCondition.length < 2)
            {
            throw new IllegalArgumentException("at least 2 conditions required");
            }

        return (AnyCondition) register(new AnyCondition(this, aCondition));
        }

    /**
     * Given the multiple conditions, obtain an AllCondition that represents them.
     *
     * @param aCondition  an array of conditions
     *
     * @return an AllCondition
     */
    public AllCondition ensureAllCondition(ConditionalConstant... aCondition)
        {
        checkElementsNonNull(aCondition);
        if (aCondition.length < 2)
            {
            throw new IllegalArgumentException("at least 2 conditions required");
            }

        return (AllCondition) register(new AllCondition(this, aCondition));
        }

    /**
     * Obtain a Constant that represents the specified module.
     *
     * @param sName  a fully qualified module name
     *
     * @return the ModuleConstant for the specified qualified module name
     */
    public ModuleConstant ensureModuleConstant(String sName)
        {
        if (!isValidQualifiedModule(sName))
            {
            throw new IllegalArgumentException("illegal qualified module name: " + quotedString(sName));
            }

        ModuleConstant constant = (ModuleConstant) ensureLocatorLookup(Format.Module).get(sName);
        if (constant == null)
            {
            constant = (ModuleConstant) register(new ModuleConstant(this, sName));
            }
        return constant;
        }

    /**
     * Given the specified package name and the context (module or package) within which it exists,
     * obtain a PackageConstant that represents it.
     *
     * @param constParent  the ModuleConstant or PackageConstant that contains the specified package
     * @param sPackage     the unqualified name of the package
     *
     * @return the specified PackageConstant
     */
    public PackageConstant ensurePackageConstant(IdentityConstant constParent, String sPackage)
        {
        if (constParent == null)
            {
            throw new IllegalArgumentException("ModuleConstant or PackageConstant required");
            }

        // validate the package name
        if (!isValidIdentifier(sPackage))
            {
            throw new IllegalArgumentException("illegal package name: " + sPackage);
            }

        switch (constParent.getFormat())
            {
            case Module:
            case Package:
                return (PackageConstant) register(new PackageConstant(this, constParent, sPackage));

            default:
                throw new IllegalArgumentException("constant " + constParent.getFormat()
                        + " is not a Module or Package");
            }
        }

    /**
     * Given the specified class name and the context (module, package, class, method) within which
     * it exists, obtain a ClassConstant that represents it.
     *
     * @param constParent  the parent's identity
     * @param sClass       the class name
     *
     * @return the specified class constant
     */
    public ClassConstant ensureClassConstant(IdentityConstant constParent, String sClass)
        {
        switch (constParent.getFormat())
            {
            case Module:
            case Package:
            case Class:
            case Method:
            case Property:
                return (ClassConstant) register(new ClassConstant(this, constParent, sClass));

            default:
                throw new IllegalArgumentException("constant " + constParent.getFormat()
                        + " is not a valid parent");
            }
        }

    /**
     * Given the specified class type, obtain a ClassConstant that represents it.
     *
     * @param type  the class type
     *
     * @return the specified class constant
     */
    public IdentityConstant ensureClassConstant(TypeConstant type)
        {
        // check the pre-existing constants first
        IdentityConstant constant = (DecoratedClassConstant) ensureLocatorLookup(
                Format.DecoratedClass).get(type);
        if (constant == null)
            {
            if (!type.isExplicitClassIdentity(true))
                {
                throw new IllegalArgumentException("type must have an explicit class identity: " + type);
                }

            if (type.isImmutabilitySpecified() || type.isAccessSpecified())
                {
                throw new IllegalArgumentException("type must not have immutability or access: " + type);
                }

            // try to avoid using the more complicated "decorated" form of the class constant
            boolean      fUseType = false;
            TypeConstant typeCur  = type;
            do
                {
                if (typeCur.getParamsCount() > 0 || typeCur.isAnnotated())
                    {
                    fUseType = true;
                    break;
                    }

                typeCur = typeCur.isVirtualChild()
                        ? typeCur.getParentType()
                        : null;
                }
            while (typeCur != null);

            constant = fUseType || !(type.getDefiningConstant() instanceof ClassConstant)
                    ? (DecoratedClassConstant) register(new DecoratedClassConstant(this, type))
                    : (ClassConstant) type.getDefiningConstant();
            }
        return constant;
        }

    /**
     * Given an IdentityConstant for a singleton const class, obtain a constant that represents the
     * singleton instance of that class.
     *
     * @param constClass  the IdentityConstant of the singleton const class
     *
     * @return an SingletonConstant representing the singleton const value
     */
    public SingletonConstant ensureSingletonConstConstant(IdentityConstant constClass)
        {
        return constClass.getComponent().getFormat() == Component.Format.ENUMVALUE
                ? (EnumValueConstant) register(new EnumValueConstant(this, (ClassConstant) constClass))
                : (SingletonConstant) register(new SingletonConstant(this, Format.SingletonConst, constClass));
        }

    /**
     * Given a ClassConstant for a service, obtain a constant that represents the singleton instance
     * of the class.
     *
     * @param constClass  a ClassConstant of a singleton service
     *
     * @return an SingletonConstant representing the singleton service instance
     */
    public SingletonConstant ensureSingletonServiceConstant(ClassConstant constClass)
        {
        return (SingletonConstant) register(new SingletonConstant(this, Format.SingletonService, constClass));
        }

    /**
     * Helper to get a TypeConstant for a class in the Ecstasy core module.
     *
     * @param sClass  the qualified class name, dot-delimited
     *
     * @return the type for the specified class from the Ecstasy core module
     */
    public TypeConstant ensureEcstasyTypeConstant(String sClass)
        {
        return ensureTerminalTypeConstant(ensureEcstasyClassConstant(sClass));
        }

    /**
     * Helper to get a ClassConstant for a class in the Ecstasy core module.
     *
     * @param sClass  the qualified class name, dot-delimited
     *
     * @return the specified ClassConstant referring to a class from the Ecstasy core module
     */
    public ClassConstant ensureEcstasyClassConstant(String sClass)
        {
        IdentityConstant constParent = modEcstasy();
        int              ofStart     = 0;
        int              ofEnd       = sClass.indexOf('.');
        while (ofEnd >= 0)
            {
            String sName = sClass.substring(ofStart, ofEnd);
            constParent = constParent instanceof ClassConstant || sName.charAt(0) <= 'Z'
                    ? ensureClassConstant(constParent, sName)
                    : ensurePackageConstant(constParent, sName);
            ofStart = ofEnd + 1;
            ofEnd   = sClass.indexOf('.', ofStart);
            }
        return ensureClassConstant(constParent, sClass.substring(ofStart));
        }

    /**
     * This is the implementation of the "automatically imported" names that constitute the default
     * set of known names in the language. This implementation should correspond to the source file
     * "implicit.x".
     *
     * @param sName  the unqualified name to look up
     *
     * @return the IdentityConstant for the specified name, or null if the name is not implicitly
     *         imported
     */
    public IdentityConstant getImplicitlyImportedIdentity(String sName)
        {
        IdentityConstant id = m_implicits.get(sName);

        if (id == null)
            {
            String[] asParts = s_implicits.get(sName);
            if (asParts == null)
                {
                return null;
                }

            int iPart = 1;
            int cParts = asParts.length;
            assert (cParts >= 2);

            // module portion
            assert asParts[0].equals(X_PKG_IMPORT);
            id = modEcstasy();

            // handle package portion
            while (iPart < cParts)
                {
                String sPkg = asParts[iPart];
                char ch = sPkg.charAt(0);
                if (ch >= 'A' && ch <= 'Z')
                    {
                    // not a package
                    break;
                    }
                else
                    {
                    id = ensurePackageConstant(id, sPkg);
                    ++iPart;
                    }
                }

            // handle class portion
            while (iPart < cParts)
                {
                String sClz = asParts[iPart++];
                id = ensureClassConstant(id, sClz);
                }

            m_implicits.put(sName, id);
            }

        return id;
        }

    /**
     * This is the implementation of the "automatically imported" names that constitute the default
     * set of known names in the language. This implementation should correspond to the source file
     * "implicit.x".
     *
     * @param sName  the unqualified name to look up
     *
     * @return the Component for the specified name, or null if the name is not implicitly imported
     */
    public Component getImplicitlyImportedComponent(String sName)
        {
        IdentityConstant constId = getImplicitlyImportedIdentity(sName);
        if (constId == null)
            {
            return null;
            }

        return constId.getComponent();
        }

    /**
     * Look up the simple name by which the specified name is implicitly imported.
     *
     * @param sPath  the fully qualified identity path
     *
     * @return the "import as" name, or null
     */
    public String getImplicitImportName(String sPath)
        {
        return s_implicitsByPath.get(sPath);
        }

    /**
     * Given the specified typedef name and the context (module, package, class, method) within
     * which it exists, obtain a TypedefConstant that represents it.
     *
     * @param constParent  the constant representing the container of the typedef, for example a
     *                     ClassConstant
     * @param sName        the name of the typedef
     *
     * @return the specified TypedefConstant
     */
    public TypedefConstant ensureTypedefConstant(IdentityConstant constParent, String sName)
        {
        return (TypedefConstant) register(new TypedefConstant(this, constParent, sName));
        }

    /**
     * Given the specified property name and the context (module, package, class, method) within
     * which it exists, obtain a PropertyConstant that represents it.
     *
     * @param constParent  the constant representing the container of the property, for example a
     *                     ClassConstant
     * @param sName        the name of the property
     *
     * @return the specified PropertyConstant
     */
    public PropertyConstant ensurePropertyConstant(IdentityConstant constParent, String sName)
        {
        return (PropertyConstant) register(new PropertyConstant(this, constParent, sName));
        }

    /**
     * Given the specified method name and the context (module, package, class, property, method)
     * within which it exists, obtain a MultiMethodConstant that represents it.
     *
     * @param constParent  the constant representing the container of the multi-method, for example
     *                     a ClassConstant
     * @param sName        the name of the multi-method
     *
     * @return the specified MultiMethodConstant
     */
    public MultiMethodConstant ensureMultiMethodConstant(IdentityConstant constParent, String sName)
        {
        return (MultiMethodConstant) register(new MultiMethodConstant(this, constParent, sName));
        }

    /**
     * Obtain a Constant that represents the specified method.
     *
     * @param constParent    specifies the module, package, class, multi-method, method, or property
     *                       that contains the method
     * @param sName          the method name
     * @param aconstParams   the invocation parameters for the method
     * @param aconstReturns  the return values from the method
     *
     * @return the MethodConstant
     */
    public MethodConstant ensureMethodConstant(IdentityConstant constParent, String sName,
            TypeConstant[] aconstParams, TypeConstant[] aconstReturns)
        {
        assert constParent != null;

        MultiMethodConstant constMultiMethod;
        switch (constParent.getFormat())
            {
            case MultiMethod:
                constMultiMethod = (MultiMethodConstant) constParent;
                break;

            case Module:
            case Package:
            case Class:
            case Method:
            case Property:
            case TypeParameter:
            case FormalTypeChild:
                constMultiMethod = ensureMultiMethodConstant(constParent, sName);
                break;

            default:
                throw new IllegalArgumentException("constant " + constParent.getFormat()
                        + " is not a Module, Package, Class, Method, or Property");
            }

        return (MethodConstant) register(new MethodConstant(this, constMultiMethod,
                aconstParams, aconstReturns));
        }

    /**
     * Obtain a constant that represents the specified methods.
     *
     * @param constParent  the constant identifying the parent of the method
     * @param constSig     the signature of the method
     *
     * @return the MethodConstant
     */
    public MethodConstant ensureMethodConstant(IdentityConstant constParent, SignatureConstant constSig)
        {
        assert constParent != null;
        assert constSig    != null;

        MultiMethodConstant constMultiMethod;
        switch (constParent.getFormat())
            {
            case MultiMethod:
                constMultiMethod = (MultiMethodConstant) constParent;
                break;

            case Module:
            case Package:
            case Class:
            case NativeClass:
            case Method:
            case Property:
            case TypeParameter:
            case FormalTypeChild:
                constMultiMethod = ensureMultiMethodConstant(constParent, constSig.getName());
                break;

            default:
                throw new IllegalArgumentException("constant " + constParent.getFormat()
                        + " is not a Module, Package, Class, Method, or Property");
            }

        return (MethodConstant) register(new MethodConstant(this, constMultiMethod, constSig));
        }

    /**
     * Obtain a constant that represents a method signature.
     *
     * @param sName          the method name
     * @param aconstParams   the parameter types
     * @param aconstReturns  the return value types
     *
     * @return the SignatureConstant
     */
    public SignatureConstant ensureSignatureConstant(String sName, TypeConstant[] aconstParams,
            TypeConstant[] aconstReturns)
        {
        return (SignatureConstant) register(new SignatureConstant(this, sName, aconstParams,
                aconstReturns));
        }

    /**
     * Given the specified class, access, and optional type parameters, obtain a ClassTypeConstant
     * that represents that combination.
     *
     * @param constClass  a ModuleConstant, PackageConstant, or ClassConstant
     * @param access      the access level
     * @param constTypes  the optional type parameters
     *
     * @return a ClassTypeConstant
     */
    public TypeConstant ensureClassTypeConstant(Constant constClass,
                                                Access access, TypeConstant... constTypes)
        {
        TypeConstant constType = (TypeConstant) ensureLocatorLookup(Format.TerminalType).get(constClass);
        if (constType == null)
            {
            constType = (TypeConstant) register(new TerminalTypeConstant(this, constClass));
            }

        if (constTypes != null)
            {
            constType = ensureParameterizedTypeConstant(constType, constTypes);
            }

        if (access != null && !access.equals(Access.PUBLIC))
            {
            constType = ensureAccessTypeConstant(constType, access);
            }

        return constType;
        }

    /**
     * Obtain a constant that represents a type constant of a particular accessibility.
     *
     * @param constType  the underlying constant
     * @param access     the desired accessibility
     *
     * @return a type constant with the specified accessibility
     */
    public TypeConstant ensureAccessTypeConstant(TypeConstant constType, Access access)
        {
        if (access == null)
            {
            access = Access.PUBLIC;
            }

        if (constType.isAccessSpecified())
            {
            if (constType.getAccess() == access)
                {
                return constType;
                }

            if (constType instanceof AccessTypeConstant)
                {
                return ensureAccessTypeConstant(constType.getUnderlyingType(), access);
                }
            // we cannot simply remove the access modifier
            throw new IllegalArgumentException("type already has an access specified");
            }

        TypeConstant constAccess = null;

        if (access == Access.PUBLIC)
            {
            constAccess = (TypeConstant) ensureLocatorLookup(Format.AccessType).get(constType);
            }

        if (constAccess == null)
            {
            constAccess = (TypeConstant) register(new AccessTypeConstant(this, constType, access));
            }

        return constAccess;
        }

    /**
     * Obtain a constant that represents a type parameterized by the specified type parameter types.
     *
     * @param constType   the parameterized type
     * @param constTypes  the types of the parameters of the parameterized type
     *
     * @return a type constant with the specified type parameter types
     */
    public TypeConstant ensureParameterizedTypeConstant(TypeConstant constType, TypeConstant... constTypes)
        {
        if (constType.isParamsSpecified())
            {
            TypeConstant[] atypeParam = constType.getParamTypesArray();
            int c = atypeParam.length;
            CheckTypes: if (c == constTypes.length)
                {
                for (int i = 0; i < c; ++i)
                    {
                    if (!constTypes[i].equals(atypeParam[i]))
                        {
                        break CheckTypes;
                        }
                    }

                // types all match
                return constType;
                }

            // types do not match
            throw new IllegalArgumentException("type already has parameters specified");
            }

        checkElementsNonNull(constTypes);

        return (TypeConstant) register(new ParameterizedTypeConstant(this, constType, constTypes));
        }

    /**
     * Given the specified type, obtain a TypeConstant that represents the explicitly immutable form
     * of that type.
     *
     * @param constType  the TypeConstant to obtain an explicitly immutable form of
     *
     * @return the explicitly immutable form of the passed TypeConstant
     */
    public TypeConstant ensureImmutableTypeConstant(TypeConstant constType)
        {
        TypeConstant constant;
        if (constType.isImmutabilitySpecified() ||
                (!constType.containsUnresolved() && constType.isImmutable()))
            {
            constant = constType;
            }
        else
            {
            constant = (TypeConstant) ensureLocatorLookup(Format.ImmutableType).get(constType);
            if (constant != null)
                {
                return constant;
                }

            constant = new ImmutableTypeConstant(this, constType);
            }

        return (TypeConstant) register(constant);
        }

    /**
     * Given the specified type and name, obtain a TypeConstant representing a virtual child.
     *
     * @param constParent  the parent's TypeConstant (can be parameterized)
     * @param sName        the child name
     *
     * @return the TypeConstant of the virtual child type
     */
    public TypeConstant ensureVirtualChildTypeConstant(TypeConstant constParent, String sName)
        {
        return (TypeConstant) register(new VirtualChildTypeConstant(this, constParent, sName, false));
        }

    /**
     * Given the specified type and name, obtain a TypeConstant representing a virtual child.
     *
     * @param constParent  the parent's TypeConstant (can be parameterized)
     * @param sName        the child name
     *
     * @return the TypeConstant of the virtual child type
     */
    public TypeConstant ensureThisVirtualChildTypeConstant(TypeConstant constParent, String sName)
        {
        return (TypeConstant) register(new VirtualChildTypeConstant(this, constParent, sName, true));
        }

    /**
     * Given the specified base and child classes, a TypeConstant representing a virtual child.
     *
     * @param clzBase        the base class
     * @param clzChild       the child class
     * @param fFormal        if true, create formal types for the parent type constants,
     *                       otherwise canonical
     * @param fParameterize  if true, parameterize the newly created child
     * @param fAutoNarrow    if true, the virtual child represents an auto-narrowing "this" class
     *
     * @return the TypeConstant of the virtual child type
     */
    public TypeConstant ensureVirtualTypeConstant(ClassStructure clzBase, ClassStructure clzChild,
                                                  boolean fFormal, boolean fParameterize, boolean fAutoNarrow)
        {
        assert clzChild.isVirtualChild();

        String           sName     = clzChild.getName();
        ClassStructure   clzParent = (ClassStructure) clzChild.getParent();
        IdentityConstant idParent  = clzParent.getIdentityConstant();

        TypeConstant typeParent;
        TypeConstant typeTarget;
        if (clzBase.equals(clzParent) || clzBase.hasContribution(idParent, true))
            {
            // we've reached the "top"
            typeParent = fFormal
                            ? clzBase.getFormalType()
                            : clzBase.getCanonicalType();
            typeTarget = fAutoNarrow
                            ? ensureThisVirtualChildTypeConstant(typeParent, sName)
                            : ensureVirtualChildTypeConstant(typeParent, sName);
            }
        else if (clzParent.isVirtualChild())
            {
            // as we recurse, always parameterize the parent, which is never auto-narrowing
            typeParent = ensureVirtualTypeConstant(clzBase, clzParent, fFormal, true, false);
            typeTarget = ensureVirtualChildTypeConstant(typeParent, sName);
            }
        else
            {
            // somehow the classes didn't coalesce
            return null;
            }

        if (fParameterize && clzChild.getTypeParamCount() > 0)
            {
            TypeConstant[] atypeParams = fFormal
                    ? clzChild.getFormalType().getParamTypesArray()
                    : clzChild.getCanonicalType().getParamTypesArray();

            typeTarget = ensureParameterizedTypeConstant(typeTarget, atypeParams);
            }
        return typeTarget;
        }

    /**
     * Given the specified parent type and the anonymous class id, obtain a TypeConstant
     * representing an anonymous class of that parent.
     *
     * @param constParent  the parent's TypeConstant (can be parameterized)
     * @param idAnon       the anonymous class id
     *
     * @return the TypeConstant of the anonymous class type
     */
    public TypeConstant ensureAnonymousClassTypeConstant(TypeConstant constParent, ClassConstant idAnon)
        {
        return (TypeConstant) register(new AnonymousClassTypeConstant(this, constParent, idAnon));
        }

    /**
     * Given the specified type and property, obtain a TypeConstant representing a property class.
     *
     * @param constParent  the parent's TypeConstant (can be parameterized)
     * @param idProp       the property id
     *
     * @return the TypeConstant of the instance child type
     */
    public TypeConstant ensurePropertyClassTypeConstant(TypeConstant constParent, PropertyConstant idProp)
        {
        return (TypeConstant) register(new PropertyClassTypeConstant(this, constParent, idProp));
        }

    /**
     * Obtain an auto-narrowing constant that represents the class of "this".
     *
     * @return an auto-narrowing constant that represents the class of "this"
     */
    public ThisClassConstant ensureThisClassConstant(IdentityConstant constClass)
        {
        ThisClassConstant constant = (ThisClassConstant) ensureLocatorLookup(Format.ThisClass).get(constClass);
        if (constant != null)
            {
            return constant;
            }

        return (ThisClassConstant) register(new ThisClassConstant(this, constClass));
        }

    /**
     * Obtain an auto-narrowing constant that represents the parent class of the passed constant.
     *
     * @param constClass  one of: ThisClassConstant, ParentClassConstant, ChildClassConstant
     *
     * @return an auto-narrowing constant that represents the parent class of the passe constant
     */
    public PseudoConstant ensureParentClassConstant(PseudoConstant constClass)
        {
        if (constClass instanceof ChildClassConstant)
            {
            return ((ChildClassConstant) constClass).getParent();
            }

        // the ParentClassConstant's locator is the underlying constClass
        ParentClassConstant constant = (ParentClassConstant)
                ensureLocatorLookup(Format.ParentClass).get(constClass);
        if (constant != null)
            {
            return constant;
            }

        return (ParentClassConstant) register(new ParentClassConstant(this, constClass));
        }

    /**
     * Obtain an auto-narrowing constant that represents the named child class of the passed
     * constant.
     *
     * @param constClass  one of: ThisClassConstant, ParentClassConstant, ChildClassConstant
     *
     * @return an auto-narrowing constant that represents the named child class of the passed
     *         constant
     */
    public PseudoConstant ensureChildClassConstant(PseudoConstant constClass, String sName)
        {
        // eventually, we could check to see if the passed class is the parent of the child class
        // being requested, but that seems like a lot of work for something that will never happen
        return (ChildClassConstant) register(new ChildClassConstant(this, constClass, sName));
        }

    /**
     * Obtain an auto-narrowing class type constant that represents the type of "this".
     *
     * @param access  the access modifier, or null
     *
     * @return an auto-narrowing class type constant that represents the type of "this"
     */
    public TypeConstant ensureThisTypeConstant(Constant constClass, Access access)
        {
        ThisClassConstant constId;
        switch (constClass.getFormat())
            {
            case ThisClass:
                constId = (ThisClassConstant) constClass;
                break;

            case Module:
            case Package:
            case Class:
                constId = ensureThisClassConstant((IdentityConstant) constClass);
                break;

            default:
                throw new IllegalStateException("constant=" + constClass);
            }

        // get the raw type
        TypeConstant constType = (TypeConstant) ensureLocatorLookup(Format.TerminalType).get(constId);
        if (constType == null)
            {
            constType = (TypeConstant) register(new TerminalTypeConstant(this, constId));
            }
        if (access == null)
            {
            return constType;
            }

        // apply access modifier
        if (access == Access.PUBLIC)
            {
            TypeConstant constAccess = (AccessTypeConstant) ensureLocatorLookup(Format.AccessType).get(constType);
            if (constAccess != null)
                {
                return constAccess;
                }
            }
        return (TypeConstant) register(new AccessTypeConstant(this, constType, access));
        }

    /**
     * Obtain an auto-narrowing class type constant that represents the parent of the specified
     * auto-narrowing type constant, which itself must represent a non-static inner class.
     *
     * @param constChild  an auto-narrowing type constant
     *
     * @return a type representing the parent of the specified child type constant
     */
    public TypeConstant ensureParentTypeConstant(TypeConstant constChild)
        {
        if (constChild == null || !constChild.isAutoNarrowing(false)
                || !constChild.isSingleDefiningConstant() || constChild.isParamsSpecified())
            {
            throw new IllegalArgumentException("single, auto-narrowing, non-parameterized child required");
            }

        PseudoConstant constId     = ensureParentClassConstant((PseudoConstant) constChild.getDefiningConstant());
        TypeConstant   constParent = new TerminalTypeConstant(this, constId);

        // wrap the parent in the same way that the child was wrapped
        if (constChild.isAccessSpecified())
            {
            constParent = new AccessTypeConstant(this, constParent, constChild.getAccess());
            }
        if (constChild.isImmutabilitySpecified())
            {
            constParent = new ImmutableTypeConstant(this, constParent);
            }

        return (TypeConstant) register(constParent);
        }

    /**
     * Obtain an auto-narrowing class type that represents an inner class child of the
     * specified auto-narrowing type.
     *
     * @param constParent  an auto-narrowing type constant
     * @param sChild       the name of the non-static inner class to obtain a type constant for
     *
     * @return a type representing the named child of the class of the specified type constant
     */
    public TypeConstant ensureChildTypeConstant(TypeConstant constParent, String sChild)
        {
        if (constParent == null || !constParent.isAutoNarrowing(false)
                || !constParent.isSingleDefiningConstant() || constParent.isParamsSpecified())
            {
            throw new IllegalArgumentException("single, auto-narrowing, non-parameterized parent required");
            }

        PseudoConstant constId    = ensureChildClassConstant((PseudoConstant) constParent.getDefiningConstant(), sChild);
        TypeConstant   constChild = new TerminalTypeConstant(this, constId);

        // wrap the child in the same way that the parent was wrapped
        if (constParent.isAccessSpecified())
            {
            constChild = new AccessTypeConstant(this, constChild, constParent.getAccess());
            }
        if (constParent.isImmutabilitySpecified())
            {
            constChild = new ImmutableTypeConstant(this, constChild);
            }

        return constChild;
        }

    /**
     * Given the specified register index, obtain a TypeConstant that represents the type parameter.
     *
     * @param constMethod  the containing method
     * @param iReg         the register number
     * @param sName        the type parameter name
     *
     * @return the RegisterTypeConstant for the specified register number
     */
    public TypeParameterConstant ensureRegisterConstant(MethodConstant constMethod, int iReg, String sName)
        {
        TypeParameterConstant constReg = null;
        if (iReg == 0)
            {
            constReg = (TypeParameterConstant) ensureLocatorLookup(Format.TypeParameter).get(constMethod);
            }
        if (constReg == null)
            {
            constReg = (TypeParameterConstant) register(
                            new TypeParameterConstant(this, constMethod, sName, iReg));
            }
        return constReg;
        }

    /**
     * Given the specified FormalConstant and the name, obtain a constant representing the formal
     * type child constant.
     *
     * @param constFormal  a FormalConstant parent
     * @param sName        the child name
     *
     * @return the FormalTypeChildConstant corresponding to the specified parent and name
     */
    public FormalTypeChildConstant ensureFormalTypeChildConstant(IdentityConstant constFormal, String sName)
        {
        return (FormalTypeChildConstant) register(new FormalTypeChildConstant(this, constFormal, sName));
        }

    /**
     * Obtain a constant representing the type of the specified identity.
     *
     * @param constId  an identity of a class, package, module, type definition, or type parameter
     *
     * @return the TerminalTypeConstant corresponding to the specified identity
     */
    public TypeConstant ensureTerminalTypeConstant(Constant constId)
        {
        assert constId != null && !(constId instanceof TypeConstant);

        // the TerminalClassConstant's locator is the underlying constId
        TypeConstant constType = (TerminalTypeConstant)
                ensureLocatorLookup(Format.TerminalType).get(constId);
        if (constType == null)
            {
            constType = (TypeConstant) register(new TerminalTypeConstant(this, constId));
            }

        return constType;
        }

    /**
     * Create an Annotation.
     *
     * @param constClass   the class of the annotation
     * @param aconstParam  the parameters of the annotation, or null
     *
     * @return the specified Annotation constant
     */
    public Annotation ensureAnnotation(Constant constClass, Constant... aconstParam)
        {
        return (Annotation) register(new Annotation(this, constClass, aconstParam));
        }

    /**
     * Given the specified annotation class and parameters, obtain a type that represents the
     * annotated form of the specified type.
     *
     * @param constClass   the annotation class
     * @param aconstParam  the parameters for the annotation
     * @param constType    the type being annotated
     *
     * @return an annotated type constant
     */
    public AnnotatedTypeConstant ensureAnnotatedTypeConstant(Constant constClass,
            Constant[] aconstParam, TypeConstant constType)
        {
        return (AnnotatedTypeConstant) register(new AnnotatedTypeConstant(this, constClass, aconstParam, constType));
        }

    /**
     * Given the specified annotations, obtain a type that represents the annotated form of the
     * specified type.
     *
     * @param constType    the type being annotated
     * @param annotations  the annotations (will be applied in the inverse order)
     *
     * @return an annotated type constant
     */
    public AnnotatedTypeConstant ensureAnnotatedTypeConstant(TypeConstant constType, Annotation... annotations)
        {
        if (annotations.length == 0)
            {
            throw new IllegalArgumentException("annotations are required");
            }

        TypeConstant type = constType;
        for (int i = annotations.length - 1; i >= 0; --i)
            {
            type = (TypeConstant) register(new AnnotatedTypeConstant(this, annotations[i], type));
            }

        return (AnnotatedTypeConstant) type;
        }

    /**
     * Obtain a type that represents a "FutureVar" for the specified type.
     *
     * @return a FutureVar for the specified type
     */
    public TypeConstant ensureFutureVar(TypeConstant type)
        {
        return ensureAnnotatedTypeConstant(ensureParameterizedTypeConstant(typeVar(), type),
                ensureAnnotation(clzFuture()));
        }

    /**
     * Obtain a type that represents a constraint for a formal type that materializes into a
     * sequence of types.
     *
     * @return a sequence of types type constant
     */
    public TypeSequenceTypeConstant ensureTypeSequenceTypeConstant()
        {
        return (TypeSequenceTypeConstant) register(new TypeSequenceTypeConstant(this));
        }

    /**
     * Given a TypeConstant for a type to which Null may or may not be assignable, obtain a
     * TypeConstant to which Null is assignable. This corresponds to the "?" operator when applied
     * to types.
     *
     * @param constType  the type being made Nullable
     *
     * @return the intersection of the specified type and Nullable
     */
    public TypeConstant ensureNullableTypeConstant(TypeConstant constType)
        {
        // note: this is a much looser definition than the "Nullable" definition used by the
        //       TypeConstant, such that "Object" would NOT be made into "Nullable|Object"
        return typeNull().isA(constType)
                ? constType
                : ensureIntersectionTypeConstant(typeNullable(), constType);
        }

    /**
     * Given two types, obtain a TypeConstant that represents the intersection of those two types.
     * This corresponds to the "|" operator when applied to types, and is also used when a type is
     * permitted to be null (i.e. "intersection of Nullable and the other type").
     *
     * @param constType1  the first type
     * @param constType2  the second type
     *
     * @return the intersection of the two specified types
     */
    public IntersectionTypeConstant ensureIntersectionTypeConstant(TypeConstant constType1, TypeConstant constType2)
        {
        return (IntersectionTypeConstant) register(new IntersectionTypeConstant(this, constType1, constType2));
        }

    /**
     * Given two types, obtain a TypeConstant that represents the union of those two types. This
     * corresponds to the "+" operator when applied to types.
     *
     * @param constType1  the first type
     * @param constType2  the second type
     *
     * @return the union of the two specified types
     */
    public UnionTypeConstant ensureUnionTypeConstant(TypeConstant constType1, TypeConstant constType2)
        {
        return (UnionTypeConstant) register(new UnionTypeConstant(this, constType1, constType2));
        }

    /**
     * Given two types, obtain a TypeConstant that represents the difference of those two types.
     * This corresponds to the "-" operator when applied to types.
     *
     * @param constType1  the first type
     * @param constType2  the second type
     *
     * @return the difference of the two specified types
     */
    public DifferenceTypeConstant ensureDifferenceTypeConstant(TypeConstant constType1, TypeConstant constType2)
        {
        return (DifferenceTypeConstant) register(new DifferenceTypeConstant(this, constType1, constType2));
        }


    // ----- caching helpers -----------------------------------------------------------------------

    public ModuleConstant    modEcstasy()       {ModuleConstant    c = m_valEcstasy;      if (c == null) {m_valEcstasy      = c = ensureModuleConstant(ECSTASY_MODULE)                        ;} return c;}

    public ClassConstant     clzObject()        {ClassConstant     c = m_clzObject;       if (c == null) {m_clzObject       = c = (ClassConstant) getImplicitlyImportedIdentity("Object"          );} return c;}
    public ClassConstant     clzInner()         {ClassConstant     c = m_clzInner;        if (c == null) {m_clzInner        = c = (ClassConstant) getImplicitlyImportedIdentity("Inner"           );} return c;}
    public ClassConstant     clzOuter()         {ClassConstant     c = m_clzOuter;        if (c == null) {m_clzOuter        = c = (ClassConstant) getImplicitlyImportedIdentity("Outer"           );} return c;}
    public ClassConstant     clzRef()           {ClassConstant     c = m_clzRef;          if (c == null) {m_clzRef          = c = (ClassConstant) getImplicitlyImportedIdentity("Ref"             );} return c;}
    public ClassConstant     clzVar()           {ClassConstant     c = m_clzVar;          if (c == null) {m_clzVar          = c = (ClassConstant) getImplicitlyImportedIdentity("Var"             );} return c;}
    public ClassConstant     clzStruct()        {ClassConstant     c = m_clzStruct;       if (c == null) {m_clzStruct       = c = (ClassConstant) getImplicitlyImportedIdentity("Struct"          );} return c;}
    public ClassConstant     clzType()          {ClassConstant     c = m_clzType;         if (c == null) {m_clzType         = c = (ClassConstant) getImplicitlyImportedIdentity("Type"            );} return c;}
    public ClassConstant     clzConst()         {ClassConstant     c = m_clzConst;        if (c == null) {m_clzConst        = c = (ClassConstant) getImplicitlyImportedIdentity("Const"           );} return c;}
    public ClassConstant     clzService()       {ClassConstant     c = m_clzService;      if (c == null) {m_clzService      = c = (ClassConstant) getImplicitlyImportedIdentity("Service"         );} return c;}
    public ClassConstant     clzModule()        {ClassConstant     c = m_clzModule;       if (c == null) {m_clzModule       = c = (ClassConstant) getImplicitlyImportedIdentity("Module"          );} return c;}
    public ClassConstant     clzPackage()       {ClassConstant     c = m_clzPackage;      if (c == null) {m_clzPackage      = c = (ClassConstant) getImplicitlyImportedIdentity("Package"         );} return c;}
    public ClassConstant     clzEnum()          {ClassConstant     c = m_clzEnum;         if (c == null) {m_clzEnum         = c = (ClassConstant) getImplicitlyImportedIdentity("Enum"            );} return c;}
    public ClassConstant     clzEnumeration()   {ClassConstant     c = m_clzEnumeration;  if (c == null) {m_clzEnumeration  = c = (ClassConstant) getImplicitlyImportedIdentity("Enumeration"     );} return c;}
    public ClassConstant     clzCloseable()     {ClassConstant     c = m_clzCloseable;    if (c == null) {m_clzCloseable    = c = (ClassConstant) getImplicitlyImportedIdentity("Closeable"       );} return c;}
    public ClassConstant     clzException()     {ClassConstant     c = m_clzException;    if (c == null) {m_clzException    = c = (ClassConstant) getImplicitlyImportedIdentity("Exception"       );} return c;}
    public ClassConstant     clzProperty()      {ClassConstant     c = m_clzProperty;     if (c == null) {m_clzProperty     = c = (ClassConstant) getImplicitlyImportedIdentity("Property"        );} return c;}
    public ClassConstant     clzMethod()        {ClassConstant     c = m_clzMethod;       if (c == null) {m_clzMethod       = c = (ClassConstant) getImplicitlyImportedIdentity("Method"          );} return c;}
    public ClassConstant     clzFunction()      {ClassConstant     c = m_clzFunction;     if (c == null) {m_clzFunction     = c = (ClassConstant) getImplicitlyImportedIdentity("Function"        );} return c;}
    public ClassConstant     clzNullable()      {ClassConstant     c = m_clzNullable;     if (c == null) {m_clzNullable     = c = (ClassConstant) getImplicitlyImportedIdentity("Nullable"        );} return c;}
    public ClassConstant     clzCollection()    {ClassConstant     c = m_clzCollection;   if (c == null) {m_clzCollection   = c = (ClassConstant) getImplicitlyImportedIdentity("Collection"      );} return c;}
    public ClassConstant     clzSet()           {ClassConstant     c = m_clzSet;          if (c == null) {m_clzSet          = c = (ClassConstant) getImplicitlyImportedIdentity("Set"             );} return c;}
    public ClassConstant     clzList()          {ClassConstant     c = m_clzList;         if (c == null) {m_clzList         = c = (ClassConstant) getImplicitlyImportedIdentity("List"            );} return c;}
    public ClassConstant     clzArray()         {ClassConstant     c = m_clzArray;        if (c == null) {m_clzArray        = c = (ClassConstant) getImplicitlyImportedIdentity("Array"           );} return c;}
    public ClassConstant     clzMatrix()        {ClassConstant     c = m_clzMatrix;       if (c == null) {m_clzMatrix       = c = (ClassConstant) getImplicitlyImportedIdentity("Matrix"          );} return c;}
    public ClassConstant     clzMap()           {ClassConstant     c = m_clzMap;          if (c == null) {m_clzMap          = c = (ClassConstant) getImplicitlyImportedIdentity("Map"             );} return c;}
    public ClassConstant     clzSliceable()     {ClassConstant     c = m_clzSliceable;    if (c == null) {m_clzSliceable    = c = (ClassConstant) getImplicitlyImportedIdentity("Sliceable"       );} return c;}
    public ClassConstant     clzOrderable()     {ClassConstant     c = m_clzOrderable;    if (c == null) {m_clzOrderable    = c = (ClassConstant) getImplicitlyImportedIdentity("Orderable"       );} return c;}
    public ClassConstant     clzTuple()         {ClassConstant     c = m_clzTuple;        if (c == null) {m_clzTuple        = c = (ClassConstant) getImplicitlyImportedIdentity("Tuple"           );} return c;}
    public ClassConstant     clzCondTuple()     {ClassConstant     c = m_clzCondTuple;    if (c == null) {m_clzCondTuple    = c = (ClassConstant) getImplicitlyImportedIdentity("ConditionalTuple");} return c;}
    public ClassConstant     clzAuto()          {ClassConstant     c = m_clzAuto;         if (c == null) {m_clzAuto         = c = (ClassConstant) getImplicitlyImportedIdentity("Auto"            );} return c;}
    public ClassConstant     clzOp()            {ClassConstant     c = m_clzOp;           if (c == null) {m_clzOp           = c = (ClassConstant) getImplicitlyImportedIdentity("Op"              );} return c;}
    public ClassConstant     clzRO()            {ClassConstant     c = m_clzRO;           if (c == null) {m_clzRO           = c = (ClassConstant) getImplicitlyImportedIdentity("RO"              );} return c;}
    public ClassConstant     clzFinal()         {ClassConstant     c = m_clzFinal;        if (c == null) {m_clzFinal        = c = (ClassConstant) getImplicitlyImportedIdentity("Final"           );} return c;}
    public ClassConstant     clzInject()        {ClassConstant     c = m_clzInject;       if (c == null) {m_clzInject       = c = (ClassConstant) getImplicitlyImportedIdentity("Inject"          );} return c;}
    public ClassConstant     clzAbstract()      {ClassConstant     c = m_clzAbstract;     if (c == null) {m_clzAbstract     = c = (ClassConstant) getImplicitlyImportedIdentity("Abstract"        );} return c;}
    public ClassConstant     clzAtomic()        {ClassConstant     c = m_clzAtomic;       if (c == null) {m_clzAtomic       = c = (ClassConstant) getImplicitlyImportedIdentity("Atomic"          );} return c;}
    public ClassConstant     clzFuture()        {ClassConstant     c = m_clzFuture;       if (c == null) {m_clzFuture       = c = (ClassConstant) getImplicitlyImportedIdentity("Future"          );} return c;}
    public ClassConstant     clzOverride()      {ClassConstant     c = m_clzOverride;     if (c == null) {m_clzOverride     = c = (ClassConstant) getImplicitlyImportedIdentity("Override"        );} return c;}
    public ClassConstant     clzLazy()          {ClassConstant     c = m_clzLazy;         if (c == null) {m_clzLazy         = c = (ClassConstant) getImplicitlyImportedIdentity("Lazy"            );} return c;}
    public ClassConstant     clzUnchecked()     {ClassConstant     c = m_clzUnchecked;    if (c == null) {m_clzUnchecked    = c = (ClassConstant) getImplicitlyImportedIdentity("Unchecked"       );} return c;}
    public ClassConstant     clzObscure()       {ClassConstant     c = m_clzObscure;      if (c == null) {m_clzObscure      = c = (ClassConstant) getImplicitlyImportedIdentity("Obscure"         );} return c;}
    public ClassConstant     clzUnassigned()    {ClassConstant     c = m_clzUnassigned;   if (c == null) {m_clzUnassigned   = c = (ClassConstant) getImplicitlyImportedIdentity("Unassigned"      );} return c;}

    public TypeConstant      typeObject()       {TypeConstant      c = m_typeObject;      if (c == null) {m_typeObject      = c = ensureTerminalTypeConstant(clzObject()                     );} return c;}
    public TypeConstant      typeInner()        {TypeConstant      c = m_typeInner;       if (c == null) {m_typeInner       = c = ensureVirtualChildTypeConstant(typeOuter(), "Inner"        );} return c;}
    public TypeConstant      typeOuter()        {TypeConstant      c = m_typeOuter;       if (c == null) {m_typeOuter       = c = ensureTerminalTypeConstant(clzOuter()                      );} return c;}
    public TypeConstant      typeRef()          {TypeConstant      c = m_typeRef;         if (c == null) {m_typeRef         = c = ensureTerminalTypeConstant(clzRef()                        );} return c;}
    public TypeConstant      typeRefRB()        {TypeConstant      c = m_typeRefRB;       if (c == null) {m_typeRefRB       = c = makeNativeRebase(clzRef()                                  );} return c;}
    public TypeConstant      typeVar()          {TypeConstant      c = m_typeVar;         if (c == null) {m_typeVar         = c = ensureTerminalTypeConstant(clzVar()                        );} return c;}
    public TypeConstant      typeVarRB()        {TypeConstant      c = m_typeVarRB;       if (c == null) {m_typeVarRB       = c = makeNativeRebase(clzVar()                                  );} return c;}
    public TypeConstant      typeStruct()       {TypeConstant      c = m_typeStruct;      if (c == null) {m_typeStruct      = c = ensureTerminalTypeConstant(clzStruct()                     );} return c;}
    public TypeConstant      typeType()         {TypeConstant      c = m_typeType;        if (c == null) {m_typeType        = c = ensureTerminalTypeConstant(clzType()                       );} return c;}
    public TypeConstant      typeClass()        {TypeConstant      c = m_typeClass;       if (c == null) {m_typeClass       = c = ensureTerminalTypeConstant(clzClass()                      );} return c;}
    public TypeConstant      typeConst()        {TypeConstant      c = m_typeConst;       if (c == null) {m_typeConst       = c = ensureTerminalTypeConstant(clzConst()                      );} return c;}
    public TypeConstant      typeConstRB()      {TypeConstant      c = m_typeConstRB;     if (c == null) {m_typeConstRB     = c = makeNativeRebase(clzConst()                                );} return c;}
    public TypeConstant      typeService()      {TypeConstant      c = m_typeService;     if (c == null) {m_typeService     = c = ensureTerminalTypeConstant(clzService()                    );} return c;}
    public TypeConstant      typeServiceRB()    {TypeConstant      c = m_typeServiceRB;   if (c == null) {m_typeServiceRB   = c = makeNativeRebase(clzService()                              );} return c;}
    public TypeConstant      typeModule()       {TypeConstant      c = m_typeModule;      if (c == null) {m_typeModule      = c = ensureTerminalTypeConstant(clzModule()                     );} return c;}
    public TypeConstant      typeModuleRB()     {TypeConstant      c = m_typeModuleRB;    if (c == null) {m_typeModuleRB    = c = makeNativeRebase(clzModule()                               );} return c;}
    public TypeConstant      typePackage()      {TypeConstant      c = m_typePackage;     if (c == null) {m_typePackage     = c = ensureTerminalTypeConstant(clzPackage()                    );} return c;}
    public TypeConstant      typePackageRB()    {TypeConstant      c = m_typePackageRB;   if (c == null) {m_typePackageRB   = c = makeNativeRebase(clzPackage()                              );} return c;}
    public TypeConstant      typeEnum()         {TypeConstant      c = m_typeEnum;        if (c == null) {m_typeEnum        = c = ensureTerminalTypeConstant(clzEnum()                       );} return c;}
    public TypeConstant      typeEnumRB()       {TypeConstant      c = m_typeEnumRB;      if (c == null) {m_typeEnumRB      = c = makeNativeRebase(clzEnum()                                 );} return c;}
    public TypeConstant      typeEnumeration()  {TypeConstant      c = m_typeEnumeration; if (c == null) {m_typeEnumeration = c = ensureTerminalTypeConstant(clzEnumeration()                );} return c;}
    public TypeConstant      typeException()    {TypeConstant      c = m_typeException;   if (c == null) {m_typeException   = c = ensureTerminalTypeConstant(clzException()                  );} return c;}
    public TypeConstant      typeCloseable()    {TypeConstant      c = m_typeCloseable;   if (c == null) {m_typeCloseable   = c = ensureTerminalTypeConstant(clzCloseable()                  );} return c;}
    public TypeConstant      typeProperty()     {TypeConstant      c = m_typeProperty;    if (c == null) {m_typeProperty    = c = ensureTerminalTypeConstant(clzProperty()                   );} return c;}
    public TypeConstant      typeMethod()       {TypeConstant      c = m_typeMethod;      if (c == null) {m_typeMethod      = c = ensureTerminalTypeConstant(clzMethod()                     );} return c;}
    public TypeConstant      typeFunction()     {TypeConstant      c = m_typeFunction;    if (c == null) {m_typeFunction    = c = ensureTerminalTypeConstant(clzFunction()                   );} return c;}
    public TypeConstant      typeBoolean()      {TypeConstant      c = m_typeBoolean;     if (c == null) {m_typeBoolean     = c = ensureTerminalTypeConstant(clzBoolean()                    );} return c;}
    public TypeConstant      typeTrue()         {TypeConstant      c = m_typeTrue;        if (c == null) {m_typeTrue        = c = ensureTerminalTypeConstant(clzTrue()                       );} return c;}
    public TypeConstant      typeFalse()        {TypeConstant      c = m_typeFalse;       if (c == null) {m_typeFalse       = c = ensureTerminalTypeConstant(clzFalse()                      );} return c;}
    public TypeConstant      typeNullable()     {TypeConstant      c = m_typeNullable;    if (c == null) {m_typeNullable    = c = ensureTerminalTypeConstant(clzNullable()                   );} return c;}
    public TypeConstant      typeOrdered()      {TypeConstant      c = m_typeOrdered;     if (c == null) {m_typeOrdered     = c = ensureTerminalTypeConstant(clzOrdered()                    );} return c;}
    public TypeConstant      typeNull()         {TypeConstant      c = m_typeNull;        if (c == null) {m_typeNull        = c = ensureTerminalTypeConstant(clzNull()                       );} return c;}
    public TypeConstant      typeChar()         {TypeConstant      c = m_typeChar;        if (c == null) {m_typeChar        = c = ensureTerminalTypeConstant(clzChar()                       );} return c;}
    public TypeConstant      typeIntLiteral()   {TypeConstant      c = m_typeIntLiteral;  if (c == null) {m_typeIntLiteral  = c = ensureTerminalTypeConstant(clzIntLiteral()                 );} return c;}
    public TypeConstant      typeFPLiteral()    {TypeConstant      c = m_typeFPLiteral;   if (c == null) {m_typeFPLiteral   = c = ensureTerminalTypeConstant(clzFPLiteral()                  );} return c;}
    public TypeConstant      typeString()       {TypeConstant      c = m_typeString;      if (c == null) {m_typeString      = c = ensureTerminalTypeConstant(clzString()                     );} return c;}
    public TypeConstant      typeStringable()   {TypeConstant      c = m_typeStringable;  if (c == null) {m_typeStringable  = c = ensureTerminalTypeConstant(clzStringable()                 );} return c;}
    public TypeConstant      typeStringBuffer() {TypeConstant      c = m_typeStringBuffer;if (c == null) {m_typeStringBuffer= c = ensureTerminalTypeConstant(clzStringBuffer()               );} return c;}
    public TypeConstant      typeBit()          {TypeConstant      c = m_typeBit;         if (c == null) {m_typeBit         = c = ensureTerminalTypeConstant(clzBit()                        );} return c;}
    public TypeConstant      typeByte()         {TypeConstant      c = m_typeByte;        if (c == null) {m_typeByte        = c = ensureTerminalTypeConstant(clzByte()                       );} return c;}
    public TypeConstant      typeInt()          {TypeConstant      c = m_typeInt;         if (c == null) {m_typeInt         = c = ensureTerminalTypeConstant(clzInt()                        );} return c;}
    public TypeConstant      typeIndexed()      {TypeConstant      c = m_typeIndexed;     if (c == null) {m_typeIndexed     = c = ensureTerminalTypeConstant(clzIndexed()                    );} return c;}
    public TypeConstant      typeArray()        {TypeConstant      c = m_typeArray;       if (c == null) {m_typeArray       = c = ensureTerminalTypeConstant(clzArray()                      );} return c;}
    public TypeConstant      typeMatrix()       {TypeConstant      c = m_typeMatrix;      if (c == null) {m_typeMatrix      = c = ensureTerminalTypeConstant(clzMatrix()                     );} return c;}
    public TypeConstant      typeCollection()   {TypeConstant      c = m_typeCollection;  if (c == null) {m_typeCollection  = c = ensureTerminalTypeConstant(clzCollection()                 );} return c;}
    public TypeConstant      typeSet()          {TypeConstant      c = m_typeSet;         if (c == null) {m_typeSet         = c = ensureTerminalTypeConstant(clzSet()                        );} return c;}
    public TypeConstant      typeList()         {TypeConstant      c = m_typeList;        if (c == null) {m_typeList        = c = ensureTerminalTypeConstant(clzList()                       );} return c;}
    public TypeConstant      typeMap()          {TypeConstant      c = m_typeMap;         if (c == null) {m_typeMap         = c = ensureTerminalTypeConstant(clzMap()                        );} return c;}
    public TypeConstant      typeSliceable()    {TypeConstant      c = m_typeSliceable;   if (c == null) {m_typeSliceable   = c = ensureTerminalTypeConstant(clzSliceable()                  );} return c;}
    public TypeConstant      typeOrderable()    {TypeConstant      c = m_typeOrderable;   if (c == null) {m_typeOrderable   = c = ensureTerminalTypeConstant(clzOrderable()                  );} return c;}
    public TypeConstant      typeSequential()   {TypeConstant      c = m_typeSequential;  if (c == null) {m_typeSequential  = c = ensureTerminalTypeConstant(clzSequential()                 );} return c;}
    public TypeConstant      typeNumber()       {TypeConstant      c = m_typeNumber;      if (c == null) {m_typeNumber      = c = ensureTerminalTypeConstant(clzNumber()                     );} return c;}
    public TypeConstant      typeRange()        {TypeConstant      c = m_typeRange;       if (c == null) {m_typeRange       = c = ensureTerminalTypeConstant(clzRange()                      );} return c;}
    public TypeConstant      typeInterval()     {TypeConstant      c = m_typeInterval;    if (c == null) {m_typeInterval    = c = ensureTerminalTypeConstant(clzInterval()                   );} return c;}
    public TypeConstant      typeHashable()     {TypeConstant      c = m_typeHashable;    if (c == null) {m_typeHashable    = c = ensureTerminalTypeConstant(clzHashable()                   );} return c;}
    public TypeConstant      typeIterable()     {TypeConstant      c = m_typeIterable;    if (c == null) {m_typeIterable    = c = ensureTerminalTypeConstant(clzIterable()                   );} return c;}
    public TypeConstant      typeIterator()     {TypeConstant      c = m_typeIterator;    if (c == null) {m_typeIterator    = c = ensureTerminalTypeConstant(clzIterator()                   );} return c;}
    public TypeConstant      typeTuple()        {TypeConstant      c = m_typeTuple;       if (c == null) {m_typeTuple       = c = ensureTerminalTypeConstant(clzTuple()                      );} return c;}
    public TypeConstant      typeTuple0()       {TypeConstant      c = m_typeTuple0;      if (c == null) {m_typeTuple0      = c = ensureParameterizedTypeConstant(typeTuple()                );} return c;}
    public TypeConstant      typeCondTuple()    {TypeConstant      c = m_typeCondTuple;   if (c == null) {m_typeCondTuple   = c = ensureTerminalTypeConstant(clzCondTuple()                  );} return c;}
    public TypeConstant      typeDate()         {TypeConstant      c = m_typeDate;        if (c == null) {m_typeDate        = c = ensureTerminalTypeConstant(clzDate()                       );} return c;}
    public TypeConstant      typeTime()         {TypeConstant      c = m_typeTime;        if (c == null) {m_typeTime        = c = ensureTerminalTypeConstant(clzTime()                       );} return c;}
    public TypeConstant      typeDateTime()     {TypeConstant      c = m_typeDateTime;    if (c == null) {m_typeDateTime    = c = ensureTerminalTypeConstant(clzDateTime()                   );} return c;}
    public TypeConstant      typeTimeZone()     {TypeConstant      c = m_typeTimeZone;    if (c == null) {m_typeTimeZone    = c = ensureTerminalTypeConstant(clzTimeZone()                   );} return c;}
    public TypeConstant      typeDuration()     {TypeConstant      c = m_typeDuration;    if (c == null) {m_typeDuration    = c = ensureTerminalTypeConstant(clzDuration()                   );} return c;}
    public TypeConstant      typeVersion()      {TypeConstant      c = m_typeVersion;     if (c == null) {m_typeVersion     = c = ensureTerminalTypeConstant(clzVersion()                    );} return c;}
    public TypeConstant      typePath()         {TypeConstant      c = m_typePath;        if (c == null) {m_typePath        = c = ensureTerminalTypeConstant(clzPath()                       );} return c;}
    public TypeConstant      typeFileStore()    {TypeConstant      c = m_typeFileStore;   if (c == null) {m_typeFileStore   = c = ensureTerminalTypeConstant(clzFileStore()                  );} return c;}
    public TypeConstant      typeDirectory()    {TypeConstant      c = m_typeDirectory;   if (c == null) {m_typeDirectory   = c = ensureTerminalTypeConstant(clzDirectory()                  );} return c;}
    public TypeConstant      typeFile()         {TypeConstant      c = m_typeFile;        if (c == null) {m_typeFile        = c = ensureTerminalTypeConstant(clzFile()                       );} return c;}
    public TypeConstant      typeFileNode()     {TypeConstant      c = m_typeFileNode;    if (c == null) {m_typeFileNode    = c = ensureTerminalTypeConstant(clzFileNode()                   );} return c;}
    public TypeConstant      typeFrame()        {TypeConstant      c = m_typeFrame;       if (c == null) {m_typeFrame       = c = ensureTerminalTypeConstant(clzFrame()                      );} return c;}

    public TypeConstant      typeBitArray()     {TypeConstant      c = m_typeBitArray;    if (c == null) {m_typeBitArray    = c = ensureClassTypeConstant(clzArray(), null, typeBit()        );} return c;}
    public TypeConstant      typeByteArray()    {TypeConstant      c = m_typeByteArray;   if (c == null) {m_typeByteArray   = c = ensureClassTypeConstant(clzArray(), null, typeByte()       );} return c;}
    public TypeConstant      typeBinary()       {TypeConstant      c = m_typeBinary;      if (c == null) {m_typeBinary      = c = ensureImmutableTypeConstant(typeByteArray()                );} return c;}

    public TypeConstant      typeException१()   {TypeConstant      c = m_typeException१;  if (c == null) {m_typeException१  = c = ensureNullableTypeConstant(typeException()                  );} return c;}
    public TypeConstant      typeString१()      {TypeConstant      c = m_typeString१;     if (c == null) {m_typeString१     = c = ensureNullableTypeConstant(typeString()                     );} return c;}

    public IntConstant       val0()             {IntConstant       c = m_val0;            if (c == null) {m_val0            = c = ensureIntConstant(0)                                        ;} return c;}
    public IntConstant       val1()             {IntConstant       c = m_val1;            if (c == null) {m_val1            = c = ensureIntConstant(1)                                        ;} return c;}
    public SingletonConstant valFalse()         {SingletonConstant c = m_valFalse;        if (c == null) {m_valFalse        = c = ensureSingletonConstConstant(clzFalse())                    ;} return c;}
    public SingletonConstant valTrue()          {SingletonConstant c = m_valTrue;         if (c == null) {m_valTrue         = c = ensureSingletonConstConstant(clzTrue())                     ;} return c;}
    public SingletonConstant valLesser()        {SingletonConstant c = m_valLesser;       if (c == null) {m_valLesser       = c = ensureSingletonConstConstant(clzLesser())                   ;} return c;}
    public SingletonConstant valEqual()         {SingletonConstant c = m_valEqual;        if (c == null) {m_valEqual        = c = ensureSingletonConstConstant(clzEqual())                    ;} return c;}
    public SingletonConstant valGreater()       {SingletonConstant c = m_valGreater;      if (c == null) {m_valGreater      = c = ensureSingletonConstConstant(clzGreater())                  ;} return c;}
    public SingletonConstant valNull()          {SingletonConstant c = m_valNull;         if (c == null) {m_valNull         = c = ensureSingletonConstConstant(clzNull())                     ;} return c;}

    public SignatureConstant sigToString()      {SignatureConstant c = m_sigToString;     if (c == null) {m_sigToString     = c = getSignature("Object",    "toString",  0)                   ;} return c;}
    public SignatureConstant sigEquals()        {SignatureConstant c = m_sigEquals;       if (c == null) {m_sigEquals       = c = getSignature("Object",    "equals",    3)                   ;} return c;}
    public SignatureConstant sigCompare()       {SignatureConstant c = m_sigCompare;      if (c == null) {m_sigCompare      = c = getSignature("Orderable", "compare",   3)                   ;} return c;}
    public SignatureConstant sigValidator()     {SignatureConstant c = m_sigValidator;    if (c == null) {m_sigValidator    = c = ensureSignatureConstant("assert", NO_TYPES, NO_TYPES)       ;} return c;}


    // ---- internal class helpers -----------------------------------------------------------------

    protected ClassConstant  clzClass()        {return (ClassConstant) getImplicitlyImportedIdentity("Class"         );}
    protected ClassConstant  clzBoolean()      {return (ClassConstant) getImplicitlyImportedIdentity("Boolean"       );}
    protected ClassConstant  clzFalse()        {return (ClassConstant) getImplicitlyImportedIdentity("False"         );}
    protected ClassConstant  clzTrue()         {return (ClassConstant) getImplicitlyImportedIdentity("True"          );}
    protected ClassConstant  clzOrdered()      {return (ClassConstant) getImplicitlyImportedIdentity("Ordered"       );}
    protected ClassConstant  clzLesser()       {return (ClassConstant) getImplicitlyImportedIdentity("Lesser"        );}
    protected ClassConstant  clzEqual()        {return (ClassConstant) getImplicitlyImportedIdentity("Equal"         );}
    protected ClassConstant  clzGreater()      {return (ClassConstant) getImplicitlyImportedIdentity("Greater"       );}
    protected ClassConstant  clzNull()         {return (ClassConstant) getImplicitlyImportedIdentity("Null"          );}
    protected ClassConstant  clzChar()         {return (ClassConstant) getImplicitlyImportedIdentity("Char"          );}
    protected ClassConstant  clzString()       {return (ClassConstant) getImplicitlyImportedIdentity("String"        );}
    protected ClassConstant  clzStringable()   {return (ClassConstant) getImplicitlyImportedIdentity("Stringable"    );}
    protected ClassConstant  clzStringBuffer() {return (ClassConstant) getImplicitlyImportedIdentity("StringBuffer"  );}
    protected ClassConstant  clzIntLiteral()   {return (ClassConstant) getImplicitlyImportedIdentity("IntLiteral"    );}
    protected ClassConstant  clzFPLiteral()    {return (ClassConstant) getImplicitlyImportedIdentity("FPLiteral"     );}
    protected ClassConstant  clzBit()          {return (ClassConstant) getImplicitlyImportedIdentity("Bit"           );}
    protected ClassConstant  clzByte()         {return (ClassConstant) getImplicitlyImportedIdentity("Byte"          );}
    protected ClassConstant  clzInt()          {return (ClassConstant) getImplicitlyImportedIdentity("Int"           );}
    protected ClassConstant  clzIndexed()      {return (ClassConstant) getImplicitlyImportedIdentity("UniformIndexed");}
    protected ClassConstant  clzInterval()     {return (ClassConstant) getImplicitlyImportedIdentity("Interval"      );}
    protected ClassConstant  clzIterable()     {return (ClassConstant) getImplicitlyImportedIdentity("Iterable"      );}
    protected ClassConstant  clzIterator()     {return (ClassConstant) getImplicitlyImportedIdentity("Iterator"      );}
    protected ClassConstant  clzNumber()       {return (ClassConstant) getImplicitlyImportedIdentity("Number"        );}
    protected ClassConstant  clzRange()        {return (ClassConstant) getImplicitlyImportedIdentity("Range"         );}
    protected ClassConstant  clzSequential()   {return (ClassConstant) getImplicitlyImportedIdentity("Sequential"    );}
    protected ClassConstant  clzHashable()     {return (ClassConstant) getImplicitlyImportedIdentity("Hashable"      );}
    protected ClassConstant  clzDate()         {return (ClassConstant) getImplicitlyImportedIdentity("Date"          );}
    protected ClassConstant  clzDateTime()     {return (ClassConstant) getImplicitlyImportedIdentity("DateTime"      );}
    protected ClassConstant  clzDuration()     {return (ClassConstant) getImplicitlyImportedIdentity("Duration"      );}
    protected ClassConstant  clzTime()         {return (ClassConstant) getImplicitlyImportedIdentity("Time"          );}
    protected ClassConstant  clzTimeZone()     {return (ClassConstant) getImplicitlyImportedIdentity("TimeZone"      );}
    protected ClassConstant  clzPath()         {return (ClassConstant) getImplicitlyImportedIdentity("Path"          );}
    protected ClassConstant  clzVersion()      {return (ClassConstant) getImplicitlyImportedIdentity("Version"       );}
    protected ClassConstant  clzDirectory()    {return (ClassConstant) getImplicitlyImportedIdentity("Directory"     );}
    protected ClassConstant  clzFile()         {return (ClassConstant) getImplicitlyImportedIdentity("File"          );}
    protected ClassConstant  clzFileNode()     {return                 ensureEcstasyClassConstant   ("fs.FileNode"   );}
    protected ClassConstant  clzFileStore()    {return (ClassConstant) getImplicitlyImportedIdentity("FileStore"     );}
    protected ClassConstant  clzFrame()        {return (ClassConstant) getImplicitlyImportedIdentity("Frame"         );}

    /**
     * A special TypeInfo that acts as a place-holder for "this TypeInfo is currently being built".
     */
    public TypeInfo infoPlaceholder()
        {
        TypeInfo info = m_infoPlaceholder;
        if (info == null)
            {
            m_infoPlaceholder = info = new TypeInfo(
                typeObject(), 0, null, 0, true, Collections.EMPTY_MAP, Annotation.NO_ANNOTATIONS,
                typeObject(), null, typeObject(),
                Collections.EMPTY_LIST, new ListMap<>(), new ListMap<>(),
                Collections.EMPTY_MAP, Collections.EMPTY_MAP,
                Collections.EMPTY_MAP, Collections.EMPTY_MAP,
                ListMap.EMPTY, Progress.Building)
                    {
                    public String toString()
                        {
                        return "Placeholder";
                        }
                    };
            }
        return info;
        }

    public SingletonConstant valOf(boolean f)
        {
        return f ? valTrue() : valFalse();
        }

    public SingletonConstant valOrd(int n)
        {
        if (n < 0)
            {
            return valLesser();
            }
        else if (n == 0)
            {
            return valEqual();
            }
        else
            {
            return valGreater();
            }
        }

    private TypeConstant makeNativeRebase(ClassConstant constClass)
        {
        return new NativeRebaseConstant(constClass).getType();
        }

    private SignatureConstant getSignature(String sClass, String sMethod, int cParams)
        {
        return (SignatureConstant) register(((ClassStructure) getImplicitlyImportedComponent(sClass)).
                findMethod(sMethod, cParams).getIdentityConstant().getSignature());
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public ConstantPool getConstantPool()
        {
        return this;
        }

    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        return new Iterator<>()
            {
            private final ArrayList<Constant> listConst = m_listConst;
            private int iNext = 0;

            @Override
            public boolean hasNext()
                {
                return iNext < listConst.size();
                }

            @Override
            public XvmStructure next()
                {
                if (iNext >= listConst.size())
                    {
                    throw new NoSuchElementException();
                    }

                return listConst.get(iNext++);
                }
            };
        }

    @Override
    public boolean isModified()
        {
        // changes to the constant pool only modify the resulting file if there are changes to other
        // structures that reference the changes in the constant pool; the constants themselves are
        // constant
        return false;
        }

    @Override
    protected void markModified()
        {
        }

    @Override
    protected void resetModified()
        {
        }

    @Override
    public boolean isConditional()
        {
        return false;
        }

    @Override
    public void purgeCondition(ConditionalConstant condition)
        {
        }

    @Override
    public boolean isPresent(LinkerContext ctx)
        {
        return true;
        }

    @Override
    public boolean isResolved()
        {
        return true;
        }

    @Override
    public void resolve(LinkerContext ctx)
        {
        }

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_listConst.clear();
        m_mapConstants.clear();
        m_mapLocators.clear();

        // read the number of constants in the pool
        int cConst = readMagnitude(in);
        m_listConst.ensureCapacity(cConst);

        // load the constant pool from the stream
        for (int i = 0; i < cConst; ++i)
            {
            Constant constant;
            int      nFmt   = in.readUnsignedByte();
            Format   format = Constant.Format.valueOf(nFmt);
            switch (format)
                {
                /*
                * Values.
                */
                case IntLiteral:
                case FPLiteral:
                case Date:
                case Time:
                case DateTime:
                case Duration:
                case Path:
                    constant = new LiteralConstant(this, format, in);
                    break;

                case Int8:
                    constant = new Int8Constant(this, format, in);
                    break;

                case Bit:
                case Nibble:
                case UInt8:
                    constant = new UInt8Constant(this, format, in);
                    break;

                case Int16:
                case Int32:
                case Int64:
                case Int128:
                case VarInt:
                case UInt16:
                case UInt32:
                case UInt64:
                case UInt128:
                case VarUInt:
                    constant = new IntConstant(this, format, in);
                    break;

                case BFloat16:
                    constant = new BFloat16Constant(this, format, in);
                    break;

                case Float16:
                    constant = new Float16Constant(this, format, in);
                    break;

                case Float32:
                    constant = new Float32Constant(this, format, in);
                    break;

                case Float64:
                    constant = new Float64Constant(this, format, in);
                    break;

                case Float128:
                    constant = new Float128Constant(this, format, in);
                    break;

                case Dec32:
                case Dec64:
                case Dec128:
                    constant = new DecimalConstant(this, format, in);
                    break;

                case VarFloat:
                case VarDec:
                    constant = new VarFPConstant(this, format, in);
                    break;

                case Char:
                    constant = new CharConstant(this, format, in);
                    break;

                case String:
                    constant = new StringConstant(this, format, in);
                    break;

                case Version:
                    constant = new VersionConstant(this, format, in);
                    break;

                case SingletonConst:
                case SingletonService:
                    constant = new SingletonConstant(this, format, in);
                    break;

                case EnumValueConst:
                    constant = new EnumValueConstant(this, format, in);
                    break;

                case Array:
                case Tuple:
                case Set:
                    constant = new ArrayConstant(this, format, in);
                    break;

                case UInt8Array:
                    constant = new UInt8ArrayConstant(this, format, in);
                    break;

                case MapEntry:
                case Map:
                    constant = new MapConstant(this, format, in);
                    break;

                case Range:
                case RangeInclusive:
                case RangeExclusive:
                    constant = new RangeConstant(this, format, in);
                    break;

                case Any:
                    constant = new MatchAnyConstant(this, format, in);
                    break;

                case FileStore:
                    constant = new FileStoreConstant(this, format, in);
                    break;

                case FSDir:
                case FSFile:
                case FSLink:
                    constant = new FSNodeConstant(this, format, in);
                    break;

                /*
                * Structural identifiers.
                */
                case Module:
                    constant = new ModuleConstant(this, format, in);
                    break;

                case Package:
                    constant = new PackageConstant(this, format, in);
                    break;

                case Class:
                    constant = new ClassConstant(this, format, in);
                    break;

                case Typedef:
                    constant = new TypedefConstant(this, format, in);
                    break;

                case Property:
                    constant = new PropertyConstant(this, format, in);
                    break;

                case MultiMethod:
                    constant = new MultiMethodConstant(this, format, in);
                    break;

                case Method:
                    constant = new MethodConstant(this, format, in);
                    break;

                case Annotation:
                    constant = new Annotation(this, in);
                    break;

                case Register:
                    constant = new RegisterConstant(this, in);
                    break;

                /*
                * Pseudo identifiers.
                */
                case UnresolvedName:
                    throw new IOException("UnresolvedName not supported persistently");

                case ThisClass:
                    constant = new ThisClassConstant(this, format, in);
                    break;

                case ParentClass:
                    constant = new ParentClassConstant(this, format, in);
                    break;

                case ChildClass:
                    constant = new ChildClassConstant(this, format, in);
                    break;

                case TypeParameter:
                    constant = new TypeParameterConstant(this, format, in);
                    break;

                case FormalTypeChild:
                    constant = new FormalTypeChildConstant(this, format, in);
                    break;

                case Signature:
                    constant = new SignatureConstant(this, format, in);
                    break;

                case DecoratedClass:
                    constant = new DecoratedClassConstant(this, format, in);
                    break;

                case NativeClass:
                    // it is not used in the persistent form of the module
                    throw new IllegalStateException();

                /*
                * Types.
                */
                case UnresolvedType:
                    throw new IOException("UnresolvedType not supported persistently");

                case TerminalType:
                    constant = new TerminalTypeConstant(this, format, in);
                    break;

                case ImmutableType:
                    constant = new ImmutableTypeConstant(this, format, in);
                    break;

                case AccessType:
                    constant = new AccessTypeConstant(this, format, in);
                    break;

                case AnnotatedType:
                    constant = new AnnotatedTypeConstant(this, format, in);
                    break;

                case ParameterizedType:
                    constant = new ParameterizedTypeConstant(this, format, in);
                    break;

                case TurtleType:
                    constant = new TypeSequenceTypeConstant(this);
                    break;

                case VirtualChildType:
                    constant = new VirtualChildTypeConstant(this, format, in);
                    break;

                case AnonymousClassType:
                    constant = new AnonymousClassTypeConstant(this, format, in);
                    break;

                case PropertyClassType:
                    constant = new PropertyClassTypeConstant(this, format, in);
                    break;

                case UnionType:
                    constant = new UnionTypeConstant(this, format, in);
                    break;

                case IntersectionType:
                    constant = new IntersectionTypeConstant(this, format, in);
                    break;

                case DifferenceType:
                    constant = new DifferenceTypeConstant(this, format, in);
                    break;

                case RecursiveType:
                    constant = new RecursiveTypeConstant(this, format, in);
                    break;

                /*
                 * Conditions.
                 */
                case ConditionNot:
                    constant = new NotCondition(this, format, in);
                    break;

                case ConditionAll:
                    constant = new AllCondition(this, format, in);
                    break;

                case ConditionAny:
                    constant = new AnyCondition(this, format, in);
                    break;

                case ConditionNamed:
                    constant = new NamedCondition(this, format, in);
                    break;

                case ConditionPresent:
                    constant = new PresentCondition(this, format, in);
                    break;

                case ConditionVersionMatches:
                    constant = new VersionMatchesCondition(this, format, in);
                    break;

                case ConditionVersioned:
                    constant = new VersionedCondition(this, format, in);
                    break;

                default:
                    throw new IOException("Unsupported constant format: " + nFmt);
                }

            constant.setPosition(i);
            m_listConst.add(constant);
            }

        // convert indexes into constant references
        for (Constant constant : m_listConst)
            {
            constant.resolveConstants();
            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        // the ConstantPool does contain constants, but it does not itself reference any constants,
        // so it has nothing to register itself. furthermore, this must be over-ridden here to avoid
        // the super implementation calling to each of the contained Constants (some of which may no
        // longer be referenced by any XVM Structure) and having them accidentally register
        // everything that they in turn depend upon
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        writePackedLong(out, m_listConst.size());
        for (Constant constant : m_listConst)
            {
            constant.assemble(out);
            }
        }


    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("module=")
          .append(getFileStructure().getModuleName())
          .append(", size=")
          .append(m_listConst.size());

        if (m_fRecurseReg)
            {
            sb.append(", recursive registration on");
            }
        return sb.toString();
        }

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        dumpStructureCollection(out, sIndent, "Constants", m_listConst);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof ConstantPool))
            {
            return false;
            }

        ConstantPool that = (ConstantPool) obj;

        // compare each constant in the pool for equality
        return this.m_listConst.equals(that.m_listConst);
        }


    // ----- methods exposed to FileStructure ------------------------------------------------------

    /**
     * Before the registration of constants begins as part of assembling the FileStructure, the
     * ConstantPool is notified of the impending assembly process so that it can determine which
     * constants are actually used, and how many times each is used. This is important because the
     * unused constants can be discarded, and the most frequently used constants can be written out
     * first in the ConstantPool, allowing their ordinal position to be addressed using a smaller
     * number of bytes throughout the FileStructure.
     */
    protected void preRegisterAll()
        {
        assert !m_fRecurseReg;
        m_fRecurseReg = true;

        m_listConst.forEach(Constant::resetRefs);
        }

    /**
     * Called after all of the Constants have been registered by the bulk registration process.
     *
     * @param fOptimize pass true to optimize the order of the constants, or false to maintain the
     *                  present order
     */
    protected void postRegisterAll(final boolean fOptimize)
        {
        assert m_fRecurseReg;
        m_fRecurseReg = false;

        if (fOptimize)
            {
            optimize();
            }
        }

    /**
     * Discard unused Constants and order the remaining constants so that the most-referred-to
     * Constants occur before the less used constants.
     */
    private void optimize()
        {
        ArrayList<Constant> list = m_listConst;

        // remove unused constants
        int cBefore       = list.size();
        Constant[] aconst = new Constant[cBefore];
        int cAfter        = 0;

        for (int i = 0; i < cBefore; i++)
            {
            Constant constant = list.get(i);
            if (constant.hasRefs())
                {
                aconst[cAfter++] = constant;
                }
            else
                {
                constant.setPosition(-1);
                }
            }

        m_valEcstasy      = null;
        m_clzObject       = null;
        m_clzInner        = null;
        m_clzOuter        = null;
        m_clzRef          = null;
        m_clzVar          = null;
        m_clzStruct       = null;
        m_clzType         = null;
        m_clzCloseable    = null;
        m_clzConst        = null;
        m_clzService      = null;
        m_clzModule       = null;
        m_clzPackage      = null;
        m_clzEnum         = null;
        m_clzEnumeration  = null;
        m_clzException    = null;
        m_clzProperty     = null;
        m_clzMethod       = null;
        m_clzFunction     = null;
        m_clzNullable     = null;
        m_clzCollection   = null;
        m_clzSet          = null;
        m_clzList         = null;
        m_clzArray        = null;
        m_clzMatrix       = null;
        m_clzMap          = null;
        m_clzOrderable    = null;
        m_clzTuple        = null;
        m_clzCondTuple    = null;
        m_clzAuto         = null;
        m_clzOp           = null;
        m_clzRO           = null;
        m_clzFinal        = null;
        m_clzInject       = null;
        m_clzAbstract     = null;
        m_clzAtomic       = null;
        m_clzFuture       = null;
        m_clzOverride     = null;
        m_clzLazy         = null;
        m_clzUnchecked    = null;
        m_clzObscure      = null;
        m_clzUnassigned   = null;
        m_typeObject      = null;
        m_typeInner       = null;
        m_typeOuter       = null;
        m_typeRef         = null;
        m_typeRefRB       = null;
        m_typeVar         = null;
        m_typeVarRB       = null;
        m_typeStruct      = null;
        m_typeType        = null;
        m_typeClass       = null;
        m_typeConst       = null;
        m_typeConstRB     = null;
        m_typeService     = null;
        m_typeServiceRB   = null;
        m_typeModule      = null;
        m_typeModuleRB    = null;
        m_typePackage     = null;
        m_typePackageRB   = null;
        m_typeEnum        = null;
        m_typeEnumRB      = null;
        m_typeEnumeration = null;
        m_typeException   = null;
        m_typeException१  = null;
        m_typeProperty    = null;
        m_typeMethod      = null;
        m_typeFunction    = null;
        m_typeBoolean     = null;
        m_typeTrue        = null;
        m_typeFalse       = null;
        m_typeCloseable   = null;
        m_typeNullable    = null;
        m_typeOrdered     = null;
        m_typeNull        = null;
        m_typeChar        = null;
        m_typeIntLiteral  = null;
        m_typeFPLiteral   = null;
        m_typeString      = null;
        m_typeStringable  = null;
        m_typeStringBuffer= null;
        m_typeString१     = null;
        m_typeBit         = null;
        m_typeByte        = null;
        m_typeBitArray    = null;
        m_typeByteArray   = null;
        m_typeBinary      = null;
        m_typeInt         = null;
        m_typeIndexed     = null;
        m_typeArray       = null;
        m_typeMatrix      = null;
        m_typeCollection  = null;
        m_typeSet         = null;
        m_typeList        = null;
        m_typeMap         = null;
        m_typeSliceable   = null;
        m_typeOrderable   = null;
        m_typeSequential  = null;
        m_typeNumber      = null;
        m_typeHashable    = null;
        m_typeRange       = null;
        m_typeInterval    = null;
        m_typeIterable    = null;
        m_typeIterator    = null;
        m_typeTuple       = null;
        m_typeTuple0      = null;
        m_typeCondTuple   = null;
        m_typeDate        = null;
        m_typeTime        = null;
        m_typeDateTime    = null;
        m_typeTimeZone    = null;
        m_typeDuration    = null;
        m_typeVersion     = null;
        m_typePath        = null;
        m_typeFileStore   = null;
        m_typeDirectory   = null;
        m_typeFile        = null;
        m_typeFileNode    = null;
        m_typeFrame       = null;
        m_val0            = null;
        m_val1            = null;
        m_valFalse        = null;
        m_valTrue         = null;
        m_valLesser       = null;
        m_valEqual        = null;
        m_valGreater      = null;
        m_valNull         = null;
        m_sigToString     = null;
        m_sigEquals       = null;
        m_sigCompare      = null;
        m_sigValidator    = null;
        m_infoPlaceholder = null;

        // sort the Constants by how often they are referred to within the FileStructure, with the
        // most frequently referred-to Constants appearing first
        Arrays.sort(aconst, 0, cAfter, DEBUG
                ? Comparator.<Constant>naturalOrder()
                : Constant.MFU_ORDER);

        // mark each constant with its new position and add to the list
        list.clear();
        for (int i = 0; i < cAfter; ++i)
            {
            Constant constant = aconst[i];
            constant.setPosition(i);
            list.add(constant);
            }

        // discard any previous lookup structures, since contents may have changed
        m_mapConstants.clear();
        m_mapLocators.clear();
        m_implicits.clear();
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Obtain a Constant lookup table for Constants of the specified type, using Constants as the
     * keys of the lookup table.
     * <p/>
     * Constants are natural identities, so they act as the keys in this lookup structure. This data
     * structure allows there to be exactly one instance of each Constant identity held by the
     * ConstantPool, similar to how String objects are "interned" in Java.
     *
     * @param format  the Constant Type
     *
     * @return the map from Constant to Constant
     */
    private Map<Constant, Constant> ensureConstantLookup(Format format)
        {
        ensureLookup();
        return m_mapConstants.get(format);
        }

    /**
     * Obtain a Constant lookup table for Constants of the specified type, using locators as the
     * keys of the lookup table.
     * <p/>
     * Locators are optional identities that are specific to each different Type of Constant:
     * <ul>
     * <li>A Constant Type may not support locators at all;</li>
     * <li>A Constant Type may support locators, but only for some of the
     * Constant values of that Type;</li>
     * <li>A Constant Type may support locators for all of the Constant values
     * of that Type.</li>
     * </ul>
     *
     * @param format  the Constant Type
     *
     * @return the map from locator to Constant
     */
     private synchronized Map<Object, Constant> ensureLocatorLookup(Format format)
        {
        // lazily instantiate the locator map for the specified type
        return m_mapLocators.computeIfAbsent(format, _format -> new ConcurrentHashMap<>());
        }

    /**
     * Create the necessary structures for looking up Constant objects quickly, and populate those
     * structures with the set of existing Constant objects.
     */
    private synchronized void ensureLookup()
        {
        if (m_mapConstants.isEmpty())
            {
            for (Format format : Format.values())
                {
                m_mapConstants.put(format, new ConcurrentHashMap<>());
                }

            for (Constant constant : m_listConst)
                {
                Constant constantOld = m_mapConstants.get(constant.getFormat()).put(constant, constant);
                if (constantOld != null && constantOld != constant)
                    {
                    throw new IllegalStateException("constant collision: old=" + constantOld + ", new=" + constant);
                    }

                Object oLocator = constant.getLocator();
                if (oLocator != null)
                    {
                    constantOld = ensureLocatorLookup(constant.getFormat()).put(oLocator, constant);
                    if (constantOld != null && constantOld != constant)
                        {
                        throw new IllegalStateException("locator collision: old=" + constantOld + ", new=" + constant);
                        }
                    }
                }
            }
        }


    // ----- TypeInfo helpers ----------------------------------------------------------------------

    /**
     * Add a TypeConstant that needs its TypeInfo to be built or rebuilt.
     *
     * @param type  the TypeConstant to defer the building of a TypeInfo for
     */
    void addDeferredTypeInfo(TypeConstant type)
        {
        assert type != null;

        List<TypeConstant> list = f_tlolistDeferred.get();
        if (list == null)
            {
            list = new ArrayList<>();
            f_tlolistDeferred.set(list);
            }

        if (!list.contains(type))
            {
            list.add(type);
            }
        }

    /**
     * @return true iff there are any TypeConstants that have deferred the building of a TypeInfo
     */
    boolean hasDeferredTypeInfo()
        {
        return f_tlolistDeferred.get() != null;
        }

    /**
     * @return the List of TypeConstants to build (or rebuild) TypeInfo objects for
     */
    List<TypeConstant> takeDeferredTypeInfo()
        {
        List<TypeConstant> list = f_tlolistDeferred.get();
        if (list == null)
            {
            list = Collections.EMPTY_LIST;
            }
        else
            {
            f_tlolistDeferred.set(null);
            }

        return list;
        }

    /**
     * Cause all TypeInfos that are built from the specified class to re-build. This is necessary
     * during compilation when additional information becomes visible as the compilation progresses,
     * for example, when the compiler pierces a method boundary and discovers nested methods and/or
     * properties.
     *
     * @param id  an IdentityConstant specifying a class
     */
    public void invalidateTypeInfos(IdentityConstant id)
        {
        assert id.isClass();
        f_listInvalidated.add((IdentityConstant) register(id));
        }

    /**
     * @return  the current TypeInfo invalidation count
     */
    public int getInvalidationCount()
        {
        return f_listInvalidated.size();
        }

    /**
     * Determine what classes have new information since the specified invalidation count.
     *
     * @param cOld  the old invalidation count to start from
     *
     * @return a set of all the invalidated IdentityConstants of the classes causing the
     *         invalidation to occur
     */
    public Set<IdentityConstant> invalidationsSince(int cOld)
        {
        int cNew = f_listInvalidated.size();
        if (cOld == cNew)
            {
            return Collections.EMPTY_SET;
            }

        assert cNew > cOld;

        HashSet<IdentityConstant> set = new HashSet<>();
        for (int i = cOld; i < cNew; ++i)
            {
            set.add(f_listInvalidated.get(i));
            }

        return set;
        }


    // ----- TypeConstant helpers  -----------------------------------------------------------------

    /**
     * While it's known that the left type is a function:
     * <pre>
     *    {@code typeLeft.getSingleUnderlyingClass(true).equals(clzFunction())}
     * </pre>
     * and the right type has a single underlying class:
     * <pre>
     *    {@code typeRight.isSingleUnderlyingClass(true))}
     * </pre>
     * calculate whether the {@code typeRight} is assignable to {@code typeLeft}.
     *
     * @return one of {@link Relation} constants
     */
    public Relation checkFunctionCompatibility(TypeConstant typeLeft, TypeConstant typeRight)
        {
        IdentityConstant idRight = typeRight.getSingleUnderlyingClass(true);
        if (!idRight.equals(clzFunction()))
            {
            // compare the "naked" contribution
            ClassStructure clzRight = (ClassStructure) idRight.getComponent();
            if (clzRight.calculateRelation(typeFunction(), idRight.getType(), true) != Relation.IS_A)
                {
                return Relation.INCOMPATIBLE;
                }
            }

        return checkFunctionOrMethodCompatibility(typeLeft, typeRight, false);
        }

    /**
     * While it's known that the left type is a method:
     * <pre>
     *    {@code typeLeft.getSingleUnderlyingClass(true).equals(clz.Method())}
     * </pre>
     * and the right type has a single underlying class:
     * <pre>
     *    {@code typeRight.isSingleUnderlyingClass(true))}
     * </pre>
     * the {@code typeRight} is assignable to {@code typeLeft}.
     *
     * @return one of {@link Relation} constants
     */
    public Relation checkMethodCompatibility(TypeConstant typeLeft, TypeConstant typeRight)
        {
        IdentityConstant idRight = typeRight.getSingleUnderlyingClass(true);
        if (!idRight.equals(clzMethod()))
            {
            // compare the "naked" contribution
            ClassStructure clzRight = (ClassStructure) idRight.getComponent();
            if (clzRight.calculateRelation(typeMethod(), idRight.getType(), true) != Relation.IS_A)
                {
                return Relation.INCOMPATIBLE;
                }
            }

        return checkFunctionOrMethodCompatibility(typeLeft, typeRight, true);
        }

    private Relation checkFunctionOrMethodCompatibility(TypeConstant typeLeft, TypeConstant typeRight,
                                                        boolean fMethod)
        {
        // the only modification that is allowed on the Function is "this:class"
        if (typeLeft.isSingleDefiningConstant() && typeRight.isSingleDefiningConstant())
            {
            Constant constIdLeft  = typeLeft.getDefiningConstant();
            Constant constIdRight = typeRight.getDefiningConstant();
            if (constIdLeft.getFormat()  == Format.ThisClass &&
                constIdRight.getFormat() == Format.ThisClass)
                {
                // to allow assignment of this:class(X) to this:class(Function),
                // X should be a Function or an Object
                typeRight = typeRight.resolveAutoNarrowing(typeRight.getConstantPool(), false, null);
                typeLeft  = typeLeft .resolveAutoNarrowing(typeLeft.getConstantPool(), false, null);

                if (typeRight.equals(typeObject()))
                    {
                    return Relation.IS_A;
                    }
                // continue with auto-narrowing resolved
                }
            }

        // Function<TupleRP, TupleRR> is assignable to Function<TupleLP, TupleLR> iff
        // (RP/RR - right parameters/return, LP/LR - left parameter/return)
        // 1) TupleLP has less or equal arity arity as TupleRP
        //    (assuming there are default values and relying on the run-time checks)
        // 2) every parameter type on the right should be assignable to a corresponding parameter
        //    on the left (e.g. "function void (Number)" is assignable to "function void (Int)")
        // 3) TupleLR has less or equal arity than TupleRR
        // 4) every return type on the left should be assignable to a corresponding return
        //    on the right (e.g. "function Int ()" is assignable to "function Number ()")
        //
        // The same rules apply to Method<Target, TupleP, TupleR>,

        int ixParams, ixReturns, cMinParams, cMaxParams;
        if (fMethod)
            {
            TypeConstant typeTargetL = typeLeft .getParamType(0);
            TypeConstant typeTargetR = typeRight.getParamType(0);
            if (!typeTargetR.isA(typeTargetL))
                {
                return Relation.INCOMPATIBLE;
                }

            ixParams = 1; ixReturns = 2; cMinParams = 1; cMaxParams = 3;
            }
        else
            {
            ixParams = 0; ixReturns = 1; cMinParams = 0; cMaxParams = 2;
            }

        int cL = typeLeft .getParamsCount();
        int cR = typeRight.getParamsCount();
        if (cL <= cMinParams)
            {
            // Function<> <-- Function<RP, RR> is allowed
            return Relation.IS_A;
            }
        if (cL != cMaxParams || cR != cMaxParams)
            {
            // the only valid scenario is:
            // Function<<>, <>> <-- Function<>
            if (cL == cMaxParams && cR == cMinParams)
                {
                TypeConstant typeLP = typeLeft .getParamType(ixParams);
                TypeConstant typeLR = typeLeft .getParamType(ixReturns);

                if (typeLP.isTuple() && typeLR.isTuple() &&
                    typeLP.getParamsCount() == 0 && typeLR.getParamsCount() == 0)
                    {
                    return Relation.IS_A;
                    }
                }
            return Relation.INCOMPATIBLE;
            }

        TypeConstant typeLP = typeLeft .getParamType(ixParams);
        TypeConstant typeLR = typeLeft .getParamType(ixReturns);
        TypeConstant typeRP = typeRight.getParamType(ixParams);
        TypeConstant typeRR = typeRight.getParamType(ixReturns);

        if (!typeLP.isTuple() || !typeLR.isTuple() ||
            !typeRP.isTuple() || !typeRR.isTuple())
            {
            // ill-constructed function type; not our responsibility to report
            return Relation.INCOMPATIBLE;
            }

        int cLP = typeLP.getParamsCount();
        int cLR = typeLR.getParamsCount();
        int cRP = typeRP.getParamsCount();
        int cRR = typeRR.getParamsCount();

        if (cLP > cRP || cLR > cRR)
            {
            return Relation.INCOMPATIBLE;
            }

        // functions do not produce, so we cannot have "weak" relations
        for (int i = 0; i < cLP; i++)
            {
            TypeConstant typeL = typeLP.getParamType(i);
            TypeConstant typeR = typeRP.getParamType(i);
            if (!typeL.isA(typeR))
                {
                return Relation.INCOMPATIBLE;
                }
            }

        for (int i = 0; i < cLR; i++)
            {
            TypeConstant typeL = typeLR.getParamType(i);
            TypeConstant typeR = typeRR.getParamType(i);
            if (!typeR.isA(typeL))
                {
                return Relation.INCOMPATIBLE;
                }
            }

        return Relation.IS_A;
        }

    /**
     * Build a TypeConstant for a function.
     *
     * @param atypeParams   the parameter types of the function
     * @param atypeReturns  the return types of the function
     *
     * @return the function type
     */
    public TypeConstant buildFunctionType(TypeConstant[] atypeParams, TypeConstant... atypeReturns)
        {
        return ensureParameterizedTypeConstant(
                typeFunction(),
                ensureParameterizedTypeConstant(typeTuple(), atypeParams),
                ensureParameterizedTypeConstant(typeTuple(), atypeReturns));
        }

    /**
     * Build a TypeConstant for a function with conditional return.
     *
     * @param atypeParams   the parameter types of the function
     * @param atypeReturns  the return types of the function
     *
     * @return the function type
     */
    public TypeConstant buildConditionalFunctionType(TypeConstant[] atypeParams, TypeConstant... atypeReturns)
        {
        assert atypeReturns.length > 1 && atypeReturns[0].equals(typeBoolean());
        return ensureParameterizedTypeConstant(
                typeFunction(),
                ensureParameterizedTypeConstant(typeTuple(), atypeParams),
                ensureParameterizedTypeConstant(typeCondTuple(), atypeReturns));
        }

    /**
     * Get the parameter types from a function type.
     *
     * @param typeFunction  the type to extract from
     *
     * @return the parameter types for the function, or null if the types cannot be determined
     */
    public TypeConstant[] extractFunctionParams(TypeConstant typeFunction)
        {
        if (typeFunction != null && typeFunction.isA(typeFunction()))
            {
            if (typeFunction.getParamsCount() > 0)
                {
                TypeConstant typeParams = typeFunction.getParamType(0);
                if (typeParams.isA(typeTuple()))
                    {
                    return typeParams.getParamTypesArray();
                    }
                }
            return TypeConstant.NO_TYPES;
            }

        return null;
        }

    /**
     * Get the return types from a function type.
     *
     * @param typeFunction  the type to extract from
     *
     * @return the return types of the function, or null if the types cannot be determined
     */
    public TypeConstant[] extractFunctionReturns(TypeConstant typeFunction)
        {
        if (typeFunction != null && typeFunction.isA(typeFunction()))
            {
            if (typeFunction.getParamsCount() > 1)
                {
                TypeConstant typeParams = typeFunction.getParamType(1);
                if (typeParams.isA(typeTuple()))
                    {
                    return typeParams.getParamTypesArray();
                    }
                }
            return TypeConstant.NO_TYPES;
            }

        return null;
        }

    /**
     * @return true iff the specified function type has a conditional return
     */
    public boolean isConditionalReturn(TypeConstant typeFunction)
        {
        if (typeFunction != null && typeFunction.isA(typeFunction()))
            {
            if (typeFunction.getParamsCount() > 1)
                {
                return typeFunction.getParamType(1).isA(typeCondTuple());
                }
            }

        return false;
        }

    /**
     * Create a new function type by binding the specified parameter of the passed in function type.
     *
     * @param typeFn    the function type
     * @param iParam    the parameter index
     * @param resolver  (optional) generic type resolver to resolve the resulting type
     *
     * @return a new function type that skips the specified parameter
     */
    public TypeConstant bindFunctionParam(TypeConstant typeFn, int iParam,
                                          GenericTypeResolver resolver)
        {
        assert typeFn.isA(typeFunction()) && typeFn.getParamsCount() > 0;

        TypeConstant typeP = typeFn.getParamType(0);
        TypeConstant typeR = typeFn.getParamType(1);

        int cParamsNew = typeP.getParamsCount() - 1;
        assert typeP.isTuple() && iParam <= cParamsNew;

        TypeConstant[] atypeParams = typeP.getParamTypesArray();
        if (cParamsNew == 0)
            {
            // canonical Tuple represents Void
            typeP = ensureParameterizedTypeConstant(typeTuple());
            }
        else
            {
            TypeConstant[] atypeNew = new TypeConstant[cParamsNew];
            if (iParam > 0)
                {
                System.arraycopy(atypeParams, 0, atypeNew, 0, iParam);
                }
            if (iParam < cParamsNew)
                {
                System.arraycopy(atypeParams, iParam + 1, atypeNew, iParam, cParamsNew - iParam);
                }
            typeP = ensureParameterizedTypeConstant(typeTuple(), atypeNew);
            }

        TypeConstant typeNew = ensureParameterizedTypeConstant(typeFunction(), typeP, typeR);
        return resolver == null
                ? typeNew
                : typeNew.resolveGenerics(this, resolver);
        }


    // ----- run-time helpers  ---------------------------------------------------------------------

    /**
     * Produce a ClassComposition for the specified inception type.
     *
     * Note: the passed inception type should be normalized (all formal parameters resolved).
     */
    public ClassComposition ensureClassComposition(TypeConstant typeInception, ClassTemplate template)
        {
        assert typeInception.getConstantPool() == this && typeInception.getPosition() >= 0;
        assert !typeInception.isAccessSpecified();
        assert typeInception.normalizeParameters().equals(typeInception);

        ClassComposition clz = m_mapCompositions.computeIfAbsent(typeInception, (type) ->
            {
            OpSupport support = type.isAnnotated() && type.isIntoVariableType()
                    ? type.getOpSupport(template.f_templates)
                    : template;

            return new ClassComposition(support, type);
            });

        // we need to make this call outside of the constructor due to a possible recursion
        // (ConcurrentHashMap.computeIfAbsent doesn't allow that)
        clz.ensureFieldLayout();
        return clz;
        }


    // ----- out-of-context helpers  ---------------------------------------------------------------

    public TypeConstant getNakedRefType()
        {
        return m_typeNakedRef;
        }

    public void setNakedRefType(TypeConstant typeNakedRef)
        {
        m_typeNakedRef = typeNakedRef;
        }

    public TypeInfo getNakedRefInfo(TypeConstant typeReferent)
        {
        return m_mapRefTypes.computeIfAbsent(typeReferent, this::computeNakedRefInfo);
        }

    private TypeInfo computeNakedRefInfo(TypeConstant typeReferent)
        {
        GenericTypeResolver resolver =
                sFormalName -> sFormalName.equals("Referent") ? typeReferent : null;

        TypeInfo info = m_typeNakedRef.ensureTypeInfo();

        Map<Object, ParamInfo> mapTypeParams = new HashMap<>();
        mapTypeParams.put("Referent", new ParamInfo("Referent", typeReferent, typeObject()));

        MethodConstant id        = info.findMethods("get", 0, TypeInfo.MethodKind.Method).iterator().next();
        MethodInfo     method    = info.getMethodById(id);
        MethodBody     body      = method.getHead();
        MethodBody     bodyNew   = body.resolveGenerics(this, resolver);
        MethodInfo     methodNew = new MethodInfo(bodyNew);

        Map<MethodConstant, MethodInfo> mapMethods = new HashMap<>(info.getMethods());
        mapMethods.remove(id);
        mapMethods.put(methodNew.getIdentity(), methodNew);

        Map<Object, MethodInfo> mapVirtMethods = new HashMap<>(info.getVirtMethods());
        mapVirtMethods.remove(id.getSignature());
        mapVirtMethods.put(bodyNew.getSignature(), methodNew);

        return new TypeInfo(
                info.getType(),         // unresolved formal type from the "native" pool
                0,                      // cInvals
                null,                   // struct
                0,                      // depth
                true,                   // synthetic
                mapTypeParams,
                Annotation.NO_ANNOTATIONS,
                null,                   // typeExtends
                null,                   // typeRebase
                null,                   // typeInto
                Collections.EMPTY_LIST, // listProcess,
                ListMap.EMPTY,          // listmapClassChain
                ListMap.EMPTY,          // listmapDefaultChain
                Collections.EMPTY_MAP,  // mapProps
                mapMethods,
                Collections.EMPTY_MAP,  // mapVirtProps
                mapVirtMethods,
                ListMap.EMPTY,          // mapChildren
                Progress.Complete
                );
        }

    /**
     * @return a ContextPool associated with the current thread
     */
    public static ConstantPool getCurrentPool()
        {
        return s_tloPool.get()[0];
        }

    /**
     * Associate the specified ConstantPool with the current thread.
     *
     * @param pool  a ContextPool
     */
    public static void setCurrentPool(ConstantPool pool)
        {
        s_tloPool.get()[0] = pool;
        }

    /**
     * Temporarily update the current ConstantPool, restoring it when the returned AutoCloseable
     * is closed.
     *
     * @param pool the new pool
     *
     * @return an Auto which will revert to the prior pool
     */
    public static Auto withPool(ConstantPool pool) {
        ConstantPool[] poolHolder = s_tloPool.get();
        ConstantPool pollPrior = poolHolder[0];
        poolHolder[0] = pool;
        return () -> poolHolder[0] = pollPrior;
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * An immutable, empty, zero-length array of types.
     */
    public static final TypeConstant[] NO_TYPES = TypeConstant.NO_TYPES;

    /**
     * Storage of Constant objects by index.
     */
    private final ArrayList<Constant> m_listConst = new ArrayList<>();

    /**
     * Reverse lookup structure to find a particular constant by constant.
     */
    private final EnumMap<Format, Map<Constant, Constant>> m_mapConstants = new EnumMap<>(Format.class);

    /**
     * Reverse lookup structure to find a particular constant by locator.
     */
    private final EnumMap<Format, Map<Object, Constant>> m_mapLocators = new EnumMap<>(Format.class);

    /**
     * Set of references to ConstantPool instances, defining the only ConstantPool references that
     * may be referred to (directly or indirectly) from constants stored in this pool.
     */
    private final Set<ConstantPool> m_setValidPools = Collections.newSetFromMap(new IdentityHashMap());

    /**
     * Tracks whether the ConstantPool should recursively register constants.
     */
    private transient boolean m_fRecurseReg;

    private transient ModuleConstant    m_valEcstasy;
    private transient ClassConstant     m_clzObject;
    private transient ClassConstant     m_clzInner;
    private transient ClassConstant     m_clzOuter;
    private transient ClassConstant     m_clzRef;
    private transient ClassConstant     m_clzVar;
    private transient ClassConstant     m_clzType;
    private transient ClassConstant     m_clzStruct;
    private transient ClassConstant     m_clzConst;
    private transient ClassConstant     m_clzService;
    private transient ClassConstant     m_clzModule;
    private transient ClassConstant     m_clzPackage;
    private transient ClassConstant     m_clzEnum;
    private transient ClassConstant     m_clzEnumeration;
    private transient ClassConstant     m_clzException;
    private transient ClassConstant     m_clzCloseable;
    private transient ClassConstant     m_clzProperty;
    private transient ClassConstant     m_clzMethod;
    private transient ClassConstant     m_clzFunction;
    private transient ClassConstant     m_clzNullable;
    private transient ClassConstant     m_clzCollection;
    private transient ClassConstant     m_clzSet;
    private transient ClassConstant     m_clzList;
    private transient ClassConstant     m_clzArray;
    private transient ClassConstant     m_clzMatrix;
    private transient ClassConstant     m_clzMap;
    private transient ClassConstant     m_clzSliceable;
    private transient ClassConstant     m_clzOrderable;
    private transient ClassConstant     m_clzTuple;
    private transient ClassConstant     m_clzCondTuple;
    private transient ClassConstant     m_clzAuto;
    private transient ClassConstant     m_clzOp;
    private transient ClassConstant     m_clzRO;
    private transient ClassConstant     m_clzFinal;
    private transient ClassConstant     m_clzInject;
    private transient ClassConstant     m_clzAbstract;
    private transient ClassConstant     m_clzAtomic;
    private transient ClassConstant     m_clzFuture;
    private transient ClassConstant     m_clzOverride;
    private transient ClassConstant     m_clzLazy;
    private transient ClassConstant     m_clzUnchecked;
    private transient ClassConstant     m_clzObscure;
    private transient ClassConstant     m_clzUnassigned;
    private transient TypeConstant      m_typeObject;
    private transient TypeConstant      m_typeInner;
    private transient TypeConstant      m_typeOuter;
    private transient TypeConstant      m_typeRef;
    private transient TypeConstant      m_typeRefRB;
    private transient TypeConstant      m_typeVar;
    private transient TypeConstant      m_typeVarRB;
    private transient TypeConstant      m_typeType;
    private transient TypeConstant      m_typeStruct;
    private transient TypeConstant      m_typeClass;
    private transient TypeConstant      m_typeConst;
    private transient TypeConstant      m_typeConstRB;
    private transient TypeConstant      m_typeService;
    private transient TypeConstant      m_typeServiceRB;
    private transient TypeConstant      m_typeModule;
    private transient TypeConstant      m_typeModuleRB;
    private transient TypeConstant      m_typePackage;
    private transient TypeConstant      m_typePackageRB;
    private transient TypeConstant      m_typeEnum;
    private transient TypeConstant      m_typeEnumRB;
    private transient TypeConstant      m_typeEnumeration;
    private transient TypeConstant      m_typeException;
    private transient TypeConstant      m_typeCloseable;
    private transient TypeConstant      m_typeException१;
    private transient TypeConstant      m_typeProperty;
    private transient TypeConstant      m_typeMethod;
    private transient TypeConstant      m_typeFunction;
    private transient TypeConstant      m_typeBoolean;
    private transient TypeConstant      m_typeTrue;
    private transient TypeConstant      m_typeFalse;
    private transient TypeConstant      m_typeNullable;
    private transient TypeConstant      m_typeOrdered;
    private transient TypeConstant      m_typeNull;
    private transient TypeConstant      m_typeChar;
    private transient TypeConstant      m_typeIntLiteral;
    private transient TypeConstant      m_typeFPLiteral;
    private transient TypeConstant      m_typeString;
    private transient TypeConstant      m_typeStringable;
    private transient TypeConstant      m_typeStringBuffer;
    private transient TypeConstant      m_typeString१;
    private transient TypeConstant      m_typeBit;
    private transient TypeConstant      m_typeByte;
    private transient TypeConstant      m_typeBitArray;
    private transient TypeConstant      m_typeByteArray;
    private transient TypeConstant      m_typeBinary;
    private transient TypeConstant      m_typeInt;
    private transient TypeConstant      m_typeIndexed;
    private transient TypeConstant      m_typeArray;
    private transient TypeConstant      m_typeMatrix;
    private transient TypeConstant      m_typeCollection;
    private transient TypeConstant      m_typeSet;
    private transient TypeConstant      m_typeList;
    private transient TypeConstant      m_typeMap;
    private transient TypeConstant      m_typeSliceable;
    private transient TypeConstant      m_typeOrderable;
    private transient TypeConstant      m_typeSequential;
    private transient TypeConstant      m_typeNumber;
    private transient TypeConstant      m_typeHashable;
    private transient TypeConstant      m_typeRange;
    private transient TypeConstant      m_typeInterval;
    private transient TypeConstant      m_typeIterable;
    private transient TypeConstant      m_typeIterator;
    private transient TypeConstant      m_typeTuple;
    private transient TypeConstant      m_typeTuple0;
    private transient TypeConstant      m_typeCondTuple;
    private transient TypeConstant      m_typeDate;
    private transient TypeConstant      m_typeTime;
    private transient TypeConstant      m_typeDateTime;
    private transient TypeConstant      m_typeTimeZone;
    private transient TypeConstant      m_typeDuration;
    private transient TypeConstant      m_typeVersion;
    private transient TypeConstant      m_typePath;
    private transient TypeConstant      m_typeFileStore;
    private transient TypeConstant      m_typeDirectory;
    private transient TypeConstant      m_typeFile;
    private transient TypeConstant      m_typeFileNode;
    private transient TypeConstant      m_typeFrame;
    private transient IntConstant       m_val0;
    private transient IntConstant       m_val1;
    private transient SingletonConstant m_valFalse;
    private transient SingletonConstant m_valTrue;
    private transient SingletonConstant m_valLesser;
    private transient SingletonConstant m_valEqual;
    private transient SingletonConstant m_valGreater;
    private transient SingletonConstant m_valNull;
    private transient SignatureConstant m_sigToString;
    private transient SignatureConstant m_sigEquals;
    private transient SignatureConstant m_sigCompare;
    private transient SignatureConstant m_sigValidator;
    private transient TypeInfo          m_infoPlaceholder;

    /**
     * A cached and pre-parsed image of the "implicit.x" file.
     */
    private static final Map<String, String[]> s_implicits;
    private static final Map<String, String>   s_implicitsByPath;
    static
        {
        try
            {
            ClassLoader loader = ConstantPool.class.getClassLoader();
            if (loader == null)
                {
                loader = ClassLoader.getSystemClassLoader();
                }
            Source src = new Source(loader.getResourceAsStream("implicit.x"));

            ErrorList errs   = new ErrorList(1);
            Parser    parser = new Parser(src, errs);
            s_implicits       = parser.parseImplicits();
            s_implicitsByPath = new HashMap<>();
            for (Map.Entry<String, String[]> entry : s_implicits.entrySet())
                {
                StringBuilder sb     = new StringBuilder();
                boolean       fFirst = true;
                for (String sPart : entry.getValue())
                    {
                    if (fFirst)
                        {
                        fFirst = false;
                        }
                    else
                        {
                        sb.append('.');
                        }
                    sb.append(sPart);
                    }
                s_implicitsByPath.putIfAbsent(sb.toString(), entry.getKey());
                }

            for (ErrorListener.ErrorInfo err : errs.getErrors())
                {
                System.err.println(err);
                }
            if (errs.hasSeriousErrors())
                {
                throw new IllegalStateException();
                }
            }
        catch (Exception e)
            {
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
        }

    /**
     * Cache of implicitly imported identities.
     */
    private Map<String, IdentityConstant> m_implicits = new HashMap<>();

    /**
     * A special "chicken and egg" list of TypeConstants that need to have their TypeInfos rebuilt.
     */
    private final ThreadLocal<List<TypeConstant>> f_tlolistDeferred = new ThreadLocal<>();

    /**
     * A list of classes that cause any derived TypeInfos to be invalidated.
     */
    private final List<IdentityConstant> f_listInvalidated = new Vector<>();

    /**
     * A cache of "instantiate-able" ClassCompositions keyed by the "inception type".
     *
     * Any ClassComposition in this map is defined by a {@link ClassConstant} referring to a
     * concrete natural class. It also keeps the secondary map of compositions for revealed types.
     */
    private Map<TypeConstant, ClassComposition> m_mapCompositions = new ConcurrentHashMap<>();

    /**
     * NakedRef is a fundamental formal type that comes from the "_native" module,
     */
    private transient TypeConstant m_typeNakedRef;

    /**
     * A cache of TypeInfo for parameterized NakedRef types.
     */
    private Map<TypeConstant, TypeInfo> m_mapRefTypes = new ConcurrentHashMap<>();

    /**
     * Thread local allowing to get the "current" ConstantPool without any context.
     */
    private static final ThreadLocal<ConstantPool[]> s_tloPool = ThreadLocal
        .withInitial(() -> new ConstantPool[1]);
    }
