package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.xvm.asm.Constant.Format;
import org.xvm.asm.Constant.Type;
import org.xvm.asm.StructureContainer.MethodContainer;

import org.xvm.util.Handy;
import org.xvm.util.PackedInteger;

import static org.xvm.compiler.Lexer.isValidIdentifier;
import static org.xvm.compiler.Lexer.isValidQualifiedModule;

import static org.xvm.util.Handy.appendIntAsHex;
import static org.xvm.util.Handy.byteArrayToHexString;
import static org.xvm.util.Handy.byteToHexString;
import static org.xvm.util.Handy.checkElementsNonNull;
import static org.xvm.util.Handy.quotedChar;
import static org.xvm.util.Handy.quotedString;
import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readUtf8Char;
import static org.xvm.util.Handy.readUtf8String;
import static org.xvm.util.Handy.writePackedLong;
import static org.xvm.util.Handy.writeUtf8Char;
import static org.xvm.util.Handy.writeUtf8String;


/**
 * A shared pool of all Constant objects used in a particular FileStructure.
 *
 * @author cp  2015.12.04
 */
public class ConstantPool
        extends XvmStructure
    {
    // ----- constructors ------------------------------------------------------

    /**
     * Construct a ConstantPool.
     *
     * @param fstruct  the FileStructure that contains this ConstantPool
     */
    public ConstantPool(FileStructure fstruct)
        {
        super(fstruct);
        }


    // ----- public API --------------------------------------------------------

    /**
     * Obtain the Constant that is currently stored at the specified index. A
     * runtime exception will occur if the index is invalid.
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
     * Register a Constant. This is used when a new Constant is created by the
     * ConstantPool, but it can also be used directly by a consumer, and it's
     * used during the bulk (re-)registration of Constants by the
     * {@link XvmStructure#registerConstants} method of all of the various parts
     * of the FileStructure.
     *
     * @param constant  the Constant to register
     *
     * @return if the passed Constant was not previously registered, then it is
     *         returned; otherwise, the previously registered Constant (which
     *         should be used in lieu of the passed Constant) is returned
     */
    public Constant register(Constant constant)
        {
        // to allow this method to be used blindly, i.e. for constants that may
        // be optional within a given structure, simply pass back null refs
        if (constant == null)
            {
            return null;
            }

        // check if the Constant is already registered
        final HashMap<Constant, Constant> mapConstants = ensureConstantLookup(constant.getType());
        final Constant constantOld = mapConstants.get(constant);
        if (constantOld == null)
            {
            if (constant.getContaining() != this)
                {
                // REVIEW maybe just clone it instead, and register the clone
                throw new IllegalStateException("wrong ConstantPool");
                }

            // add the Constant
            constant.setPosition(m_listConst.size());
            m_listConst.add(constant);
            mapConstants.put(constant, constant);

            // also allow the constant to be looked up by a locator
            Object oLocator = constant.getLocator();
            if (oLocator != null)
                {
                ensureLocatorLookup(constant.getType()).put(oLocator, constant);
                }

            // make sure that the recursively referenced constants are all
            // registered (and that they are aware of their being referenced)
            constant.registerConstants(this);
            }
        else
            {
            constant = constantOld;
            }

        if (m_fRecurseReg)
            {
            final boolean fDidHaveRefs = constant.hasRefs();
            constant.addRef();
            if (!fDidHaveRefs)
                {
                // first time to register this constant; recursively register
                // any constants that it refers to
                constant.registerConstants(this);
                }
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
    public ByteConstant ensureByteConstant(int b)
        {
        // check the pre-existing constants first
        ByteConstant constant = (ByteConstant) ensureLocatorLookup(Type.Byte).get(Byte.valueOf((byte) b));
        if (constant == null)
            {
            constant = (ByteConstant) register(new ByteConstant(this, b));
            }
        return constant;
        }

    /**
     * Given the specified byte array value, obtain a ByteStringConstant that
     * represents it.
     *
     * @param ab  the byte array value
     *
     * @return a ByteStringConstant for the passed byte array value
     */
    public ByteStringConstant ensureByteStringConstant(byte[] ab)
        {
        ByteStringConstant constant = new ByteStringConstant(this, ab.clone());
        return (ByteStringConstant) register(constant);
        }

    /**
     * Given the specified character value, obtain a CharConstant that
     * represents it.
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
            CharConstant constant = (CharConstant) ensureLocatorLookup(Type.Char).get(Character.valueOf((char) ch));
            if (constant != null)
                {
                return constant;
                }
            }

        return (CharConstant) register(new CharConstant(this, ch));
        }

    /**
     * Given the specified String value, obtain a CharStringConstant that
     * represents it.
     *
     * @param s  the String value
     *
     * @return a CharStringConstant for the passed String value
     */
    public CharStringConstant ensureCharStringConstant(String s)
        {
        // check the pre-existing constants first
        CharStringConstant constant = (CharStringConstant) ensureLocatorLookup(Type.CharString).get(s);
        if (constant == null)
            {
            constant = (CharStringConstant) register(new CharStringConstant(this, s));
            }
        return constant;
        }

    /**
     * Given the specified <tt>long</tt> value, obtain a IntConstant that
     * represents it.
     *
     * @param n  the <tt>long</tt> value of the integer
     *
     * @return an IntConstant for the passed <tt>long</tt> value
     */
    public IntConstant ensureIntConstant(long n)
        {
        return ensureIntConstant(PackedInteger.valueOf(n));
        }

    /**
     * Given the specified PackedInteger value, obtain a IntConstant that
     * represents it.
     *
     * @param pint  the PackedInteger value
     *
     * @return an IntConstant for the passed PackedInteger value
     */
    public IntConstant ensureIntConstant(PackedInteger pint)
        {
        // check the pre-existing constants first
        IntConstant constant = (IntConstant) ensureLocatorLookup(Type.Int).get(pint);
        if (constant == null)
            {
            constant = (IntConstant) register(new IntConstant(this, pint));
            }
        return constant;
        }

    public VersionConstant ensureVersionConstant(Version ver)
        {
        // TODO VersionConstant(ConstantPool pool, VersionConstant constBaseVer, int nVer, boolean fDot, String sDesc)
        return null;
        }

    public ConditionalConstant.NamedCondition ensureNamedCondition(String sName)
        {
        // TODO locator?
        return new ConditionalConstant.NamedCondition(this, ensureCharStringConstant(sName));
        }

    public ConditionalConstant.PresentCondition ensurePresentCondition(Constant constVMStruct)
        {
        // TODO locator?
        return ensurePresentCondition(constVMStruct, null, false);
        }

    public ConditionalConstant.PresentCondition ensurePresentCondition(Constant constVMStruct, VersionConstant constVer, boolean fExactVer)
        {
        // TODO PresentCondition(ConstantPool pool, Constant constVMStruct, VersionConstant constVer, boolean fExactVer)
        return null;
        }

    /**
     * TODO
     *
     * @param condition1
     * @param condition2
     *
     * @return
     */
    public ConditionalConstant.AllCondition ensureAllCondition(ConditionalConstant condition1, ConditionalConstant condition2)
        {
        if (condition1 == null || condition2 == null)
            {
            throw new IllegalArgumentException("conditions required");
            }

        return (ConditionalConstant.AllCondition) register(new ConditionalConstant.AllCondition(this,
                new ConditionalConstant[] {condition1, condition2}));
        }

    /**
     * TODO
     *
     * @param acondition
     *
     * @return
     */
    public ConditionalConstant.AllCondition ensureAllCondition(ConditionalConstant[] acondition)
        {
        checkElementsNonNull(acondition);
        if (acondition.length < 2)
            {
            throw new IllegalArgumentException("at least 2 conditions required");
            }

        return (ConditionalConstant.AllCondition) register(new ConditionalConstant.AllCondition(this, acondition.clone()));
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

        ModuleConstant constant = (ModuleConstant) ensureLocatorLookup(Type.Module).get(sName);
        if (constant == null)
            {
            constant = (ModuleConstant) register(new ModuleConstant(this, sName));
            }
        return constant;
        }

    /**
     * Given the specified package name and the context (module or package)
     * within which it exists, obtain a PackageConstant that represents it.
     *
     * @param constParent  the ModuleConstant or PackageConstant that contains
     *                     the specified package
     * @param sPackage     the unqualified name of the package
     *
     * @return the specified PackageConstant
     */
    public PackageConstant ensurePackageConstant(Constant constParent, String sPackage)
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

        switch (constParent.getType())
            {
            case Module:
            case Package:
                PackageConstant constant = (PackageConstant) ensureLocatorLookup(Type.Package).get(sPackage);
                if (constant == null)
                    {
                    constant = (PackageConstant) register(new PackageConstant(this, constParent, sPackage, null));
                    }
                return constant;

            default:
                throw new IllegalArgumentException("constant type " + constParent.getType()
                        + " is not a Module or Package");
            }
        }

    // REVIEW
    /**
     * Given a specified module to import, and given the module that it is
     * being imported into and the package name at which it will be imported,
     * obtain the PackageConstant for the package that will represent the
     * imported module.
     *
     * @param constParent  the ModuleConstant the the module into which a
     *                     different module is being imported
     * @param sPackage     the name of the package which will be used to
     *                     refer to the imported module
     * @param constModule  the ModuleConstant for the module to import
     *
     * @return the PackageConstant that represents the imported module
     */
    public PackageConstant ensurePackageConstant(ModuleConstant constParent, String sPackage, ModuleConstant constModule)
        {
        if (constParent == null)
            {
            throw new IllegalArgumentException("ModuleConstant to import into required");
            }

        if (constModule == null)
            {
            throw new IllegalArgumentException("ModuleConstant to import required");
            }

        // validate the package name
        if (!isValidIdentifier(sPackage))
            {
            throw new IllegalArgumentException("illegal package name: " + sPackage);
            }

        PackageConstant constant = (PackageConstant) ensureLocatorLookup(Type.Package).get(sPackage);
        if (constant == null)
            {
            constant = (PackageConstant) register(new PackageConstant(this, constParent, sPackage, constModule));
            }

// REVIEW
//        else if (Handy.equals(constModule, constant.getImportedModule()))
//            {
//            throw new IllegalStateException("Imported module mismatch: old=" + constant.getImportedModule()
//                    + ", new=" + constModule);
//            }

        // TODO
        return constant;
        }

    /**
     * TODO
     *
     * @param constParent
     * @param sClass
     *
     * @return
     */
    public ClassConstant ensureClassConstant(Constant constParent, String sClass)
        {
        switch (constParent.getType())
            {
            case Module:
            case Package:
            case Class:
                // TODO if qualified
            case Method:
                return (ClassConstant) register(new ClassConstant(this, constParent, sClass));

            default:
                throw new IllegalArgumentException("constant type " + constParent.getType()
                        + " is not a Module, Package, Class, or Method");
            }
        }

    public PropertyConstant ensurePropertyConstant(Constant constParent,
            ClassConstant constType,
            String sName)
        {
        // TODO
        return (PropertyConstant) register(new PropertyConstant(this, constParent, constType, sName));
        }

    /**
     * Obtain a Constant that represents the specified method.
     *
     * @param constParent         specifies the module, package, class, method,
     *                            or property that contains the method
     * @param sName               the method name
     * @param aconstGenericParam  the parameters of the genericized method
     * @param aconstInvokeParam   the invocation parameters for the method
     * @param aconstReturnParam   the return values from the method
     *
     * @return the MethodConstant for the specified method name of the specified
     *         container with the specified parameters and return values
     */
    public MethodConstant ensureMethodConstant(Constant constParent, String sName,
            ParameterConstant[] aconstGenericParam,
            ParameterConstant[] aconstInvokeParam,
            ParameterConstant[] aconstReturnParam)
        {
        assert constParent != null;

        switch (constParent.getType())
            {
            case Module:
            case Package:
            case Class:
            case Method:
            case Property:
                return (MethodConstant) register(new MethodConstant(this, constParent,
                        sName, aconstGenericParam, aconstInvokeParam, aconstReturnParam));

            default:
                throw new IllegalArgumentException("constant type " + constParent.getType()
                        + " is not a Module, Package, Class, Method, or Property");
            }
        }

    /**
     * Given the specified type and name, obtain a ParameterConstant that
     * represents it.
     *
     * @param constType  the type of the parameter
     * @param sName      the name of the parameter
     *
     * @return a ParameterConstant
     */
    public ParameterConstant ensureParameterConstant(ClassConstant constType, String sName)
        {
        // note: the parameter constant is NOT registered, because it is not an
        // actual constant type; it is simply a sub-component of a method
        // constant
        return new ParameterConstant(this, constType, sName);
        }


    // ----- XvmStructure operations -------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    protected ConstantPool getConstantPool()
        {
        return this;
        }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        return m_listConst.iterator();
        }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isModified()
        {
        // changes to the constant pool only modify the resulting file if there are changes to other structures that
        // reference the changes in the constant pool; the constants themselves are constant
        return false;
        }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void markModified()
        {
        }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
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
                case Byte:
                    constant = new ByteConstant(this, format, in);
                    break;

                case ByteString:
                    constant = new ByteStringConstant(this, format, in);
                    break;

                case Char:
                    constant = new CharConstant(this, format, in);
                    break;

                case CharString:
                    constant = new CharStringConstant(this, format, in);
                    break;

                case Int:
                    constant = new IntConstant(this, format, in);
                    break;

                case Module:
                    constant = new ModuleConstant(this, format, in);
                    break;

                case Package:
                    constant = new PackageConstant(this, format, in);
                    break;

                case Class:
                    constant = new ClassConstant(this, format, in);
                    break;

                case Property:
                    constant = new PropertyConstant(this, format, in);
                    break;

                case Method:
                    constant = new MethodConstant(this, format, in);
                    break;

                // TODO

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

    /**
     * {@inheritDoc}
     */
    @Override
    protected void registerConstants(ConstantPool pool)
        {
        // the ConstantPool does contain constants, but it does not itself
        // reference any constants, so it has nothing to register itself.
        // furthermore, this must be over-ridden here to avoid the super
        // implementation calling to each of the contained Constants (some of
        // which may no longer be referenced by any XVM Structure) and having
        // them accidentally register everything that they in turn depend
        // upon
        }

    /**
     * {@inheritDoc}
     */
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


    // ----- debugging support -------------------------------------------------

    @Override
    public String getDescription()
        {
        return "size=" + m_listConst.size()
                + ", recurse-reg=" + m_fRecurseReg;
        }

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        dumpStructureCollection(out, sIndent, "Constants", m_listConst);
        }


    // ----- Object methods ----------------------------------------------------

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


    // ----- methods exposed to FileStructure ----------------------------------

    /**
     * Before the registration of constants begins as part of assembling the
     * FileStructure, the ConstantPool is notified of the impending assembly
     * process so that it can determine which constants are actually used, and
     * how many times each is used. This is important because the unused
     * constants can be discarded, and the most frequently used constants can
     * be written out first in the ConstantPool, allowing their ordinal position
     * to be addressed using a smaller number of bytes throughout the
     * FileStructure.
     */
    protected void preRegisterAll()
        {
        assert !m_fRecurseReg;
        m_fRecurseReg = true;

        for (Constant constant : m_listConst)
            {
            constant.resetRefs();
            }
        }

    /**
     * Called after all of the Constants have been registered by the bulk
     * registration process.
     *
     * @param fOptimize pass true to optimize the order of the constants, or
     *                  false to maintain the present order
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
     * Discard unused Constants and order the remaining constants so that the
     * most-referred-to Constants occur before the less used constants.
     */
    private void optimize()
        {
        // sort the Constants by how often they are referred to within the
        // FileStructure, with the most frequently referred-to Constants
        // appearing first
        m_listConst.sort(Constant.MFU_ORDER);

        // go through and mark each constant with its new position; the
        // iteration is backwards to support the efficient removal of all of the
        // unused Constant from the end of the list
        for (int i = m_listConst.size() - 1; i >= 0; --i)
            {
            Constant constant = m_listConst.get(i);
            if (constant.hasRefs())
                {
                if (i != constant.getPosition())
                    {
                    constant.setPosition(i);
                    }
                }
            else
                {
                constant.setPosition(-1);
                m_listConst.remove(i);
                }
            }

        // discard any previous lookup structures, since contents may have
        // changed
        m_mapConstants.clear();
        m_mapLocators.clear();
        }


    // ----- internal ----------------------------------------------------------

    /**
     * Obtain a Constant lookup table for Constants of the specified type, using
     * Constants as the keys of the lookup table.
     * <p>
     * Constants are natural identities, so they act as the keys in this lookup
     * structure. This data structure allows there to be exactly one instance
     * of each Constant identity held by the ConstantPool, similar to how
     * String objects are "interned" in Java.
     *
     * @param type  the Constant Type
     *
     * @return the map from Constant to Constant
     */
    private HashMap<Constant, Constant> ensureConstantLookup(Type type)
        {
        ensureLookup();
        return m_mapConstants.get(type);
        }

    /**
     * Obtain a Constant lookup table for Constants of the specified type, using
     * locators as the keys of the lookup table.
     * <p>
     * Locators are optional identities that are specific to each different
     * Type of Constant:
     * <ul>
     * <li>A Constant Types may not support locators at all;</li>
     * <li>A Constant Types may support locators, but only for some of the
     * Constant values of that Type;</li>
     * <li>A Constant Types may support locators for all of the Constant values
     * of that Type.</li>
     * </ul>
     * The
     *
     * @param type  the Constant Type
     *
     * @return the map from locator to Constant
     */
    private HashMap<Object, Constant> ensureLocatorLookup(Type type)
        {
        final EnumMap<Type, HashMap<Object, Constant>> mapLocatorMaps = m_mapLocators;

        HashMap<Object, Constant> mapLocators = mapLocatorMaps.get(type);
        if (mapLocators == null)
            {
            // lazily instantiate the locator map for the specified type
            mapLocators = new HashMap<>();
            mapLocatorMaps.put(type, mapLocators);
            }

        return mapLocators;
        }

    /**
     * Create the necessary structures for looking up Constant objects quickly,
     * and populate those structures with the set of existing Constant objects.
     */
    private void ensureLookup()
        {
        if (m_mapConstants.isEmpty())
            {
            for (Type type : Type.values())
                {
                m_mapConstants.put(type, new HashMap<>());
                }

            // TODO intrinsics (note: not sure what this comment means)

            for (Constant constant : m_listConst)
                {
                Constant constantOld = m_mapConstants.get(constant.getType()).put(constant, constant);
                assert constantOld == null;

                Object oLocator = constant.getLocator();
                if (oLocator != null)
                    {
                    constantOld = ensureLocatorLookup(constant.getType()).put(oLocator, constant);
                    assert constantOld == null;
                    }
                }
            }
        }


    // ----- inner class: ByteConstant -----------------------------------------

    /**
     * Represent an octet (unsigned 8-bit byte) constant.
     */
    public static class ByteConstant
            extends Constant
        {
        /**
         * Constructor used for deserialization.
         *
         * @param pool    the ConstantPool that will contain this Constant
         * @param format  the format of the Constant in the stream
         * @param in      the DataInput stream to read the Constant value from
         *
         * @throws IOException  if an issue occurs reading the Constant value
         */
        protected ByteConstant(ConstantPool pool, Format format, DataInput in)
                throws IOException
            {
            super(pool);
            m_nVal = in.readUnsignedByte();
            }

        /**
         * Construct a constant whose value is an octet.
         *
         * @param pool  the ConstantPool that will contain this Constant
         * @param bVal  the octet value
         */
        protected ByteConstant(ConstantPool pool, int bVal)
            {
            super(pool);
            m_nVal = bVal & 0xFF;
            }

        @Override
        public Type getType()
            {
            return Type.Byte;
            }

        @Override
        public Format getFormat()
            {
            return Format.Byte;
            }

        @Override
        protected Object getLocator()
            {
            // Byte caches all possible values
            return Byte.valueOf((byte) m_nVal);
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            out.writeByte(getFormat().ordinal());
            out.writeByte(m_nVal);
            }

        @Override
        protected int compareDetails(Constant that)
            {
            return this.m_nVal - ((ByteConstant) that).m_nVal;
            }

        @Override
        public String getValueString()
            {
            return byteToHexString(m_nVal);
            }

        @Override
        public String getDescription()
            {
            return "byte=" + getValueString();
            }

        @Override
        public int hashCode()
            {
            return m_nVal;
            }

        /**
         * Get the value of the constant.
         *
         * @return  the constant's octet value as an <tt>int</tt> in the
         *          range 0 to 255
         */
        public int getValue()
            {
            return m_nVal;
            }

        /**
         * The constant octet value stored as an integer.
         */
        private final int m_nVal;
        }


    // ----- inner class: ByteStringConstant -----------------------------------

    /**
     * Represent an octet string (string of unsigned 8-bit bytes) constant.
     */
    public static class ByteStringConstant
            extends Constant
        {
        /**
         * Constructor used for deserialization.
         *
         * @param pool    the ConstantPool that will contain this Constant
         * @param format  the format of the Constant in the stream
         * @param in      the DataInput stream to read the Constant value from
         *
         * @throws IOException  if an issue occurs reading the Constant value
         */
        protected ByteStringConstant(ConstantPool pool, Format format, DataInput in)
                throws IOException
            {
            super(pool);

            int    cb = readMagnitude(in);
            byte[] ab = new byte[cb];
            in.readFully(ab);
            m_abVal = ab;
            }

        /**
         * Construct a constant whose value is an octet string. Note that this
         * constructor does not make a copy of the passed <tt>byte[]</tt>.
         *
         * @param pool   the ConstantPool that will contain this Constant
         * @param abVal  the octet string value
         */
        protected ByteStringConstant(ConstantPool pool, byte[] abVal)
            {
            super(pool);

            assert abVal != null;
            m_abVal = abVal;
            }

        @Override
        public Type getType()
            {
            return Type.ByteString;
            }

        @Override
        public Format getFormat()
            {
            return Format.ByteString;
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            out.writeByte(getFormat().ordinal());
            final byte[] ab = m_abVal;
            writePackedLong(out, ab.length);
            out.write(ab);
            }

        @Override
        protected int compareDetails(Constant that)
            {
            byte[] abThis = this.m_abVal;
            byte[] abThat = ((ByteStringConstant) that).m_abVal;

            int cbThis  = abThis.length;
            int cbThat  = abThat.length;
            for (int of = 0, cb = Math.min(cbThis, cbThat); of < cb; ++of)
                {
                if (abThis[of] != abThat[of])
                    {
                    return (abThis[of] & 0xFF) - (abThat[of] & 0xFF);
                    }
                }
            return cbThis - cbThat;
            }

        @Override
        public String getValueString()
            {
            return byteArrayToHexString(m_abVal);
            }

        @Override
        public String getDescription()
            {
            return "byte-string=" + getValueString();
            }

        @Override
        public int hashCode()
            {
            int nHash = m_nHash;
            if (nHash == 0)
                {
                byte[] ab = m_abVal;
                nHash = ab.length;
                for (int of = 0, cb = ab.length, cbInc = Math.max(1, cb >>> 6); of < cb; of += cbInc)
                    {
                    nHash *= 19 + ab[of];
                    }
                m_nHash = nHash;
                }
            return nHash;
            }

        /**
         * Get the value of the constant.
         *
         * @return  the constant's octet string value as a <tt>byte[]</tt>; the
         *          caller must treat the value as immutable
         */
        public byte[] getValue()
            {
            return m_abVal;
            }

        /**
         * The constant octet string value stored as a <tt>byte[]</tt>.
         */
        private final byte[] m_abVal;

        /**
         * Cached hash code.
         */
        private transient int m_nHash;
        }


    // ----- inner class: CharConstant -----------------------------------------

    /**
     * Represent a unicode character constant.
     */
    public static class CharConstant
            extends Constant
        {
        /**
         * Constructor used for deserialization.
         *
         * @param pool    the ConstantPool that will contain this Constant
         * @param format  the format of the Constant in the stream
         * @param in      the DataInput stream to read the Constant value from
         *
         * @throws IOException  if an issue occurs reading the Constant value
         */
        protected CharConstant(ConstantPool pool, Format format, DataInput in)
                throws IOException
            {
            super(pool);
            m_chVal = readUtf8Char(in);
            }

        /**
         * Construct a constant whose value is a unicode character.
         *
         * @param pool   the ConstantPool that will contain this Constant
         * @param chVal  the unicode character value
         */
        public CharConstant(ConstantPool pool, int chVal)
            {
            super(pool);
            m_chVal = chVal;
            }

        @Override
        public Type getType()
            {
            return Type.Char;
            }

        @Override
        public Format getFormat()
            {
            return Format.Char;
            }

        @Override
        public Object getLocator()
            {
            // Character only guarantees that the ASCII characters are cached
            return m_chVal <= 0x7F ? Character.valueOf((char) m_chVal) : null;
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            out.writeByte(getFormat().ordinal());
            writeUtf8Char(out, m_chVal);
            }

        @Override
        protected int compareDetails(Constant that)
            {
            int nThis = this.m_chVal;
            int nThat = ((CharConstant) that).m_chVal;
            return nThis - nThat;
            }

        @Override
        public String getValueString()
            {
            return m_chVal > 0xFFFF
                    ? appendIntAsHex(new StringBuilder("\'\\U"), m_chVal).append('\'').toString()
                    : quotedChar((char) m_chVal);
            }

        @Override
        public String getDescription()
            {
            return "char=" + getValueString();
            }

        @Override
        public int hashCode()
            {
            return m_chVal;
            }

        /**
         * Get the value of the constant.
         *
         * @return  the constant's unicode character value as an <tt>int</tt>
         */
        public int getValue()
            {
            return m_chVal;
            }

        /**
         * The constant character code-point value stored as an integer.
         */
        private final int m_chVal;
        }


    // ----- inner class: CharStringConstant -----------------------------------

    /**
     * Represent an XVM char string (string of unicode characters) constant.
     */
    public static class CharStringConstant
            extends Constant
        {
        /**
         * Constructor used for deserialization.
         *
         * @param pool    the ConstantPool that will contain this Constant
         * @param format  the format of the Constant in the stream
         * @param in      the DataInput stream to read the Constant value from
         *
         * @throws IOException  if an issue occurs reading the Constant value
         */
        protected CharStringConstant(ConstantPool pool, Format format, DataInput in)
                throws IOException
            {
            super(pool);
            m_sVal = readUtf8String(in);
            }

        /**
         * Construct a constant whose value is a char string.
         *
         * @param pool  the ConstantPool that will contain this Constant
         * @param sVal  the char string value
         */
        protected CharStringConstant(ConstantPool pool, String sVal)
            {
            super(pool);

            assert sVal != null;
            m_sVal = sVal;
            }

        @Override
        public Type getType()
            {
            return Type.CharString;
            }

        @Override
        public Format getFormat()
            {
            return Format.CharString;
            }

        @Override
        public Object getLocator()
            {
            return m_sVal;
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            out.writeByte(getFormat().ordinal());
            writeUtf8String(out, m_sVal);
            }

        @Override
        protected int compareDetails(Constant that)
            {
            return this.m_sVal.compareTo(((CharStringConstant) that).m_sVal);
            }

        @Override
        public String getValueString()
            {
            return quotedString(m_sVal);
            }

        @Override
        public String getDescription()
            {
            return "char-string=" + getValueString();
            }

        @Override
        public int hashCode()
            {
            return m_sVal.hashCode();
            }

        /**
         * Get the value of the constant.
         *
         * @return the constant's char string value as a <tt>String</tt>
         */
        public String getValue()
            {
            return m_sVal;
            }

        /**
         * The constant octet string value stored as a <tt>byte[]</tt>.
         */
        private String m_sVal;
        }


    // ----- inner class: IntConstant ------------------------------------------

    /**
     * Represent an integer constant, signed or unsigned, from 8 through 256
     * bits (in powers of 2).
     */
    public static class IntConstant
            extends Constant
        {
        /**
         * Constructor used for deserialization.
         *
         * @param pool    the ConstantPool that will contain this Constant
         * @param format  the format of the Constant in the stream
         * @param in      the DataInput stream to read the Constant value from
         *
         * @throws IOException  if an issue occurs reading the Constant value
         */
        protected IntConstant(ConstantPool pool, Format format, DataInput in)
                throws IOException
            {
            super(pool);
            m_pint = new PackedInteger(in);
            }

        /**
         * Construct a constant whose value is a PackedInteger.
         *
         * @param pool  the ConstantPool that will contain this Constant
         * @param pint  the PackedInteger value
         */
        protected IntConstant(ConstantPool pool, PackedInteger pint)
            {
            super(pool);
            pint.verifyInitialized();
            m_pint = pint;
            }

        @Override
        public Type getType()
            {
            return Type.Int;
            }

        @Override
        public Format getFormat()
            {
            return Format.Int;
            }

        @Override
        public Object getLocator()
            {
            return m_pint;
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            out.writeByte(getFormat().ordinal());
            m_pint.writeObject(out);
            }

        @Override
        protected int compareDetails(Constant that)
            {
            return this.m_pint.compareTo(((IntConstant) that).m_pint);
            }

        @Override
        public String getValueString()
            {
            return m_pint.toString();
            }

        @Override
        public String getDescription()
            {
            return "int=" + getValueString();
            }

        @Override
        public int hashCode()
            {
            return m_pint.hashCode();
            }

        /**
         * Get the value of the constant.
         *
         * @return  the constant's PackedInteger value
         */
        public PackedInteger getValue()
            {
            return m_pint;
            }

        /**
         * The constant integer value stored as a PackedInteger.
         */
        private final PackedInteger m_pint;
        }


    // ----- inner class: IntParmedConstant ------------------------------------

    // REVIEW get rid of this?
    /**
     * Represent an integer constant of a particular class, which is to say of a
     * specific range (min/max value).
     */
