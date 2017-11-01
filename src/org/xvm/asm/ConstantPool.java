package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.xvm.asm.Constant.Format;

import org.xvm.asm.constants.AccessTypeConstant;
import org.xvm.asm.constants.AllCondition;
import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.AnyCondition;
import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.DecimalConstant;
import org.xvm.asm.constants.CharConstant;
import org.xvm.asm.constants.ChildClassConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.DifferenceTypeConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.Float128Constant;
import org.xvm.asm.constants.Float16Constant;
import org.xvm.asm.constants.Float32Constant;
import org.xvm.asm.constants.Float64Constant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ImmutableTypeConstant;
import org.xvm.asm.constants.Int8Constant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.IntersectionTypeConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.MapConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.NamedCondition;
import org.xvm.asm.constants.NotCondition;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.ParentClassConstant;
import org.xvm.asm.constants.PresentCondition;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PseudoConstant;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TerminalTypeConstant;
import org.xvm.asm.constants.ThisClassConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypedefConstant;
import org.xvm.asm.constants.UInt8Constant;
import org.xvm.asm.constants.UInt8ArrayConstant;
import org.xvm.asm.constants.UnionTypeConstant;
import org.xvm.asm.constants.VarFPConstant;
import org.xvm.asm.constants.VersionConstant;
import org.xvm.asm.constants.VersionMatchesCondition;
import org.xvm.asm.constants.VersionedCondition;

import org.xvm.type.Decimal;
import org.xvm.util.PackedInteger;

import static org.xvm.compiler.Lexer.isValidIdentifier;
import static org.xvm.compiler.Lexer.isValidQualifiedModule;

