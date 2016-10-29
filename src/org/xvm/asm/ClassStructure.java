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
        m_constModule = (ModuleConstant) getConstantPool().getConstant(readIndex(in));

        super.disassemble(in);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constModule = (ModuleConstant) pool.register(m_constModule);

        super.registerConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        writePackedLong(out, m_constModule == null ? -1 : m_constModule.getPosition());

        super.assemble(out);
        }

    // TODO validate

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription())
                .append(", import-module=")
                .append(m_constModule == null ? "n/a" : m_constModule);
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

    public Map<CharStringConstant, Constant> getTypeParams()
        {
        return Collections.unmodifiableMap(m_mapParams);
        }

    // TODO type parameters: index (contiguous starting with 0), name (unique), type (defaulting to "Object")
    // public addTypeParameter(String sName, sType)


    // ----- enumeration: class categories -------------------------------------

    /**
     * Types of classes.
     * TODO what about "type" i.e. a class without identity?
     */
    public enum Category
        {
        Interface,
        Trait,
        Mixin,
        Class,
        Service,
        Value,
        Enum,
        }


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
    private ListMap<CharStringConstant, ? extends Constant> m_mapParams;
    }

