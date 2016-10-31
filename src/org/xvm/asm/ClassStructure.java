package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
import java.util.Map;

import org.xvm.asm.StructureContainer.MethodContainer;
import org.xvm.asm.ConstantPool.CharStringConstant;
import org.xvm.asm.ConstantPool.ClassConstant;

import org.xvm.util.ListMap;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents an entire Class.
 *
 * @author cp 2016.04.14
 */
public class ClassStructure
        extends MethodContainer
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
                mapNew.put((CharStringConstant) pool.register(entry.getKey()), (ClassConstant) pool.register(entry.getValue()));
                }
            m_mapParams = mapNew;
            }

        super.registerConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(m_category.ordinal() | (m_fSingleton ?SINGLETON : 0) | (m_fSynthetic ? SYNTHETIC : 0));
        assembleTypeParams(m_mapParams, out);

        super.assemble(out);
        }

    // TODO validate

    @Override
    public String getDescription()
        {
        // <K extends Hashable, V> -- because V is "extends Object" .. see Constants

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
            for (Map.Entry<CharStringConstant, ClassConstant> entry : map.entrySet())
                {
                sb.append(entry.getKey().getValue());
                if (entry.getValue())

                entry.getValue()
                }
            sb.append('>');
            }

        sb.append(", singleton=")
          .append(m_fSingleton)
          .append(", synthetic=")
          .append(m_fSynthetic);
        return sb.toString();
        }


    // ----- Object methods ----------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof PackageStructure && super.equals(obj)))
            {
            return false;
            }

        // compare imported modules
        PackageStructure that = (PackageStructure) obj;
        return Handy.equals(this.m_constModule, that.m_constModule);
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

    public Category getCategory()
        {
        return m_category;
        }

    public void setCategory(Category category)
        {
        m_category = category;
        markModified();
        }
    
    public boolean isSingleton()
        {
        return m_fSingleton;
        }
    
    public void setSingleton(boolean fSingleton)
        {
        m_fSingleton = fSingleton;
        markModified();
        }

    public boolean isSynthetic()
        {
        return m_fSynthetic;
        }

    public void setSynthetic(boolean fSynthetic)
        {
        m_fSynthetic = fSynthetic;
        markModified();
        }

    public Map<CharStringConstant, ? extends Constant> getTypeParams()
        {
        final ListMap<CharStringConstant, ? extends Constant> map = m_mapParams;
        return map == null ? Collections.EMPTY_MAP : Collections.unmodifiableMap(map);
        }

    // TODO type parameters: index (contiguous starting with 0), name (unique), type (defaulting to "Object")
    // public addTypeParameter(String sName, sType)


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
    private Category m_category;

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
    }

