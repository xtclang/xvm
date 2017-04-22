package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.StructureContainer;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a Method constant.
 * REVIEW parent return(type, name?)* name param(type, name)* attrs?
 */
public class MethodConstant
        extends Constant
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
    public MethodConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iParent = readMagnitude(in);
        m_iName   = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a method identifier.
     *
     * @param pool                the ConstantPool that will contain this Constant
     * @param constParent         specifies the module, package, class, method, or property that
     *                            contains this method
     * @param sName               the method name
     * @param aconstGenericParam  the parameters of the genericized method
     * @param aconstInvokeParam   the invocation parameters for the method
     * @param aconstReturnParam   the return values from the method
     */
    public MethodConstant(ConstantPool pool, Constant constParent, String sName,
            ParameterConstant[] aconstGenericParam,
            ParameterConstant[] aconstInvokeParam,
            ParameterConstant[] aconstReturnParam)
        {
        super(pool);

        if (constParent == null ||
                !( constParent.getType() == Type.Module
                || constParent.getType() == Type.Package
                || constParent.getType() == Type.Class
                || constParent.getType() == Type.Method
                || constParent.getType() == Type.Property ))
            {
            throw new IllegalArgumentException("parent module, package, class, method, or property required");
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


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Obtain the identity of the module, package, class, method, or property that this method is
     * contained within.
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


    // ----- Constant methods ----------------------------------------------------------------------

    /**
     * Internal helper to scan a parameter array for nulls.
     *
     * @param aconst  an array of ParameterConstant; may be null
     *
     * @return a non-null array of ParameterConstant, each element of which is non-null; note that
     *         the returned array is a new (and thus safe) copy of the passed array
     */
    private ParameterConstant[] validateParameterArray(ParameterConstant[] aconst)
        {
        if (aconst == null)
            {
            return ConstantPool.NO_PARAMS;
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


    // ----- XvmStructure methods ------------------------------------------------------------------

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
    public String getDescription()
        {
        return "method=" + getValueString(); // TODO
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        // TODO
        return m_constParent.hashCode() * 17 + m_constName.hashCode();
        }


    // ----- inner class: MethodConstant Builder ---------------------------------------------------

    /**
     * A utility class used to build MethodConstant objects.
     */
    public static class Builder
        {
        /**
         * Construct a MethodConstant Builder.
         *
         * @param container  the MethodContainer that the MethodConstant will be used in
         * @param sMethod    the name of the method
         */
        public Builder(StructureContainer.MethodContainer container, ConstantPool pool, String sMethod)
            {
            assert container != null && sMethod != null;

            m_container = container;
            m_pool      = pool;
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
         * Convert the information provided to the builder into a MethodConstant.
         *
         * @return the new MethodConstant
         */
        public MethodConstant toConstant()
            {
            MethodConstant constmethod = m_constmethod;
            if (constmethod == null)
                {
                constmethod = m_pool.ensureMethodConstant(m_container.getIdentityConstant(), m_sMethod,
                        toArray(m_listTypeParam),
                        toArray(m_listParam),
                        toArray(m_listReturnValue));
                m_constmethod = constmethod;
                }
            return constmethod;
            }

        /**
         * Obtain the ModuleStructure that is identified by this builder's name and various
         * parameters.
         *
         * @return the corresponding MethodStructure, created if it did not previously exist
         */
        public MethodStructure ensureMethod()
            {
            return m_container.ensureMethod(toConstant());
            }

        /**
         * Add the specified parameter information to the passed list of parameters.
         *
         * @param list       the list to add to; may be null, in which case a list will be created
         * @param constType  the parameter type
         * @param sName      the parameter name
         *
         * @return the updated parameter list
         */
        private List<ParameterConstant> add(List<ParameterConstant> list, ClassConstant constType, String sName)
            {
            if (list == null)
                {
                list = new ArrayList<>(4);
                }

            list.add(m_pool.ensureParameterConstant(constType, sName));

            m_constmethod = null;
            return list;
            }

        /**
         * Given a list of ParameterConstant objects, produce an array of the same.
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
        StructureContainer.MethodContainer m_container;
        /**
         * The constant pool.
         */
        ConstantPool m_pool;
        /**
         * The name of the method.
         */
        String                             m_sMethod;
        /**
         * The return values from the method.
         */
        List<ParameterConstant>            m_listReturnValue;
        /**
         * The type parameters for the method.
         */
        List<ParameterConstant>            m_listTypeParam;
        /**
         * The method parameters.
         */
        List<ParameterConstant>            m_listParam;
        /**
         * A cached MethodConstant produced from this builder.
         */
        MethodConstant                     m_constmethod;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the parent of this
     * method.
     */
    private int m_iParent;

    /**
     * During disassembly, this holds the index of the constant that specifies the name of this
     * method.
     */
    private int m_iName;

    /**
     * The constant that represents the parent of this method. A Method can be a child of a Module,
     * a Package, a Class, a Method, or a Property.
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
