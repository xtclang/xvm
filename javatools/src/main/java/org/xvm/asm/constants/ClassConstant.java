package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a Class constant, which identifies a specific class structure.
 */
public sealed class ClassConstant
        extends NamedConstant
        permits NativeRebaseConstant {
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
            throws IOException {
        super(pool, format, in);
    }

    /**
     * Construct a constant whose value is a class identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the module, package, class, or method that contains this class
     * @param sName        the unqualified class name
     */
    public ClassConstant(ConstantPool pool, IdentityConstant constParent, String sName) {
        super(pool, constParent, sName);

        switch (constParent.getFormat()) {
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
    public MethodConstant findConstructor(TypeConstant... types) {
        ClassStructure structClz = getComponent();
        if (structClz == null) {
            throw new IllegalStateException("could not find class " + this);
        }
        return structClz.findConstructor(types).getIdentityConstant();
    }

    /**
     * @return the parent (containing) class or null if this class is the "outermost"
     */
    public ClassConstant getParentClass() {
        IdentityConstant parent = getParentConstant();
        while (true) {
            // exhaustive over the sealed IdentityConstant tree: the old format switch treated
            // every kind it had never heard of as a silent terminator; each kind now states
            // its role, and a new identity kind is a compile error instead of "no parent".
            // The arms marked "was the silent default" keep the old behavior byte-for-byte.
            switch (parent) {
            case NativeRebaseConstant _  -> { return null; }        // was the silent default
            case ClassConstant clz       -> { return clz; }
            case FormalTypeChildConstant _ -> { return null; }      // was the silent default
            case PropertyConstant _,
                 MethodConstant _        -> parent = parent.getNamespace(); // use its parent
            case ModuleConstant _,
                 PackageConstant _       -> { return null; }  // packages/modules terminate
            case DecoratedClassConstant _,
                 MultiMethodConstant _,
                 TypedefConstant _,
                 TypeParameterConstant _,
                 DynamicFormalConstant _,
                 PureIdentityConstant _  -> { return null; }        // was the silent default
            }
        }
    }

    /**
     * @return the "outermost" class (including itself) that is not a package or module
     */
    public ClassConstant getOutermost() {
        ClassConstant    outermost = this;
        IdentityConstant parent    = getParentConstant();
        while (true) {
            // exhaustive over the sealed IdentityConstant tree; see getParentClass()
            switch (parent) {
            case NativeRebaseConstant _  -> { return outermost; }   // was the silent default
            case ClassConstant clz       -> outermost = clz;
            case FormalTypeChildConstant _ -> { return outermost; } // was the silent default
            case PropertyConstant _,
                 MethodConstant _        -> { }               // ignored (we'll use its parent)
            case ModuleConstant _,
                 PackageConstant _       -> { return outermost; } // packages/modules terminate
            case DecoratedClassConstant _,
                 MultiMethodConstant _,
                 TypedefConstant _,
                 TypeParameterConstant _,
                 DynamicFormalConstant _,
                 PureIdentityConstant _  -> { return outermost; }   // was the silent default
            }

            parent = parent.getNamespace();
        }
    }

    public int getDepthFromOutermost() {
        int cLevelsDown = 0;
        IdentityConstant parent = getParentConstant();
        while (true) {
            // exhaustive over the sealed IdentityConstant tree; see getParentClass()
            switch (parent) {
            case NativeRebaseConstant _,
                 FormalTypeChildConstant _ -> { return cLevelsDown; } // was the silent default
            case ClassConstant _,
                 PropertyConstant _,
                 MethodConstant _,
                 MultiMethodConstant _   -> {
                ++cLevelsDown;
                parent = parent.getParentConstant();
            }
            case ModuleConstant _,
                 PackageConstant _       -> { return cLevelsDown; } // passed the outermost
            case DecoratedClassConstant _,
                 TypedefConstant _,
                 TypeParameterConstant _,
                 DynamicFormalConstant _,
                 PureIdentityConstant _  -> { return cLevelsDown; } // was the silent default
            }
        }
    }

    /**
     * This method is almost identical to {@link #getOutermost()} except that a method parent
     * terminates the "next parent up" search.
     *
     * @return the class that represents an auto-narrowing base
     */
    public ClassConstant getAutoNarrowingBase() {
        ClassConstant    outermost = this;
        IdentityConstant parent    = outermost.getParentConstant();
        while (true) {
            // exhaustive over the sealed IdentityConstant tree; see getParentClass()
            switch (parent) {
            case NativeRebaseConstant _    -> { return outermost; } // was the silent default
            case ClassConstant clz         -> outermost = clz;
            case FormalTypeChildConstant _ -> { return outermost; } // was the silent default
            case PropertyConstant _        -> { }         // ignored (we'll use its parent)
            case MethodConstant _,
                 ModuleConstant _,
                 PackageConstant _         -> { return outermost; } // terminate this search
            case DecoratedClassConstant _,
                 MultiMethodConstant _,
                 TypedefConstant _,
                 TypeParameterConstant _,
                 DynamicFormalConstant _,
                 PureIdentityConstant _    -> { return outermost; } // was the silent default
            }

            parent = parent.getParentConstant();
        }
    }

    public int getDepthFromAutoNarrowingBase() {
        int cLevelsDown = 0;
        IdentityConstant parent = getParentConstant();
        while (true) {
            // exhaustive over the sealed IdentityConstant tree; see getParentClass()
            switch (parent) {
            case NativeRebaseConstant _,
                 FormalTypeChildConstant _ -> { return cLevelsDown; } // was the silent default
            case ClassConstant _,
                 PropertyConstant _      -> {
                ++cLevelsDown;
                parent = parent.getParentConstant();
            }
            case MethodConstant _,
                 ModuleConstant _,
                 PackageConstant _       -> { return cLevelsDown; } // passed the outermost
            case DecoratedClassConstant _,
                 MultiMethodConstant _,
                 TypedefConstant _,
                 TypeParameterConstant _,
                 DynamicFormalConstant _,
                 PureIdentityConstant _  -> { return cLevelsDown; } // was the silent default
            }
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
    public Constant calculateAutoNarrowingConstant(ClassConstant constThatClass) {
        ClassConstant constThisClass = this;
        if (!constThisClass.getComponent().isAutoNarrowingAllowed() ||
            !constThatClass.getComponent().isAutoNarrowingAllowed()) {
            return constThatClass;
        }

        // if "this:class" is the same as constId, then use ThisClassConstant(constId)
        if (constThisClass.equals(constThatClass)) {
            return new ThisClassConstant(getConstantPool(), constThisClass);
        }

        // check that the "outermost class" for both "this:class" and constId are the same
        ClassConstant constThisOutermost = constThisClass.getAutoNarrowingBase();
        ClassConstant constThatOutermost = constThatClass.getAutoNarrowingBase();
        if (!constThisOutermost.equals(constThatOutermost)) {
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
        while (cThisDepth > cThatDepth) {
            constPath = pool.ensureParentClassConstant(constPath);
            constThis = constThis.getParentConstant();
            --cThisDepth;
        }
        while (cThatDepth > cThisDepth) {
            ++cReDescend;
            constThat = constThat.getParentConstant();
            --cThatDepth;
        }
        while (!constThis.equals(constThat)) {
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
    private PseudoConstant redescend(PseudoConstant constPath, IdentityConstant constChild, int cLevels) {
        if (cLevels == 0) {
            return constPath;
        }

        if (cLevels > 1) {
            constPath = redescend(constPath, constChild.getParentConstant(), cLevels-1);
        }

        return getConstantPool().ensureChildClassConstant(constPath, constChild.getName());
    }

    /**
     * @return if this ClassConstant represents an implicitly imported class return it's
     *         implicit name; null otherwise
     */
    public String getImplicitImportName() {
        return getModuleConstant().isEcstasyModule()
                ? ConstantPool.getImplicitImportName("ecstasy." + getPathString())
                : null;
    }


    // ----- IdentityConstant methods --------------------------------------------------------------

    /**
     * Covariant narrowing: this identity always names a ClassStructure, so callers no longer downcast. Eight overrides like this one remove 222 casts.
     *
     * @return the ClassStructure this identity names
     */
    @Override
    public ClassStructure getComponent() {
        return (ClassStructure) super.getComponent();
    }

    @Override
    public IdentityConstant replaceParentConstant(IdentityConstant idParent) {
        return new ClassConstant(getConstantPool(), idParent, getName());
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    protected ClassConstant copyForAdoption(AdoptionContext context) {
        // Class identity is a logical parent+name path. Register the parent in the target pool before
        // publishing this shell so later component/JIT caches are rebuilt by the target owner.
        var pool = context.pool();
        return new ClassConstant(pool, pool.register(getParentConstant()), getName());
    }

    @Override
    public Format getFormat() {
        return Format.Class;
    }

    @Override
    public boolean isClass() {
        return true;
    }

    @Override
    public TypeConstant getType() {
        ClassStructure clz = getComponent();

        return clz.isVirtualChild()   ? getConstantPool().ensureVirtualChildTypeConstant(
                                            getParentConstant().getType(), getName())
             : clz.isInnerChild()     ? getConstantPool().ensureInnerChildTypeConstant(
                                            getParentConstant().getType(), this)
             : clz.isAnonInnerClass() ? getConstantPool().ensureAnonymousClassTypeConstant(
                                            getParentConstant().getType(), this)
             : super.getType();
    }

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that) {
        return that.getConstantPool().ensureClassConstant(that, getName());
    }

    @Override
    public String getValueString() {
        String sImport = getImplicitImportName();
        return sImport == null
                ? super.getValueString()
                : sImport;
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription() {
        Constant constParent = getNamespace();
        while (constParent instanceof ClassConstant idParent) {
            constParent = idParent.getNamespace();
        }

        return "class=" + getValueString() + ", " + constParent.getDescription();
    }
}
