package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a Class constant, which identifies a specific class structure.
 */
public class ClassConstant
        extends NamedConstant
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
    public ClassConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct a constant whose value is a class identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the module, package, class, or method that contains this class
     * @param sName        the unqualified class name
     */
    public ClassConstant(ConstantPool pool, IdentityConstant constParent, String sName)
        {
        super(pool, constParent, sName);

        switch (constParent.getFormat())
            {
            case Module:
            case Package:
            case Class:
            case Method:
            case Property:
                break;

            default:
                throw new IllegalArgumentException("invalid parent format: " + constParent);
            }
        }


    // ----- ClassConstant methods -----------------------------------------------------------------

    /**
     * Find the specified constructor of this class.
     *
     * @param types  the types of the constructor parameters
     *
     * @return the constructor; never null
     *
     * @throws IllegalStateException if the constructor cannot be found
     */
    public MethodConstant findConstructor(TypeConstant... types)
        {
        ClassStructure structClz = (ClassStructure) getComponent();
        if (structClz == null)
            {
            throw new IllegalStateException("could not find class " + this);
            }
        return structClz.findConstructor(types).getIdentityConstant();
        }

    /**
     * @return true iff this class is a virtual child class
     */
    public boolean isVirtualChild()
        {
        ClassStructure clz = (ClassStructure) getComponent();
        return clz != null && clz.isVirtualChild();
        }

    /**
     * @return the "outermost" class
     */
    public ClassConstant getOutermost()
        {
        ClassConstant    outermost = this;
        IdentityConstant parent    = outermost.getParentConstant();
        while (true)
            {
            switch (parent.getFormat())
                {
                case Class:
                    outermost = (ClassConstant) parent;
                    break;

                case Property:
                case Method:
                    // ignored (we'll use its parent)
                    break;

                // packages and modules "terminate" this search
                default:
                    return outermost;
                }

            parent = parent.getNamespace();
            }
        }

    public int getDepthFromOutermost()
        {
        int cLevelsDown = 0;
        IdentityConstant parent = getParentConstant();
        while (true)
            {
            switch (parent.getFormat())
                {
                case Class:
                case Property:
                case Method:
                case MultiMethod:
                    ++cLevelsDown;
                    break;

                // packages and modules mean we've passed the outer-most
                default:
                    return cLevelsDown;
                }

            parent = parent.getParentConstant();
            }
        }

    /**
     * @return the class that represents an auto-narrowing base
     */
    public ClassConstant getAutoNarrowingBase()
        {
        ClassConstant    outermost = this;
        IdentityConstant parent    = outermost.getParentConstant();
        while (true)
            {
            switch (parent.getFormat())
                {
                case Class:
                    outermost = (ClassConstant) parent;
                    break;

                case Property:
                    // ignored (we'll use its parent)
                    break;

                // methods, packages, modules all "terminate" this search
                default:
                    return outermost;
                }

            parent = parent.getParentConstant();
            }
        }

    public int getDepthFromAutoNarrowingBase()
        {
        int cLevelsDown = 0;
        IdentityConstant parent = getParentConstant();
        while (true)
            {
            switch (parent.getFormat())
                {
                case Class:
                case Property:
                    ++cLevelsDown;
                    break;

                // methods, packages, modules all mean we've passed the outer-most
                default:
                    return cLevelsDown;
                }

            parent = parent.getParentConstant();
            }
        }

    /**
     * Calculate an auto-narrowing constant that describes a "relative path" from this
     * class constant to the specified one.
     *
     * @param constThatClass  the class constant to calculate the "path" for
     *
     * @return a PseudoConstant representing the path or the specified constant itself if no path
     *         can be found
     */
    public Constant calculateAutoNarrowingConstant(ClassConstant constThatClass)
        {
        ClassConstant constThisClass = this;
        if (!constThisClass.getComponent().isAutoNarrowingAllowed() ||
            !constThatClass.getComponent().isAutoNarrowingAllowed())
            {
            return constThatClass;
            }

        // if "this:class" is the same as constId, then use ThisClassConstant(constId)
        if (constThisClass.equals(constThatClass))
            {
            return new ThisClassConstant(getConstantPool(), constThisClass);
            }

        // check that the "outermost class" for both "this:class" and constId are the same
        ClassConstant constThisOutermost = constThisClass.getAutoNarrowingBase();
        ClassConstant constThatOutermost = constThatClass.getAutoNarrowingBase();
        if (!constThisOutermost.equals(constThatOutermost))
            {
            return constThatClass;
            }

        // the two classes are related, so figure out how to describe "that" in relation
        // to "this"
        ConstantPool     pool       = getConstantPool();
        PseudoConstant   constPath  = pool.ensureThisClassConstant(constThisClass);
        IdentityConstant constThis  = constThisClass;
        IdentityConstant constThat  = constThatClass;
        int              cThisDepth = constThisClass.getDepthFromAutoNarrowingBase();
        int              cThatDepth = constThatClass.getDepthFromAutoNarrowingBase();
        int              cReDescend = 0;
        while (cThisDepth > cThatDepth)
            {
            constPath = pool.ensureParentClassConstant(constPath);
            constThis = constThis.getParentConstant();
            --cThisDepth;
            }
        while (cThatDepth > cThisDepth)
            {
            ++cReDescend;
            constThat = constThat.getParentConstant();
            --cThatDepth;
            }
        while (!constThis.equals(constThat))
            {
            assert cThisDepth == cThatDepth && cThisDepth >= 0;

            ++cReDescend;
            constPath = pool.ensureParentClassConstant(constPath);

            constThis = constThis.getParentConstant();
            constThat = constThat.getParentConstant();
            --cThisDepth;
            --cThatDepth;
            }

        return redescend(constPath, constThatClass, cReDescend);
        }

    /**
     * Recursively build onto the passed path to navigate the specified number of levels down to the
     * specified child.
     *
     * @param constPath   the path, thus far
     * @param constChild  the child to navigate to
     * @param cLevels     the number of levels down that the child is
     *
     * @return a PseudoConstant that represents the navigation down to the child
     */
    private PseudoConstant redescend(PseudoConstant constPath, IdentityConstant constChild, int cLevels)
        {
        if (cLevels == 0)
            {
            return constPath;
            }

        if (cLevels > 1)
            {
            constPath = redescend(constPath, constChild.getParentConstant(), cLevels-1);
            }

        return getConstantPool().ensureChildClassConstant(constPath, constChild.getName());
        }


    /**
     * @return if this ClassConstant represents an implicitly imported class return it's
     *         implicit name; null otherwise
     */
    public String getImplicitImportName()
        {
        return getModuleConstant().isEcstasyModule()
                ? ConstantPool.getImplicitImportName("ecstasy." + getPathString())
                : null;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Class;
        }

    @Override
    public boolean isClass()
        {
        return true;
        }

    @Override
    public TypeConstant getType()
        {
        return isVirtualChild()
                ? getConstantPool().ensureVirtualChildTypeConstant(
                        getParentConstant().getType(), getName())
                : super.getType();
        }

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that)
        {
        return that.getConstantPool().ensureClassConstant(that, getName());
        }

    @Override
    public String getValueString()
        {
        String sImport = getImplicitImportName();
        return sImport == null
                ? super.getValueString()
                : sImport;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        Constant constParent = getNamespace();
        while (constParent instanceof ClassConstant idParent)
            {
            constParent = idParent.getNamespace();
            }

        return "class=" + getValueString() + ", " + constParent.getDescription();
        }
    }