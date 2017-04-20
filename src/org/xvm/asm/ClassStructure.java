package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xvm.asm.StructureContainer.ClassContainer;

import org.xvm.asm.ConstantPool.CharStringConstant;
import org.xvm.asm.ConstantPool.ClassConstant;

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
        extends ClassContainer
    {
    // ----- constructors ------------------------------------------------------
    
    /**
     * Construct a ClassStructure with the specified identity.
     *
     * @param structParent  the XvmStructure (probably a FileStructure, a
     *                      ModuleStructure, a PackageStructure, a
     *                      ClassStructure, or a MethodStructure) that
     *                      contains this ClassStructure
     * @param constclass    the constant that specifies the identity of the
     *                      Class
     */
    ClassStructure(XvmStructure structParent, ConstantPool.ClassConstant constclass)
        {
        super(structParent, constclass);
        }


    // ----- XvmStructure methods ----------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        final int nCategory = in.readUnsignedByte();
        m_fSingleton = (nCategory & SINGLETON) != 0;
        m_fSynthetic = (nCategory & SYNTHETIC) != 0;
        m_category   = Category.valueOf(nCategory & CATEGORY);
        m_mapParams  = disassembleTypeParams(in);

        // read in the "contributions"
        int c = readMagnitude(in);
        if (c > 0)
            {
            final List<Contribution> list = new ArrayList<>();
            final ConstantPool       pool = getConstantPool();
            for (int i = 0; i < c; ++i)
                {
                final Composition   composition = Composition.valueOf(in.readUnsignedByte());
                final ClassConstant constant    = (ClassConstant) pool.getConstant(readIndex(in));
                list.add(new Contribution(composition, constant));
                }
            m_listContribs = list;
            }

        super.disassemble(in);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        final ListMap<CharStringConstant, ClassConstant> mapOld = m_mapParams;
        if (mapOld != null && mapOld.size() > 0)
            {
            final ListMap<CharStringConstant, ClassConstant> mapNew = new ListMap<>();
            for (Map.Entry<CharStringConstant, ClassConstant> entry : mapOld.entrySet())
                {
                mapNew.put((CharStringConstant) pool.register(entry.getKey()),
                           (ClassConstant     ) pool.register(entry.getValue()));
                }
            m_mapParams = mapNew;
            }

        final List<Contribution> listOld = m_listContribs;
        if (listOld != null && listOld.size() > 0)
            {
            final List<Contribution> listNew = new ArrayList<>();
            for (Contribution contribution : listOld)
                {
                listNew.add(new Contribution(contribution.getComposition(),
                        (ClassConstant) pool.register(contribution.getClassConstant())));
                }
            m_listContribs = listNew;
            }

        super.registerConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(m_category.ordinal() | (m_fSingleton ?SINGLETON : 0) | (m_fSynthetic ? SYNTHETIC : 0));
        assembleTypeParams(m_mapParams, out);

        // write out the contributions
        final List<Contribution> listContribs = m_listContribs;
        final int cContribs = listContribs == null ? 0 : listContribs.size();
        writePackedLong(out, cContribs);
        if (cContribs > 0)
            {
            for (Contribution contribution : listContribs)
                {
                out.writeByte(contribution.getComposition().ordinal());
                writePackedLong(out, contribution.getClassConstant().getPosition());
                }
            }

        super.assemble(out);
        }

    // TODO validate

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription())
          .append(", type-params=");

        final ListMap<CharStringConstant, ClassConstant> map = m_mapParams;
        if (map == null || map.size() == 0)
            {
            sb.append("none");
            }
        else
            {
            sb.append('<');
            boolean fFirst = true;
            for (Map.Entry<CharStringConstant, ClassConstant> entry : map.entrySet())
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

                ClassConstant constType = entry.getValue();
                if (!constType.isEcstasyObject())
                    {
                    sb.append(" extends ")
                      .append(entry.getValue().getName());
                    }
                }
            sb.append('>');
            }

        sb.append(", singleton=")
          .append(m_fSingleton)
          .append(", synthetic=")
          .append(m_fSynthetic);
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

        dumpStructureMap(out, sIndent, "Methods", getMethodMap());
        }


    // ----- Object methods ----------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof ClassStructure && super.equals(obj)))
            {
            return false;
            }

        ClassStructure that = (ClassStructure) obj;
        if (! (this.m_category   == that.m_category
            && this.m_fSingleton == that.m_fSingleton
            && this.m_fSynthetic == that.m_fSynthetic))
            {
            return false;
            }

        final Map mapThis = this.m_mapParams;
        final Map mapThat = that.m_mapParams;
        final int cThis = mapThis == null ? 0 : mapThis.size();
        final int cThat = mapThat == null ? 0 : mapThat.size();
        return cThis == cThat && (cThis == 0 || mapThis.equals(mapThat));
        // TODO list of contributions
        }


    // ----- accessors ---------------------------------------------------------

    /**
     * Obtain the ClassConstant that holds the identity of this Class.
     *
     * @return the ClassConstant representing the identity of this
     *         ClassStructure
     */
    public ClassConstant getClassConstant()
        {
        return (ClassConstant) getIdentityConstant();
        }

    /**
     * TODO
     *
     * @return
     */
    public Category getCategory()
        {
        return m_category;
        }

    /**
     * TODO
     *
     * @param category
     */
    public void setCategory(Category category)
        {
        m_category = category;
        markModified();
        }

    /**
     * TODO
     *
     * @return
     */
    public boolean isSingleton()
        {
        return m_fSingleton;
        }

    /**
     * TODO
     *
     * @param fSingleton
     */
    public void setSingleton(boolean fSingleton)
        {
        m_fSingleton = fSingleton;
        markModified();
        }

    /**
     * TODO
     *
     * @return
     */
    public boolean isSynthetic()
        {
        return m_fSynthetic;
        }

    /**
     * TODO
     *
     * @param fSynthetic
     */
    public void setSynthetic(boolean fSynthetic)
        {
        m_fSynthetic = fSynthetic;
        markModified();
        }

    /**
     * TODO
     *
     * @return
     */
    public Map<CharStringConstant, ClassConstant> getTypeParams()
        {
        final ListMap<CharStringConstant, ClassConstant> map = m_mapParams;
        return map == null ? Collections.EMPTY_MAP : Collections.unmodifiableMap(map);
        }

    /**
     * TODO
     *
     * @return
     */
    public List<Map.Entry<CharStringConstant, ClassConstant>> getTypeParamsAsList()
        {
        final ListMap<CharStringConstant, ClassConstant> map = m_mapParams;
        return map == null ? Collections.EMPTY_LIST : map.asList();
        }

    /**
     * TODO
     *
     * @param sName
     * @param clz
     */
    public void addTypeParam(String sName, ClassConstant clz)
        {
        ListMap<CharStringConstant, ClassConstant> map = m_mapParams;
        if (map == null)
            {
            m_mapParams = map = new ListMap<>();
            }

        map.put(getConstantPool().ensureCharStringConstant(sName), clz);
        }

    /**
     * TODO
     *
     * @param sName
     */
    public void removeTypeParam(String sName)
        {
        final ListMap<CharStringConstant, ? extends Constant> map = m_mapParams;
        if (map != null)
            {
            map.remove(getConstantPool().ensureCharStringConstant(sName));
            }
        }

    /**
     * TODO
     *
     * @return
     */
    public List<Contribution> getContributionsAsList()
        {
        return m_listContribs == null ? Collections.EMPTY_LIST : Collections.unmodifiableList(m_listContribs);
        }

    /**
     * TODO
     *
     * @param composition
     * @param constant
     */
    public void addContribution(Composition composition, ClassConstant constant)
        {
        List<Contribution> list = m_listContribs;
        if (list == null)
            {
            m_listContribs = list = new ArrayList<>();
            }

        list.add(new Contribution(composition, constant));
        }

    /**
     * TODO
     *
     * @param i
     */
    public void removeContribution(int i)
        {
        assert m_listContribs != null;

        m_listContribs.remove(i);
        }


    // ----- enumeration: class categories -------------------------------------

    /**
     * Types of classes.
     */
    public enum Category
        {
        /**
         * A type that is identified only by its members.
         */
        Type,
        /**
         * A type that is itself a reference to a type parameter. (The exact
         * type is unknown at compile time.)
         */
        TypeParam,
        /**
         * An <tt>interface</tt> type.
         */
        Interface,
        /**
         * A <tt>trait</tt> type.
         */
        Trait,
        /**
         * A <tt>mixin</tt> type.
         */
        Mixin,
        /**
         * A <tt>class</tt> type.
         */
        Class,
        /**
         * A <tt>service</tt> type.
         */
        Service,
        /**
         * A <tt>value</tt> type.
         */
        Value,
        /**
         * An <tt>enum</tt> type.
         */
        Enum,;

        /**
         * Look up a Category enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Category enum for the specified ordinal
         */
        public static Category valueOf(int i)
            {
            return CATEGORIES[i];
            }

        /**
         * All of the Category enums.
         */
        private static final Category[] CATEGORIES = Category.values();
        }


    // ----- enumeration: class composition ------------------------------------

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
         * Represents the combining-in of a trait or mix-in.
         */
        Incorporates,
        /**
         * Represents that the class being composed is one of the enumeration of
         * a specified type.
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
     * Represents one contribution to the definition of a class. A class (with
     * the term used in the abstract sense, meaning any class, interface, mixin,
     * trait, value, enum, or service) can be composed of any number of
     * contributing components.
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
            assert composition != null && constant != null;

            m_composition = composition;
            m_constant    = constant;
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
            return m_constant;
            }

        @Override
        public boolean equals(Object obj)
            {
            if (!(obj instanceof Contribution))
                {
                return false;
                }

            Contribution that = (Contribution) obj;
            return this == that ||
                    (this.m_composition == that.m_composition && this.m_constant.equals(that.m_constant));
            }

        @Override
        public String toString()
            {
            return m_composition.toString().toLowerCase() + ' ' + m_constant.getDescription();
            }

        /**
         * Defines the form of composition that this component contributes to
         * the class.
         */
        private Composition   m_composition;
        /**
         * Defines the class that was used as part of the composition.
         */
        private ClassConstant m_constant;
        }

    // ----- constants ---------------------------------------------------------

    /**
     * A mask for the various class category indicators.
     */
    public static final int CATEGORY = 0x0F;

    /**
     * A mask for specifying that the class is a singleton.
     */
    public static final int SINGLETON = 0x80;

    /**
     * A mask for specifying that the class is synthetic.
     */
    public static final int SYNTHETIC = 0x40;


    // ----- data members ------------------------------------------------------

    /**
     * The category of the class.
     */
    private Category m_category = Category.Class;

    /**
     * True if the class is a singleton within its module/container at runtime.
     */
    private boolean m_fSingleton;

    /**
     * True if the class is synthetic (created by the compiler, not explicitly
     * specified by the developer.
     */
    private boolean m_fSynthetic;

    /**
     * The name-to-type information for type parameters.
     */
    private ListMap<CharStringConstant, ClassConstant> m_mapParams;

    /**
     * The contributions that make up this class.
     */
    private List<Contribution> m_listContribs;
    }
