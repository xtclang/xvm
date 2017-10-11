package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;
import java.util.function.Consumer;

import org.xvm.asm.Component;
import org.xvm.asm.CompositeComponent;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.TypedefStructure;

import org.xvm.runtime.TypeSet;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents a type that is defined by some other structure within the module.
 * Specifically, the definition pointed to by this TypeConstant can be any one of:
 * <p/>
 * <ul>
 * <li>{@link ModuleConstant} for a module</li>
 * <li>{@link PackageConstant} for a package</li>
 * <li>{@link ClassConstant} for a class</li>
 * <li>{@link TypedefConstant} for a typedef</li>
 * <li>{@link PropertyConstant} for a class' type parameter</li>
 * <li>{@link RegisterConstant} for a method's type parameter</li>
 * <li>{@link ThisClassConstant} to indicate the auto-narrowing "this" class</li>
 * <li>{@link ParentClassConstant} for an auto-narrowing parent of an auto-narrowing class</li>
 * <li>{@link ChildClassConstant} for a named auto-narrowing child of an auto-narrowing class</li>
 * <li>{@link UnresolvedNameConstant} for a definition that has not been resolved at this point</li>
 * </ul>
 *
 * @author cp 2017.04.25
 */
public class TerminalTypeConstant
        extends TypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param format the format of the Constant in the stream
     * @param in     the DataInput stream to read the Constant value from
     *
     * @throws IOException if an issue occurs reading the Constant value
     */
    public TerminalTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iDef = readIndex(in);
        }

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param constId a ModuleConstant, PackageConstant, or ClassConstant
     */
    public TerminalTypeConstant(ConstantPool pool, Constant constId)
        {
        super(pool);

        assert !(constId instanceof TypeConstant);

        switch (constId.getFormat())
            {
            case Module:
            case Package:
            case Class:
            case Typedef:
            case Property:
            case Register:
            case ThisClass:
            case ParentClass:
            case ChildClass:
            case UnresolvedName:
                break;

            default:
                throw new IllegalArgumentException("constant " + constId.getFormat()
                        + " is not a Module, Package, Class, Typedef, or formal type parameter");
            }

        m_constId = constId;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------


    @Override
    public boolean isImmutabilitySpecified()
        {
        return false;
        }

    @Override
    public boolean isAccessSpecified()
        {
        return false;
        }

    @Override
    public Access getAccess()
        {
        return Access.PUBLIC;
        }

    @Override
    public boolean isParamsSpecified()
        {
        return false;
        }

    @Override
    public boolean isAnnotated()
        {
        return false;
        }

    @Override
    public boolean isSingleDefiningConstant()
        {
        return true;
        }

    @Override
    public Constant getDefiningConstant()
        {
        Constant constId = m_constId;
        if (constId instanceof ResolvableConstant)
            {
            Constant constResolved = ((ResolvableConstant) constId).getResolvedConstant();
            if (constResolved != null)
                {
                m_constId = constId = constResolved;
                }
            }
        return constId;
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return m_constId.isAutoNarrowing();
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        return (TypeConstant) simplify();
        }


    // ----- run-time support -------------------------------------------------------------------

    @Override
    public boolean isA(TypeConstant that)
        {
        if (super.isA(that))
            {
            return true;
            }

        // the only "extra" scenario we can cover at this layer is:
        // that = T (formal parameter type)
        // this = U (another formal parameter type), where U extends T
        Constant constIdThis = this.getDefiningConstant();
        Constant constIdThat = that.getDefiningConstant();

        if (constIdThis.getFormat() == Format.Property &&
            constIdThat.getFormat() == Format.Property)
            {
            PropertyConstant propThis = (PropertyConstant) constIdThis;
            PropertyConstant propThat = (PropertyConstant) constIdThat;

            PropertyStructure property = (PropertyStructure) propThis.getComponent();
            Constant constTypeId = property.getType().getDefiningConstant();
            if (constTypeId.getFormat() == Format.Property)
                {
                PropertyConstant propThisType = (PropertyConstant) constTypeId;
                return propThisType.getName().equals(propThat.getName());
                }
            }
        return false;
        }

    @Override
    public boolean consumesFormalType(String sTypeName, TypeSet types, Access access)
        {
        return false;
        }

    @Override
    public boolean producesFormalType(String sTypeName, TypeSet types, Access access)
        {
        Constant constId = getDefiningConstant();

        return constId.getFormat() == Format.Property &&
            ((PropertyConstant) constId).getName().equals(sTypeName);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.TerminalType;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_constId.containsUnresolved();
        }

    @Override
    public Constant simplify()
        {
        m_constId = m_constId.simplify();

        // compile down all of the types that refer to typedefs so that they refer to the underlying
        // types instead
        if (m_constId instanceof TypedefConstant)
            {
            Component    typedef   = ((TypedefConstant) m_constId).getComponent();
            TypeConstant constType;
            if (typedef instanceof CompositeComponent)
                {
                List<Component> typdefs = ((CompositeComponent) typedef).components();
                constType = (TypeConstant) ((TypedefStructure) typdefs.get(0)).getType().simplify();
                for (int i = 1, c = typdefs.size(); i < c; ++i)
                    {
                    TypeConstant constTypeN = (TypeConstant) ((TypedefStructure) typdefs.get(i)).getType().simplify();
                    if (!constType.equals(constTypeN))
                        {
                        // typedef points to more than one type, conditionally, so just leave the
                        // typedef in place
                        return this;
                        }
                    }
                }
            else
                {
                constType = (TypeConstant) ((TypedefStructure) typedef).getType().simplify();
                }
            assert constType != null;
            return constType;
            }

        return this;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constId);
        }

    @Override
    protected Object getLocator()
        {
        return m_constId.getFormat() == Format.UnresolvedName
                ? null
                : m_constId;
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        TerminalTypeConstant that = (TerminalTypeConstant) obj;
        Constant constThis = this.m_constId;
        if (constThis instanceof ResolvableConstant)
            {
            constThis = ((ResolvableConstant) constThis).unwrap();
            }
        Constant constThat = that.m_constId;
        if (constThat instanceof ResolvableConstant)
            {
            constThat = ((ResolvableConstant) constThat).unwrap();
            }
        return constThis.compareTo(constThat);
        }

    @Override
    public String getValueString()
        {
        return m_constId.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constId = getConstantPool().getConstant(m_iDef);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constId = pool.register(m_constId);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constId.getPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constId.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that defines this type.
     */
    private transient int m_iDef;

    /**
     * The class referred to. May be an IdentityConstant (ModuleConstant, PackageConstant,
     * ClassConstant, TypedefConstant, PropertyConstant), or a PseudoConstant (ThisClassConstant,
     * ParentClassConstant, ChildClassConstant, RegisterConstant, or UnresolvedNameConstant).
     */
    private Constant m_constId;
    }