//    public class IntParmedConstant
//            extends IntConstant
//        {
//        // TODO location of the constant for the class of this int
//        // TODO reference to the constant for the class of this int
//        }


    // ----- inner class: VersionConstant --------------------------------------

    /**
     * TODO update comment
     * Represent a version number. A version is either a base version, the
     * subsequent version of another version, or an revision of another version.
     * A version number is represented as a dot-delimited string of integer
     * values; for example, version "1" is a potential base version number,
     * version "2" is a subsequent version of version "1", and version "1.1" is
     * a revision of version 1.
     * <p>
     * For each integer in the version string, the first integer is considered
     * the most significant version indicator, and each following integer is
     * less significant, with the last integer being the least significant
     * version indicator. If the least significant version indicator is zero,
     * then the version is identical to a version that does not include that
     * least significant version indicator; in other words, version "1", version
     * "1.0", and version "1.0.0" (etc.) all refer to the same identical
     * version. For purposes of comparison:
     * <ul><li>The actual versions <tt>v<sub>A</sub></tt> is <b>identical to</b>
     * the requested version <tt>v<sub>R</sub></tt> iff after removing every
     * trailing (least significant) "0" indicator, each version indicator
     * from the most significant to the least significant is identical; in other
     * words, version "1.2.1" is identical only to version "1.2.1" (which is
     * identical to version "1.2.1.0").</li>
     * <li>The actual versions <tt>v<sub>A</sub></tt> is <b>substitutable
     * for</b> the requested version <tt>v<sub>R</sub></tt> iff each version
     * indicator of the requested version from the most significant to the least
     * significant is identical to the corresponding version indicator in the
     * actual version, or if the first different version indicator in the actual
     * version is greater than the corresponding version indicator in the
     * requested version; in other words, version "1.2", "1.2.1", and "1.2.1.7",
     * "1.3", "2.0", and "2.1" are all substitutable for version "1.2".</li>
     * <li>In the previous example, to use only one of the versions that begins
     * with "1.2", the requested version <tt>v<sub>R</sub></tt> should be
     * specified as "1.2.0"; versions "1.2", "1.2.1", and "1.2.1.7" are
     * subsitutes for 1.2.0, but versions "1.3", "2.0", and "2.1" are not.</li>
     * </ul>
     */
    public static class VersionConstant
            extends Constant
        {
        /**
         * Constructor used for deserialization.
         *
         * @param pool    the ConstantPool that will contain this Constant
         * @param format  the format of the Constant in the stream
         * @param in      the DataInput stream to read the Constant value from
         *
         * @throws IOException  if an issue occurs reading the Constant value
         */
        protected VersionConstant(ConstantPool pool, Format format, DataInput in)
                throws IOException
            {
            super(pool);
            m_iVer = readIndex(in);
            }

        /**
         * Construct a constant whose value is a PackedInteger.
         *
         * @param pool  the ConstantPool that will contain this Constant
         * @param ver   the version
         */
        protected VersionConstant(ConstantPool pool, Version ver)
            {
            super(pool);

            assert ver != null;
            m_ver = ver;
            }

        @Override
        public Type getType()
            {
            return Type.Version;
            }

        @Override
        public Format getFormat()
            {
            return Format.Version;
            }

        @Override
        public Object getLocator()
            {
            return m_ver.toString();
            }

        @Override
        protected void disassemble(DataInput in)
                throws IOException
            {
            final ConstantPool pool = getConstantPool();
            m_ver = new Version(((CharStringConstant) pool.getConstant(m_iVer)).getValue());
            }

        @Override
        protected void registerConstants(ConstantPool pool)
            {
            pool.register(pool.ensureCharStringConstant(m_ver.toString()));
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            out.writeByte(getFormat().ordinal());
            writePackedLong(out, getConstantPool().ensureCharStringConstant(m_ver.toString()).getPosition());
            }

        @Override
        protected int compareDetails(Constant that)
            {
            return this.m_ver.compareTo(((VersionConstant) that).m_ver);
            }

        @Override
        public String getValueString()
            {
            return m_ver.toString();
            }

        @Override
        public String getDescription()
            {
            return "version=" + getValueString();
            }

        @Override
        public int hashCode()
            {
            return m_ver.hashCode();
            }

        /**
         * Determine the dot-delimited version number string.
         *
         * @return the fully qualified version number
         */
        public Version getVersion()
            {
            return m_ver;
            }

        /**
         * During disassembly, this holds the index of the constant that
         * specifies the version String for this version.
         */
        private int m_iVer;

        /**
         * The version indicator for this version.
         */
        private Version m_ver;
        }


    // ----- inner class: ConditionalConstant ----------------------------------

    /**
     * Represents a condition that can be evaluated at link-time. Conditional
     * constants are used to encode boolean expressions into VM structures
     * themselves, allowing structures and portions of structures to be
     * conditionally present at runtime. This has the net effect of supporting
     * the same "conditional compilation" as a pre-processor would provide for
     * a language such as C/C++, but instead of compiling only one "path"
     * through the specified conditionals, XTC compiles all of the paths,
     * verifying that every single path meets the requirements of the language
     * and the dependencies implied by (and deferred to) link-time. Among other
     * purposes, this allows XTC code to be tailored to the presence (or
     * absence) of a specific library, such as only implementing a
     * library-specific interface and only consuming library functionality if
     * that particular library is present. Additionally, multiple versions of
     * VM structures can be combined into a single VM structure (for example,
     * multiple versions of a module can be combined into a single module), by
     * using version conditions to delineate gthe differences among versions.
     * <p>
     * Structural inclusion/exclusion occurs when a conditional constant is
     * referenced by another VM structure, indicating that the presence at
     * runtime of the VM structure depends on the result of the evaluation of
     * the conditional constant. Similarly, logical inclusion/exclusion occurs
     * when a conditional constant is referenced by an XTC op-code, indicating
     * that the presence at runtime of that particular block of code depends on
     * the result of the evaluation of the conditional constant.
     * <p>
     * Three basic conditional constants exist to test for specific
     * <ul>
     * <li>{@link NamedCondition NamedCondition} - similar in concept to the
     *     use of <tt>#ifdef</tt> in the C/C++ pre-processor, a NamedCondition
     *     evaluates to true iff the specified name is defined;</li>
     * <li>{@link PresentCondition PresentCondition} - evaluates to true iff
     *     the specified VM structure (and optionally a particular version of
     *     that VM structure) is present at runtime, allowing optional
     *     dependencies to be supported;</li>
     * <li>{@link VersionCondition VersionCondition} - evaluates to true iff the
     *     version of this module is of a specified version.</li>
     * </ul>
     * <p>
     * Four additional conditional constants support the composition of
     * other conditions:
     * <ul>
     * <li>{@link NotCondition NotCondition} - evaluates to true iff the
     *     specified condition evaluates to false, i.e. a "not" condition;</li>
     * <li>{@link AllCondition AllCondition} - evaluates to true iff each of the
     *     specified conditions evaluate to true, i.e. an "and" condition;</li>
     * <li>{@link AnyCondition AnyCondition} - evaluates to false iff each of
     *     the specified conditions evaluate to false, i.e. an "or"
     *     condition;</li>
     * <li>{@link Only1Condition Only1Condition} - evaluates to true iff
     *     exactly one of the specified conditions evaluates to true, i.e. a
     *     "xor" condition;</li>
     * </ul>
     */
    public abstract static class ConditionalConstant
            extends Constant
        {
        /**
         * Construct a ConditionalConstant.
         *
         * @param pool the ConstantPool that will contain this Constant
         */
        protected ConditionalConstant(ConstantPool pool)
            {
            super(pool);
            }

        @Override
        public Type getType()
            {
            return Type.Condition;
            }

        @Override
        public String getDescription()
            {
            return "condition=" + getValueString();
            }

        /**
         * Evaluate this condition for inclusion in a container whose context
         * is
         * provided.
         *
         * @param ctx the context of the container being created
         *
         * @return true whether this condition is met in the container
         */
        public abstract boolean evaluate(LinkerContext ctx);

        /**
         * Implements the logical "not" of a condition.
         */
        public static class NotCondition
                extends ConditionalConstant
            {
            /**
             * Constructor used for deserialization.
             *
             * @param pool    the ConstantPool that will contain this Constant
             * @param format  the format of the Constant in the stream
             * @param in      the DataInput stream to read the Constant value
             *                from
             *
             * @throws IOException  if an issue occurs reading the Constant
             *                      value
             */
            protected NotCondition(ConstantPool pool, Format format, DataInput in)
                    throws IOException
                {
                super(pool);
                m_iCond = readMagnitude(in);
                }

            /**
             * Construct a NotCondition.
             *
             * @param pool       the ConstantPool that will contain this
             *                   Constant
             * @param constCond  the underlying condition to evaluate
             */
            protected NotCondition(ConstantPool pool, ConditionalConstant constCond)
                {
                super(pool);

                if (constCond == null)
                    {
                    throw new IllegalArgumentException("condition required");
                    }

                m_constCond = constCond;
                }

            @Override
            public Format getFormat()
                {
                return Format.ConditionNot;
                }

            @Override
            protected void disassemble(DataInput in)
                    throws IOException
                {
                m_constCond = (ConditionalConstant) getConstantPool().getConstant(m_iCond);
                }

            @Override
            protected void registerConstants(ConstantPool pool)
                {
                m_constCond = (ConditionalConstant) pool.register(m_constCond);
                }

            @Override
            protected void assemble(DataOutput out)
                    throws IOException
                {
                out.writeByte(getFormat().ordinal());

                writePackedLong(out, m_constCond.getPosition());
                }

            @Override
            protected int compareDetails(Constant that)
                {
                return m_constCond.compareTo(((NotCondition) that).m_constCond);
                }

            @Override
            public String getValueString()
                {
                return "!" + m_constCond.getValueString();
                }

            @Override
            public int hashCode()
                {
                return Handy.hashCode(m_constCond);
                }

            @Override
            public boolean evaluate(LinkerContext ctx)
                {
                return !m_constCond.evaluate(ctx);
                }

            /**
             * During disassembly, this holds the index of the underlying
             * condition.
             */
            private transient int m_iCond;

            /**
             * The underlying condition to evaluate.
             */
            private ConditionalConstant m_constCond;
            }


        /**
         * Implements the logical "and" of any number of conditions.
         */
        public abstract static class MultiCondition
                extends ConditionalConstant
            {
            /**
             * Constructor used for deserialization.
             *
             * @param pool    the ConstantPool that will contain this Constant
             * @param format  the format of the Constant in the stream
             * @param in      the DataInput stream to read the Constant value
             *                from
             *
             * @throws IOException  if an issue occurs reading the Constant
             *                      value
             */
            protected MultiCondition(ConstantPool pool, Format format, DataInput in)
                    throws IOException
                {
                super(pool);

                final int c = readMagnitude(in);
                if (c < 1 || c > 1000)
                    {
                    throw new IllegalStateException("# conditions=" + c);
                    }

                int[] ai = new int[c];
                for (int i = 0; i < c; ++i)
                    {
                    ai[i] = readMagnitude(in);
                    }

                m_aiCond = ai;
                }

            /**
             * Construct a MultiCondition.
             *
             * @param pool        the ConstantPool that will contain this
             *                    Constant
             * @param aconstCond  an array of underlying conditions to evaluate
             */
            protected MultiCondition(ConstantPool pool, ConditionalConstant[] aconstCond)
                {
                super(pool);

                if (aconstCond == null)
                    {
                    throw new IllegalArgumentException("conditions required");
                    }

                final int c = aconstCond.length;
                if (c < 1 || c > 1000)
                    {
                    throw new IllegalArgumentException("# conditions: " + c);
                    }

                for (int i = 0; i < c; ++i)
                    {
                    if (aconstCond[i] == null)
                        {
                        throw new IllegalArgumentException("condition " + i + " required");
                        }
                    }

                m_aconstCond = aconstCond;
                }

            @Override
            protected void disassemble(DataInput in)
                    throws IOException
                {
                final int[] ai = m_aiCond;
                final int c = ai.length;
                final ConditionalConstant[] aconstCond = new ConditionalConstant[c];

                if (c > 0)
                    {
                    final ConstantPool pool = getConstantPool();
                    for (int i = 0; i < c; ++i)
                        {
                        aconstCond[i] = (ConditionalConstant) pool.getConstant(ai[i]);
                        }
                    }

                m_aconstCond = aconstCond;
                }

            @Override
            protected void registerConstants(ConstantPool pool)
                {
                final ConditionalConstant[] aconstCond = m_aconstCond;
                for (int i = 0, c = aconstCond.length; i < c; ++i)
                    {
                    aconstCond[i] = (ConditionalConstant) pool.register(aconstCond[i]);
                    }
                }

            @Override
            protected void assemble(DataOutput out)
                    throws IOException
                {
                out.writeByte(getFormat().ordinal());

                final ConditionalConstant[] aconstCond = m_aconstCond;

                final int c = aconstCond.length;
                writePackedLong(out, c);

                for (int i = 0; i < c; ++i)
                    {
                    writePackedLong(out, aconstCond[i].getPosition());
                    }
                }

            @Override
            protected int compareDetails(Constant that)
                {
                return Handy.compareArrays(m_aconstCond, ((MultiCondition) that).m_aconstCond);
                }

            /**
             * @return the operator represented by this condition
             */
            protected abstract String getOperatorString();

            @Override
            public String getValueString()
                {
                final ConditionalConstant[] aconstCond = m_aconstCond;

                StringBuilder sb = new StringBuilder();
                sb.append('(')
                  .append(m_aconstCond[0].getValueString());

                for (int i = 1, c = aconstCond.length; i < c; ++i)
                    {
                    sb.append(' ')
                      .append(getOperatorString())
                      .append(' ')
                      .append(aconstCond[i].getValueString());
                    }

                return sb.append(')').toString();
                }

            @Override
            public int hashCode()
                {
                int nHash = m_nHash;
                if (nHash == 0)
                    {
                    m_nHash = nHash = getOperatorString().hashCode() ^ Handy.hashCode(m_aconstCond);
                    }
                return nHash;
                }

            /**
             * During disassembly, this holds the indexes of the underlying
             * conditions.
             */
            private int[] m_aiCond;

            /**
             * The underlying conditions to evaluate.
             */
            protected ConditionalConstant[] m_aconstCond;

            /**
             * Cached hash value.
             */
            private transient int m_nHash;
            }


        /**
         * Implements the logical "and" of any number of conditions.
         */
        public static class AllCondition
                extends MultiCondition
            {
            /**
             * Constructor used for deserialization.
             *
             * @param pool    the ConstantPool that will contain this Constant
             * @param format  the format of the Constant in the stream
             * @param in      the DataInput stream to read the Constant value
             *                from
             *
             * @throws IOException  if an issue occurs reading the Constant
             *                      value
             */
            protected AllCondition(ConstantPool pool, Format format, DataInput in)
                    throws IOException
                {
                super(pool, format, in);
                }

            /**
             * Construct an AllCondition.
             *
             * @param pool        the ConstantPool that will contain this
             *                    Constant
             * @param aconstCond  an array of underlying conditions to evaluate
             */
            protected AllCondition(ConstantPool pool, ConditionalConstant[] aconstCond)
                {
                super(pool, aconstCond);
                }

            @Override
            public Format getFormat()
                {
                return Format.ConditionAll;
                }

            @Override
            protected String getOperatorString()
                {
                return "&&";
                }

            @Override
            public boolean evaluate(LinkerContext ctx)
                {
                for (ConditionalConstant constCond : m_aconstCond)
                    {
                    if (!constCond.evaluate(ctx))
                        {
                        return false;
                        }
                    }
                return true;
                }
            }


        /**
         * Implements the logical "or" of any number of conditions.
         */
        public static class AnyCondition
                extends MultiCondition
            {
            /**
             * Constructor used for deserialization.
             *
             * @param pool    the ConstantPool that will contain this Constant
             * @param format  the format of the Constant in the stream
             * @param in      the DataInput stream to read the Constant value
             *                from
             *
             * @throws IOException  if an issue occurs reading the Constant
             *                      value
             */
            protected AnyCondition(ConstantPool pool, Format format, DataInput in)
                    throws IOException
                {
                super(pool, format, in);
                }

            /**
             * Construct an AnyCondition.
             *
             * @param pool        the ConstantPool that will contain this
             *                    Constant
             * @param aconstCond  an array of underlying conditions to evaluate
             */
            protected AnyCondition(ConstantPool pool, ConditionalConstant[] aconstCond)
                {
                super(pool, aconstCond);
                }

            @Override
            public Format getFormat()
                {
                return Format.ConditionAny;
                }

            @Override
            protected String getOperatorString()
                {
                return "||";
                }

            @Override
            public boolean evaluate(LinkerContext ctx)
                {
                for (ConditionalConstant constCond : m_aconstCond)
                    {
                    if (constCond.evaluate(ctx))
                        {
                        return true;
                        }
                    }
                return false;
                }
            }


        /**
         * Implements the logical "exclusive or" of any number of conditions.
         */
        public static class Only1Condition
                extends MultiCondition
            {
            /**
             * Constructor used for deserialization.
             *
             * @param pool    the ConstantPool that will contain this Constant
             * @param format  the format of the Constant in the stream
             * @param in      the DataInput stream to read the Constant value
             *                from
             *
             * @throws IOException  if an issue occurs reading the Constant
             *                      value
             */
            protected Only1Condition(ConstantPool pool, Format format, DataInput in)
                    throws IOException
                {
                super(pool, format, in);
                }

            /**
             * Construct an Only1Condition.
             *
             * @param pool        the ConstantPool that will contain this
             *                    Constant
             * @param aconstCond  an array of underlying conditions to evaluate
             */
            protected Only1Condition(ConstantPool pool, ConditionalConstant[] aconstCond)
                {
                super(pool, aconstCond);
                }

            @Override
            public Format getFormat()
                {
                return Format.ConditionOnly1;
                }

            @Override
            protected String getOperatorString()
                {
                return "^";
                }

            @Override
            public boolean evaluate(LinkerContext ctx)
                {
                boolean fAny = false;
                for (ConditionalConstant constCond : m_aconstCond)
                    {
                    if (constCond.evaluate(ctx))
                        {
                        if (fAny)
                            {
                            return false;
                            }
                        fAny = true;
                        }
                    }
                return fAny;
                }
            }


        /**
         * Evaluates if a named condition is defined.
         */
        public static class NamedCondition
                extends ConditionalConstant
            {
            /**
             * Constructor used for deserialization.
             *
             * @param pool    the ConstantPool that will contain this Constant
             * @param format  the format of the Constant in the stream
             * @param in      the DataInput stream to read the Constant value
             *                from
             *
             * @throws IOException  if an issue occurs reading the Constant
             *                      value
             */
            protected NamedCondition(ConstantPool pool, Format format, DataInput in)
                    throws IOException
                {
                super(pool);
                m_iName = readMagnitude(in);
                }

            /**
             * Construct a NotCondition.
             *
             * @param pool       the ConstantPool that will contain this
             *                   Constant
             * @param constName  specifies the named condition; it is simply a
             *                   name that is either defined or undefined
             */
            protected NamedCondition(ConstantPool pool, CharStringConstant constName)
                {
                super(pool);
                assert constName != null;
                m_constName = constName;
                }

            @Override
            public Format getFormat()
                {
                return Format.ConditionNamed;
                }

            @Override
            protected void disassemble(DataInput in)
                    throws IOException
                {
                m_constName = (CharStringConstant) getConstantPool().getConstant(m_iName);
                }

            @Override
            protected void registerConstants(ConstantPool pool)
                {
                m_constName = (CharStringConstant) pool.register(m_constName);
                }

            @Override
            protected void assemble(DataOutput out)
                    throws IOException
                {
                out.writeByte(getFormat().ordinal());
                writePackedLong(out, m_constName.getPosition());
                }

            @Override
            protected int compareDetails(Constant that)
                {
                return getName().compareTo(((NamedCondition) that).getName());
                }

            @Override
            public String getValueString()
                {
                return "isSpecified(\"" + getName() + "\")";
                }

            @Override
            public int hashCode()
                {
                return Handy.hashCode(m_constName);
                }

            @Override
            public boolean evaluate(LinkerContext ctx)
                {
                return ctx.isSpecified(m_constName.getValue());
                }

            /**
             * Obtain the name of the option represented by this conditional.
             *
             * @return the name of the option that is either defined in the
             *         container or not
             */
            public String getName()
                {
                return m_constName.getValue();
                }

            /**
             * During disassembly, this holds the index of the name.
             */
            private transient int m_iName;

            /**
             * The constant representing the name of this named condition.
             */
            private CharStringConstant m_constName;
            }


        /**
         * Evaluates if a specified VM structure will be available in the
         * container.
         */
        public static class PresentCondition
                extends ConditionalConstant
            {
            /**
             * Constructor used for deserialization.
             *
             * @param pool    the ConstantPool that will contain this Constant
             * @param format  the format of the Constant in the stream
             * @param in      the DataInput stream to read the Constant value
             *                from
             *
             * @throws IOException  if an issue occurs reading the Constant
             *                      value
             */
            protected PresentCondition(ConstantPool pool, Format format, DataInput in)
                    throws IOException
                {
                super(pool);
                m_iStruct   = readMagnitude(in);
                m_iVer      = readIndex(in);
                m_fExactVer = in.readBoolean();
                }

            /**
             * Construct a PresentCondition.
             *
             * @param pool           the ConstantPool that will contain this
             *                       Constant
             * @param constVMStruct  the Module, Package, Class, Property or
             *                       Method
             * @param constVer       the optional specific version of the
             *                       Module, Package, Class, Property or
             *                       Method
             * @param fExactVer      true if the version has to match exactly
             */
            protected PresentCondition(ConstantPool pool, Constant constVMStruct,
                    VersionConstant constVer, boolean fExactVer)
                {
                super(pool);
                m_constStruct = constVMStruct;
                m_constVer    = constVer;
                m_fExactVer   = fExactVer;
                }

            @Override
            public Format getFormat()
                {
                return Format.ConditionPresent;
                }

            @Override
            protected void disassemble(DataInput in)
                    throws IOException
                {
                final ConstantPool pool = getConstantPool();

                m_constStruct = pool.getConstant(m_iStruct);
                assert m_constStruct instanceof ModuleConstant
                        || m_constStruct instanceof PropertyConstant
                        || m_constStruct instanceof ClassConstant
                        || m_constStruct instanceof PropertyConstant
                        || m_constStruct instanceof MethodConstant;

                final int iVer = m_iVer;
                if (iVer >= 0)
                    {
                    m_constVer = (VersionConstant) pool.getConstant(iVer);
                    }
                }

            @Override
            protected void registerConstants(ConstantPool pool)
                {
                m_constStruct = pool.register(m_constStruct);
                m_constVer = (VersionConstant) pool.register(m_constVer);
                }

            @Override
            protected void assemble(DataOutput out)
                    throws IOException
                {
                out.writeByte(getFormat().ordinal());
                writePackedLong(out, m_constStruct.getPosition());
                writePackedLong(out, m_constVer == null ? -1 : m_constVer.getPosition());
                out.writeBoolean(m_fExactVer);
                }

            @Override
            protected int compareDetails(Constant that)
                {
                PresentCondition constThat = (PresentCondition) that;
                int nResult = m_constStruct.compareTo(constThat.m_constStruct);
                if (nResult == 0)
                    {
                    nResult = Handy.compareObjects(m_constVer, constThat.m_constVer);
                    if (nResult == 0)
                        {
                        nResult = Boolean.valueOf(m_fExactVer).compareTo(Boolean.valueOf(constThat.m_fExactVer));
                        }
                    }

                return nResult;
                }

            @Override
            public String getValueString()
                {
                StringBuilder sb = new StringBuilder();

                sb.append("isPresent(")
                  .append(m_constStruct);

                final VersionConstant constVer = m_constVer;
                if (constVer != null)
                    {
                    sb.append(", ")
                      .append(m_constVer.getValueString());

                    if (m_fExactVer)
                        {
                        sb.append(", EXACT");
                        }
                    }

                return  sb.append(')').toString();
                }

            @Override
            public int hashCode()
                {
                return Handy.hashCode(m_constStruct) ^ Handy.hashCode(m_constVer);
                }

            @Override
            public boolean evaluate(LinkerContext ctx)
                {
                final VersionConstant constVer = m_constVer;
                return constVer == null
                        ? ctx.isVisible(m_constStruct)
                        : ctx.isVisible(m_constStruct, m_constVer, m_fExactVer);
                }

            /**
             * Obtain the constant of the XVM Structure that this conditional
             * constant represents the conditional presence of.
             *
             * @return the constant representing the XVM Structure to be tested
             *         for
             */
            public Constant getPresentConstant()
                {
                return m_constStruct;
                }

            /**
             * Obtain the version of the XVM Structure that must exist, or null
             * if the test does not evaluate the version.
             *
             * @return the version that is required, or null if any version is
             *         acceptable
             */
            public VersionConstant getVersionConstant()
                {
                return m_constVer;
                }

            /**
             * Determine if the exact specified version is required, or if
             * subsequent versions are acceptable.
             *
             * @return true if the exact version specified is required
             */
            public boolean isExactVersion()
                {
                return m_fExactVer;
                }

            /**
             * During disassembly, this holds the index of the structure id.
             */
            private transient int m_iStruct;

            /**
             * During disassembly, this holds the index of the version or -1.
             */
            private transient int m_iVer;

            /**
             * A ModuleConstant, PackageConstant, ClassConstant,
             * PropertyConstant, or MethodConstant.
             */
            private Constant m_constStruct;

            /**
             * The optional version identifier for the VMStructure that must be
             * present.
             */
            private VersionConstant m_constVer;

            /**
             * True if the version has to match exactly.
             */
            private boolean m_fExactVer;
            }


        /**
         * Evaluates if the module is of a specified version. The
         * VersionCondition applies to (tests for) the version of the current
         * module only; in other words, the VersionConsant is used to
         * conditionally include or exclude VMStructures within <b>this</b>
         * module based on the version of <b>this</b> module. This allows
         * multiple versions of a module to be colocated within a single
         * FileStructure, for example.
         * <p>
         * To evaluate if another module (or component thereof) is of a
         * specified version, a {@link ConditionalConstant.PresentCondition}
         * is used.
         */
        public static class VersionCondition
                extends ConditionalConstant
            {
            /**
             * Constructor used for deserialization.
             *
             * @param pool    the ConstantPool that will contain this Constant
             * @param format  the format of the Constant in the stream
             * @param in      the DataInput stream to read the Constant value
             *                from
             *
             * @throws IOException  if an issue occurs reading the Constant
             *                      value
             */
            protected VersionCondition(ConstantPool pool, Format format, DataInput in)
                    throws IOException
                {
                super(pool);
                m_iVer      = readMagnitude(in);
                m_fExactVer = in.readBoolean();
                }

            /**
             * Construct a VersionCondition.
             *
             * @param pool       the ConstantPool that will contain this
             *                   Constant
             * @param constVer   the version of this Module to evaluate
             * @param fExactVer  true if the version has to match exactly
             */
            protected VersionCondition(ConstantPool pool, VersionConstant constVer, boolean fExactVer)
                {
                super(pool);
                m_constVer  = constVer;
                m_fExactVer = fExactVer;
                }

            @Override
            public Format getFormat()
                {
                return Format.ConditionVersion;
                }

            @Override
            protected void disassemble(DataInput in)
                    throws IOException
                {
                m_constVer = (VersionConstant) getConstantPool().getConstant(m_iVer);
                }

            @Override
            protected void registerConstants(ConstantPool pool)
                {
                m_constVer = (VersionConstant) pool.register(m_constVer);
                }

            @Override
            protected void assemble(DataOutput out)
                    throws IOException
                {
                out.writeByte(getFormat().ordinal());
                writePackedLong(out, m_constVer.getPosition());
                out.writeBoolean(m_fExactVer);
                }

            @Override
            protected int compareDetails(Constant that)
                {
                VersionCondition constThat = (VersionCondition) that;
                int nResult = m_constVer.compareTo(constThat.m_constVer);
                if (nResult == 0)
                    {
                    nResult = Boolean.valueOf(m_fExactVer).compareTo(Boolean.valueOf(constThat.m_fExactVer));
                    }

                return nResult;
                }

            @Override
            public String getValueString()
                {
                StringBuilder sb = new StringBuilder();

                sb.append("isVersion(")
                  .append(m_constVer.getValueString());

                if (m_fExactVer)
                    {
                    sb.append(", EXACT");
                    }

                return sb.append(')').toString();
                }

            @Override
            public int hashCode()
                {
                return Handy.hashCode(m_constVer);
                }

            @Override
            public boolean evaluate(LinkerContext ctx)
                {
                return ctx.isVersionMatch(m_constVer, m_fExactVer);
                }

            /**
             * Obtain the version of the current module that the condition is
             * predicated on.
             *
             * @return the version that is required
             */
            public VersionConstant getVersionConstant()
                {
                return m_constVer;
                }

            /**
             * Determine if the exact specified version is required, or if
             * subsequent versions are acceptable.
             *
             * @return true if the exact version specified is required
             */
            public boolean isExactVersion()
                {
                return m_fExactVer;
                }

            /**
             * During disassembly, this holds the index of the version or -1.
             */
            private transient int m_iVer;

            /**
             * A ModuleConstant, PackageConstant, ClassConstant,
             * PropertyConstant, or MethodConstant.
             */
            private VersionConstant m_constVer;

            /**
             * True if the version has to match exactly.
             */
            private boolean m_fExactVer;
            }
        }


    // ----- inner class: ModuleConstant ---------------------------------------

    /**
     * Represent a Module constant. A Module constant is composed of a qualified
     * module name, which itself is composed of a domain name and an unqualified
     * (simple) module name. For example, the domain name "xtclang.org" can be
     * combined with the simple module name "core" to create a qualified module
     * name of "xtclang.org.core".
     */
    public static class ModuleConstant
            extends Constant
        {
        /**
         * Constructor used for deserialization.
         *
         * @param pool    the ConstantPool that will contain this Constant
         * @param format  the format of the Constant in the stream
         * @param in      the DataInput stream to read the Constant value from
         *
         * @throws IOException  if an issue occurs reading the Constant value
         */
        protected ModuleConstant(ConstantPool pool, Format format, DataInput in)
                throws IOException
            {
            super(pool);
            m_iName = readMagnitude(in);
            }

        /**
         * Construct a constant whose value is a module identifier.
         *
         * @param pool     the ConstantPool that will contain this Constant
         * @param sName    the qualified module name
         */
        protected ModuleConstant(ConstantPool pool, String sName)
            {
            super(pool);

            m_constName = pool.ensureCharStringConstant(sName);
            }

        @Override
        public Type getType()
            {
            return Type.Module;
            }

        @Override
        public Format getFormat()
            {
            return Format.Module;
            }

        @Override
        public Object getLocator()
            {
            return m_constName.getLocator();
            }

        @Override
        protected void disassemble(DataInput in)
                throws IOException
            {
            m_constName = (CharStringConstant) getConstantPool().getConstant(m_iName);
            }

        @Override
        protected void registerConstants(ConstantPool pool)
            {
            m_constName = (CharStringConstant) pool.register(m_constName);
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            out.writeByte(getFormat().ordinal());
            writePackedLong(out, m_constName.getPosition());
            }

        @Override
        protected ModuleStructure instantiate(XvmStructure xsParent)
            {
            return new ModuleStructure(xsParent, this);
            }

        @Override
        protected int compareDetails(Constant that)
            {
            return this.m_constName.compareTo(((ModuleConstant) that).m_constName);
            }

        @Override
        public String getValueString()
            {
            return m_constName.getValue();
            }

        @Override
        public String getDescription()
            {
            return "module=" + getValueString();
            }

        @Override
        public int hashCode()
            {
            return m_constName.hashCode();
            }

        /**
         * Get the qualified name of the Module.
         * <p>
         * The qualified name for the module is constructed by combining the
         * unqualified module name, a separating '.', and the domain name.
         *
         * @return the qualified Module name
         */
        public String getQualifiedName()
            {
            return m_constName.getValue();
            }

        /**
         * Extract the unqualified name of the module.
         *
         * @return the unqualified module name
         */
        public String getUnqualifiedName()
            {
            String sName = getQualifiedName();
            int ofDot = sName.indexOf('.');
            return ofDot < 0 ? sName : sName.substring(0, ofDot);
            }

        /**
         * Get the domain name for the Module constant.
         *
         * @return the constant's domain information as a <tt>String</tt>, or
         *         <tt>null</tt> if the module name is not qualified (i.e. does
         *         not contain a domain name)
         */
        public String getDomainName()
            {
            String sName = getQualifiedName();
            int ofDot = sName.indexOf('.');
            return ofDot < 0 ? null : sName.substring(ofDot + 1);
            }

        /**
         * Determine if this ModuleConstant is the Ecstasy core module.
         *
         * @return true iff this ModuleConstant represents the module
         *         containing the Ecstasy class library
         */
        public boolean isEcstasyModule()
            {
            return getQualifiedName().equals(ECSTASY_MODULE);
            }

        /**
         * During disassembly, this holds the index of the constant that
         * specifies the name.
         */
        private int m_iName;

        /**
         * The constant that holds the qualified name of the module.
         */
        private CharStringConstant m_constName;
        }


    // ----- inner class: PackageConstant --------------------------------------

    /**
     * Represent a Package constant. A Package constant is composed of a
     * constant identifying the Module or Package which contains this package,
     * and the unqualified name of this Package. A Module can be contained
     * within another Module (either by reference or by embedding), in which
     * case it is represented as a Package; in this case, the Package constant
     * will have a reference to the corresponding Module constant.
     */
    public static class PackageConstant
            extends Constant
        {
        /**
         * Constructor used for deserialization.
         *
         * @param pool    the ConstantPool that will contain this Constant
         * @param format  the format of the Constant in the stream
         * @param in      the DataInput stream to read the Constant value from
         *
         * @throws IOException  if an issue occurs reading the Constant value
         */
        protected PackageConstant(ConstantPool pool, Format format,
                DataInput in)
                throws IOException
            {
            super(pool);
            m_iParent = readMagnitude(in);
            m_iName   = readMagnitude(in);
            }

        /**
         * Construct a constant whose value is a package identifier.
         *
         * @param pool         the ConstantPool that will contain this Constant
         * @param constParent  the module or package that contains this package
         * @param sName        the unqualified package name
         * @param constModule  the module that this is an alias for, or null
         */
        protected PackageConstant(ConstantPool pool, Constant constParent,
                String sName, ModuleConstant constModule)
            {
            super(pool);

            if (constParent == null || !(constParent.getType() == Type.Module ||
                    constParent.getType() == Type.Package))
                {
                throw new IllegalArgumentException(
                        "parent module or package required");
                }

            if (sName == null)
                {
                throw new IllegalArgumentException("package name required");
                }

            if (constModule != null && constParent.getType() != Type.Module)
                {
                throw new IllegalArgumentException(
                        "module import can only occur directly within another module");
                }

            m_constParent = constParent;
            m_constName = pool.ensureCharStringConstant(sName);
            }

        @Override
        public Type getType()
            {
            return Type.Package;
            }

        @Override
        public Format getFormat()
            {
            return Format.Package;
            }

        @Override
        protected void disassemble(DataInput in)
                throws IOException
            {
            final ConstantPool pool = getConstantPool();
            m_constParent = pool.getConstant(m_iParent);
            m_constName   = (CharStringConstant) pool.getConstant(m_iName);
            }

        @Override
        protected void registerConstants(ConstantPool pool)
            {
            m_constParent = pool.register(m_constParent);
            m_constName   = (CharStringConstant) pool.register(m_constName);
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            out.writeByte(getFormat().ordinal());
            writePackedLong(out, m_constParent.getPosition());
            writePackedLong(out, m_constName.getPosition());
            }

        // TODO validate
        // assert m_constParent instanceof ModuleConstant || m_constParent instanceof PackageConstant;

        @Override
        protected PackageStructure instantiate(XvmStructure xsParent)
            {
            return new PackageStructure(xsParent, this);
            }

        @Override
        protected int compareDetails(Constant that)
            {
            int n = this.m_constParent.compareTo(
                    ((PackageConstant) that).m_constParent);
            if (n == 0)
                {
                n = this.m_constName.compareTo(
                        ((PackageConstant) that).m_constName);
                }
            return n;
            }

        @Override
        public String getValueString()
            {
            return m_constParent instanceof PackageConstant
                    ? m_constParent.getValueString() + '.' + m_constName.getValue()
                    : m_constName.getValue();
            }

        @Override
        public String getDescription()
            {
            Constant constParent = m_constParent;
            while (constParent instanceof PackageConstant)
                {
                constParent = ((PackageConstant) constParent).getNamespace();
                }

            return "package=" + getValueString() + ", " + constParent.getDescription();
            }

        @Override
        public int hashCode()
            {
            return m_constParent.hashCode() * 17 + m_constName.hashCode();
            }

        /**
         * Obtain the module or package that this package is contained within.
         *
         * @return the containing module or package constant
         */
        public Constant getNamespace()
            {
            return m_constParent;
            }

        /**
         * Get the unqualified name of the Package.
         *
         * @return the package's unqualified name
         */
        public String getName()
            {
            return m_constName.getValue();
            }

        /**
         * During disassembly, this holds the index of the constant that
         * specifies the parent package or module of this package.
         */
        private int m_iParent;

        /**
         * During disassembly, this holds the index of the constant that
         * specifies the name.
         */
        private int m_iName;

        /**
         * The constant that represents the module or package that contains this
         * package.
         */
        private Constant m_constParent;

        /**
         * The constant that holds the unqualified name of the package.
         */
        private CharStringConstant m_constName;
        }


    // ----- inner class: ClassConstant ----------------------------------------

    /**
     * Represent a Class constant.
     */
    public static class ClassConstant
            extends Constant
        {
        /**
         * Constructor used for deserialization.
         *
         * @param pool    the ConstantPool that will contain this Constant
         * @param format  the format of the Constant in the stream
         * @param in      the DataInput stream to read the Constant value from
         *
         * @throws IOException  if an issue occurs reading the Constant value
         */
        protected ClassConstant(ConstantPool pool, Format format, DataInput in)
                throws IOException
            {
            super(pool);
            m_iParent = readIndex(in);
            m_iName   = readIndex(in);
            }

        /**
         * Construct a constant whose value is a class identifier.
         *
         * @param pool         the ConstantPool that will contain this Constant
         * @param constParent  the module, package, class, or method that
         *                     contains this class
         * @param sName        the unqualified class name
         */
        protected ClassConstant(ConstantPool pool, Constant constParent, String sName)
            {
            super(pool);

            if (constParent == null || !(constParent.getType() == Type.Module
                    || constParent.getType() == Type.Package
                    || constParent.getType() == Type.Class
                    || constParent.getType() == Type.Method))
                {
                throw new IllegalArgumentException("parent module, package, class, or method required");
                }

            if (sName == null)
                {
                throw new IllegalArgumentException("class name required");
                }

            m_constParent = constParent;
            m_constName   = pool.ensureCharStringConstant(sName);
            }

        @Override
        public Type getType()
            {
            return Type.Class;
            }

        @Override
        public Format getFormat()
            {
            return Format.Class;
            }

        @Override
        protected void disassemble(DataInput in)
                throws IOException
            {
            final ConstantPool pool = getConstantPool();
            m_constParent = pool.getConstant(m_iParent);
            m_constName   = (CharStringConstant) pool.getConstant(m_iName);
            }

        @Override
        protected void registerConstants(ConstantPool pool)
            {
            m_constParent = pool.register(m_constParent);
            m_constName   = (CharStringConstant) pool.register(m_constName);
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            out.writeByte(getFormat().ordinal());
            writePackedLong(out, indexOf(m_constParent));
            writePackedLong(out, indexOf(m_constName));
            }

        @Override
        protected ClassStructure instantiate(XvmStructure xsParent)
            {
            return new ClassStructure(xsParent, this);
            }

        @Override
        protected int compareDetails(Constant that)
            {
            int n = this.m_constParent.compareTo(((ClassConstant) that).m_constParent);     // TODO nulls?
            if (n == 0)
                {
                n = this.m_constName.compareTo(((ClassConstant) that).m_constName);         // TODO nulls?
                }
            return n;
            }

        @Override
        public String getValueString()
            {
            return m_constParent instanceof ClassConstant                                   // TODO nulls?
                    ? m_constParent.getValueString() + '.' + m_constName.getValue()
                    : m_constName.getValue();
            }

        @Override
        public String getDescription()
            {
            Constant constParent = m_constParent;
            while (constParent instanceof ClassConstant)
                {
                constParent = ((ClassConstant) constParent).getNamespace();
                }

            return "class=" + getValueString() + ", " + constParent.getDescription();       // TODO nulls?
            }

        @Override
        public int hashCode()
            {
            return m_constParent.hashCode() * 17 + m_constName.hashCode();                  // TODO nulls?
            }

        /**
         * Obtain the module, package, class, method, or property that this
         * class is contained within.
         *
         * @return the containing constant
         */
        public Constant getNamespace()
            {
            return m_constParent;
            }

        /**
         * Get the unqualified name of the class.
         *
         * @return the class's unqualified name
         */
        public String getName()
            {
            return m_constName.getValue();                                                  // TODO nulls?
            }

        /**
         * Determine if this ClassConstant is the "Object" class.
         *
         * @return true iff this ClasConstant represents the root Object class
         *         of the Ecstasy type hierarchy
         */
        public boolean isEcstasyObject()
            {
            return getName().equals(CLASS_OBJECT)
                    && getNamespace().getType() == Type.Module
                    && ((ModuleConstant) getNamespace()).isEcstasyModule();
            }

        /**
         * During disassembly, this holds the index of the constant that
         * specifies the parent of this class.
         */
        private int m_iParent;

        /**
         * During disassembly, this holds the index of the constant that
         * specifies the name.
         */
        private int m_iName;

        /**
         * The constant that represents the parent of this class. Null in the
         * case of a class that does not have a global identity.
         */
        private Constant m_constParent;

        /**
         * The constant that holds the unqualified name of the class. Null in
         * the case of a class that does not have a name, such as a synthetic
         * class, or a class of type "Type".
         */
        private CharStringConstant m_constName;
        }


    // ----- inner class: PropertyConstant -------------------------------------

    /**
     * Represent a Class constant.
     */
    public static class PropertyConstant
            extends Constant
        {
        /**
         * Constructor used for deserialization.
         *
         * @param pool    the ConstantPool that will contain this Constant
         * @param format  the format of the Constant in the stream
         * @param in      the DataInput stream to read the Constant value from
         *
         * @throws IOException  if an issue occurs reading the Constant value
         */
        protected PropertyConstant(ConstantPool pool, Format format, DataInput in)
                throws IOException
            {
            super(pool);
            m_iParent = readMagnitude(in);
            m_iType   = readMagnitude(in);
            m_iName   = readMagnitude(in);
            }

        /**
         * Construct a constant whose value is a property identifier.
         *
         * @param pool         the ConstantPool that will contain this Constant
         * @param constParent  the module, package, class, or method that
         *                     contains this property
         * @param constType    the type of the property
         * @param sName        the property name
         */
        protected PropertyConstant(ConstantPool pool, Constant constParent,
                ClassConstant constType, String sName)
            {
            super(pool);

            if (constParent == null || !(constParent.getType() == Type.Module
                    || constParent.getType() == Type.Package
                    || constParent.getType() == Type.Class
                    || constParent.getType() == Type.Method))
                {
                throw new IllegalArgumentException(
                        "parent module, package, class, or method required");
                }

            if (constType == null)
                {
                throw new IllegalArgumentException("property type required");
                }

            if (sName == null)
                {
                throw new IllegalArgumentException("property name required");
                }

            m_constParent = constParent;
            m_constType   = constType;
            m_constName   = pool.ensureCharStringConstant(sName);
            }

        @Override
        public Type getType()
            {
            return Type.Property;
            }

        @Override
        public Format getFormat()
            {
            return Format.Property;
            }

        @Override
        protected void disassemble(DataInput in)
                throws IOException
            {
            final ConstantPool pool = getConstantPool();
            m_constParent = pool.getConstant(m_iParent);
            m_constType   = (ClassConstant) pool.getConstant(m_iType);
            m_constName   = (CharStringConstant) pool.getConstant(m_iName);
            }

        @Override
        protected void registerConstants(ConstantPool pool)
            {
            m_constParent = pool.register(m_constParent);
            m_constType   = (ClassConstant) pool.register(m_constType);
            m_constName   = (CharStringConstant) pool.register(m_constName);
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            out.writeByte(getFormat().ordinal());
            writePackedLong(out, m_constParent.getPosition());
            writePackedLong(out, m_constType.getPosition());
            writePackedLong(out, m_constName.getPosition());
            }

        @Override
        protected PropertyStructure instantiate(XvmStructure xsParent)
            {
            return new PropertyStructure(xsParent, this);
            }

        @Override
        protected int compareDetails(Constant that)
            {
            int n = this.m_constParent.compareTo(((PropertyConstant) that).m_constParent);
            if (n == 0)
                {
                n = this.m_constName.compareTo(((PropertyConstant) that).m_constName);
                if (n == 0)
                    {
                    n = this.m_constType.compareTo(((PropertyConstant) that).m_constType);
                    }
                }
            return n;
            }

        @Override
        public String getValueString()
            {
            String sParent;
            final Constant constParent = m_constParent;
            switch (constParent.getType())
                {
                case Module:
                    sParent = ((ModuleConstant) constParent).getUnqualifiedName();
                    break;
                case Package:
                    sParent = ((PackageConstant) constParent).getName();
                    break;
                case Class:
                    sParent = ((ClassConstant) constParent).getName();
                    break;
                case Method:
                    sParent = ((MethodConstant) constParent).getName() + "(..)";
                    break;
                default:
                    throw new IllegalStateException();
                }
            return sParent + '.' + m_constName.getValue();
            }

        @Override
        public String getDescription()
            {
            return "property=" + getValueString() + ", type=" + m_constType.getValueString();
            }

        @Override
        public int hashCode()
            {
            return (m_constParent.hashCode() * 17
                    + m_constName.hashCode()) * 31
                    + m_constType.hashCode();
            }

        /**
         * Obtain the module, package, class or method that this property is
         * contained within.
         *
         * @return the containing constant
         */
        public Constant getNamespace()
            {
            return m_constParent;
            }

        /**
         * Get the type of the property.
         *
         * @return the property type
         */
        public ClassConstant getPropertyType()
            {
            return m_constType;
            }

        /**
         * Get the name of the property.
         *
         * @return the property name
         */
        public String getName()
            {
            return m_constName.getValue();
            }

        /**
         * During disassembly, this holds the index of the constant that
         * specifies the parent of this property.
         */
        private int m_iParent;

        /**
         * During disassembly, this holds the index of the constant that
         * specifies the type of this property.
         */
        private int m_iType;

        /**
         * During disassembly, this holds the index of the constant that
         * specifies the name of this property.
         */
        private int m_iName;

        /**
         * The constant that represents the parent of this property. A Property
         * can be a child of a Module, a Package, a Class, or a Method.
         */
        private Constant m_constParent;

        /**
         * The constant that represents the type of this property.
         */
        private ClassConstant m_constType;

        /**
         * The constant that holds the name of the property.
         */
        private CharStringConstant m_constName;
        }


    // ----- inner class: MethodConstant ---------------------------------------


    /**
     * Represent a Method constant.
     * REVIEW parent return(type, name?)* name param(type, name)* attrs?
     */
    public static class MethodConstant
            extends Constant
        {
        /**
         * Constructor used for deserialization.
         *
         * @param pool    the ConstantPool that will contain this Constant
         * @param format  the format of the Constant in the stream
         * @param in      the DataInput stream to read the Constant value from
         *
         * @throws IOException  if an issue occurs reading the Constant value
         */
        protected MethodConstant(ConstantPool pool, Format format, DataInput in)
                throws IOException
            {
            super(pool);
            m_iParent = readMagnitude(in);
            m_iName   = readMagnitude(in);
            }

        /**
         * Construct a constant whose value is a method identifier.
         *
         * @param pool                the ConstantPool that will contain this
         *                            Constant
         * @param constParent         specifies the module, package, class,
         *                            method, or property that contains this
         *                            method
         * @param sName               the method name
         * @param aconstGenericParam  the parameters of the genericized method
         * @param aconstInvokeParam   the invocation parameters for the method
         * @param aconstReturnParam   the return values from the method
         */
        protected MethodConstant(ConstantPool pool, Constant constParent,
                String sName,
                ParameterConstant[] aconstGenericParam,
                ParameterConstant[] aconstInvokeParam,
                ParameterConstant[] aconstReturnParam)
            {
            super(pool);

            if (constParent == null || !(constParent.getType() == Type.Module
                    || constParent.getType() == Type.Package
                    || constParent.getType() == Type.Class
                    || constParent.getType() == Type.Method
                    || constParent.getType() == Type.Property))
                {
                throw new IllegalArgumentException(
                        "parent module, package, class, method, or property required");
                }

            if (sName == null)
                {
                throw new IllegalArgumentException("property name required");
                }

            m_constParent        = constParent;
            m_constName          = pool.ensureCharStringConstant(sName);
            m_aconstGenericParam = validateParameterArray(aconstGenericParam);
            m_aconstInvokeParam  = validateParameterArray(aconstInvokeParam);
            m_aconstReturnParam  = validateParameterArray(aconstReturnParam);
            }

        /**
         * Internal helper to scan a parameter array for nulls.
         *
         * @param aconst  an array of ParameterConstant; may be null
         *
         * @return a non-null array of ParameterConstant, each element of which
         *         is non-null; note that the returned array is a new (and thus
         *         safe) copy of the passed array
         */
        private ParameterConstant[] validateParameterArray(ParameterConstant[] aconst)
            {
            if (aconst == null)
                {
                return NO_PARAMS;
                }

            for (ParameterConstant constant : aconst)
                {
                if (constant == null)
                    {
                    throw new IllegalArgumentException("parameter required");
                    }
                }

            return aconst.clone();
            }

        @Override
        public Type getType()
            {
            return Type.Method;
            }

        @Override
        public Format getFormat()
            {
            return Format.Method;
            }

        @Override
        protected void disassemble(DataInput in)
                throws IOException
            {
            final ConstantPool pool = getConstantPool();
            m_constParent = pool.getConstant(m_iParent);
            m_constName   = (CharStringConstant) pool.getConstant(m_iName);
            // TODO
            }

        @Override
        protected void registerConstants(ConstantPool pool)
            {
            m_constParent = pool.register(m_constParent);
            m_constName = (CharStringConstant) pool.register(m_constName);
            // TODO
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            out.writeByte(getFormat().ordinal());
            writePackedLong(out, m_constParent.getPosition());
            writePackedLong(out, m_constName.getPosition());
            // TODO
            }

        @Override
        protected int compareDetails(Constant that)
            {
            int n = this.m_constParent.compareTo(((MethodConstant) that).m_constParent);
            if (n == 0)
                {
                n = this.m_constName.compareTo(((MethodConstant) that).m_constName);
                if (n == 0)
                    {
                    // TODO
                    }
                }
            return n;
            }

        @Override
        public String getValueString()
            {
            String sParent;
            final Constant constParent = m_constParent;
            switch (constParent.getType())
                {
                case Module:
                    sParent = ((ModuleConstant) constParent).getUnqualifiedName();
                    break;
                case Package:
                    sParent = ((PackageConstant) constParent).getName();
                    break;
                case Class:
                    sParent = ((ClassConstant) constParent).getName();
                    break;
                case Property:
                    sParent = ((PropertyConstant) constParent).getName();
                    break;
                case Method:
                    sParent = ((MethodConstant) constParent).getName() + "(..)";
                    break;
                default:
                    throw new IllegalStateException();
                }
            return sParent + '.' + m_constName.getValue() + "(..)"; // TODO
            }

        @Override
        public String getDescription()
            {
            return "method=" + getValueString(); // TODO
            }

        @Override
        public int hashCode()
            {
            // TODO
            return m_constParent.hashCode() * 17 + m_constName.hashCode();
            }

        /**
         * Obtain the identity of the module, package, class, method, or
         * property that this method is contained within.
         *
         * @return the containing constant
         */
        public Constant getNamespace()
            {
            return m_constParent;
            }

        /**
         * Get the name of the method.
         *
         * @return the method name
         */
        public String getName()
            {
            return m_constName.getValue();
            }

        /**
         * A utility class used to build MethodConstant objects.
         */
        public static class Builder
            {
            /**
             * Construct a MethodConstant Builder.
             *
             * @param container  the MethodContainer that the MethodConstant
             *                   will be used in
             * @param sMethod    the name of the method
             */
            public Builder(MethodContainer container, String sMethod)
                {
                assert container != null && sMethod != null;

                m_container = container;
                m_sMethod   = sMethod;
                }

            /**
             * Add information about a return value.
             *
             * @param constType  parameter type
             * @param sName      parameter name
             */
            public Builder addReturnValue(ClassConstant constType, String sName)
                {
                m_listReturnValue = add(m_listReturnValue, constType, sName);
                return this;
                }

            /**
             * Add information about a type parameter of a generic method.
             *
             * @param constType  parameter type
             * @param sName      parameter name
             */
            public Builder addTypeParameter(ClassConstant constType, String sName)
                {
                m_listTypeParam = add(m_listTypeParam, constType, sName);
                return this;
                }

            /**
             * Add information about a method invocation parameter.
             *
             * @param constType  parameter type
             * @param sName      parameter name
             */
            public Builder addParameter(ClassConstant constType, String sName)
                {
                m_listParam = add(m_listParam, constType, sName);
                return this;
                }

            /**
             * Convert the information provided to the builder into a
             * MethodConstant.
             *
             * @return the new MethodConstant
             */
            public MethodConstant toConstant()
                {
                // TODO remove
                assert m_container.getIdentityConstant() != null;

                final MethodConstant constmethod = m_constmethod;
                return constmethod == null
                        ? m_constmethod = m_container.getConstantPool().ensureMethodConstant(
                                m_container.getIdentityConstant(), m_sMethod,
                                toArray(m_listTypeParam),
                                toArray(m_listParam),
                                toArray(m_listReturnValue))
                        : constmethod;
                }

            /**
             * Obtain the ModuleStructure that is identified by this builder's
             * name and various parameters.
             *
             * @return the corresponding MethodStructure, created if it did not
             *         previously exist
             */
            public MethodStructure ensureMethod()
                {
                return m_container.ensureMethod(toConstant());
                }

            /**
             * Add the specified parameter information to the passed list of
             * parameters.
             *
             * @param list       the list to add to; may be null, in which case
             *                   a list will be created
             * @param constType  the parameter type
             * @param sName      the parameter name
             *
             * @return the updated parameter list
             */
            private List<ParameterConstant> add(List<ParameterConstant> list,
                    ClassConstant constType, String sName)
                {
                if (list == null)
                    {
                    list = new ArrayList<>(4);
                    }

                list.add(m_container.getConstantPool().ensureParameterConstant(constType, sName));

                m_constmethod = null;
                return list;
                }

            /**
             * Given a list of ParameterConstant objects, produce an array of
             * the same.
             *
             * @param list  the list to turn into an array; may be null
             *
             * @return an array of parameters, or null if the list was null
             */
            private ParameterConstant[] toArray(List<ParameterConstant> list)
                {
                return list == null
                        ? null
                        : list.toArray(new ParameterConstant[list.size()]);
                }

            /**
             * The MethodContainer that the MethodConstant is being built for.
             */
            MethodContainer         m_container;
            /**
             * The name of the method.
             */
            String                  m_sMethod;
            /**
             * The return values from the method.
             */
            List<ParameterConstant> m_listReturnValue;
            /**
             * The type parameters for the method.
             */
            List<ParameterConstant> m_listTypeParam;
            /**
             * The method parameters.
             */
            List<ParameterConstant> m_listParam;
            /**
             * A cached MethodConstant produced from this builder.
             */
            MethodConstant          m_constmethod;
            }

        /**
         * During disassembly, this holds the index of the constant that
         * specifies the parent of this method.
         */
        private int m_iParent;

        /**
         * During disassembly, this holds the index of the constant that
         * specifies the name of this method.
         */
        private int m_iName;

        /**
         * The constant that represents the parent of this method. A Method
         * can be a child of a Module, a Package, a Class, a Method, or a
         * Property.
         */
        private Constant m_constParent;

        /**
         * The constant that holds the name of the method.
         */
        private CharStringConstant m_constName;

        /**
         * The parameters of the parameterized method.
         */
        ParameterConstant[] m_aconstGenericParam;

        /**
         * The invocation parameters of the method.
         */
        ParameterConstant[] m_aconstInvokeParam;

        /**
         * The return values from the method.
         */
        ParameterConstant[] m_aconstReturnParam;
        }


    // ----- inner class: ParameterConstant ------------------------------------

    /**
     * Represent a Parameter constant. A Parameter is a combination of a type
     * and a name, representing a type parameter, a method invocation parameter,
     * and a return value.
     * <p>
     * Note that there is no separate "parameter constant" structure defined in
     * the XVM file format itself, so a ParameterConstant does not have either a
     * "format" or a "position". The choice to use the Constant interface as the
     * basis for managing parameter information was made simply for consistency,
     * and because parameter information is a sub-component of an actual
     * constant structure.
     *
     * REVIEW should there be a "default value"?
     * REVIEW should there be a "type constraint" capability? e.g. param'd types
     */
    public static class ParameterConstant
            extends Constant
        {
        /**
         * Constructor used for deserialization.
         *
         * @param pool    the ConstantPool that will contain this Constant
         * @param format  the format of the Constant in the stream
         * @param in      the DataInput stream to read the Constant value from
         *
         * @throws IOException  if an issue occurs reading the Constant value
         */
        protected ParameterConstant(ConstantPool pool, Format format, DataInput in)
                throws IOException
            {
            super(pool);
            m_iType  = readMagnitude(in);
            m_iName  = readMagnitude(in);
            }

        /**
         * Construct a constant whose value is a Parameter definition.
         *
         * @param pool       the ConstantPool that will contain this Constant
         * @param constType  the type of the parameter
         * @param sName      the parameter name
         */
        protected ParameterConstant(ConstantPool pool, ClassConstant constType, String sName)
            {
            super(pool);

            if (constType == null)
                {
                throw new IllegalArgumentException("parameter type required");
                }

            if (sName == null)
                {
                throw new IllegalArgumentException("parameter name required");
                }

            m_constType = constType;
            m_constName = pool.ensureCharStringConstant(sName);
            }

        @Override
        public Type getType()
            {
            return Type.Parameter;
            }

        @Override
        public Format getFormat()
            {
            return Format.Parameter;
            }

        @Override
        protected void disassemble(DataInput in)
                throws IOException
            {
            final ConstantPool pool = getConstantPool();
            m_constType = (ClassConstant) pool.getConstant(m_iType);
            m_constName = (CharStringConstant) pool.getConstant(m_iName);
            }

        @Override
        protected void registerConstants(ConstantPool pool)
            {
            m_constType = (ClassConstant) pool.register(m_constType);
            m_constName = (CharStringConstant) pool.register(m_constName);
            }

        @Override
        protected void assemble(DataOutput out)
                throws IOException
            {
            out.writeByte(getFormat().ordinal());
            writePackedLong(out, m_constType.getPosition());
            writePackedLong(out, m_constName.getPosition());
            }

        @Override
        protected int compareDetails(Constant that)
            {
            int n = this.m_constType.compareDetails(((ParameterConstant) that).m_constType);
            if (n == 0)
                {
                n = this.m_constName.compareDetails(((ParameterConstant) that).m_constName);
                }
            return n;
            }

        @Override
        public String getValueString()
            {
            return m_constType.getValueString() + ' ' + m_constName.getValue();
            }

        @Override
        public String getDescription()
            {
            return "parameter=" + m_constName.getValue() + ", type=" + m_constType.getValueString();
            }

        @Override
        public int hashCode()
            {
            return m_constName.hashCode() * 17 + m_constType.hashCode();
            }

        /**
         * Get the type of the parameter.
         *
         * @return the parameter type
         */
        public ClassConstant getParameterType()
            {
            return m_constType;
            }

        /**
         * Get the name of the parameter.
         *
         * @return the parameter name
         */
        public String getName()
            {
            return m_constName.getValue();
            }

        /**
         * During disassembly, this holds the index of the constant that
         * specifies the type of this parameter.
         */
        private int m_iType;

        /**
         * During disassembly, this holds the index of the constant that
         * specifies the name of this parameter.
         */
        private int m_iName;

        /**
         * The constant that represents the type of this parameter.
         */
        private ClassConstant m_constType;

        /**
         * The constant that holds the name of the parameter.
         */
        private CharStringConstant m_constName;
        }


    // ----- data members ------------------------------------------------------

    /**
     * An immutable, empty, zero-length array of parameters.
     */
    public static final ParameterConstant[] NO_PARAMS = new ParameterConstant[0];

    /**
     * Storage of Constant objects by index.
     */
    private final ArrayList<Constant> m_listConst = new ArrayList<>();

    /**
     * Reverse lookup structure to find a particular constant by constant.
     */
    private final EnumMap<Type, HashMap<Constant, Constant>> m_mapConstants = new EnumMap<>(Type.class);

    /**
     * Reverse lookup structure to find a particular constant by locator.
     */
    private final EnumMap<Type, HashMap<Object, Constant>> m_mapLocators = new EnumMap<>(Type.class);

    /**
     * Tracks whether the ConstantPool should recursively register constants.
     */
    private transient boolean m_fRecurseReg;
    }
