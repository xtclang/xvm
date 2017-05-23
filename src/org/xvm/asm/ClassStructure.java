package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xvm.asm.constants.CharStringConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.util.Handy;
import org.xvm.util.ListMap;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents an entire Class.
 *
 * @author cp 2016.04.14
 */
public class ClassStructure
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ClassStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure (probably a FileStructure) that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module
     * @param condition  the optional condition for this ModuleStructure
     */
    protected ClassStructure(XvmStructure xsParent, int nFlags, ClassConstant constId, ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Obtain the ClassConstant that holds the identity of this Class.
     *
     * @return the ClassConstant representing the identity of this ClassStructure
     */
    public ClassConstant getClassConstant()
        {
        return (ClassConstant) getIdentityConstant();
        }

    /**
     * Obtain the type parameters for the class as an ordered read-only map, keyed by name and with
     * a corresponding value of the type constraint for the parameter.
     *
     * @return a read-only map of type parameter name to type
     */
    public Map<CharStringConstant, TypeConstant> getTypeParams()
        {
        Map<CharStringConstant, TypeConstant> map = m_mapParams;
        if (map == null)
            {
            return Collections.EMPTY_MAP;
            }
        assert (map = Collections.unmodifiableMap(map)) != null;
        return map;
        }

    /**
     * Obtain the type parameters for the class as a list of map entries from name to type.
     *
     * @return a read-only list of map entries from type parameter name to type
     */
    public List<Map.Entry<CharStringConstant, TypeConstant>> getTypeParamsAsList()
        {
        final ListMap<CharStringConstant, TypeConstant> map = m_mapParams;
        return map == null ? Collections.EMPTY_LIST : map.asList();
        }

    /**
     * Add a type parameter.
     *
     * @param sName  the type parameter name
     * @param clz    the type parameter type
     */
    public void addTypeParam(String sName, TypeConstant clz)
        {
        ListMap<CharStringConstant, TypeConstant> map = m_mapParams;
        if (map == null)
            {
            m_mapParams = map = new ListMap<>();
            }

        map.put(getConstantPool().ensureCharStringConstant(sName), clz);
        markModified();
        }

    /**
     * Obtain the class contributions as a list.
     *
     * @return a read-only list of class contributions
     */
    public List<Contribution> getContributionsAsList()
        {
        List<Contribution> list = m_listContribs;
        if (list == null)
            {
            return Collections.EMPTY_LIST;
            }
        assert (list = Collections.unmodifiableList(m_listContribs)) != null;
        return list;
        }

    /**
     * Add a class contribution.
     *
     * @param composition  the contribution type
     * @param constClass   the contribution class
     */
    public void addContribution(Composition composition, ClassConstant constClass)
        {
        addContribution(new Contribution(composition, constClass));
        }

    /**
     * Add an interface delegation.
     *
     * @param constClass  the type to delegate
     * @param constProp   the property specifying the reference to delegate to
     */
    public void addContribution(ClassConstant constClass, PropertyConstant constProp)
        {
        addContribution(new Contribution(Composition.Delegates, constClass, constProp));
        }

    /**
     * Helper to add a contribution to the lazily-instantiated list of contributions.
     *
     * @param contrib  the contribution to add to the end of the list
     */
    protected void addContribution(Contribution contrib)
        {
        List<Contribution> list = m_listContribs;
        if (list == null)
            {
            m_listContribs = list = new ArrayList<>();
            }

        list.add(contrib);
        markModified();
        }


    // ----- component methods ---------------------------------------------------------------------

    @Override
    public boolean isClassContainer()
        {
        return true;
        }

    @Override
    public boolean isMethodContainer()
        {
        return true;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        super.disassemble(in);

        // read in the type parameters
        m_mapParams = disassembleTypeParams(in);

        // read in the "contributions"
        int c = readMagnitude(in);
        if (c > 0)
            {
            final List<Contribution> list = new ArrayList<>();
            final ConstantPool       pool = getConstantPool();
            for (int i = 0; i < c; ++i)
                {
                final Composition      composition = Composition.valueOf(in.readUnsignedByte());
                final ClassConstant    constClass  = (ClassConstant) pool.getConstant(readIndex(in));
                final PropertyConstant constProp   = (PropertyConstant) pool.getConstant(readIndex(in));
                list.add(new Contribution(composition, constClass, constProp));
                }
            m_listContribs = list;
            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        // register the type parameters
        m_mapParams = registerTypeParams(m_mapParams);

        // register the contributions
        final List<Contribution> listOld = m_listContribs;
        if (listOld != null && listOld.size() > 0)
            {
            final List<Contribution> listNew = new ArrayList<>();
            for (Contribution contribution : listOld)
                {
                listNew.add(new Contribution(
                        contribution.getComposition(),
                        (ClassConstant) pool.register(contribution.getClassConstant()),
                        (PropertyConstant) pool.register(contribution.getDelegatePropertyConstant())));
                }
            m_listContribs = listNew;
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        // write out the type parameters
        assembleTypeParams(m_mapParams, out);

        // write out the contributions
        final List<Contribution> listContribs = m_listContribs;
        final int cContribs = listContribs == null ? 0 : listContribs.size();
        writePackedLong(out, cContribs);
        if (cContribs > 0)
            {
            final ConstantPool pool = getConstantPool();
            for (Contribution contribution : listContribs)
                {
                out.writeByte(contribution.getComposition().ordinal());
                writePackedLong(out, contribution.getClassConstant().getPosition());
                writePackedLong(out, Constant.indexOf(contribution.getDelegatePropertyConstant()));
                }
            }
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription())
          .append(", type-params=");

        final ListMap<CharStringConstant, TypeConstant> map = m_mapParams;
        if (map == null || map.size() == 0)
            {
            sb.append("none");
            }
        else
            {
            sb.append('<');
            boolean fFirst = true;
            for (Map.Entry<CharStringConstant, TypeConstant> entry : map.entrySet())
                {
                if (fFirst)
                    {
                    fFirst = false;
                    }
                else
                    {
                    sb.append(", ");
                    }

                sb.append(entry.getKey().getValue());

                TypeConstant constType = entry.getValue();
                if (!constType.isEcstasyObject())
                    {
                    sb.append(" extends ")
                      .append(constType);
                    }
                }
            sb.append('>');
            }

        return sb.toString();
        }

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        out.print(sIndent);
        out.println(toString());

        final List<Contribution> listContribs = m_listContribs;
        final int cContribs = listContribs == null ? 0 : listContribs.size();
        if (cContribs > 0)
            {
            out.print(sIndent);
            out.println("Contributions");
            for (int i = 0; i < cContribs; ++i)
                {
                out.println(sIndent + '[' + i + "]=" + listContribs.get(i));
                }
            }
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof ClassStructure) || !super.equals(obj))
            {
            return false;
            }

        ClassStructure that = (ClassStructure) obj;

        // type parameters
        final Map mapThisParams = this.m_mapParams;
        final Map mapThatParams = that.m_mapParams;
        final int cThisParams = mapThisParams == null ? 0 : mapThisParams.size();
        final int cThatParams = mapThatParams == null ? 0 : mapThatParams.size();
        if (cThisParams != cThatParams || (cThisParams > 0 && !mapThisParams.equals(mapThatParams)))
            {
            return  false;
            }

        // contributions (order is considered important)
        final List<Contribution> listThisContribs = this.m_listContribs;
        final List<Contribution> listThatContribs = that.m_listContribs;
        final int cThisContribs = listThisContribs == null ? 0 : listThisContribs.size();
        final int cThatContribs = listThatContribs == null ? 0 : listThatContribs.size();
        if (cThisContribs != cThatContribs || (cThisContribs > 0 && !listThisContribs.equals(listThatContribs)))
            {
            return  false;
            }

        return true;
        }


    // ----- enumeration: class composition --------------------------------------------------------

    /**
     * Types of composition.
     */
    public enum Composition
        {
        /**
         * Represents class inheritance.
         */
        Extends,
        /**
         * Represents interface inheritance.
         */
        Implements,
        /**
         * Represents interface inheritance plus default delegation of interface functionality.
         */
        Delegates,
        /**
         * Represents the combining-in of a trait or mix-in.
         */
        Incorporates,
        /**
         * Represents that the class being composed is one of the enumeration of a specified type.
         */
        Enumerates,;

        /**
         * Look up a Composition enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Composition enum for the specified ordinal
         */
        public static Composition valueOf(int i)
            {
            return COMPOSITIONS[i];
            }

        /**
         * All of the Composition enums.
         */
        private static final Composition[] COMPOSITIONS = Composition.values();
        }

    /**
     * Represents one contribution to the definition of a class. A class (with the term used in the
     * abstract sense, meaning any class, interface, mixin, trait, value, enum, or service) can be
     * composed of any number of contributing components.
     */
    public static class Contribution
        {
        /**
         * Construct a Contribution.
         *
         * @param composition  specifies the type of composition
         * @param constant     specifies the class being contributed
         */
        protected Contribution(Composition composition, ClassConstant constant)
            {
            this(composition, constant, null);
            }

        /**
         * Construct a delegation Contribution.
         *
         * @param composition  specifies the type of composition
         * @param constant     specifies the class being contributed
         * @param delegate     for a Delegates composition, this is the property that provides the
         *                     delegate reference
         */
        protected Contribution(Composition composition, ClassConstant constant, PropertyConstant delegate)
            {
            assert composition != null && constant != null;
            assert (composition == Composition.Delegates) == (delegate != null);

            m_composition = composition;
            m_constClass = constant;
            }

        /**
         * Obtain the form of composition represented by this contribution.
         *
         * @return the Composition type for this contribution
         */
        public Composition getComposition()
            {
            return m_composition;
            }

        /**
         * Obtain the class definition being contributed by this contribution.
         *
         * @return the ClassConstant for this contribution
         */
        public ClassConstant getClassConstant()
            {
            return m_constClass;
            }

        /**
         * @return the PropertyConstant specifying the reference to delegate to, or null
         */
        public PropertyConstant getDelegatePropertyConstant()
            {
            return m_constProp;
            }

        @Override
        public boolean equals(Object obj)
            {
            if (this == obj)
                {
                return true;
                }

            if (!(obj instanceof Contribution))
                {
                return false;
                }

            Contribution that = (Contribution) obj;
            return this.m_composition == that.m_composition
                && this.m_constClass.equals(that.m_constClass)
                && Handy.equals(this.m_constProp, that.m_constProp);
            }

        @Override
        public String toString()
            {
            String s = m_composition.toString().toLowerCase() + ' ' + m_constClass.getDescription();
            return m_constProp == null ? s : s + '(' + m_constProp.getDescription() + ')';
            }

        /**
         * Defines the form of composition that this component contributes to the class.
         */
        private Composition m_composition;

        /**
         * Defines the class that was used as part of the composition.
         */
        private ClassConstant m_constClass;

        /**
         * The property specifying the delegate, if this Composition represents a "delegates"
         * clause.
         */
        private PropertyConstant m_constProp;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The name-to-type information for type parameters. The type constant is used to specify a
     * type constraint for the parameter.
     */
    private ListMap<CharStringConstant, TypeConstant> m_mapParams;

    /**
     * The contributions that make up this class.
     */
    private List<Contribution> m_listContribs;
    }
