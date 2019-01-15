package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ComponentBifurcator;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PackageStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;
import org.xvm.asm.Version;
import org.xvm.asm.VersionTree;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodType;
import org.xvm.asm.op.Construct_0;
import org.xvm.asm.op.Construct_1;
import org.xvm.asm.op.Construct_N;
import org.xvm.asm.op.L_Set;
import org.xvm.asm.op.Return_0;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Composition.Default;

import org.xvm.compiler.ast.StatementBlock.RootContext;
import org.xvm.util.Handy;
import org.xvm.util.ListMap;
import org.xvm.util.Severity;

import static org.xvm.compiler.Constants.ECSTASY_MODULE;
import static org.xvm.compiler.Constants.X_PKG_IMPORT;
import static org.xvm.compiler.Lexer.CR;
import static org.xvm.compiler.Lexer.LF;
import static org.xvm.compiler.Lexer.isLineTerminator;
import static org.xvm.compiler.Lexer.isValidQualifiedModule;
import static org.xvm.compiler.Lexer.isWhitespace;
import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.indentLines;


/**
 * A type declaration.
 */
public class TypeCompositionStatement
        extends ComponentStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public TypeCompositionStatement(
            Source            source,
            long              lStartPos,
            long              lEndPos,
            Expression        condition,
            List<Token>       modifiers,
            List<Annotation>  annotations,
            Token             category,
            Token             name,
            List<Token>       qualified,
            List<Parameter>   typeParams,
            List<Parameter>   constructorParams,
            List<Composition> compositions,
            StatementBlock    body,
            Token             doc)
        {
        super(lStartPos, lEndPos);

        this.source            = source;
        this.condition         = condition;
        this.modifiers         = modifiers;
        this.annotations       = annotations;
        this.category          = category;
        this.name              = name;
        this.qualified         = qualified;
        this.typeParams        = typeParams;
        this.constructorParams = constructorParams;
        this.compositions      = compositions;
        this.body              = body;
        this.doc               = doc;
        }

    /**
     * Used by enumeration value declarations.
     */
    public TypeCompositionStatement(
            List<Annotation>     annotations,
            Token                name,
            List<TypeExpression> typeArgs,
            List<Expression>     args,
            StatementBlock       body,
            Token                doc,
            long                 lStartPos,
            long                 lEndPos)
        {
        super(lStartPos, lEndPos);

        this.annotations = annotations;
        this.category    = new Token(name.getStartPosition(), name.getStartPosition(), Token.Id.ENUM_VAL);
        this.name        = name;
        this.typeArgs    = typeArgs;
        this.args        = args;
        this.body        = body;
        this.doc         = doc;
        }

    /**
     * Used by anonymous inner class declarations.
     */
    public TypeCompositionStatement(
            NewExpression     parent,
            List<Annotation>  annotations,
            Token             category,
            Token             name,
            List<Composition> compositions,
            List<Expression>  args,
            StatementBlock    body,
            long              lStartPos,
            long              lEndPos)
        {
        super(lStartPos, lEndPos);

        this.annotations  = annotations;
        this.category     = category;
        this.name         = name;
        this.compositions = compositions;
        this.args         = args;
        this.body         = body;
        this.anonymous    = true;

        setParent(parent);
        introduceParentage();
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public Source getSource()
        {
        return source == null
                ? super.getSource()
                : source;
        }

    @Override
    public Access getDefaultAccess()
        {
        Access access = getAccess(modifiers);
        return access == null
                ? super.getDefaultAccess()
                : access;
        }

    public Token getCategory()
        {
        return category;
        }

    public String getName()
        {
        if (category.getId() == Token.Id.MODULE)
            {
            StringBuilder sb = new StringBuilder();
            for (Token suffix : qualified)
                {
                sb.append('.')
                  .append(suffix.getValue());
                }
            return sb.substring(1).toString();
            }
        else
            {
            return (String) name.getValue();
            }
        }

    /**
     * Determine the zone within which the type is declared. The rules for declaration change
     * depending on what the zone is; for example, the meaning of the "static" keyword differs
     * between each of the top level, inner class, and in-method zones.
     *
     * @return the declaration zone of the type represented by this TypeCompositionStatement
     */
    public Zone getDeclarationZone()
        {
        Component structThis = getComponent();
        switch (structThis.getFormat())
            {
            case MODULE:
            case PACKAGE:
                // modules and components are always top level
                return Zone.TopLevel;

            case CLASS:
            case INTERFACE:
            case SERVICE:
            case CONST:
            case ENUM:
            case MIXIN:
                Component structParent = structThis.getParent();
                switch (structParent.getFormat())
                    {
                    case MODULE:
                    case PACKAGE:
                        return Zone.TopLevel;

                    case CLASS:
                    case INTERFACE:
                    case SERVICE:
                    case CONST:
                    case ENUM:
                    case ENUMVALUE:
                    case MIXIN:
                        return Zone.InClass;

                    case METHOD:
                        return Zone.InMethod;

                    default:
                        throw new IllegalStateException("this=" + structThis.getFormat()
                                + ", parent=" + structParent.getFormat());
                    }

            case ENUMVALUE:
                // enum values are ALWAYS nested inside an enumeration class
                return Zone.InClass;

            default:
                throw new IllegalStateException("this=" + structThis.getFormat());
            }
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compile phases ------------------------------------------------------------------------

    /**
     * Add an enclosed type composition to this type composition. Because the parser may have to
     * wrap the parsed type composition into a statement block, this method takes a Statement
     * instead of a TypeCompositionStatement, but the idea is the same: the argument to this method
     * should be an object that was returned from {@link org.xvm.compiler.Parser#parseSource()}.
     * <p/>
     * This method is used to combine multiple files that were parsed independently into a single
     * parse tree -- a single "AST" for an entire module.
     *
     * @param stmt  a statement returned from {@link org.xvm.compiler.Parser#parseSource()}
     */
    public void addEnclosed(Statement stmt)
        {
        // statement cannot be null, except that during development we ignore certain errors to
        // allow the compiler to progress, so tolerate that condition for the time being
        if (stmt == null)
            {
            return;
            }

        if (enclosed == null)
            {
            enclosed = adopt(new StatementBlock(new ArrayList<>()));
            enclosed.markFileBoundary();
            ensureBody().addStatement(enclosed);
            }

        enclosed.addStatement(stmt);
        }

    public StatementBlock ensureBody()
        {
        if (body == null)
            {
            body = adopt(new StatementBlock(new ArrayList<>()));
            }
        return body;
        }

    @Override
    protected void registerStructures(StageMgr mgr, ErrorListener errs)
        {
        if (getComponent() != null)
            {
            // stage assumed to be complete
            return;
            }

        // if this is a module, this stage is responsible for linking each child AstNode to its
        // parent (the parents already know the children, as a result of parsing)
        AstNode parent = getParent();
        if (parent == null)
            {
            assert category.getId() == Token.Id.MODULE;
            introduceParentage();
            }

        // create the structure for this module, package, or class (etc.)
        String              sName        = (String) name.getValue();
        Access              access       = getDefaultAccess();
        Component           container    = parent == null ? null : parent.getComponent();
        ClassStructure      containerClz = null;
        ConstantPool        pool         = pool();
        ConditionalConstant constCond    = condition == null ? null : condition.toConditionalConstant();
        if (container != null)
            {
            if (container instanceof ModuleStructure && sName.equals(X_PKG_IMPORT))
                {
                // it is illegal to take the "Ecstasy" namespace, because doing so would effectively
                // hide the Ecstasy core module
                name.log(errs, getSource(), Severity.ERROR, Compiler.NAME_COLLISION, X_PKG_IMPORT);
                }
            else
                {
// TODO use container.id to find the parent and what class contains this one (and if it's a module or package)
// TODO if this is a not an enum/enumvalue/module/package and if the container class is not a module or package, then this is an inner class
                Component containerTmp = container;
                while (true)
                    {
                    if (containerTmp instanceof ClassStructure)
                        {
                        containerClz = (ClassStructure) containerTmp;
                        break;
                        }

                    containerTmp = containerTmp.getParent();
                    }
                }
            }

        // TODO containerTmp of not-module/-package indicates virtual inner (container is not a method) or inner (is method)
        // TODO @Override on child implies that parent has same-name child, so auto-add "extends" (error if already there)
        // TODO no @Override on child implies no same-name child on parent super (error if there), default extends to Object
        // TODO child with extends clause cannot extend virtual child
        // TODO rules for class child vs. interface child vs. mixin child

        ClassStructure component = null;
        switch (category.getId())
            {
            case MODULE:
                if (constCond != null)
                    {
                    condition.log(errs, Severity.ERROR, Compiler.CONDITIONAL_NOT_ALLOWED);
                    }
                else if (container == null)
                    {
                    // validate the module name
                    String sModule = getName();
                    if (!isValidQualifiedModule(sModule))
                        {
                        log(errs, Severity.ERROR, Compiler.MODULE_BAD_NAME, sModule);
                        throw new CompilerException("unable to create module with illegal name: " + sModule);
                        }

                    // create the FileStructure and "this" ModuleStructure
                    FileStructure struct = new FileStructure(sModule);
                    component = struct.getModule();
                    pool      = struct.getConstantPool();

                    // create Ecstasy package for auto-import of core Ecstasy library
                    ModuleStructure modX;
                    if (getName().equals(ECSTASY_MODULE))
                        {
                        // yes, the Ecstasy core module imports itself, so by the law of turtles,
                        // there exists a class named Ecstasy.Ecstasy.Ecstasy.Ecstasy.Ecstasy.Object
                        modX = (ModuleStructure) component;
                        }
                    else
                        {
                        modX = struct.ensureModule(ECSTASY_MODULE);
                        modX.fingerprintRequired();
                        }

                    // "ecstasy" package
                    PackageStructure pkgX = component.createPackage(Access.PUBLIC, X_PKG_IMPORT, null);
                    pkgX.setSynthetic(true);
                    pkgX.setStatic(true);
                    pkgX.setImportedModule(modX);
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.MODULE_UNEXPECTED);
                    }
                break;

            case PACKAGE:
                if (container.isPackageContainer())
                    {
                    component = container.createPackage(access, sName, constCond);
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.PACKAGE_UNEXPECTED, container.toString());
                    }
                break;

            case CLASS:
            case INTERFACE:
            case SERVICE:
            case CONST:
            case ENUM:
            case ENUM_VAL:
            case MIXIN:
                if (container.isClassContainer())
                    {
                    Format format;
                    switch (category.getId())
                        {
                        case CLASS:
                            format = Format.CLASS;
                            break;

                        case INTERFACE:
                            format = Format.INTERFACE;
                            break;

                        case SERVICE:
                            format = Format.SERVICE;
                            break;

                        case CONST:
                            format = Format.CONST;
                            break;

                        case ENUM:
                            format = Format.ENUM;
                            break;

                        case ENUM_VAL:
                            format = Format.ENUMVALUE;
                            break;

                        case MIXIN:
                            format = Format.MIXIN;
                            break;

                        default:
                            throw new IllegalStateException();
                        }

                    component = container.createClass(getDefaultAccess(), format, sName, constCond);
                    if (anonymous)
                        {
                        component.setSynthetic(true);
                        }
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.CLASS_UNEXPECTED, container.toString());
                    }
                break;

            default:
                throw new UnsupportedOperationException("unable to guess structure for: "
                        + category.getId().TEXT);
            }
        setComponent(component);
        if (component == null)
            {
            // must have been an error
            return;
            }

        // documentation
        if (doc != null)
            {
            component.setDocumentation(extractDocumentation(doc));
            }

        // the "global" namespace is composed of the union of the top-level namespace and the "inner"
        // namespace of each component in the global namespace.
        //
        // modifiers for "top-level" namespace structures:
        // - "top-level" means nested within a file, module, or package structure
        // - static means "singleton"
        // - public means visible outside of the module
        // - protected means t.b.d.
        // - private means no visibility outside of the module
        //
        //              public      protected   private     static
        //              ----------  ----------  ----------  ----------
        // module       (implicit)                          (implicit)
        // package      x           x           x           (implicit)
        // class        x           x           x
        // interface    x           x           x
        // service      x           x           x           x
        // const        x           x           x           x
        // enum         x           x           x           (implicit)
        // mixin        x           x           x
        //
        // modifiers for "inner" namespace structures:
        // - "inner" means nested within a class
        // - static means "no ref to parent, no virtual new"; it only applies to something that can be new'd
        //   or init'd from a constant, so it does not apply to interface, or mixin
        //
        //              public      protected   private     static
        //              ----------  ----------  ----------  ----------
        // class        x           x           x           x
        // interface    x           x           x
        // service      x           x           x           x - required if parent is not const or service
        // const        x           x           x           x - required if parent is not const
        // enum         x           x           x           (implicit)
        // - enum val                                       (implicit)
        // mixin        x           x           x
        //
        // modifiers for "local" namespace structures:
        // - "local" means declared within a method; items declared within a method are not visible outside
        //   of (above on the hierarchy) the method
        // - static means "no ref to the method frame", i.e. no ability to capture, not even the "this"
        //   from the method
        //
        //              public      protected   private     static
        //              ----------  ----------  ----------  ----------
        // class                                            x
        // interface
        // service                                          x
        // const                                            x
        // enum                                             (implicit)
        // mixin
        int nAllowed = 0;
        switch (component.getFormat())
            {
            case SERVICE:
            case CONST:
            case CLASS:
                // class is not allowed to be declared static if it is top-level, otherwise all of
                // these can always be declared static
                if (!(component.getFormat() == Format.CLASS && getDeclarationZone() == Zone.TopLevel))
                    {
                    nAllowed |= Component.STATIC_BIT;
                    }
                // fall through
            case PACKAGE:
            case ENUM:
            case INTERFACE:
            case MIXIN:
                {
                // these are all allowed to be declared public/private/protected, except when they
                // appear inside a method body
                if (getDeclarationZone() != Zone.InMethod)
                    {
                    nAllowed |= Component.ACCESS_MASK;
                    }
                }
            }

        // validate modifiers
        boolean fExplicitlyStatic = false;
        if (modifiers != null && !modifiers.isEmpty())
            {
            int     nSpecified           = 0;
            boolean fExplicitlyPublic    = false;
            boolean fExplicitlyProtected = false;
            boolean fExplicitlyPrivate   = false;

            NextModifier: for (int i = 0, c = modifiers.size(); i < c; ++i)
                {
                Token token = modifiers.get(i);
                int     nBits;
                boolean fAlready;
                switch (token.getId())
                    {
                    case PUBLIC:
                        fAlready          = fExplicitlyPublic;
                        fExplicitlyPublic = true;
                        nBits             = Component.ACCESS_MASK;
                        break;

                    case PROTECTED:
                        fAlready             = fExplicitlyProtected;
                        fExplicitlyProtected = true;
                        nBits                = Component.ACCESS_MASK;
                        break;

                    case PRIVATE:
                        fAlready           = fExplicitlyPrivate;
                        fExplicitlyPrivate = true;
                        nBits              = Component.ACCESS_MASK;
                        break;

                    case STATIC:
                        fAlready          = fExplicitlyStatic;
                        fExplicitlyStatic = true;
                        nBits             = Component.STATIC_BIT;
                        break;

                    default:
                        throw new IllegalStateException("token=" + token);
                    }

                if (fAlready)
                    {
                    token.log(errs, getSource(), Severity.ERROR, Compiler.DUPLICATE_MODIFIER);
                    }
                else if ((nAllowed & nBits) == 0)
                    {
                    token.log(errs, getSource(), Severity.ERROR, Compiler.ILLEGAL_MODIFIER);
                    }
                else if ((nSpecified & nBits) != 0)
                    {
                    token.log(errs, getSource(), Severity.ERROR, Compiler.CONFLICTING_MODIFIER);
                    }

                nSpecified |= nBits;
                }

            // verification that if one access modifier is explicit, that the component correctly
            // used that access modifier
            if (fExplicitlyPublic ^ fExplicitlyProtected ^ fExplicitlyPrivate)
                {
                assert (component.getAccess() == Access.PUBLIC   ) == fExplicitlyPublic;
                assert (component.getAccess() == Access.PROTECTED) == fExplicitlyProtected;
                assert (component.getAccess() == Access.PRIVATE  ) == fExplicitlyPrivate;
                }
            }

        // inner const/service classes must be declared static if the parent is not const/service
        if (!fExplicitlyStatic && getDeclarationZone() == Zone.InClass)
            {
            if (component.getFormat() == Format.CONST)
                {
                // parent MUST be a const (because parent will be automatically captured, and a
                // const can't capture a non-const)
                if (container.getFormat() != Format.CONST)
                    {
                    log(errs, Severity.ERROR, Compiler.INNER_CONST_NOT_STATIC);
                    fExplicitlyStatic = true;
                    }
                }
            else if (component.getFormat() == Format.SERVICE)
                {
                // parent MUST be a const or a service (because parent is automatically captured,
                // and a service can't capture an object that isn't either a const or a service)
                if (container.getFormat() != Format.CONST && container.getFormat() != Format.SERVICE)
                    {
                    log(errs, Severity.ERROR, Compiler.INNER_SERVICE_NOT_STATIC);
                    fExplicitlyStatic = true;
                    }
                }
            }

        // configure the static bit on the component
        if (fExplicitlyStatic || component.getFormat().isImplicitlyStatic())
            {
            component.setStatic(true);
            }

        // validate that type parameters are allowed, and register them (the actual validation of
        // the type parameters themselves happens in a later phase)
        final ClassConstant OBJECT_CLASS = pool.clzObject();
        boolean fSingleton = false;
        switch (component.getFormat())
            {
            case MODULE:
            case PACKAGE:
                fSingleton = true;
                // type parameters are not permitted
                disallowTypeParams(errs);
                // constructor params are only allowed if they have defaulted values
                requireConstructorParamValues(errs);
                break;

            case ENUMVALUE:
                fSingleton = true;
                // type parameters are not permitted
                disallowTypeParams(errs);
                // number of type arguments must match the number of the enum's type parameters
                assert container instanceof ClassStructure && container.getFormat() == Format.ENUM;
                ClassStructure enumeration = (ClassStructure) container;
                if (enumeration.getTypeParams().size() != (typeArgs == null ? 0 : typeArgs.size()))
                    {
                    log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_MISMATCH);
                    }
                break;

            case SERVICE:
            case CONST:
                // these compositions are new-able, and thus can usually declare type parameters;
                // the exception is when the composition is not new-able, which is the case for
                // singleton compositions
                if (fExplicitlyStatic && getDeclarationZone() == Zone.TopLevel)
                    {
                    fSingleton = true;
                    disallowTypeParams(errs);
                    requireConstructorParamValues(errs);
                    break;
                    }
                // fall through
            case ENUM:
                // while an enum is not new-able (it is abstract), it can have type params which
                // must each be explicitly defined by each enum value; the same goes for constructor
                // params
            case CLASS:
            case INTERFACE:
            case MIXIN:
                // register the type parameters
                if (typeParams != null && !typeParams.isEmpty())
                    {
                    HashSet<String> names = new HashSet<>();
                    for (Parameter param : typeParams)
                        {
                        String sParam = param.getName();
                        if (names.contains(sParam))
                            {
                            log(errs, Severity.ERROR, Compiler.DUPLICATE_TYPE_PARAM, sName);
                            }
                        else
                            {
                            // add the type parameter information to the component
                            TypeExpression exprType  = param.getType();
                            TypeConstant   constType = exprType == null
                                    ? OBJECT_CLASS.getType()
                                    : exprType.ensureTypeConstant();
                            component.addTypeParam(sParam, constType);
                            }
                        }
                    }
                break;

            default:
                throw new IllegalStateException();
            }

        // validate and register annotations (as if they had been written as "incorporates" clauses)
        // note that the annotations are added to the end of the list, and in reverse order of how
        // they were encountered (as if they were the outer-most shells of a russian nesting doll)
        if (annotations != null && !annotations.isEmpty())
            {
            if (compositions == null)
                {
                compositions = new ArrayList<>();
                }
            for (int i = annotations.size()-1; i >= 0; --i)
                {
                Annotation annotation = annotations.get(i);
                annotation.ensureAnnotation(pool);
                compositions.add(new Composition.Incorporates(annotation));
                }
            }

        // validate compositions (at least what can be validated at this point)
        //
        //              extends     implements  delegates   incorporates  import*     into
        //              ----------  ----------  ----------  ------------  ----------  ----------
        // module       0/1 [1]     n           n           n
        // package      0/1 [1]     n           n           n             0/1 [2]
        // class        0/1 [3]     n           n           n
        // service      0/1 [4]     n           n           n
        // const        0/1 [1]     n           n           n
        // enum         0/1 [1]     n           n           n
        // enum-value       [5]     n           n           n
        // mixin        0/1 [6]     n           n           n                         0/1 [7]
        // interface    n   [8]     n [8]       n [9]
        //
        // [1] module/package/const/enum may explicitly extend a class or a const; otherwise extends
        //     Object
        // [2] package may import a module
        // [3] class may explicitly extend a class; otherwise extends Object (the one exception is
        //     Object itself, which does NOT extend itself)
        // [4] service may explicitly extend a class or service; otherwise extends Object
        // [5] enum values always implicitly extend the enum to which they belong
        // [6] mixin may explicitly extend a mixin
        // [7] mixins may specify a type that they can be mixed into; otherwise implicitly Object
        // [8] in the source code, an interface "extends" any number of interfaces, but the compiler
        //     produces those relationships using the "implements" composition
        // [9] a delegates clause on an interface simply implies default implementations for those
        //     methods
        //
        // at this point, the class names are not yet resolvable, so defer most of these checks
        // until the next phase. what must be done at this point:
        // 1. register imported modules, so that we can create the necessary module fingerprint
        //    structures to track dependencies;
        // 2. verify that only legitimate compositions are present for the type of this component;
        // 3. verify that no more than one "extends", "import*", or "into" clause is used (subject
        //    to the exception defined by rule #8 above)
        // 4. verify that a package with an import does not have a body
        boolean       fAlreadyExtends   = false;
        boolean       fAlreadyImports   = false;
        boolean       fAlreadyIntos     = false;
        ClassConstant constDefaultSuper = OBJECT_CLASS;
        ClassConstant constDefaultInto  = null;
        switch (component.getFormat())
            {
            case CLASS:
                if (component.getIdentityConstant().equals(OBJECT_CLASS))
                    {
                    // Object has no super
                    constDefaultSuper = null;
                    }
                break;

            case INTERFACE:
                // interface has no super
                constDefaultSuper = null;
                break;

            case ENUMVALUE:
                // enum values extend their abstract enum class
                assert container != null && container.getFormat() == Format.ENUM;
                constDefaultSuper = (ClassConstant) container.getIdentityConstant();
                break;

            case MIXIN:
                // mixins apply to ("mix into") any Object by default
                constDefaultSuper = null;
                constDefaultInto  = OBJECT_CLASS;
                break;
            }

        int                 cImports      = 0;
        ModuleStructure     moduleImport  = null;
        boolean             fHasDefault   = false;
        Format              format        = component.getFormat();
        ComponentBifurcator bifurcator    = new ComponentBifurcator(component);
        ConditionalConstant condPrev      = null;
        List<Component>     componentList = new ArrayList<>();
        componentList.add(component);
        // note: from this point down (and through the remainder of compilation), the component may
        // be conditionally bifurcated. (it's even possible that already there are multiple
        // components with the same identity, due to conditionality, but what is meant here is that
        // the one component that we just created and configured above may be split into multiple
        // "siblings" of itself based on any conditionals that are encountered in the processing of
        // the "Composition" objects)
        for (Composition composition : compositions == null ? Collections.<Composition>emptyList() : compositions)
            {
            // most compositions are allowed to be conditional; conditional compositions will
            // bifurcate the component
            Expression          exprCond = composition.getCondition();
            ConditionalConstant condCur  = exprCond == null ? null : exprCond.toConditionalConstant();
            if (!Handy.equals(condCur, condPrev))
                {
                componentList.clear();
                bifurcator.collectMatchingComponents(condCur, componentList);
                condPrev = condCur;
                }

            Token.Id keyword = composition.getKeyword().getId();
            switch (keyword)
                {
                case EXTENDS:
                    // interface is allowed to have any number of "extends"
                    if (format == Format.INTERFACE)
                        {
                        for (ClassStructure struct : (List<? extends ClassStructure>) (List) componentList)
                            {
                            // when an interface "extends" an interface, it is actually implementing
                            struct.addContribution(ClassStructure.Composition.Implements,
                                    composition.getType().ensureTypeConstant());
                            }
                        }
                    else
                        {
                        // only other format that can't have "extends" is an enum value, but that
                        // should be impossible to even parse, hence the assertion
                        assert format != Format.ENUMVALUE;

                        // only one extends is allowed
                        if (fAlreadyExtends)
                            {
                            composition.log(errs, Severity.ERROR, Compiler.MULTIPLE_EXTEND_CLAUSES,
                                    category.getId().TEXT);
                            }
                        else
                            {
                            // make sure that there is only one "extends" clause, but defer the
                            // analysis of conditional clauses (we can't evaluate conditions yet)
                            fAlreadyExtends = composition.condition == null;

                            for (ClassStructure struct : (List<? extends ClassStructure>) (List) componentList)
                                {
                                // register the class that the component extends
                                struct.addContribution(ClassStructure.Composition.Extends,
                                        composition.getType().ensureTypeConstant());
                                }
                            }
                        m_compositionExtend = (Composition.Extends) composition;
                        }
                    break;

                case IMPORT:
                case IMPORT_EMBED:
                case IMPORT_REQ:
                case IMPORT_WANT:
                case IMPORT_OPT:
                    {
                    ++cImports;
                    // "import" not allowed (only used by packages)
                    if (format != Format.PACKAGE)
                        {
                        composition.log(errs, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED, keyword.TEXT);
                        break;
                        }

                    // make sure that there is only one "import" clause
                    if (fAlreadyImports)
                        {
                        composition.log(errs, Severity.ERROR, Compiler.MULTIPLE_IMPORT_CLAUSES);
                        break;
                        }
                    fAlreadyImports = true;

                    // conditions are not allowed on the import composition, mainly because it is
                    // too complicated to handle the case when "this module is optional if
                    // some other module is being imported, but required if it isn't ..."; this
                    // could be improved in a later Ecstasy version, once we get the basics down
                    if (composition.condition != null)
                        {
                        composition.log(errs, Severity.ERROR, Compiler.CONDITIONAL_NOT_ALLOWED);
                        }

                    // create the fingerprint for the imported module
                    Composition.Import  compImport = (Composition.Import) composition;
                    NamedTypeExpression type       = (NamedTypeExpression) compImport.type;
                    String              sModule    = type.getName();
                    if (!type.isValidModuleName())
                        {
                        composition.log(errs, Severity.ERROR, Compiler.MODULE_BAD_NAME, sModule);
                        break;
                        }

                    boolean fNewFingerprint = false;
                    if (moduleImport == null)
                        {
                        // create the fingerprint
                        moduleImport = (ModuleStructure) component.getFileStructure().getChild(sModule);
                        if (moduleImport == null)
                            {
                            moduleImport    = component.getFileStructure().ensureModule(sModule);
                            fNewFingerprint = true;
                            }
                        // note that the component is not bifurcated because import compositions
                        // are not allowed to be conditional
                        ((PackageStructure) component).setImportedModule(moduleImport);
                        }

                    List<Version>        listPrefer = compImport.getPreferVersionList();
                    VersionTree<Boolean> vtreeAllow = compImport.getAllowVersionTree();
                    if (moduleImport.isMainModule())
                        {
                        // it is possible to import "this" module, which would be useful
                        // for example if names within this module were inadvertently
                        // hidden; however, an import of "this" module must not use
                        // version specifiers or any modifiers e.g. "optional" or
                        // "desired"
                        if (keyword != Token.Id.IMPORT
                                || !listPrefer.isEmpty() || !vtreeAllow.isEmpty())
                            {
                            composition.log(errs, Severity.ERROR, Compiler.ILLEGAL_SELF_IMPORT);
                            }
                        }
                    else
                        {
                        switch (keyword)
                            {
                            case IMPORT_OPT:
                                moduleImport.fingerprintOptional();
                                break;

                            case IMPORT_WANT:
                                moduleImport.fingerprintDesired();
                                break;

                            case IMPORT:
                            case IMPORT_REQ:
                                moduleImport.fingerprintRequired();
                                break;

                            case IMPORT_EMBED:
                                // the embedding is performed much later, long after the fingerprint
                                // is fully formed; for now, just mark the module import as required
                                moduleImport.fingerprintRequired();
                                break;

                            default:
                                throw new IllegalStateException();
                            }

                        // the imported module must always be imported with the same
                        // exact version override (or none)
                        if (!listPrefer.isEmpty())
                            {
                            List<Version> listOld = moduleImport.getFingerprintVersionPrefs();
                            if (listOld == null || listOld.isEmpty())
                                {
                                moduleImport.setFingerprintVersionPrefs(listPrefer);
                                }
                            else if (!listOld.equals(listPrefer))
                                {
                                composition.log(errs, Severity.ERROR, Compiler.CONFLICTING_VERSIONS);
                                }
                            }
                        if (!vtreeAllow.isEmpty())
                            {
                            VersionTree<Boolean> vtreeOld = moduleImport.getFingerprintVersions();
                            if (vtreeOld == null || vtreeOld.isEmpty())
                                {
                                moduleImport.setFingerprintVersions(vtreeAllow);
                                }
                            else if (!vtreeOld.equals(vtreeAllow))
                                {
                                composition.log(errs, Severity.ERROR, Compiler.CONFLICTING_VERSIONS);
                                }
                            }
                        }

                    // if the package statement is subject to a condition (note that the import
                    // composition cannot be conditional itself), then the module must be marked
                    // with that condition
                    ConditionalConstant condSum = component.getAggregateCondition();
                    if (condSum == null || fNewFingerprint)
                        {
                        // if the path through the AST down to this import is unconditional, then
                        // the fingerprint exists unconditionally; if the fingerprint is new, then
                        // whatever condition must be met to reach this package statement will also
                        // govern the existence of the imported module
                        moduleImport.setCondition(condSum);
                        }
                    else
                        {
                        // if the module import is already unconditional, then it has to remain
                        // unconditional; otherwise, the condition is the intersection of the
                        // existing condition and on the module import and whatever condition must
                        // be met to reach this package statement in the AST
                        ConditionalConstant condPre = moduleImport.getCondition();
                        if (condPre != null)
                            {
                            moduleImport.setCondition(condPre.addOr(condSum));
                            }
                        }
                    }
                    break;

                case INTO:
                    if (format == Format.MIXIN)
                        {
                        // only one "into" clause is allowed
                        if (fAlreadyIntos)
                            {
                            composition.log(errs, Severity.ERROR, Compiler.MULTIPLE_INTO_CLAUSES);
                            }
                        else
                            {
                            // make sure that there is only one "into" clause, but defer the
                            // analysis of conditional clauses (we can't evaluate conditions yet)
                            fAlreadyIntos = composition.condition == null;

                            // register the "into" clause
                            for (ClassStructure struct : (List<? extends ClassStructure>) (List) componentList)
                                {
                                struct.addContribution(ClassStructure.Composition.Into,
                                        composition.getType().ensureTypeConstant());
                                }
                            }
                        }
                    else
                        {
                        // "into" not allowed (only used by mixins)
                        composition.log(errs, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED, keyword.TEXT);
                        }
                    break;

                case IMPLEMENTS:
                    if (format == Format.INTERFACE)
                        {
                        // interface can't implement
                        composition.log(errs, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED, keyword.TEXT);
                        }
                    else
                        {
                        // register the "implements"
                        for (ClassStructure struct : (List<? extends ClassStructure>) (List) componentList)
                            {
                            struct.addContribution(ClassStructure.Composition.Implements,
                                    composition.getType().ensureTypeConstant());
                            }
                        }
                    break;

                case DELEGATES:
                    // these are all OK; other checks will be done after the types are resolvable
                    TypeConstant constClass = composition.getType().ensureTypeConstant();
                    PropertyConstant  constProp  = pool.ensurePropertyConstant(component.getIdentityConstant(),
                            ((Composition.Delegates) composition).getPropertyName()); // TODO change back from prop -> expr
                    for (ClassStructure struct : (List<? extends ClassStructure>) (List) componentList)
                        {
                        // register the class whose interface the component delegates, and the
                        // property whose value indicates the object to delegate to
                        struct.addDelegation(constClass, constProp);
                        }
                    break;

                case INCORPORATES:
                    if (format == Format.INTERFACE)
                        {
                        // interface can't incorporate
                        composition.log(errs, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED, keyword.TEXT);
                        }
                    else
                        {
                        // these are all OK; other checks will be done after the types are resolvable
                        Composition.Incorporates incorp = (Composition.Incorporates) composition;
                        ListMap<String, TypeConstant> mapConstraints = null;
                        if (incorp.isConditional())
                            {
                            mapConstraints = new ListMap<>();
                            for (Parameter constraint : incorp.getConstraints())
                                {
                                // type is null means no constraint
                                TypeExpression type = constraint.getType();
                                if (type != null)
                                    {
                                    mapConstraints.put(constraint.getName(),
                                            type.ensureTypeConstant());
                                    }
                                }
                            }

                        for (ClassStructure struct : (List<? extends ClassStructure>) (List) componentList)
                            {
                            // register the mixin that the component incorporates
                            struct.addIncorporates(composition.getType().ensureTypeConstant(),
                                    mapConstraints);
                            }
                        }
                    break;

                case DEFAULT:
                    {
                    // the default contribution becomes a constant property of the type
                    if (fHasDefault)
                        {
                        composition.log(errs, Severity.ERROR, Compiler.DUPLICATE_DEFAULT_VALUE, sName);
                        }
                    else
                        {
                        NamedTypeExpression typeDefault = new NamedTypeExpression(null,
                                Collections.singletonList(name), null, null, null, name.getEndPosition());
                        Expression exprValue = ((Default) composition).getValueExpression();
                        PropertyDeclarationStatement propDefault = new PropertyDeclarationStatement(
                                composition.getStartPosition(),
                                composition.getEndPosition(),
                                composition.getCondition(),
                                null, null,
                                typeDefault,
                                composition.keyword,
                                exprValue,
                                null, null);
                        propDefault.markSynthetic();
                        typeDefault.setParent(propDefault);
                        exprValue.setParent(propDefault);
                        ensureBody().addStatement(propDefault);
                        fHasDefault = true;
                        }
                    }
                    break;

                default:
                    throw new IllegalStateException("illegal composition: " + composition);
                }
            }

        // need to go through all components for the next bit
        componentList.clear();
        bifurcator.collectMatchingComponents(null, componentList);

        // add default super ("extends")
        if (!fAlreadyExtends && constDefaultSuper != null)
            {
            for (ClassStructure struct : (List<? extends ClassStructure>) (List) componentList)
                {
                if (!struct.getContributionsAsList().stream().anyMatch(contribution ->
                        contribution.getComposition() == Component.Composition.Extends))
                    {
                    struct.addContribution(ClassStructure.Composition.Extends,
                            pool.ensureTerminalTypeConstant(constDefaultSuper));
                    }
                }
            }

        // add default applies-to ("into")
        if (!fAlreadyIntos && constDefaultInto != null)
            {
            for (ClassStructure struct : (List<? extends ClassStructure>) (List) componentList)
                {
                if (!struct.getContributionsAsList().stream().anyMatch(contribution ->
                        contribution.getComposition() == Component.Composition.Into))
                    {
                    // TODO there is still an issue with this w.r.t. conditionals; verify there is no "into" on this struct
                    struct.addContribution(ClassStructure.Composition.Into,
                            pool.ensureTerminalTypeConstant(constDefaultInto));
                    }
                }
            }

        // verify that a package that imports does nothing else
        if (cImports > 0)
            {
            if (cImports != compositions.size() || body != null
                    || (modifiers != null && !modifiers.isEmpty())
                    || (typeParams != null && !typeParams.isEmpty())
                    || (constructorParams != null && !constructorParams.isEmpty()))
                {
                // oops, the package doesn't just import a module
                log(errs, Severity.ERROR, Compiler.IMPURE_MODULE_IMPORT);
                }
            }

        // recursively register structures
        mgr.processChildren();

        // if there are any constructor parameters, then that implies the existence both of
        // properties and of a constructor; we will handle the constructor creation later (the
        // resolve-names pass) once we we can figure out what types mean; for now, if the properties
        // are missing (i.e. not redundantly declared), then create them, and either way, we will
        // double-check them once we can resolve types
        if (constructorParams != null && !constructorParams.isEmpty())
            {
            Map<String, Component> mapChildren = component.ensureChildByNameMap();
            for (Parameter param : constructorParams)
                {
                // the name should either not exist already, or if it does, it needs to be a
                // property and the type will have to match the parameter
                String sParam = param.getName();
                Component child = mapChildren.get(sParam);
                if (child == null)
                    {
                    // create the property and get it caught up to where we are
                    PropertyDeclarationStatement prop = new PropertyDeclarationStatement(
                            param.getStartPosition(), param.getEndPosition(), null, null, null,
                            param.getType(), param.getNameToken(), null, null, null);
                    prop.markSynthetic();
                    prop.setParent(this);
                    prop.registerStructures(mgr, errs);
                    }
                else if (!(child instanceof PropertyStructure))
                    {
                    // the parameter implies a property, but we found something else instead
                    param.log(errs, Severity.ERROR, Compiler.NAME_COLLISION, sParam);
                    }
                }
            }
        }

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        ClassStructure component     = (ClassStructure) getComponent();
        Format         format        = component.getFormat();
        boolean        fModuleImport = false;

        if (format == Format.MODULE)
            {
            // the upstream modules were all linked in the previous pass, so this pass is able to
            // start out by building the entire (transitive closure) set of pools that are upstream
            component.getConstantPool().buildValidPoolSet();
            }

        if (format == Format.PACKAGE && m_moduleImported == null)
            {
            for (Composition composition : compositions)
                {
                Token.Id keyword = composition.getKeyword().getId();
                switch (keyword)
                    {
                    case IMPORT:
                    case IMPORT_EMBED:
                    case IMPORT_REQ:
                    case IMPORT_WANT:
                    case IMPORT_OPT:
                        PackageStructure structPkg = (PackageStructure) component;
                        ModuleStructure  structMod = structPkg == null ? null : structPkg.getImportedModule();
                        ModuleStructure  structAct = structMod == null ? null : structMod.getFingerprintOrigin();
                        if (structAct == null)
                            {
                            // this is obviously an error -- we can't compile without the module
                            // being available
                            Composition.Import  imp     = (Composition.Import) composition;
                            NamedTypeExpression type    = (NamedTypeExpression) imp.type;
                            String              sModule = type.getName();
                            type.log(errs, Severity.ERROR, Compiler.MODULE_MISSING, sModule);
                            }
                        else
                            {
                            m_moduleImported = structAct;
                            }
                        fModuleImport = true;
                        break;
                    }
                }
            }

        mgr.processChildren();
        Map<String, Component> mapChildren  = component.ensureChildByNameMap();
        MultiMethodStructure   constructors = (MultiMethodStructure) mapChildren.get("construct");
        if (constructorParams != null && !constructorParams.isEmpty())
            {
            if (fModuleImport)
                {
                constructorParams.get(0).log(errs, Severity.ERROR, Compiler.IMPURE_MODULE_IMPORT);
                }

            // resolve the type of each constructor parameter
            int            cParams     = constructorParams.size();
            TypeConstant[] atypeParams = new TypeConstant[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                Parameter         param     = constructorParams.get(i);
                TypeConstant      typeParam = param.getType().ensureTypeConstant();
                String            sParam    = param.getName();
                PropertyStructure prop      = (PropertyStructure) mapChildren.get(sParam);
                TypeConstant      typeProp  = prop.getType();
                if (typeParam.containsUnresolved() || typeProp.containsUnresolved())
                    {
                    mgr.requestRevisit();
                    return;
                    }

                // the property type must be compatible with the parameter type,
                // but it is too early in the compilation cycle to use "isA()";
                // we will check for the match while building the type info
                // TODO: point where exactly
                atypeParams[i] = typeParam;
                }

            // look for a constructor that corresponds to those parameter types, and create the
            // constructor if it does not already exist. the constructor may have been declared
            // explicitly, but we can't verify that for certain because the types may appear
            // slightly different here (since we can't resolve types at this stage), so what we
            // will look for is any constructor that has a matching number of parameters (or a
            // higher number of parameters if the additional parameters are optional), and each
            // parameter type must match the type specified in the short-hand constructor notation
            boolean fFound = false;
            if (constructors != null)
                {
                NextConstructor: for (MethodStructure constructor : constructors.getMethodByConstantMap().values())
                    {
                    org.xvm.asm.Parameter[] aConsParams = constructor.getParamArray();
                    int                     cConsParams = aConsParams.length;
                    if (cConsParams >= cParams)
                        {
                        // verify that any additional parameters are defaulted
                        for (int i = cParams; i < cConsParams; ++i)
                            {
                            if (!aConsParams[i].hasDefaultValue())
                                {
                                continue NextConstructor;
                                }
                            }

                        // parameters are allowed to widen from the abbreviated declaration to the
                        // explicit declaration
                        for (int i = 0; i < cParams; ++i)
                            {
                            TypeConstant typeConsParam = aConsParams[i].getType();
                            if (typeConsParam.containsUnresolved())
                                {
                                mgr.requestRevisit();
                                return;
                                }
                            // note: it is too early in the compilation cycle to use "isA()" (REVIEW GG)
                            if (!atypeParams[i].equals(typeConsParam))
                                {
                                continue NextConstructor;
                                }
                            }

                        // should only find one match; make sure we didn't already find one
                        if (fFound)
                            {
                            StringBuilder sb = new StringBuilder();
                            sb.append("construct(");
                            for (int i = 0; i < cParams; ++i)
                                {
                                if (i > 0)
                                    {
                                    sb.append(", ");
                                    }
                                sb.append(atypeParams[i].getValueString());
                                }
                            sb.append(')');

                            long lStart = name.getStartPosition();
                            long lEnd   = name.getEndPosition();
                            if (cParams > 0)
                                {
                                lStart = constructorParams.get(0).getStartPosition();
                                lEnd   = constructorParams.get(cParams - 1).getEndPosition();
                                }
                            errs.log(Severity.ERROR, Compiler.SIGNATURE_AMBIGUOUS,
                                    new String[] {sb.toString()}, getSource(), lStart, lEnd);
                            break NextConstructor;
                            }

                        fFound = true;
                        }
                    }
                }

            if (!fFound)
                {
                // create a constructor based on the "short-hand notation" implicit constructor
                // definition
                org.xvm.asm.Parameter[] aParams = org.xvm.asm.Parameter.NO_PARAMS;
                if (cParams > 0)
                    {
                    aParams = new org.xvm.asm.Parameter[cParams];
                    ConstantPool pool = pool();
                    for (int i = 0; i < cParams; ++i)
                        {
                        Parameter param = constructorParams.get(i);
                        aParams[i] = new org.xvm.asm.Parameter(pool, atypeParams[i],
                                param.getName(), null, false, i, false);
                        if (param.value != null)
                            {
                            aParams[i].markDefaultValue();
                            }
                        }
                    }
                MethodStructure constructor = component.createMethod(true, Access.PUBLIC,
                        org.xvm.asm.Annotation.NO_ANNOTATIONS, org.xvm.asm.Parameter.NO_PARAMS,
                        "construct", aParams, true, false);

                // set the synthetic flag so that the constructor knows to provide its own
                // default implementation when it emits code
                constructor.setSynthetic(true);
                }
            }

        // all non-interfaces must have at least one constructor, even if the class is abstract
        if (format != Format.INTERFACE && !mapChildren.containsKey("construct"))
            {
            // add a default constructor that will invoke the necessary super class and mixin
            // constructors (if any)
            MethodStructure constructor = component.createMethod(true, Access.PUBLIC,
                org.xvm.asm.Annotation.NO_ANNOTATIONS, org.xvm.asm.Parameter.NO_PARAMS,
                "construct", org.xvm.asm.Parameter.NO_PARAMS, true, false);

            // set the synthetic flag so that the constructor knows to provide its own
            // default implementation when it emits code
            constructor.setSynthetic(true);
            }
        else if (component.isSingleton())
            {
            // for a singleton, constructor parameters are not permitted unless they all have
            // default values (since the module is a singleton, and is automatically created,
            // i.e. it has to have all of its construction parameters available)
            boolean fFound = false;
            NextConstructor: for (MethodStructure constructor : constructors.getMethodByConstantMap().values())
                {
                for (org.xvm.asm.Parameter param : constructor.getParamArray())
                    {
                    if (!param.hasDefaultValue())
                        {
                        continue NextConstructor;
                        }
                    }
                fFound = true;
                break;
                }
            if (!fFound)
                {
                name.log(errs, getSource(), Severity.ERROR, Compiler.DEFAULT_CONSTRUCTOR_REQUIRED,
                        component.getName());
                }
            }
        }

    @Override
    public void generateCode(StageMgr mgr, ErrorListener errs)
        {
        if (!mgr.processChildren())
            {
            mgr.requestRevisit();
            return;
            }

        ClassStructure component = (ClassStructure) getComponent();
        Format         format    = component.getFormat();
        if (format != Format.INTERFACE)
            {
            MultiMethodStructure constructors = (MultiMethodStructure) component.getChild("construct");
            assert constructors != null;

            for (MethodStructure constructor : constructors.methods())
                {
                if (constructor.isSynthetic() && !constructor.ensureCode().hasOps())
                    {
                    generateConstructor(component, constructor, errs);
                    }
                }
            }
        }

    /**
     * Emit the synthetic constructor's code.
     */
    private void generateConstructor(ClassStructure component, MethodStructure constructor,
                                     ErrorListener errs)
        {
        // the constructor has two responsibilities:
        // 1) set each property based on the parameter name, value passed in as an arg
        // 2) call the super constructor
        Code           code      = constructor.ensureCode();
        StatementBlock blockBody = body;
        if (body == null)
            {
            blockBody = adopt(new StatementBlock(Collections.EMPTY_LIST));
            }
        RootContext ctxConstruct = blockBody.new RootContext(constructor);
        Context     ctxValidate  = ctxConstruct.validatingContext();
        boolean     fValid       = true;

        if (constructor.getDefaultParamCount() > 0)
            {
            // resolve the default values
            org.xvm.asm.Parameter[] aParams = constructor.getParamArray();
            for (int i = 0, cParams = aParams.length; i < cParams; ++i)
                {
                org.xvm.asm.Parameter param = aParams[i];
                if (!param.hasDefaultValue())
                    {
                    continue;
                    }

                Expression exprOld = constructorParams.get(i).value;
                Expression exprNew = exprOld.validate(ctxValidate, param.getType(), errs);
                if (exprNew == null)
                    {
                    fValid = false;
                    }
                else if (exprNew.isConstant())
                    {
                    param.setDefaultValue(exprNew.toConstant());
                    }
                else
                    {
                    exprOld.log(errs, Severity.ERROR, Compiler.CONSTANT_REQUIRED);
                    fValid = false;
                    }
                }

            if (!fValid)
                {
                return;
                }
            }

        ClassStructure clzSuper  = component.getSuper();
        TypeInfo       infoSuper = clzSuper == null ? null : clzSuper.getFormalType().ensureTypeInfo(errs);
        MethodConstant idSuper; // super constructor

        List<Expression> listArgs;
        if (args == null)
            {
            // Examples:
            //    const DeadlockException(String? text = null, Exception? cause = null)
            //        extends Exception(text, cause);
            //
            //    const Point3d(Int x, Int y, Int z = 0)
            //        extends Point(x, y);

            Composition.Extends compExtend = m_compositionExtend;
            if (compExtend == null)
                {
                listArgs = null;
                }
            else
                {
                assert clzSuper != null;

                listArgs = compExtend.args;
                }
            }
        else
            {
            // Examples:
            //
            //    enum Ordered(String symbol) { Lesser("<"), ... }
            //
            //    Entry<KeyType, ValueType> entry = new KeyBasedEntry<KeyType, ValueType>(key)
            //          {
            //          @Override
            //          ValueType getValue()
            //              {
            //              ...
            //              }
            //          }

            assert clzSuper != null;
            listArgs = args;
            }

        if (clzSuper == null)
            {
            idSuper = null;
            }
        else
            {
            idSuper = findMethod(ctxValidate, infoSuper, "construct", listArgs, MethodType.Constructor, null, errs);
            if (idSuper == null)
                {
                // if an error have already been logged, this is additional information
                log(errs, Severity.ERROR, Compiler.IMPLICIT_SUPER_CONSTRUCTOR_MISSING,
                        component.getIdentityConstant().getValueString(), clzSuper.getName());
                return;
                }
            }

        // validate
        if (idSuper != null && listArgs != null)
            {
            TypeConstant[] atypeArgs = idSuper.getRawParams();
            for (int i = 0, c = listArgs.size(); i < c; i++)
                {
                Expression exprOld = listArgs.get(i);
                Expression exprNew = exprOld.validate(ctxValidate, atypeArgs[i], errs);
                if (exprNew == null)
                    {
                    fValid = false;
                    }
                else if (exprNew != exprOld)
                    {
                    listArgs.set(i, exprNew);
                    }
                }

            if (!fValid)
                {
                return;
                }
            }

        // emit code
        Context ctxEmit = ctxConstruct.emittingContext(code);
        if (args == null)
            {
            // parameters that don't exist on the super must be properties on "this"
            // and we need to assign them

            List<org.xvm.asm.Parameter> params = constructor.getParams();
            for (int i = 0, c = params.size(); i < c; ++i)
                {
                org.xvm.asm.Parameter param  = params.get(i);
                String                sParam = param.getName();

                if (infoSuper == null || infoSuper.findProperty(sParam) == null)
                    {
                    // there must be a property by the same name
                    Component child = component.getChild(sParam);
                    if (child.getFormat() == Format.PROPERTY)
                        {
                        PropertyStructure prop     = (PropertyStructure) child;
                        TypeConstant      typeProp = prop.getType();
                        TypeConstant      typeVal  = param.getType();
                        if (param.getType().isA(prop.getType()))
                            {
                            code.add(new L_Set(prop.getIdentityConstant(), ctxEmit.getVar(sParam)));
                            }
                        else
                            {
                            log(errs, Severity.ERROR, Compiler.IMPLICIT_PROP_WRONG_TYPE,
                                    sParam, typeVal.getValueString(), typeProp.getValueString());
                            }
                        }
                    else
                        {
                        log(errs, Severity.ERROR, Compiler.IMPLICIT_PROP_MISSING, sParam);
                        }
                    }
                }
            }

        if (idSuper != null)
            {
            MethodStructure constructSuper = (MethodStructure) idSuper.getComponent();
            int             cSuperArgs     = constructSuper.getParamCount();
            Argument[]      aSuperArgs     = new Argument[cSuperArgs];
            int             cArgs          = listArgs == null ? 0 : listArgs.size();

            // generate the super constructor arguments (filling in the default values)
            for (int i = 0; i < cSuperArgs; i++)
                {
                if (i < cArgs)
                    {
                    aSuperArgs[i] = listArgs.get(i).generateArgument(ctxEmit, code, true, true, errs);
                    }
                else
                    {
                    assert constructSuper.getParam(i).hasDefaultValue();
                    aSuperArgs[i] = Register.DEFAULT;
                    }
                }

            switch (cSuperArgs)
                {
                case 0:
                    code.add(new Construct_0(idSuper));
                    break;

                case 1:
                    code.add(new Construct_1(idSuper, aSuperArgs[0]));
                    break;

                default:
                    code.add(new Construct_N(idSuper, aSuperArgs));
                    break;
                }
            }

        code.add(new Return_0());
        }

    private void disallowTypeParams(ErrorListener errs)
        {
        // type parameters are not permitted
        if (typeParams != null && !typeParams.isEmpty())
            {
            // note: currently no way to determine the location of the parameters
            // Parameter paramFirst = typeParams.get(0);
            // Parameter paramLast  = typeParams.get(typeParams.size() - 1);

            Token tokFirst = category == null ? name : category;
            Token tokLast = name == null ? category : name;
            log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
            }
        }

    private void disallowConstructorParams(ErrorListener errs)
        {
        // constructor parameters are not permitted
        if (constructorParams != null && !constructorParams.isEmpty())
            {
            // note: currently no way to determine the location of the parameters
            // Parameter paramFirst = constructorParams.get(0);
            // Parameter paramLast  = constructorParams.get(constructorParams.size() - 1);

            Token tokFirst = category == null ? name : category;
            Token tokLast = name == null ? category : name;
            log(errs, Severity.ERROR, Compiler.CONSTRUCTOR_PARAMS_UNEXPECTED);
            }
        }

    private void requireConstructorParamValues(ErrorListener errs)
        {
        // constructor parameters are not permitted
        if (constructorParams != null && !constructorParams.isEmpty())
            {
            for (Parameter param : constructorParams)
                {
                if (param.value == null)
                    {
                    // note: currently no way to determine the location of the parameter
                    Token tokFirst = category == null ? name : category;
                    Token tokLast = name == null ? category : name;
                    log(errs, Severity.ERROR, Compiler.CONSTRUCTOR_PARAM_DEFAULT_REQUIRED);
                    }
                }
            }
        }

    /**
     * Parse a documentation comment, extracting the "body" of the documentation inside it.
     *
     * @param token  a documentation token
     *
     * @return the "body" of the documentation, as LF-delimited lines, without the leading "* "
     */
    public static String extractDocumentation(Token token)
        {
        if (token == null)
            {
            return null;
            }

        String sDoc = (String) token.getValue();
        if (sDoc == null || sDoc.length() <= 1 || sDoc.charAt(0) != '*')
            {
            return null;
            }

        StringBuilder sb = new StringBuilder();
        int nState = 0;
        NextChar: for (char ch : sDoc.substring(1).toCharArray())
            {
            switch (nState)
                {
                case 0:         // leading whitespace expected
                    if (!isLineTerminator(ch))
                        {
                        if (isWhitespace(ch))
                            {
                            continue NextChar;
                            }

                        if (ch == '*')
                            {
                            nState = 1;
                            continue NextChar;
                            }

                        // weird - it's actual text to append; we didn't find the leading '*'
                        break;
                        }
                    // fall through

                case 1:         // ate the asterisk; expecting one space
                    if (!isLineTerminator(ch))
                        {
                        if (isWhitespace(ch))
                            {
                            nState = 2;
                            continue NextChar;
                            }

                        // weird - it's actual text to append; there was no ' ' after the '*'
                        break;
                        }
                    // fall through

                case 2:         // in the text
                    if (isLineTerminator(ch))
                        {
                        if (sb.length() > 0)
                            {
                            sb.append(LF);
                            }
                        nState = ch == CR ? 3 : 0;
                        continue NextChar;
                        }
                    break;

                case 3:         // ate a CR, emitted an LF
                    if (ch == LF || isWhitespace(ch))
                        {
                        nState = 0;
                        continue NextChar;
                        }

                    if (ch == '*')
                        {
                        nState = 1;
                        continue NextChar;
                        }

                    // weird - it's actual text to append; we didn't find the leading '*'
                    break;
                }

            nState = 2;
            sb.append(ch);
            }

        // trim any trailing whitespace & line terminators
        int cch = sb.length();
        while (cch > 0 && isWhitespace(sb.charAt(--cch)))
            {
            sb.setLength(cch);
            }

        return sb.toString();
        }


    // ----- debugging assistance ------------------------------------------------------------------

    public String toSignatureString()
        {
        StringBuilder sb = new StringBuilder();

        if (category.getId() == Token.Id.ENUM_VAL)
            {
            if (annotations != null)
                {
                for (Annotation annotation : annotations)
                    {
                    sb.append(annotation)
                      .append(' ');
                    }
                }

            sb.append(name.getValue());

            if (typeParams != null)
                {
                sb.append('<');
                boolean first = true;
                for (TypeExpression typeParam : typeArgs)
                    {
                    if (first)
                        {
                        first = false;
                        }
                    else
                        {
                        sb.append(", ");
                        }
                    sb.append(typeParam);
                    }
                sb.append('>');
                }

            if (args != null)
                {
                sb.append('(');
                boolean first = true;
                for (Expression arg : args)
                    {
                    if (first)
                        {
                        first = false;
                        }
                    else
                        {
                        sb.append(", ");
                        }
                    sb.append(arg);
                    }
                sb.append(')');
                }
            }
        else
            {
            if (modifiers != null)
                {
                for (Token token : modifiers)
                    {
                    sb.append(token.getId().TEXT)
                      .append(' ');
                    }
                }

            if (annotations != null)
                {
                for (Annotation annotation : annotations)
                    {
                    sb.append(annotation)
                      .append(' ');
                    }
                }

            sb.append(category.getId().TEXT)
              .append(' ');

            if (qualified == null)
                {
                sb.append(name.getValue());
                }
            else
                {
                boolean first = true;
                for (Token token : qualified)
                    {
                    if (first)
                        {
                        first = false;
                        }
                    else
                        {
                        sb.append('.');
                        }
                    sb.append(token.getValue());
                    }
                }

            if (typeParams != null)
                {
                sb.append('<');
                boolean first = true;
                for (Parameter param : typeParams)
                    {
                    if (first)
                        {
                        first = false;
                        }
                    else
                        {
                        sb.append(", ");
                        }
                    sb.append(param.toTypeParamString());
                    }
                sb.append('>');
                }

            if (constructorParams != null)
                {
                sb.append('(');
                boolean first = true;
                for (Parameter param : constructorParams)
                    {
                    if (first)
                        {
                        first = false;
                        }
                    else
                        {
                        sb.append(", ");
                        }
                    sb.append(param);
                    }
                sb.append(')');
                }
            }

        return sb.toString();
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (doc != null)
            {
            String sDoc = String.valueOf(doc.getValue());
            if (sDoc.length() > 100)
                {
                sDoc = sDoc.substring(0, 97) + "...";
                }
            appendString(sb.append("/*"), sDoc).append("*/\n");
            }

        sb.append(toSignatureString());

        if (category.getId() == Token.Id.ENUM_VAL)
            {
            if (body != null)
                {
                sb.append('\n')
                  .append(indentLines(body.toString(), "    "));
                }
            }
        else
            {
            for (Composition composition : this.compositions)
                {
                sb.append("\n        ")
                  .append(composition);
                }

            if (body == null)
                {
                sb.append(';');
                }
            else
                {
                String sBody = body.toString();
                if (sBody.indexOf('\n') >= 0)
                    {
                    sb.append('\n')
                      .append(indentLines(sBody, "    "));
                    }
                else
                    {
                    sb.append(' ')
                      .append(sBody);
                    }
                }
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toSignatureString();
        }

    /**
     * Temporary: Used to build a fake module that can be used to look up information while in the
     * debugger. (REMOVE LATER!!!)
     *
     * @param component  the "real" component to associate with the "fake" module
     */
    public void buildDumpModule(Component component)
        {
        introduceParentage();
        setComponent(component);
        }


    // ----- inner class: Zone ---------------------------------------------------------------------

    /**
     * The Zone enumeration defines the zone within which a particular type is declared.
     *
     * <ul>
     * <li><b>{@code TopLevel}</b> - the module itself, or declared within a module or package;</li>
     * <li><b>{@code InClass}</b> - declared within a class, e.g. an inner class;</li>
     * <li><b>{@code InMethod}</b> - declared within the body of a method.</li>
     * </ul>
     */
    public enum Zone
        {
        TopLevel, InClass, InMethod;

        /**
         * Look up a DeclZone enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the DeclZone enum for the specified ordinal
         */
        public static Zone valueOf(int i)
            {
            return ZONES[i];
            }

        /**
         * All of the DeclZone enums.
         */
        private static final Zone[] ZONES = Zone.values();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Source               source;
    protected Expression           condition;
    protected List<Token>          modifiers;
    protected List<Annotation>     annotations;
    protected Token                category;
    protected Token                name;
    protected List<Token>          qualified;
    protected List<Parameter>      typeParams;
    protected List<Parameter>      constructorParams;
    protected List<TypeExpression> typeArgs;
    protected List<Expression>     args;
    protected List<Composition>    compositions;
    protected StatementBlock       body;
    protected Token                doc;
    protected StatementBlock       enclosed;
    protected boolean              anonymous;


    /**
     * For a package that imports a module, this is the actual module that is imported (not just the
     * fingerprint.) Note that during compilation, the other module may itself be in the process of
     * compilation, so it may be in the same "compiler pass" as this module for any given pass.
     */
    transient private ModuleStructure m_moduleImported;

    /**
     * Cached "extends" composition.
     */
    transient Composition.Extends m_compositionExtend;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TypeCompositionStatement.class,
            "condition", "annotations", "typeParams", "constructorParams", "typeArgs", "args",
            "compositions", "body");
    }