import static org.xvm.util.Handy.checkElementsNonNull;
import static org.xvm.util.Handy.quotedString;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A shared pool of all Constant objects used in a particular FileStructure.
 *
 * @author cp  2015.12.04
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
        // with the type constant that the typedef refers to, removing a level of indirection
        constant = constant.simplify();

        // check if the Constant is already registered
        boolean fRegisterRecursively = false;
        if (constant.containsUnresolved())
            {
            if (m_fRecurseReg)
                {
                throw new IllegalStateException("unresolved constant: " + constant);
                }
            }
        else
            {
            final HashMap<Constant, Constant> mapConstants = ensureConstantLookup(constant.getFormat());
            final Constant constantOld = mapConstants.get(constant);
            if (constantOld == null)
                {
                if (constant.getContaining() != this)
                    {
                    constant = constant.adoptedBy(this);
                    }

                // add the Constant
                constant.setPosition(m_listConst.size());
                m_listConst.add(constant);
                mapConstants.put(constant, constant);

                // also allow the constant to be looked up by a locator
                Object oLocator = constant.getLocator();
                if (oLocator != null)
                    {
                    ensureLocatorLookup(constant.getFormat()).put(oLocator, constant);
                    }

                // make sure that the recursively referenced constants are all
                // registered (and that they are aware of their being referenced)
                fRegisterRecursively = true;
                }
            else
                {
                constant = constantOld;
                }
            }

        if (m_fRecurseReg)
            {
            // the first time that this constant is registered, the constant has to recursively
            // register any constants that it refers to
            fRegisterRecursively = !constant.hasRefs();

            // .. and each time the constant is registered, we tally that registration so that we
            // can later order the constants from most to least referenced
            constant.addRef();
            }

        if (fRegisterRecursively)
            {
            constant.registerConstants(this);
            }

        return constant;
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
        switch (format)
            {
            case IntLiteral:
            case FPLiteral:
            case Date:
            case Time:
            case DateTime:
            case Duration:
            case TimeInterval:
                LiteralConstant constant = (LiteralConstant) ensureLocatorLookup(format).get(s);
                if (constant == null)
                    {
                    constant = (LiteralConstant) register(new LiteralConstant(this, format, s));
                    }
                return constant;

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
                    constant = (UInt8Constant) register(new UInt8Constant(this, n));
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
     * Given a version , obtain a VersionedCondition that represents a test for that version
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
     * @param constParent
     * @param sClass
     *
     * @return
     */
    public ClassConstant ensureClassConstant(IdentityConstant constParent, String sClass)
        {
        switch (constParent.getFormat())
            {
            case Module:
            case Package:
            case Class:
            case Method:
                return (ClassConstant) register(new ClassConstant(this, constParent, sClass));

            default:
                throw new IllegalArgumentException("constant " + constParent.getFormat()
                        + " is not a Module, Package, Class, or Method");
            }
        }

    /**
     * Given a ClassConstant for a singleton const class, obtain a constant that represents the
     * singleton instance of that class.
     *
     * @param constClass  a ClassConstant of a singleton const class
     *
     * @return an SingletonConstant representing the singleton const value
     */
    public SingletonConstant ensureSingletonConstConstant(ClassConstant constClass)
        {
        return (SingletonConstant) register(new SingletonConstant(this, Format.SingletonConst, constClass));
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
     * Find the specified constructor of the specified class in the Ecstasy module.
     *
     * @param constClass  the class to find a constructor for
     * @param types       the types of the constructor parameters
     *
     * @return the constructor; never null
     *
     * @throws IllegalStateException if the constructor cannot be found
     */
    public MethodConstant ensureEcstasyConstructor(ClassConstant constClass, TypeConstant... types)
        {
        ClassStructure structClz = (ClassStructure) constClass.getComponent();
        if (structClz == null)
            {
            throw new IllegalStateException("could not find class " + constClass);
            }

        MultiMethodStructure structMM = (MultiMethodStructure) structClz.getChild("construct");
        if (structMM == null)
            {
            throw new IllegalStateException("no constructors on " + constClass);
            }

        int cParams = types.length;
        NextMethod: for (MethodStructure structMethod : structMM.methods())
            {
            if (structMethod.getParamCount() == cParams)
                {
                for (int i = 0; i < cParams; ++i)
                    {
                    if (!structMethod.getParam(i).getType().equals(types[i]))
                        {
                        continue NextMethod;
                        }
                    }

                return structMethod.getIdentityConstant();
                }
            }

        throw new IllegalStateException("no such constructor for " + cParams + " params on " + constClass);
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
        String  sPkg = null;
        String  sClz = null;
        String  sSub = null;
        String  sDef = null;

        switch (sName)
            {
            case "Ecstasy":
            case "X":
                break;

            case "Void":
                sDef = sName;
                break;

            case "Bit":
            case "Boolean":
            case "Char":
            case "Class":
            case "Const":
            case "Enum":
            case "Enumeration":
            case "Exception":
            case "Function":
            case "FPLiteral":
            case "IntLiteral":
            case "Iterable":
            case "Iterator":
            case "Module":
            case "Nullable":
            case "Object":
            case "Package":
            case "Ref":
            case "Service":
            case "String":
            case "Type":
                sClz = sName;
                break;

            case "Byte":
                sClz = "UInt8";
                break;

            case "Signum":
                sClz = "Number";
                sSub = "Signum";
                break;

            case "Int":
                sClz = "Int64";
                break;

            case "UInt":
                sClz = "UInt64";
                break;

            case "Tuple":
            case "Map":
            case "Set":
            case "Sequence":
            case "Array":
            case "Hashable":
                sPkg = "collections";
                sClz = sName;
                break;

            case "Entry":
                sPkg = "collections";
                sClz = "Map";
                sSub = "Entry";
                break;

            case "Property":
            case "Method":
                sPkg = "types";
                sClz = sName;
                break;

            case "null":
            case "Null":
                sClz = "Nullable";
                sSub = "Null";
                break;

            case "true":
            case "True":
                sClz = "Boolean";
                sSub = "True";
                break;

            case "false":
            case "False":
                sClz = "Boolean";
                sSub = "False";
                break;

            case "Atomic":
                sPkg = "annotations";
                sClz = "AtomicRef";
                break;

            case "Auto":
                sPkg = "annotations";
                sClz = "AutoConversion";
                break;

            case "FutureRef":
            case "Future":
                sPkg = "annotations";
                sClz = "FutureRef";
                break;

            case "Inject":
                sPkg = "annotations";
                sClz = "InjectedRef";
                break;

            case "Lazy":
                sPkg = "annotations";
                sClz = "LazyRef";
                break;

            case "Obscure":
                sPkg = "annotations";
                sClz = "ObscuringRef";
                break;

            case "Op":
                sPkg = "annotations";
                sClz = "Operator";
                break;

            case "Override":
                sPkg = "annotations";
                sClz = "Override";
                break;

            case "RO":
                sPkg = "annotations";
                sClz = "ReadOnly";
                break;

            case "Soft":
                sPkg = "annotations";
                sClz = "ReadOnly";
                break;

            case "Watch":
                sPkg = "annotations";
                sClz = "WatchRef";
                break;

            case "Weak":
                sPkg = "annotations";
                sClz = "WeakRef";
                break;

            default:
                return null;
            }

        IdentityConstant constId = modEcstasy();

        if (sPkg != null)
            {
            constId = ensurePackageConstant(constId, sPkg);
            }

        if (sDef != null)
            {
            constId = ensureTypedefConstant(constId, sDef);
            }
        else if (sClz != null)
            {
            constId = ensureClassConstant(constId, sClz);
            if (sSub != null)
                {
                constId = ensureClassConstant(constId, sSub);
                }
            }

        return constId;
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

        Component component = constId.getComponent();
        if (component == null)
            {
            throw new IllegalStateException("missing Ecstasy component: " + constId);
            }
        return component;
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
     * @param access         the method accessibility
     * @param aconstParams   the invocation parameters for the method
     * @param aconstReturns  the return values from the method
     *
     * @return the MethodConstant
     */
    public MethodConstant ensureMethodConstant(IdentityConstant constParent, String sName,
            Access access, TypeConstant[] aconstParams, TypeConstant[] aconstReturns)
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
                constMultiMethod = ensureMultiMethodConstant(constParent, sName);
                break;

            default:
                throw new IllegalArgumentException("constant " + constParent.getFormat()
                        + " is not a Module, Package, Class, Method, or Property");
            }

        return (MethodConstant) register(new MethodConstant(this, constMultiMethod, access,
                aconstParams, aconstReturns));
        }

    /**
     * Obtain a constant that represents the specified methods.
     *
     * @param constParent  the constant identifying the parent of the method
     * @param constSig     the signature of the method
     * @param access       the method accessibility
     *
     * @return the MethodConstant
     */
    public MethodConstant ensureMethodConstant(IdentityConstant constParent, SignatureConstant constSig, Access access)
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
            case Method:
            case Property:
                constMultiMethod = ensureMultiMethodConstant(constParent, constSig.getName());
                break;

            default:
                throw new IllegalArgumentException("constant " + constParent.getFormat()
                        + " is not a Module, Package, Class, Method, or Property");
            }

        return (MethodConstant) register(new MethodConstant(this, constMultiMethod, access, constSig));
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

        if (access != null)
            {
            constType = ensureAccessTypeConstant(constType, access);
            }

        if (constTypes != null && constTypes.length > 0)
            {
            constType = ensureParameterizedTypeConstant(constType, constTypes);
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
        TypeConstant constAccess = null;

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
            else
                {
                throw new IllegalArgumentException("type already has an access specified");
                }
            }

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
    public TypeConstant ensureParameterizedTypeConstant(TypeConstant constType, TypeConstant[] constTypes)
        {
        if (constType.isParamsSpecified())
            {
            List<TypeConstant> listTypes = constType.getParamTypes();
            int c = listTypes.size();
            CheckTypes: if (c == constTypes.length)
                {
                for (int i = 0; i < c; ++i)
                    {
                    if (!constTypes[i].equals(listTypes.get(i)))
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
    public ImmutableTypeConstant ensureImmutableTypeConstant(TypeConstant constType)
        {
        ImmutableTypeConstant constant;
        if (constType instanceof ImmutableTypeConstant)
            {
            constant = (ImmutableTypeConstant) constType;
            }
        else
            {
            constant = (ImmutableTypeConstant) ensureLocatorLookup(Format.ImmutableType).get(constType);
            if (constant != null)
                {
                return constant;
                }

            constant = new ImmutableTypeConstant(this, constType);
            }

        return (ImmutableTypeConstant) register(constant);
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
     * Obtain an auto-narrowing class type constant that represents the type of "this".
     *
     * @param access  the access modifier, or null
     *
     * @return an auto-narrowing class type constant that represents the type of "this"
     */
    public TypeConstant ensureThisTypeConstant(IdentityConstant constClass, Access access)
        {
        // get the raw type
        ThisClassConstant constId   = ensureThisClassConstant(constClass);
        TypeConstant      constType = (TypeConstant) ensureLocatorLookup(Format.TerminalType).get(constId);
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
        if (constChild == null || !constChild.isAutoNarrowing()
                || !constChild.isSingleDefiningConstant() || constChild.isParamsSpecified())
            {
            throw new IllegalArgumentException("single, auto-narrowing, non-parameterized child required");
            }

        TypeConstant constParent = new TerminalTypeConstant(this, new ParentClassConstant(this,
                (PseudoConstant) constChild.getDefiningConstant()));

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
     * Obtain an auto-narrowing class type that represents a non-static inner class child of the
     * specified auto-narrowing type.
     *
     * @param constParent  an auto-narrowing type constant
     * @param sChild       the name of the non-static inner class to obtain a type constant for
     *
     * @return
     */
    public TypeConstant ensureChildTypeConstant(TypeConstant constParent, String sChild)
        {
        if (constParent == null || !constParent.isAutoNarrowing()
                || !constParent.isSingleDefiningConstant() || constParent.isParamsSpecified())
            {
            throw new IllegalArgumentException("single, auto-narrowing, non-parameterized parent required");
            }

        TypeConstant constChild = new TerminalTypeConstant(this, new ChildClassConstant(this,
                (PseudoConstant) constParent.getDefiningConstant(), sChild));

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
     * Given the specified property or register constant, obtain a TypeConstant that represents a
     * type parameter.
     *
     * @param constId  a constant specifying a property or register of a method
     *
     * @return the constant representing the type parameter
     */
    public TerminalTypeConstant ensureTypeParameterConstant(Constant constId)
        {
        if (!(constId instanceof RegisterConstant || constId instanceof PropertyConstant))
            {
            throw new IllegalArgumentException("invalid parameter identifier: " + constId);
            }

        TerminalTypeConstant constType = (TerminalTypeConstant) ensureLocatorLookup(Format.TerminalType).get(constId);
        if (constType == null)
            {
            constType = (TerminalTypeConstant) register(new TerminalTypeConstant(this, constId));
            }
        return constType;
        }

    /**
     * Given the specified register index, obtain a TypeConstant that represents the type parameter.
     *
     * @param constMethod  the containing method
     * @param iReg         the register number
     *
     * @return the RegisterTypeConstant for the specified register number
     */
    public RegisterConstant ensureRegisterConstant(MethodConstant constMethod, int iReg)
        {
        RegisterConstant constReg = null;
        if (iReg == 0)
            {
            constReg = (RegisterConstant) ensureLocatorLookup(Format.Register).get(constMethod);
            }
        if (constReg == null)
            {
            constReg = (RegisterConstant) register(new RegisterConstant(this, constMethod, iReg));
            }
        return constReg;
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

        TypeConstant constType = null;

        Object locator  = constId.getLocator();
        if (locator != null)
            {
            constType = (TerminalTypeConstant) ensureLocatorLookup(Format.TerminalType).get(constId);
            }

        if (constType == null)
            {
            constType = (TypeConstant) register(new TerminalTypeConstant(this, constId));
            }

        return constType;
        }

    /**
     * Given the specified annotation class and parameters, obtain a type that represents the
     * annotated form of the specified type.
     *
     * @param constClass   the annotation class
     * @param aconstParam  the parameters for the annotation
     * @param constType    the type being annotated
     *
     * @return
     */
    public AnnotatedTypeConstant ensureAnnotatedTypeConstant(Constant constClass,
            Constant[] aconstParam, TypeConstant constType)
        {
        return (AnnotatedTypeConstant) register(new AnnotatedTypeConstant(this, constClass, aconstParam, constType));
        }

    /**
     * Given a type, obtain a TypeConstant that represents the intersection of that type with the
     * Nullable type.
     * This corresponds to the "?" operator when applied to types.
     *
     * @param constType  the type being made Nullable
     *
     * @return the intersection of the specified type and Nullable
     */
    public IntersectionTypeConstant ensureNullableTypeConstant(TypeConstant constType)
        {
        return ensureIntersectionTypeConstant(typeNullable(), constType);
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

    public ClassConstant     clzObject()        {ClassConstant     c = m_clzObject;       if (c == null) {m_clzObject       = c = (ClassConstant) getImplicitlyImportedIdentity("Object"     );} return c;}
    public ClassConstant     clzType()          {ClassConstant     c = m_clzType;         if (c == null) {m_clzType         = c = (ClassConstant) getImplicitlyImportedIdentity("Type"       );} return c;}
    public ClassConstant     clzClass()         {ClassConstant     c = m_clzClass;        if (c == null) {m_clzClass        = c = (ClassConstant) getImplicitlyImportedIdentity("Class"      );} return c;}
    public ClassConstant     clzConst()         {ClassConstant     c = m_clzConst;        if (c == null) {m_clzConst        = c = (ClassConstant) getImplicitlyImportedIdentity("Const"      );} return c;}
    public ClassConstant     clzService()       {ClassConstant     c = m_clzService;      if (c == null) {m_clzService      = c = (ClassConstant) getImplicitlyImportedIdentity("Service"    );} return c;}
    public ClassConstant     clzModule()        {ClassConstant     c = m_clzModule;       if (c == null) {m_clzModule       = c = (ClassConstant) getImplicitlyImportedIdentity("Module"     );} return c;}
    public ClassConstant     clzPackage()       {ClassConstant     c = m_clzPackage;      if (c == null) {m_clzPackage      = c = (ClassConstant) getImplicitlyImportedIdentity("Package"    );} return c;}
    public ClassConstant     clzEnum()          {ClassConstant     c = m_clzEnum;         if (c == null) {m_clzEnum         = c = (ClassConstant) getImplicitlyImportedIdentity("Enum"       );} return c;}
    public ClassConstant     clzEnumeration()   {ClassConstant     c = m_clzEnumeration;  if (c == null) {m_clzEnumeration  = c = (ClassConstant) getImplicitlyImportedIdentity("Enumeration");} return c;}
    public ClassConstant     clzException()     {ClassConstant     c = m_clzException;    if (c == null) {m_clzException    = c = (ClassConstant) getImplicitlyImportedIdentity("Exception"  );} return c;}
    public ClassConstant     clzFunction()      {ClassConstant     c = m_clzFunction;     if (c == null) {m_clzFunction     = c = (ClassConstant) getImplicitlyImportedIdentity("Function"   );} return c;}
    public ClassConstant     clzBoolean()       {ClassConstant     c = m_clzBoolean;      if (c == null) {m_clzBoolean      = c = (ClassConstant) getImplicitlyImportedIdentity("Boolean"    );} return c;}
    public ClassConstant     clzTrue()          {ClassConstant     c = m_clzTrue;         if (c == null) {m_clzTrue         = c = (ClassConstant) getImplicitlyImportedIdentity("True"       );} return c;}
    public ClassConstant     clzFalse()         {ClassConstant     c = m_clzFalse;        if (c == null) {m_clzFalse        = c = (ClassConstant) getImplicitlyImportedIdentity("False"      );} return c;}
    public ClassConstant     clzNullable()      {ClassConstant     c = m_clzNullable;     if (c == null) {m_clzNullable     = c = (ClassConstant) getImplicitlyImportedIdentity("Nullable"   );} return c;}
    public ClassConstant     clzNull()          {ClassConstant     c = m_clzNull;         if (c == null) {m_clzNull         = c = (ClassConstant) getImplicitlyImportedIdentity("Null"       );} return c;}
    public ClassConstant     clzChar()          {ClassConstant     c = m_clzChar;         if (c == null) {m_clzChar         = c = (ClassConstant) getImplicitlyImportedIdentity("Char"       );} return c;}
    public ClassConstant     clzIntLiteral()    {ClassConstant     c = m_clzIntLiteral;   if (c == null) {m_clzIntLiteral   = c = (ClassConstant) getImplicitlyImportedIdentity("IntLiteral" );} return c;}
    public ClassConstant     clzFPLiteral()     {ClassConstant     c = m_clzFPLiteral;    if (c == null) {m_clzFPLiteral    = c = (ClassConstant) getImplicitlyImportedIdentity("FPLiteral"  );} return c;}
    public ClassConstant     clzString()        {ClassConstant     c = m_clzString;       if (c == null) {m_clzString       = c = (ClassConstant) getImplicitlyImportedIdentity("String"     );} return c;}
    public ClassConstant     clzByte()          {ClassConstant     c = m_clzByte;         if (c == null) {m_clzByte         = c = (ClassConstant) getImplicitlyImportedIdentity("Byte"       );} return c;}
    public ClassConstant     clzInt()           {ClassConstant     c = m_clzInt;          if (c == null) {m_clzInt          = c = (ClassConstant) getImplicitlyImportedIdentity("Int"        );} return c;}
    public ClassConstant     clzArray()         {ClassConstant     c = m_clzArray;        if (c == null) {m_clzArray        = c = (ClassConstant) getImplicitlyImportedIdentity("Array"      );} return c;}
    public ClassConstant     clzSequence()      {ClassConstant     c = m_clzSequence;     if (c == null) {m_clzSequence     = c = (ClassConstant) getImplicitlyImportedIdentity("Sequence"   );} return c;}
    public ClassConstant     clzHashable()      {ClassConstant     c = m_clzHashable;     if (c == null) {m_clzHashable     = c = (ClassConstant) getImplicitlyImportedIdentity("Hashable"   );} return c;}
    public ClassConstant     clzIterable()      {ClassConstant     c = m_clzIterable;     if (c == null) {m_clzIterable     = c = (ClassConstant) getImplicitlyImportedIdentity("Iterable"   );} return c;}
    public ClassConstant     clzIterator()      {ClassConstant     c = m_clzIterator;     if (c == null) {m_clzIterator     = c = (ClassConstant) getImplicitlyImportedIdentity("Iterator"   );} return c;}
    public ClassConstant     clzTuple()         {ClassConstant     c = m_clzTuple;        if (c == null) {m_clzTuple        = c = (ClassConstant) getImplicitlyImportedIdentity("Tuple"      );} return c;}

    public TypeConstant      typeObject()       {TypeConstant      c = m_typeObject;      if (c == null) {m_typeObject      = c = ensureTerminalTypeConstant(clzObject()                     );} return c;}
    public TypeConstant      typeType()         {TypeConstant      c = m_typeType;        if (c == null) {m_typeType        = c = ensureTerminalTypeConstant(clzType()                       );} return c;}
    public TypeConstant      typeClass()        {TypeConstant      c = m_typeClass;       if (c == null) {m_typeClass       = c = ensureTerminalTypeConstant(clzClass()                      );} return c;}
    public TypeConstant      typeConst()        {TypeConstant      c = m_typeConst;       if (c == null) {m_typeConst       = c = ensureTerminalTypeConstant(clzConst()                      );} return c;}
    public TypeConstant      typeService()      {TypeConstant      c = m_typeService;     if (c == null) {m_typeService     = c = ensureTerminalTypeConstant(clzService()                    );} return c;}
    public TypeConstant      typeModule()       {TypeConstant      c = m_typeModule;      if (c == null) {m_typeModule      = c = ensureTerminalTypeConstant(clzModule()                     );} return c;}
    public TypeConstant      typePackage()      {TypeConstant      c = m_typePackage;     if (c == null) {m_typePackage     = c = ensureTerminalTypeConstant(clzPackage()                    );} return c;}
    public TypeConstant      typeEnum()         {TypeConstant      c = m_typeEnum;        if (c == null) {m_typeEnum        = c = ensureTerminalTypeConstant(clzEnum()                       );} return c;}
    public TypeConstant      typeEnumeration()  {TypeConstant      c = m_typeEnumeration; if (c == null) {m_typeEnumeration = c = ensureTerminalTypeConstant(clzEnumeration()                );} return c;}
    public TypeConstant      typeException()    {TypeConstant      c = m_typeException;   if (c == null) {m_typeException   = c = ensureTerminalTypeConstant(clzException()                  );} return c;}
    public TypeConstant      typeFunction()     {TypeConstant      c = m_typeFunction;    if (c == null) {m_typeFunction    = c = ensureTerminalTypeConstant(clzFunction()                   );} return c;}
    public TypeConstant      typeBoolean()      {TypeConstant      c = m_typeBoolean;     if (c == null) {m_typeBoolean     = c = ensureTerminalTypeConstant(clzBoolean()                    );} return c;}
    public TypeConstant      typeTrue()         {TypeConstant      c = m_typeTrue;        if (c == null) {m_typeTrue        = c = ensureTerminalTypeConstant(clzTrue()                       );} return c;}
    public TypeConstant      typeFalse()        {TypeConstant      c = m_typeFalse;       if (c == null) {m_typeFalse       = c = ensureTerminalTypeConstant(clzFalse()                      );} return c;}
    public TypeConstant      typeNullable()     {TypeConstant      c = m_typeNullable;    if (c == null) {m_typeNullable    = c = ensureTerminalTypeConstant(clzNullable()                   );} return c;}
    public TypeConstant      typeNull()         {TypeConstant      c = m_typeNull;        if (c == null) {m_typeNull        = c = ensureTerminalTypeConstant(clzNull()                       );} return c;}
    public TypeConstant      typeChar()         {TypeConstant      c = m_typeChar;        if (c == null) {m_typeChar        = c = ensureTerminalTypeConstant(clzChar()                       );} return c;}
    public TypeConstant      typeIntLiteral()   {TypeConstant      c = m_typeIntLiteral;  if (c == null) {m_typeIntLiteral  = c = ensureTerminalTypeConstant(clzIntLiteral()                 );} return c;}
    public TypeConstant      typeFPLiteral()    {TypeConstant      c = m_typeFPLiteral;   if (c == null) {m_typeFPLiteral   = c = ensureTerminalTypeConstant(clzFPLiteral()                  );} return c;}
    public TypeConstant      typeString()       {TypeConstant      c = m_typeString;      if (c == null) {m_typeString      = c = ensureTerminalTypeConstant(clzString()                     );} return c;}
    public TypeConstant      typeByte()         {TypeConstant      c = m_typeByte;        if (c == null) {m_typeByte        = c = ensureTerminalTypeConstant(clzByte()                       );} return c;}
    public TypeConstant      typeInt()          {TypeConstant      c = m_typeInt;         if (c == null) {m_typeInt         = c = ensureTerminalTypeConstant(clzInt()                        );} return c;}
    public TypeConstant      typeArray()        {TypeConstant      c = m_typeArray;       if (c == null) {m_typeArray       = c = ensureTerminalTypeConstant(clzArray()                      );} return c;}
    public TypeConstant      typeSequence()     {TypeConstant      c = m_typeSequence;    if (c == null) {m_typeSequence    = c = ensureTerminalTypeConstant(clzSequence()                   );} return c;}
    public TypeConstant      typeHashable()     {TypeConstant      c = m_typeHashable;    if (c == null) {m_typeHashable    = c = ensureTerminalTypeConstant(clzHashable()                   );} return c;}
    public TypeConstant      typeIterable()     {TypeConstant      c = m_typeIterable;    if (c == null) {m_typeIterable    = c = ensureTerminalTypeConstant(clzIterable()                   );} return c;}
    public TypeConstant      typeIterator()     {TypeConstant      c = m_typeIterator;    if (c == null) {m_typeIterator    = c = ensureTerminalTypeConstant(clzIterator()                   );} return c;}
    public TypeConstant      typeTuple()        {TypeConstant      c = m_typeTuple;       if (c == null) {m_typeTuple       = c = ensureTerminalTypeConstant(clzTuple()                      );} return c;}

    public TypeConstant      typeException१()   {TypeConstant      c = m_typeException१;  if (c == null) {m_typeException१  = c = ensureNullableTypeConstant(typeException()                 );} return c;}
    public TypeConstant      typeString१()      {TypeConstant      c = m_typeString१;     if (c == null) {m_typeString१     = c = ensureNullableTypeConstant(typeString()                    );} return c;}

    public IntConstant       val0()             {IntConstant       c = m_val0;            if (c == null) {m_val0            = c = ensureIntConstant(0)                                        ;} return c;}
    public IntConstant       val1()             {IntConstant       c = m_val1;            if (c == null) {m_val1            = c = ensureIntConstant(1)                                        ;} return c;}
    public SingletonConstant valFalse()         {SingletonConstant c = m_valFalse;        if (c == null) {m_valFalse        = c = ensureSingletonConstConstant(clzFalse())                    ;} return c;}
    public SingletonConstant valTrue()          {SingletonConstant c = m_valTrue;         if (c == null) {m_valTrue         = c = ensureSingletonConstConstant(clzTrue())                     ;} return c;}
    public SingletonConstant valNull()          {SingletonConstant c = m_valNull;         if (c == null) {m_valNull         = c = ensureSingletonConstConstant(clzNull())                     ;} return c;}


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public ConstantPool getConstantPool()
        {
        return this;
        }

    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        return m_listConst.iterator();
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
                case TimeInterval:
                    constant = new LiteralConstant(this, format, in);
                    break;

                case Int8:
                    constant = new Int8Constant(this, format, in);
                    break;

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

                case Register:
                    constant = new RegisterConstant(this, format, in);
                    break;

                case Signature:
                    constant = new SignatureConstant(this, format, in);
                    break;

                /*
                * Types.
                */
                case TerminalType:
                    constant = new ParameterizedTypeConstant(this, format, in);
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

                case UnionType:
                    constant = new UnionTypeConstant(this, format, in);
                    break;

                case IntersectionType:
                    constant = new IntersectionTypeConstant(this, format, in);
                    break;

                case DifferenceType:
                    constant = new DifferenceTypeConstant(this, format, in);
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
            constant.disassemble(null);
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
        return "size=" + m_listConst.size() + ", recurse-reg=" + m_fRecurseReg;
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
    private HashMap<Constant, Constant> ensureConstantLookup(Format format)
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
    private HashMap<Object, Constant> ensureLocatorLookup(Format format)
        {
        final EnumMap<Format, HashMap<Object, Constant>> mapLocatorMaps = m_mapLocators;

        HashMap<Object, Constant> mapLocators = mapLocatorMaps.get(format);
        if (mapLocators == null)
            {
            // lazily instantiate the locator map for the specified type
            mapLocators = new HashMap<>();
            mapLocatorMaps.put(format, mapLocators);
            }

        return mapLocators;
        }

    /**
     * Create the necessary structures for looking up Constant objects quickly, and populate those
     * structures with the set of existing Constant objects.
     */
    private void ensureLookup()
        {
        if (m_mapConstants.isEmpty())
            {
            for (Format format : Format.values())
                {
                m_mapConstants.put(format, new HashMap<>());
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


    // ----- fields --------------------------------------------------------------------------------

    /**
     * An immutable, empty, zero-length array of types.
     */
    public static final TypeConstant[] NO_TYPES = new TypeConstant[0];

    /**
     * Storage of Constant objects by index.
     */
    private final ArrayList<Constant> m_listConst = new ArrayList<>();

    /**
     * Reverse lookup structure to find a particular constant by constant.
     */
    private final EnumMap<Format, HashMap<Constant, Constant>> m_mapConstants = new EnumMap<>(Format.class);

    /**
     * Reverse lookup structure to find a particular constant by locator.
     */
    private final EnumMap<Format, HashMap<Object, Constant>> m_mapLocators = new EnumMap<>(Format.class);

    /**
     * Tracks whether the ConstantPool should recursively register constants.
     */
    private transient boolean m_fRecurseReg;

    private transient ModuleConstant    m_valEcstasy;
    private transient ClassConstant     m_clzObject;
    private transient ClassConstant     m_clzType;
    private transient ClassConstant     m_clzClass;
    private transient ClassConstant     m_clzConst;
    private transient ClassConstant     m_clzService;
    private transient ClassConstant     m_clzModule;
    private transient ClassConstant     m_clzPackage;
    private transient ClassConstant     m_clzEnum;
    private transient ClassConstant     m_clzEnumeration;
    private transient ClassConstant     m_clzException;
    private transient ClassConstant     m_clzFunction;
    private transient ClassConstant     m_clzBoolean;
    private transient ClassConstant     m_clzTrue;
    private transient ClassConstant     m_clzFalse;
    private transient ClassConstant     m_clzNullable;
    private transient ClassConstant     m_clzNull;
    private transient ClassConstant     m_clzChar;
    private transient ClassConstant     m_clzIntLiteral;
    private transient ClassConstant     m_clzFPLiteral;
    private transient ClassConstant     m_clzString;
    private transient ClassConstant     m_clzByte;
    private transient ClassConstant     m_clzInt;
    private transient ClassConstant     m_clzArray;
    private transient ClassConstant     m_clzSequence;
    private transient ClassConstant     m_clzHashable;
    private transient ClassConstant     m_clzIterable;
    private transient ClassConstant     m_clzIterator;
    private transient ClassConstant     m_clzTuple;
    private transient TypeConstant      m_typeObject;
    private transient TypeConstant      m_typeType;
    private transient TypeConstant      m_typeClass;
    private transient TypeConstant      m_typeConst;
    private transient TypeConstant      m_typeService;
    private transient TypeConstant      m_typeModule;
    private transient TypeConstant      m_typePackage;
    private transient TypeConstant      m_typeEnum;
    private transient TypeConstant      m_typeEnumeration;
    private transient TypeConstant      m_typeException;
    private transient TypeConstant      m_typeException१;
    private transient TypeConstant      m_typeFunction;
    private transient TypeConstant      m_typeBoolean;
    private transient TypeConstant      m_typeTrue;
    private transient TypeConstant      m_typeFalse;
    private transient TypeConstant      m_typeNullable;
    private transient TypeConstant      m_typeNull;
    private transient TypeConstant      m_typeChar;
    private transient TypeConstant      m_typeIntLiteral;
    private transient TypeConstant      m_typeFPLiteral;
    private transient TypeConstant      m_typeString;
    private transient TypeConstant      m_typeString१;
    private transient TypeConstant      m_typeByte;
    private transient TypeConstant      m_typeInt;
    private transient TypeConstant      m_typeArray;
    private transient TypeConstant      m_typeSequence;
    private transient TypeConstant      m_typeHashable;
    private transient TypeConstant      m_typeIterable;
    private transient TypeConstant      m_typeIterator;
    private transient TypeConstant      m_typeTuple;
    private transient IntConstant       m_val0;
    private transient IntConstant       m_val1;
    private transient SingletonConstant m_valFalse;
    private transient SingletonConstant m_valTrue;
    private transient SingletonConstant m_valNull;
    }
