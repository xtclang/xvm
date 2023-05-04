package org.xvm.compiler.ast;


import java.io.File;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;

import org.xvm.asm.Annotation;
import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Component.Injection;
import org.xvm.asm.ComponentBifurcator;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PackageStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;
import org.xvm.asm.Version;
import org.xvm.asm.VersionTree;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodKind;

import org.xvm.asm.op.Construct_0;
import org.xvm.asm.op.Construct_1;
import org.xvm.asm.op.Construct_N;
import org.xvm.asm.op.IsType;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.L_Get;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.SynInit;
import org.xvm.asm.op.L_Set;
import org.xvm.asm.op.Return_0;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.CompositionNode.Default;
import org.xvm.compiler.ast.CompositionNode.Delegates;
import org.xvm.compiler.ast.CompositionNode.Extends;
import org.xvm.compiler.ast.CompositionNode.Incorporates;
import org.xvm.compiler.ast.CompositionNode.Import;
import org.xvm.compiler.ast.Context.Branch;
import org.xvm.compiler.ast.StatementBlock.RootContext;

import org.xvm.util.Handy;
import org.xvm.util.ListMap;
import org.xvm.util.ListSet;
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
            Source                     source,
            long                       lStartPos,
            long                       lEndPos,
            Expression                 condition,
            List<Token>                modifiers,
            List<AnnotationExpression> annotations,
            Token                      category,
            Token                      name,
            List<Token>                qualified,
            List<Parameter>            typeParams,
            List<Parameter>            constructorParams,
            List<CompositionNode>      compositions,
            StatementBlock             body,
            Token                      doc)
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
            List<AnnotationExpression> annotations,
            Token                      name,
            List<TypeExpression>       typeArgs,
            List<Expression>           args,
            StatementBlock             body,
            Token                      doc,
            long                       lStartPos,
            long                       lEndPos)
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
            NewExpression              parent,
            List<AnnotationExpression> annotations,
            Token                      category,
            Token                      name,
            List<Parameter>            typeParams,
            List<CompositionNode>      compositions,
            List<Expression>           args,
            StatementBlock             body,
            long                       lStartPos,
            long                       lEndPos)
        {
        super(lStartPos, lEndPos);

        this.annotations  = annotations;
        this.category     = category;
        this.name         = name;
        this.typeParams   = typeParams;
        this.compositions = compositions;
        this.args         = args;
        this.body         = body;
        this.m_fAnon      = true;

        setParent(parent);
        introduceParentage();
        }

    /**
     * Create a fake module statement that holds onto a compiled module and uses it as the basis for
     * resolving some other AST node. This is used by the runtime, not by the compiler.
     *
     * @param module  a compiled module
     * @param source  the source code for the child
     * @param type    the child node to resolve
     */
    public TypeCompositionStatement(ModuleStructure module, Source source, TypeExpression type)
        {
        super(0,0);

        this.source    = source;
        this.category  = new Token(0, 0, Id.MODULE);
        this.name      = new Token(0, 0, Id.IDENTIFIER, module.getSimpleName());
        this.qualified = Arrays.stream(Handy.parseDelimitedString(module.getName(), '.'))
                .map(s -> new Token(0, 0, Id.IDENTIFIER, s))
                .collect(Collectors.toCollection(ArrayList::new));
        this.typeArgs  = new ArrayList<>(List.of(type));

        introduceParentage();
        setComponent(module);
        setStage(Stage.Emitted);
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
            return sb.substring(1);
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
                return switch (structParent.getFormat())
                    {
                    case MODULE, PACKAGE ->
                        Zone.TopLevel;

                    case CLASS, INTERFACE, SERVICE, CONST, ENUM, ENUMVALUE, MIXIN ->
                        Zone.InClass;

                    case METHOD ->
                        Zone.InMethod;

                    case PROPERTY ->
                        Zone.InProperty;

                    default ->
                        throw new IllegalStateException("this=" + structThis.getFormat()
                            + ", parent=" + structParent.getFormat());
                    };

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


    // ---- AstNode methods ------------------------------------------------------------------------

    @Override
    public boolean isAutoNarrowingAllowed(TypeExpression type)
        {
        // auto-narrowing is only allowed for constructor type parameters
        if (constructorParams == null)
            {
            return false;
            }
        AstNode nodeChild = findChild(type);
        return nodeChild instanceof Parameter param && constructorParams.contains(param);
        }

    @Override
    protected void discard(boolean fRecurse)
        {
        super.discard(fRecurse);

        Component component = getComponent();
        if (component != null)
            {
            pool().invalidateTypeInfos(component.getIdentityConstant());
            }
        }

    @Override
    protected AstNode getCodeContainer()
        {
        return null;
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

    /**
     * Cached contribution arguments (only for "Extends" and "Incorporates" contributions);
     * the order matters as it controls the order of super constructor calls.
     * Vast majority of classes don't incorporate, so this map holds just one Extends contribution;
     * 99% of the rest have just one Incorporates, so initial size of two seems reasonable.
     */
    private final transient Map<Contribution, List<Expression>> m_mapContribArgs = new ListMap<>(2);

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

        // determine if this is an inner class, etc.
        String              sName     = (String) name.getValue();
        Access              access    = getDefaultAccess();
        Component           container = parent == null ? null : parent.getComponent();
        boolean             fInner    = false;
        ConstantPool        pool      = pool();
        ConditionalConstant constCond = condition == null ? null : condition.toConditionalConstant();
        if (container != null)
            {
            if (container instanceof ModuleStructure && sName.equals(X_PKG_IMPORT))
                {
                // it is illegal to take the "ecstasy" namespace, because doing so would effectively
                // hide the Ecstasy core module
                name.log(errs, getSource(), Severity.ERROR, Compiler.NAME_COLLISION, X_PKG_IMPORT);
                }
            else
                {
                switch (category.getId())
                    {
                    case MODULE:
                    case PACKAGE:
                    case ENUM:
                    case ENUM_VAL:
                        // none of these is an inner class
                        break;

                    default:
                        IdentityConstant idParent = container.getIdentityConstant();
                        switch (idParent.getFormat())
                            {
                            case Module:
                            case Package:
                                // not a virtual child (it's nested under a module or package)
                                break;

                            case Method:
                            case Property:
                            case Class:
                                fInner = true;
                                break;

                            default:
                                throw new IllegalStateException("idParent=" + idParent);
                            }
                        break;
                    }
                }
            }

        // create the structure for this module, package, or class (etc.)
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
                        // there exists a class named ecstasy.ecstasy.ecstasy.ecstasy.ecstasy.Object
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
                    Format format = switch (category.getId())
                        {
                        case CLASS     -> Format.CLASS;
                        case INTERFACE -> Format.INTERFACE;
                        case SERVICE   -> Format.SERVICE;
                        case CONST     -> Format.CONST;
                        case ENUM      -> Format.ENUM;
                        case ENUM_VAL  -> Format.ENUMVALUE;
                        case MIXIN     -> Format.MIXIN;
                        default        -> throw new IllegalStateException();
                        };

                    component = container.createClass(getDefaultAccess(), format, sName, constCond);
                    if (m_fAnon)
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
                throw new IllegalStateException("unable to guess structure for: "
                        + category.getId().TEXT);
            }
        setComponent(component);
        if (component == null)
            {
            // the only reason for "container.createX()" to fail is a duplicate name
            log(errs, Severity.ERROR, Compiler.DUPLICATE_NAME, sName);
            return;
            }

        // documentation
        if (doc != null)
            {
            component.setDocumentation(extractDocumentation(doc));
            }

        // calculate the source file path for the component
        FindSource: if (source != null)
            {
            // original file name must be available
            String sOrig = source.getFileName();
            if (sOrig == null)
                {
                break FindSource;
                }

            // module must be available
            AstNode nodeModule = this;
            while (nodeModule != null)
                {
                if (nodeModule instanceof TypeCompositionStatement stmtModule
                        && stmtModule.category.getId() == Id.MODULE)
                    {
                    break;
                    }
                nodeModule = nodeModule.getParent();
                }
            if (nodeModule == null)
                {
                break FindSource;
                }

            TypeCompositionStatement stmtModule = (TypeCompositionStatement) nodeModule;
            String                   sFile      = sOrig.replace(File.separatorChar, '/');
            if (this == stmtModule)
                {
                // this statement is the module statement, so this file name also is used to
                // configure the root directory from which the module source code originated
                int ofFile = sFile.lastIndexOf('/') + 1;

                String sDir = sOrig.substring(0, ofFile);
                ((ModuleStructure) component).setSourceDir(
                        pool.ensureLiteralConstant(Constant.Format.Path, sDir));

                sFile = sFile.substring(ofFile);
                }
            else
                {
                // get the root directory of the source tree; the file name must be under that
                // directory
                String sDir = ((ModuleStructure) stmtModule.getComponent()).getSourceDir().getValue();
                if (sDir == null || !sOrig.startsWith(sDir))
                    {
                    break FindSource;
                    }

                sFile = sFile.substring(sDir.length());
                }

            component.setSourcePath(pool.ensureLiteralConstant(Constant.Format.Path, sFile));
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
        Zone zone     = getDeclarationZone();
        int  nAllowed = 0;
        switch (component.getFormat())
            {
            case SERVICE:
            case CONST:
            case CLASS:
            case INTERFACE:
            case MIXIN:
                // class is not allowed to be declared static if it is top-level, otherwise all of
                // these can always be declared static
                if (!(component.getFormat() == Format.CLASS && zone == Zone.TopLevel))
                    {
                    nAllowed |= Component.STATIC_BIT;
                    }
                // fall through
            case PACKAGE:
            case ENUM:
                {
                // these are all allowed to be declared public/private/protected, except when they
                // appear inside a method body
                if (zone != Zone.InMethod)
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

            for (int i = 0, c = modifiers.size(); i < c; ++i)
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

        boolean fImplicitlyStatic = component.getFormat().isImplicitlyStatic();
        switch (zone)
            {
            case InProperty:
                log(errs, Severity.ERROR, Compiler.NOT_IMPLEMENTED, "Class within a property");
                break;

            case InMethod:
                {
                MethodStructure method = (MethodStructure) component.getParent();

                // there is no reason to create an inner instance child of a singleton
                fImplicitlyStatic |= method.isFunction() ||
                                     method.getContainingClass().isSingleton();
                break;
                }

            case InClass:
                // inner const/service classes must be declared static if the parent is not const/service
                // TODO should also check some of these for Zone.InProperty
                if (!fExplicitlyStatic)
                    {
                    ClassStructure clzParent = (ClassStructure) container;
                    switch (component.getFormat())
                        {
                        case CONST:
                            // parent MUST be a const or a service (because parent will be
                            // automatically captured, and a const can't capture a non-const)
                            if (!clzParent.isConst() && !clzParent.isService())
                                {
                                log(errs, Severity.ERROR, Compiler.INNER_CONST_NOT_STATIC);
                                fExplicitlyStatic = true;
                                }
                            break;

                        case SERVICE:
                            // parent MUST be a const or a service (because parent is automatically
                            // captured, and a service can't capture an object that isn't either a
                            // const or a service)
                            if (!clzParent.isConst() && !clzParent.isService())
                                {
                                log(errs, Severity.ERROR, Compiler.INNER_SERVICE_NOT_STATIC);
                                fExplicitlyStatic = true;
                                }
                            break;
                        }

                    m_fVirtChild = fInner && !fExplicitlyStatic;

                    // anonymous inner classes must evaluate to inner but never virtual
                    assert !m_fAnon || fInner && !m_fVirtChild;
                    }
                break;
            }

        // configure the static bit on the component
        if (fExplicitlyStatic || fImplicitlyStatic)
            {
            component.setStatic(true);
            }

        // validate that type parameters are allowed, and register them (the actual validation of
        // the type parameters themselves happens in a later phase)
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
                assert container.getFormat() == Format.ENUM;
                ClassStructure enumeration = (ClassStructure) container;
                if (enumeration.getTypeParamCount() != (typeArgs == null ? 0 : typeArgs.size()))
                    {
                    log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_MISMATCH);
                    }
                break;

            case SERVICE:
            case CONST:
                // these compositions are new-able, and thus can usually declare type parameters;
                // the exception is when the composition is not new-able, which is the case for
                // singleton compositions
                if (fExplicitlyStatic && zone == Zone.TopLevel)
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
                    HashSet<String> setNames = new HashSet<>();
                    for (Parameter param : typeParams)
                        {
                        String sParam = param.getName();
                        if (setNames.add(sParam))
                            {
                            // add the type parameter information to the component
                            TypeExpression exprType  = param.getType();
                            TypeConstant   constType = exprType == null
                                    ? pool.typeObject()
                                    : exprType.ensureTypeConstant();
                            component.addTypeParam(sParam, constType);
                            }
                        else
                            {
                            log(errs, Severity.ERROR, Compiler.DUPLICATE_TYPE_PARAM, sName);
                            }
                        }
                    }
                break;

            default:
                throw new IllegalStateException();
            }

        assert component.isSingleton() == fSingleton;

        // validate and register annotations
        if (annotations != null && !annotations.isEmpty())
            {
            if (compositions == null || compositions == Collections.EMPTY_LIST)
                {
                compositions = new ArrayList<>();
                }
            for (int i = 0, c = annotations.size(); i < c; i++)
                {
                AnnotationExpression annotation = annotations.get(i);
                annotation.ensureAnnotation(pool);
                compositions.add(new Incorporates(annotation));
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
        // [1] module/package/const/enum may explicitly extend a class or a const; otherwise
        //    implements Object
        // [2] package may import a module
        // [3] class may explicitly extend a class; otherwise implements Object (the one exception is
        //     Object itself, which does NOT implement itself)
        // [4] service may explicitly extend a class or service; otherwise implements Object
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
        boolean      fAlreadyExtends   = false;
        boolean      fAlreadyImports   = false;
        boolean      fAlreadyIntos     = false;
        TypeConstant typeDefaultInto   = null;
        TypeConstant typeDefaultImpl   = null;
        TypeConstant typeDefaultSuper = null;

        // virtual child super is analyzed/resolved by resolveNames()
        if (!m_fVirtChild)
            {
            switch (component.getFormat())
                {
                case INTERFACE:
                    // interfaces don't implement anything
                    break;

                case ENUMVALUE:
                    // enum values extend their abstract enum class
                    assert container != null && container.getFormat() == Format.ENUM;
                    typeDefaultImpl  = null;
                    typeDefaultSuper = container.getIdentityConstant().getType();
                    break;

                case MIXIN:
                    // mixins apply to ("mix into") any Object by default
                    typeDefaultImpl = null;
                    typeDefaultInto = pool.typeObject();
                    break;

                default:
                    typeDefaultImpl = pool.typeObject();
                    break;
                }
            }

        int                  cImports      = 0;
        ModuleStructure      moduleImport  = null;
        boolean              fHasDefault   = false;
        Format               format        = component.getFormat();
        ComponentBifurcator  bifurcator    = new ComponentBifurcator(component);
        ConditionalConstant  condPrev      = null;
        List<ClassStructure> componentList = new ArrayList<>();

        componentList.add(component);

        // note: from this point down (and through the remainder of compilation), the component may
        // be conditionally bifurcated. (it's even possible that already there are multiple
        // components with the same identity, due to conditionality, but what is meant here is that
        // the one component that we just created and configured above may be split into multiple
        // "siblings" of itself based on any conditionals that are encountered in the processing of
        // the "Composition" objects)
        for (CompositionNode composition :
                    compositions == null ? Collections.<CompositionNode>emptyList() : compositions)
            {
            // most compositions are allowed to be conditional; conditional compositions will
            // bifurcate the component
            Expression          exprCond = composition.getCondition();
            ConditionalConstant condCur  = exprCond == null ? null : exprCond.toConditionalConstant();
            if (!Handy.equals(condCur, condPrev))
                {
                componentList.clear();
                bifurcator.collectMatchingComponents(condCur, (List<Component>) (List) componentList);
                condPrev = condCur;
                }

            Token.Id keyword = composition.getKeyword().getId();
            switch (keyword)
                {
                case EXTENDS:
                    // interface is allowed to have any number of "extends"
                    if (format == Format.INTERFACE)
                        {
                        TypeConstant type = composition.getType().ensureTypeConstant();
                        for (ClassStructure struct : componentList)
                            {
                            // when an interface "extends" an interface, it is actually implementing
                            composition.setContribution(
                                    struct.addContribution(Composition.Implements, type));
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
                            // analysis of conditional clauses or appropriate "into"
                            // (we can't evaluate conditions or super classes yet)
                            fAlreadyExtends = composition.condition == null;
                            typeDefaultInto = null;

                            TypeConstant type = composition.getType().ensureTypeConstant();
                            Extends      node = (Extends) composition;
                            for (ClassStructure struct : componentList)
                                {
                                // register the class that the component extends
                                Contribution contrib = struct.addContribution(Composition.Extends, type);
                                composition.setContribution(contrib);
                                m_mapContribArgs.put(contrib, node.args);
                                }
                            }
                        }
                    break;

                case IMPORT:
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
                    Import              compImport = (Import) composition;
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

                    // incorporate version requirements into the package information where the
                    // module is being mounted
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
                        switch (compImport.getImplicitModifier())
                            {
                            case OPTIONAL:
                                moduleImport.fingerprintOptional();
                                break;

                            case DESIRED:
                                moduleImport.fingerprintDesired();
                                break;

                            case EMBEDDED:
                                // the embedding is performed much later, long after the fingerprint
                                // is fully formed; for now, just mark the module import as required
                            case REQUIRED:
                                moduleImport.fingerprintRequired();
                                break;

                            default:
                                throw new IllegalStateException();
                            }

                        // the imported module must always be imported with the same
                        // exact version override (or none)
                        // TODO merge requirements instead of always making this an error, so that
                        //      the same module can be imported by multiple modules within the
                        //      dependency graph, each having their own version reqs
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

                    // check package-local injection support
                    // TODO need depth-from-root-module distance, so that if we import the same
                    //      module more than once, we will only use the injection override from the
                    //      outermost usage, and raise an error if multiple imports of the same
                    //      module at the same depth-from-root-module occur with conflicting params

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
                            for (ClassStructure struct : componentList)
                                {
                                composition.setContribution(
                                        struct.addContribution(Composition.Into,
                                            composition.getType().ensureTypeConstant()));
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
                        for (ClassStructure struct : componentList)
                            {
                            TypeConstant typeImplements = composition.getType().ensureTypeConstant();
                            if (typeImplements.equals(typeDefaultImpl))
                                {
                                typeDefaultImpl = null;
                                }
                            composition.setContribution(
                                    struct.addContribution(Composition.Implements, typeImplements));
                            }
                        }
                    break;

                case DELEGATES:
                    // these are all OK; other checks will be done after the types are resolvable
                    TypeConstant     typeTarget = composition.getType().ensureTypeConstant();
                    Expression       exprTarget = ((Delegates) composition).getDelegatee();
                    String           sTarget;
                    PropertyConstant idTarget;
                    if (exprTarget instanceof NameExpression exprName && exprName.isSimpleName())
                        {
                        sTarget = exprName.getName();
                        }
                    else
                        {
                        // create a new property to represent the value of the expression
                        sTarget = "delegate:" + typeTarget.getValueString();
                        long lStartPos = exprTarget.getStartPosition();
                        long lEndPos   = exprTarget.getEndPosition();
                        PropertyDeclarationStatement propTarget = new PropertyDeclarationStatement(
                                lStartPos,
                                lEndPos,
                                composition.getCondition(),
                                null,
                                null,
                                composition.getType(),
                                new Token(lStartPos, lEndPos, Id.IDENTIFIER, sTarget),
                                null, exprTarget, null, null);
                        propTarget.markSynthetic();
                        exprTarget.setParent(propTarget); // REVIEW
                        ensureBody().addStatement(propTarget);
                        }
                    idTarget = pool.ensurePropertyConstant(component.getIdentityConstant(), sTarget);

                    // remember the property identity that holds the target
                    ((Delegates) composition).setPropertyName(sTarget);

                    for (ClassStructure struct : componentList)
                        {
                        // register the class whose interface the component delegates, and the
                        // property whose value indicates the object to delegate to
                        composition.setContribution(
                                struct.addDelegation(typeTarget, idTarget));
                        }
                    break;

                case INCORPORATES:
                    Incorporates incorp = (Incorporates) composition;
                    if (format == Format.INTERFACE && !incorp.isAnnotation())
                        {
                        // interface can't incorporate
                        composition.log(errs, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED, keyword.TEXT);
                        }
                    else
                        {
                        // these are all OK; other checks will be done after the types are resolvable
                        if (incorp.isAnnotation())
                            {
                            for (ClassStructure struct : componentList)
                                {
                                // register the annotation
                                struct.addAnnotation(incorp.ensureAnnotation(pool));
                                }
                            }
                        else
                            {
                            ListMap<String, TypeConstant> mapConstraints = null;
                            if (incorp.isConditional())
                                {
                                mapConstraints = new ListMap<>();
                                for (Parameter constraint : incorp.getConstraints())
                                    {
                                    // type is null means no constraint
                                    TypeExpression type = constraint.getType();
                                    mapConstraints.put(constraint.getName(),
                                            type == null ? null : type.ensureTypeConstant());
                                    }
                                }

                            for (ClassStructure struct : componentList)
                                {
                                // register the mixin that the component incorporates
                                Contribution contrib = struct.addIncorporates(
                                        composition.getType().ensureTypeConstant(), mapConstraints);
                                composition.setContribution(contrib);
                                m_mapContribArgs.put(contrib, incorp.args);
                                }
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

                        long lStartPos = composition.getStartPosition();
                        long lEndPos   = composition.getEndPosition();
                        PropertyDeclarationStatement propDefault = new PropertyDeclarationStatement(
                                lStartPos, lEndPos,
                                composition.getCondition(),
                                Collections.singletonList(new Token(lStartPos, lStartPos, Id.STATIC)),
                                null,
                                typeDefault,
                                composition.keyword,
                                null, exprValue,
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
        bifurcator.collectMatchingComponents(null, (List<Component>) (List) componentList);

        // add default "implement" and/or "extend"
        if (!fAlreadyExtends && (typeDefaultImpl != null || typeDefaultSuper != null))
            {
            for (ClassStructure struct : componentList)
                {
                assert struct.findContribution(Composition.Extends) == null;

                if (typeDefaultSuper != null)
                    {
                    Contribution contrib =
                            struct.addContribution(Composition.Extends, typeDefaultSuper);
                    m_mapContribArgs.put(contrib, null);
                    }
                if (typeDefaultImpl != null)
                    {
                    struct.addContribution(Composition.Implements, typeDefaultImpl);
                    }
                }
            }

        // add default applies-to ("into")
        if (!fAlreadyIntos && typeDefaultInto != null)
            {
            for (ClassStructure struct : componentList)
                {
                assert struct.getContributionsAsList().stream().noneMatch(contribution ->
                        contribution.getComposition() == Composition.Into);

                // TODO there is still an issue with this w.r.t. conditionals; verify there is no "into" on this struct
                struct.addContribution(Composition.Into, typeDefaultInto);
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
        // resolve-names pass) once we can figure out what types mean; for now, if the properties
        // are missing (i.e. not redundantly declared), then create them, and either way, we will
        // double-check them once we can resolve types
        List<Parameter> listParams = constructorParams;
        if (listParams != null && !listParams.isEmpty())
            {
            Map<String, Component> mapChildren = component.ensureChildByNameMap();
            for (Parameter param : listParams)
                {
                // the name should either not exist already, or if it does, it needs to be a
                // property and the type will have to match the parameter
                String    sParam = param.getName();
                Component child  = mapChildren.get(sParam);
                if (child == null)
                    {
                    // create the property and get it caught up to where we are
                    PropertyDeclarationStatement prop = new PropertyDeclarationStatement(
                            param.getStartPosition(), param.getEndPosition(), null, null, null,
                            param.getType(), param.getNameToken(), null, null, null, null);
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

    /**
     * Error reporting helper for "not a class".
     */
    private void reportRequireClass(ClassStructure component, Contribution contrib,
                                    TypeConstant typeContrib, ErrorListener errs)
        {
        String sCode;
        switch (contrib.getComposition())
            {
            case Implements:
                if (component.getFormat() != Format.INTERFACE)
                    {
                    sCode = Constants.VE_IMPLEMENTS_NOT_CLASS;
                    break;
                    }
                // fall through for interfaces

            case Extends:
                sCode = Constants.VE_EXTENDS_NOT_CLASS;
                break;

            case Incorporates:
                sCode = Constants.VE_INCORPORATES_NOT_CLASS;
                break;

            default:
                throw new IllegalStateException();
            }

        findComposition(contrib).log(errs, Severity.ERROR, sCode,
                component.getIdentityConstant().getPathString(),
                typeContrib.getValueString());
        }

    /**
     * Error reporting helper for "ambiguous signature".
     */
    private void reportAmbiguousConstructor(List<Parameter> listParams, TypeConstant[] atypeParams,
                                            ErrorListener errs)
        {
        int           cParams = atypeParams.length;
        StringBuilder sb = new StringBuilder("construct(");
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
            lStart = listParams.get(0).getStartPosition();
            lEnd   = listParams.get(cParams - 1).getEndPosition();
            }
        errs.log(Severity.ERROR, Compiler.SIGNATURE_AMBIGUOUS,
            new String[] {sb.toString()}, getSource(), lStart, lEnd);
        }

    /**
     * Check whether all parameterizable contributions are indeed parameterized and infer the
     * constraints if possible.
     */
    private void addImplicitTypeConstraints(ClassStructure component, ErrorListener errs)
        {
        assert component.isParameterized();

        List<String> listUnconstrained = null;
        for (Map.Entry<StringConstant, TypeConstant> entry : component.getTypeParams().entrySet())
            {
            String       sName          = entry.getKey().getValue();
            TypeConstant typeConstraint = entry.getValue();

            if (typeConstraint.equals(pool().typeObject()))
                {
                if (listUnconstrained == null)
                    {
                    listUnconstrained = new ArrayList<>();
                    }
                listUnconstrained.add(sName);
                }
            }

        if (listUnconstrained == null)
            {
            // call with non-existing name just to report missing type parameters
            findImplicitConstraint(component, "", null, errs);
            return;
            }

        // this class is parameterized, but some generic types don't have any constraints;
        // check if any contributions do, and infer the constraint type from those contributions
        Map<String, TypeConstant> mapConstraints = null;
        for (String sName : listUnconstrained)
            {
            mapConstraints = findImplicitConstraint(component, sName, mapConstraints, errs);
            }

        if (mapConstraints != null)
            {
            for (Map.Entry<String, TypeConstant> entry : mapConstraints.entrySet())
                {
                component.updateConstraint(entry.getKey(), entry.getValue());
                }
            }
        }

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        ClassStructure component = (ClassStructure) getComponent();

        // all of the contributions need to be resolved before we can proceed (because we're
        // going to have to "follow" those contributions
        for (ClassStructure clz = component; clz != null; clz = clz.getContainingClass())
            {
            if (clz.containsUnresolvedContribution())
                {
                mgr.requestRevisit();
                return;
                }
            }

        // make sure the package import contribution is resolved
        if (component.getFormat() == Format.PACKAGE)
            {
            for (CompositionNode composition : compositions)
                {
                if (composition instanceof Import importClause)
                    {
                    NamedTypeExpression exprInjector = importClause.injector;
                    if (exprInjector != null)
                        {
// TODO GG remove
//                      List<Parameter> listInject = importClause.injects;
                        if (importClause.injector.getIdentityConstant().containsUnresolved())
// TODO GG REVIEW I don't think that this is needed (because the types will get resolved at some point):
                                // || listInject != null && listInject.stream().anyMatch(p ->
                                // p.getType().ensureTypeConstant().containsUnresolved()))
                            {
                            mgr.requestRevisit();
                            return;
                            }
                        }
                    }
                }
            }

        // check for cyclical contributions and validate contribution types
        Contribution contribCyclical = component.hasCyclicalContribution();
        if (contribCyclical != null)
            {
            errs.log(Severity.FATAL, Constants.VE_CYCLICAL_CONTRIBUTION,
                    new Object[]
                        {
                        contribCyclical.getComponent().getIdentityConstant().getValueString(),
                        contribCyclical.toString()
                        },
                    getSource(),
                    compositions.get(0).getStartPosition(),
                    compositions.get(compositions.size() - 1).getEndPosition()
                    );
            return;
            }

        for (Contribution contrib : component.getContributionsAsList())
            {
            TypeConstant typeContrib = contrib.getTypeConstant();
            Composition  composition = contrib.getComposition();
            switch (composition)
                {
                case Extends, Implements, Incorporates:
                    {
                    if (!typeContrib.isExplicitClassIdentity(true))
                        {
                        reportRequireClass(component, contrib, typeContrib, errs);
                        return;
                        }

                    if (typeContrib.isAccessSpecified() || typeContrib.isAnnotated())
                        {
                        findComposition(contrib).log(errs,
                                Severity.ERROR, Component.VE_TYPE_MODIFIER_ILLEGAL,
                                component.getIdentityConstant().getValueString(),
                                typeContrib.getValueString());
                        return;
                        }

                    if (composition == Composition.Extends)
                        {
                        Format formatThis    = component.getFormat();
                        Format formatContrib = typeContrib.getExplicitClassFormat();
                        if (!formatThis.isExtendsLegal(formatContrib))
                            {
                            findComposition(contrib).log(errs,
                                    Severity.ERROR, Constants.VE_EXTENDS_INCOMPATIBLE,
                                    component.getIdentityConstant().getValueString(), formatThis,
                                    typeContrib.getValueString(), formatContrib);
                            return;
                            }
                        }
                    break;
                    }
                }
            }

        if (m_fVirtChild)
            {
            // all of the enclosing component containers must have resolved, because we will depend
            // on their contributions all being fully evaluated and registered onto their
            // corresponding components (just like we will do in this method)
            AstNode parent = getParent();
            while (parent != null)
                {
                if (parent instanceof ComponentStatement && !parent.getStage().isAtLeast(Stage.Resolved))
                    {
                    mgr.requestRevisit();
                    return;
                    }

                parent = parent.getParent();
                }
            }

        Format format = component.getFormat();
        if (format == Format.MODULE)
            {
            // the upstream modules were all linked in the previous pass, so this pass is able to
            // start out by building the entire (transitive closure) set of pools that are upstream
            component.getConstantPool().buildValidPoolSet();
            }

        boolean fModuleImport = false;
        if (format == Format.PACKAGE)
            {
            for (CompositionNode composition : compositions)
                {
                if (composition instanceof Import compImport)
                    {
                    PackageStructure structPkg = (PackageStructure) component;
                    ModuleStructure  structMod = structPkg == null ? null : structPkg.getImportedModule();
                    ModuleStructure  structAct = structMod == null ? null : structMod.getFingerprintOrigin();
                    if (structAct == null)
                        {
                        // this is obviously an error -- we can't compile without the module
                        // being available
                        Import              imp  = compImport;
                        NamedTypeExpression type = (NamedTypeExpression) imp.type;
                        type.log(errs, Severity.ERROR, Compiler.MODULE_MISSING, type.getName());
                        }

                    // now that names have been resolved, if an injector and any injection list are
                    // specified, we can add them to the package import
                    NamedTypeExpression exprInjector  = compImport.getInjector();
                    if (exprInjector != null)
                        {
                        Constant idInjector = exprInjector.getIdentityConstant();
                        if (idInjector.isClass()
                                && idInjector instanceof IdentityConstant id
                                && id.getComponent() instanceof ClassStructure clz
                                && clz.isSingleton())
                            {
                            ConstantPool      pool          = pool();
                            SingletonConstant constInjector = pool.ensureSingletonConstConstant(id);
                            List<Injection>   listInject    = null;
                            List<Parameter>   listParam     = compImport.getSpecificInjections();
                            if (listParam != null)
                                {
                                listInject = new ArrayList<>(listParam.size());
                                for (Parameter param : listParam)
                                    {
                                    TypeConstant   constType = param.getType().ensureTypeConstant();
                                    String         sName     = param.getName();
                                    StringConstant constName = sName == null
                                            ? null
                                            : pool.ensureStringConstant(sName);
                                    listInject.add(new Injection(constType, constName));
                                    }
                                }
                            structPkg.setImportedModuleInjector(constInjector, listInject);
                            }
                        else
                            {
                            exprInjector.log(errs, Severity.ERROR, Compiler.SINGLETON_REQUIRED);
                            break;
                            }
                        }

                    fModuleImport = true;
                    }
                }
            compositions.clear(); // no longer of any use
            }

        if (m_fVirtChild)
            {
            // a virtual child either is a natural extension of its super virtual child, in which
            // case it must be marked by an "@Override" and it must NOT explicitly extend its super
            // virtual child (and if it is a class, it must NOT extend anything), or it does not
            // have a super virtual child, in which case it must NOT be marked by an "@Override",
            // and in the case of a class, it may explicitly extend some class; in the case of an
            // interface, it may naturally extend more than one super virtual child interface, which
            // requires an "@Override" designation, but whether nor not it extends any virtual child
            // interfaces, it is permitted to extend additional interfaces; however, the rule still
            // holds that it must NOT explicitly extend any of its super virtual child interfaces

            // the first step is to see what the explicit "extends" is stamped on the class, and
            // then request the class to search for and resolve its natural inner class super (which
            // will modify the class if a virtual child super is found); note that we do this work
            // even for interfaces, although they cannot / must not have a super class, thus an
            // interface that finds a natural virtual child super will be detected as an error below
            ListSet<Contribution> setContrib = new ListSet<>();
            if (!component.resolveVirtualSuper(setContrib))
                {
                mgr.requestRevisit();
                return;
                }

            boolean fReqOverride   = false;
            boolean fAlreadyLogged = false;
            switch (component.getFormat())
                {
                case INTERFACE:
                    {
                    // check if there is a virtual child super class (!!) found for this interface
                    if (setContrib.stream().anyMatch(contrib -> contrib.getComposition() == Composition.Extends))
                        {
                        // there exists a super virtual child, so it is illegal to define an
                        // interface of this name
                        category.log(errs, getSource(), Severity.ERROR, Compiler.VIRTUAL_CHILD_EXTENDS_CLASS,
                                name.getValueText());
                        fAlreadyLogged = true;
                        break;
                        }

                    // if there is an implied super-interface (or a set thereof), then @Override is
                    // required; otherwise, it must NOT be present
                    if (setContrib.isEmpty())
                        {
                        fReqOverride = false;
                        }
                    else
                        {
                        // verify that none of the virtual child super interfaces was implemented
                        // explicitly
                        for (CompositionNode composition : compositions)
                            {
                            TypeConstant type = composition.getType().ensureTypeConstant();
                            if (composition.keyword.getId() == Id.EXTENDS && setContrib.stream().anyMatch(
                                    contrib -> contrib.getTypeConstant().equals(type)))
                                {
                                composition.log(errs, Severity.ERROR,
                                        Compiler.VIRTUAL_CHILD_EXTENDS_IMPLICIT,
                                        name.getValueText());
                                fAlreadyLogged = true;
                                break;
                                }
                            }

                        fReqOverride = true;
                        }
                    break;
                    }

                case MIXIN:
                case CLASS:
                case CONST:
                case SERVICE:
                    {
                    Contribution contribImplicit = null;
                    for (Contribution contrib : setContrib)
                        {
                        // the contribution can only be Extends or Implements (see resolveVirtualSuper)
                        if (contrib.getComposition() == Composition.Extends)
                            {
                            if (contribImplicit != null)
                                {
                                name.log(errs, getSource(), Severity.ERROR, Compiler.VIRTUAL_CHILD_EXTENDS_MULTIPLE,
                                        name.getValueText());
                                fAlreadyLogged = true;
                                break;
                                }

                            contribImplicit = contrib;

                            // copy this constructor's arguments
                            List<Parameter> listParams = constructorParams;
                            if (listParams != null)
                                {
                                int              cArgs    = listParams.size();
                                List<Expression> listArgs = new ArrayList(cArgs);
                                for (int i = 0; i < cArgs; i++)
                                    {
                                    listArgs.add(new NameExpression(this, listParams.get(i).name, null));
                                    }
                                m_mapContribArgs.put(contrib, listArgs);
                                }
                            }
                        else if (!fAlreadyLogged)
                            {
                            name.log(errs, getSource(), Severity.ERROR, Compiler.VIRTUAL_CHILD_EXTENDS_INTERFACE,
                                    name.getValueText());
                            fAlreadyLogged = true;
                            }
                        }

                    Contribution contribExplicit = component.findContribution(Composition.Extends);
                    if (contribExplicit == null)
                        {
                        if (contribImplicit == null)
                            {
                            // there is no super virtual child, and the virtual child does not have
                            // an "extends" clause, so add "implements Object" to the class
                            if (component.getFormat() != Format.MIXIN)
                                {
                                // check if we already added the implicit one on a previous round
                                List<Contribution> listContribs = component.getContributionsAsList();
                                TypeConstant       typeObject   = pool().typeObject();
                                if (listContribs.isEmpty() ||
                                    listContribs.stream().noneMatch(contribPrev ->
                                                contribPrev.getComposition() == Composition.Implements &&
                                                contribPrev.getTypeConstant().equals(typeObject)))
                                    {
                                    component.addContribution(Composition.Implements, typeObject);
                                    }
                                }

                            // @Override should NOT exist
                            fReqOverride = false;
                            }
                        else
                            {
                            // there is a virtual super, so @Override is required
                            fReqOverride = true;
                            }
                        }
                    else if (contribImplicit != null)
                        {
                        // check if the resolution of the virtual child super modified the "extends"
                        // contribution
                        if (contribExplicit.equals(contribImplicit))
                            {
                            // no change to the "extends" by resolving virtual super, but we must
                            // check to make sure that the "extends" clause was not an explicit
                            // "extends" of a virtual child super
                            IdentityConstant idThis  = component.getIdentityConstant();
                            IdentityConstant idSuper = contribImplicit.getTypeConstant()
                                    .getSingleUnderlyingClass(false);
                            while (true)
                                {
                                idThis  = idThis.getParentConstant();
                                idSuper = idSuper.getParentConstant();
                                if (idThis.getFormat() != idSuper.getFormat())
                                    {
                                    // if they were both referring to the same virtual child path,
                                    // then that path would contain identical constant forms
                                    break;
                                    }

                                if (idThis instanceof ClassConstant)
                                    {
                                    ClassStructure clzSuper = ((ClassStructure) idThis.getComponent()).getSuper();
                                    if (clzSuper.getIdentityConstant().equals(idSuper))
                                        {
                                        for (CompositionNode composition : compositions)
                                            {
                                            if (composition.keyword.getId() == Id.EXTENDS)
                                                {
                                                composition.log(errs, Severity.ERROR,
                                                        Compiler.VIRTUAL_CHILD_EXTENDS_IMPLICIT,
                                                        name.getValueText());
                                                fAlreadyLogged = true;
                                                break;
                                                }
                                            }
                                        }
                                    break;
                                    }
                                }

                            fReqOverride = false;
                            }
                        else
                            {
                            // the "extends" was modified by resolving the virtual child super, so
                            // that means that the previous value was wrong, and there should have
                            // been an "@Override"
                            for (CompositionNode composition : compositions)
                                {
                                if (composition.keyword.getId() == Id.EXTENDS)
                                    {
                                    composition.log(errs, Severity.ERROR,
                                            Compiler.VIRTUAL_CHILD_EXTENDS_ILLEGAL,
                                            name.getValueText());
                                    fAlreadyLogged = true;
                                    break;
                                    }
                                }
                            fReqOverride = true;
                            }
                        }
                    break;
                    }

                default:
                    throw new IllegalStateException();
                }

            // the reason for keeping track of whether we've already logged an error is that it will
            // be quite common to have two errors from the same cause, e.g. not realizing that a
            // child class has a same-name virtual child super, in which case @Override will be
            // missing AND the virtual child may have specified a super class
            if (!fAlreadyLogged)
                {
                // scan for an @Override annotation
                boolean fHasOverride = false;
                if (annotations != null)
                    {
                    ClassConstant clzOverride  = pool().clzOverride();
                    for (AnnotationExpression annotation : annotations)
                        {
                        if (Handy.equals(clzOverride, annotation.toTypeExpression().ensureTypeConstant()
                                .getSingleUnderlyingClass(false)))
                            {
                            fHasOverride = true;
                            if (!fReqOverride)
                                {
                                // there is an @Override but one should not exist
                                annotation.log(errs, Severity.ERROR, Compiler.VIRTUAL_CHILD_OVERRIDE_ILLEGAL,
                                        name.getValueText());
                                }
                            break;
                            }
                        }
                    }

                if (fReqOverride && !fHasOverride)
                    {
                    // @Override is required but none was specified
                    name.log(errs, getSource(), Severity.ERROR, Compiler.VIRTUAL_CHILD_OVERRIDE_MISSING,
                            name.getValueText());
                    }

                // add the implicit contributions to the virtual child
                for (Contribution contrib : setContrib)
                    {
                    Composition composition = contrib.getComposition();
                    component.addContribution(composition, contrib.getTypeConstant());
                    if (composition == Composition.Extends) // we don't care about "Implements"
                        {
                        m_mapContribArgs.putIfAbsent(contrib, null);
                        }
                    }
                }
            }

        mgr.processChildren();

        Map<String, Component> mapChildren        = component.ensureChildByNameMap();
        MultiMethodStructure   constructors       = (MultiMethodStructure) mapChildren.get("construct");
        List<Parameter>        listParams         = constructorParams;
        boolean                fExplicitDefault   = false;
        boolean                fExplicitShorthand = false;

        if (listParams == null || listParams.isEmpty())
            {
            // there is no "shorthand" declaration; there must be no more than one explicit
            // no-parameters constructor
            if (constructors != null)
                {
                for (MethodStructure constructor : constructors.methods())
                    {
                    // verify that all parameters are defaulted
                    if (constructor.getParamCount() > constructor.getDefaultParamCount())
                        {
                        continue;
                        }

                    // should only find one match; make sure we didn't already find one
                    if (fExplicitDefault)
                        {
                        reportAmbiguousConstructor(listParams, TypeConstant.NO_TYPES, errs);
                        break;
                        }
                    fExplicitDefault = true;
                    }
                }
            }
        else
            {
            if (fModuleImport)
                {
                listParams.get(0).log(errs, Severity.ERROR, Compiler.IMPURE_MODULE_IMPORT);
                }

            // resolve the type of each constructor parameter
            IdentityConstant idThis      = component.getIdentityConstant();
            int              cParams     = listParams.size();
            TypeConstant[]   atypeParams = new TypeConstant[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                Parameter    param     = listParams.get(i);
                TypeConstant typeParam = param.getType().ensureTypeConstant();

                if (typeParam.containsUnresolved())
                    {
                    mgr.requestRevisit();
                    return;
                    }

                String            sParam = param.getName();
                PropertyStructure prop   = (PropertyStructure) mapChildren.get(sParam);

                // whether or not the property is defined by the super class, we must've created
                // a synthetic structure for it during registerStructures() phase
                assert prop != null;

                if (prop.getType().containsUnresolved())
                    {
                    mgr.requestRevisit();
                    return;
                    }

                if (component.isStatic() && typeParam.isGenericType())
                    {
                    PropertyConstant idFormal = (PropertyConstant) typeParam.getDefiningConstant();
                    if (!idFormal.getParentConstant().equals(idThis))
                        {
                        // DEFERRED: consider a possibility to turn this error into a "semi-virtual"
                        //           child type that carries the parent's type, but not reference
                        param.log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_INACCESSIBLE,
                            idFormal.getName(), component.getName());
                        }
                    }

                // the property type must be compatible with the parameter type,
                // but it is too early in the compilation cycle to use "isA()";
                // we will check for the match while building the type info
                atypeParams[i] = typeParam;
                }

            // look for a constructor that corresponds to those parameter types, and create the
            // constructor if it does not already exist. the constructor may have been declared
            // explicitly, but we can't verify that for certain because the types may appear
            // slightly different here (since we can't resolve types at this stage), so what we
            // will look for is any constructor that has a matching number of parameters (or a
            // higher number of parameters if the additional parameters are optional), and each
            // parameter type must match the type specified in the shorthand constructor notation
            if (constructors != null)
                {
                NextConstructor:
                for (MethodStructure constructor : constructors.methods())
                    {
                    org.xvm.asm.Parameter[] aConsParams = constructor.getParamArray();
                    int                     cConsParams = aConsParams.length;

                    if (cParams > cConsParams ||
                        cParams < cConsParams - constructor.getDefaultParamCount())
                        {
                        continue;
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
                        // note: it is too early in the compilation cycle to use "isA()"
                        if (!atypeParams[i].equals(typeConsParam))
                            {
                            continue NextConstructor;
                            }
                        }

                    // should only find one match; make sure we didn't already find one
                    if (fExplicitShorthand)
                        {
                        reportAmbiguousConstructor(listParams, atypeParams, errs);
                        break;
                        }

                    constructor.markAsShorthand();
                    fExplicitShorthand = true;
                    }
                }

            if (!fExplicitShorthand)
                {
                // create a constructor based on the "shorthand notation" implicit constructor
                // definition
                ConstantPool            pool    = pool();
                org.xvm.asm.Parameter[] aParams = new org.xvm.asm.Parameter[cParams];
                for (int i = 0; i < cParams; ++i)
                    {
                    Parameter param = listParams.get(i);
                    aParams[i] = new org.xvm.asm.Parameter(pool, atypeParams[i],
                            param.getName(), null, false, i, false);
                    if (param.value != null)
                        {
                        aParams[i].markDefaultValue();
                        }
                    }

                MethodStructure constructor = component.createMethod(true, Access.PUBLIC,
                        org.xvm.asm.Annotation.NO_ANNOTATIONS, org.xvm.asm.Parameter.NO_PARAMS,
                        "construct", aParams, true, false);

                // set the synthetic flag so that the constructor knows to provide its own
                // default implementation when it emits code
                constructor.setSynthetic(true);
                constructor.markAsShorthand();
                constructors = (MultiMethodStructure) constructor.getParent();
                }
            }

        if (fExplicitDefault || fExplicitShorthand)
            {
            // when there is an explicit shorthand or default constructor, we don't allow
            // parameterized contributions ("extends" or "incorporates")
            for (CompositionNode composition : compositions)
                {
                List<Expression> listArgs =
                        composition instanceof Extends nodeExtends
                            ? nodeExtends.args :
                        composition instanceof Incorporates nodeIncorp
                                && !nodeIncorp.isAnnotation()
                            ? nodeIncorp.args
                            : null;

                if (listArgs != null && !listArgs.isEmpty())
                    {
                    composition.log(errs, Severity.ERROR, Compiler.CONTRIBUTION_PARAMS_UNEXPECTED);
                    }
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
            constructor.markAsShorthand();
            }
        else if (component.isSingleton())
            {
            // for a singleton, constructor parameters are not permitted unless they all have
            // default values (since the module is a singleton, and is automatically created,
            // i.e. it has to have all of its construction parameters available)
            boolean fFound = false;
            NextConstructor: for (MethodStructure constructor : constructors.methods())
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

        if (component.isParameterized())
            {
            addImplicitTypeConstraints(component, errs);
            }
        else if (format == Format.MIXIN)
            {
            // mixins naturally imply formal type parameters from their contributions (most
            // likely the "into" clauses); there is logic in NameTypeExpression that defers the name
            // resolution until the implicit type parameters are added by this method (if any)
            errs = errs.branch(this);
            component.addImplicitTypeParameters(pool(), errs);
            errs.merge();
            }

        if (format == Format.CONST)
            {
            component.synthesizeConstInterface(false);
            }
        }

    @Override
    public void validateContent(StageMgr mgr, ErrorListener errs)
        {
        if (!mgr.processChildren())
            {
            mgr.requestRevisit();
            return;
            }

        ConstantPool   pool      = pool();
        ClassStructure component = (ClassStructure) getComponent();
        if (component instanceof PackageStructure pkg && pkg.isModuleImport())
            {
            SingletonConstant constInjector = pkg.getModuleInjector();
            if (constInjector != null && !constInjector.getType().isA(
                    pool().ensureEcstasyTypeConstant("mgmt.ResourceProvider")))
                {
                log(errs, Severity.ERROR, Compiler.INJECTOR_REQUIRED);
                }
            }

        // adjust synthetic properties created during registerStructures() phase if necessary
        ClassStructure  clzSuper       = component.getSuper();
        boolean         fAllowOverride = true;
        List<Parameter> listParams     = constructorParams;

        switch (component.getFormat())
            {
            case INTERFACE:
                clzSuper = null; // skip the check
                break;

            case ENUM:
                clzSuper       = (ClassStructure) pool.clzEnum().getComponent();
                fAllowOverride = false;
                break;

            case SERVICE:
                if (clzSuper == null)
                    {
                    clzSuper = (ClassStructure) pool.clzService().getComponent();
                    }
                break;
            }

        if (clzSuper != null && listParams != null)
            {
            TypeConstant typeSuper = pool.ensureAccessTypeConstant(
                                        clzSuper.getFormalType(), Access.PROTECTED);
            TypeInfo     infoSuper = typeSuper.ensureTypeInfo(errs);

            Map<String, Component> mapChildren = component.ensureChildByNameMap();
            boolean                fInvalidate = false;
            for (int i = 0, cParams = listParams.size(); i < cParams; ++i)
                {
                Parameter param = listParams.get(i);
                String    sProp = param.getName();

                PropertyStructure prop = (PropertyStructure) mapChildren.get(sProp);
                if (!prop.isSynthetic())
                    {
                    // the property has been explicitly modified
                    continue;
                    }

                PropertyInfo infoProp = infoSuper.findProperty(sProp);
                if (infoProp == null)
                    {
                    // the "super" property doesn't exist or not accessible
                    continue;
                    }

                if (!fAllowOverride)
                    {
                    param.log(errs, Severity.ERROR, Compiler.PROPERTY_NAME_RESERVED, sProp);
                    }

                if (!infoProp.isVar() && !infoProp.isAbstract())
                    {
                    // the setter for the super's property is not reachable, mark the synthetic one
                    // as "@Override @RO"
                    prop.addAnnotation(pool.clzOverride());
                    prop.addAnnotation(pool.clzRO());
                    fInvalidate = true;
                    }

                TypeConstant typeThis = prop.getType().resolveTypedefs();
                TypeConstant typeBase = infoProp.getType().removeAccess().resolveGenerics(pool, typeThis);
                // ensure the type equivalency, which slightly more lax than equality
                if (!typeBase.isA(typeThis) || !typeThis.isA(typeBase))
                    {
                    param.log(errs, Severity.ERROR, Compiler.PROPERTY_TYPE_COLLISION,
                            sProp, clzSuper.getName());
                    }
                }

            if (fInvalidate)
                {
                pool.invalidateTypeInfos(component.getIdentityConstant());
                }
            }

        TypeConstant typeThis  = component.getFormalType();
        TypeConstant typeClass = pool.ensureParameterizedTypeConstant(
                                    pool.typeClass(), component.getIdentityConstant().getType());
        if (validateAnnotations(component.collectAnnotations(false), typeThis, errs) &&
            validateAnnotations(component.collectAnnotations(true),  typeClass, errs))
            {
            if (component.isParameterized())
                {
                resolveAnnotationTypes(component, errs);
                }
            }
        }

    /**
     * Validate the specified annotations.
     *
     * @param aAnno       the annotations
     * @param typeTarget  the annotation target type
     * @param errs        the error listener
     *
     * @return false iff there is a validation error
     */
    private boolean validateAnnotations(Annotation[] aAnno, TypeConstant typeTarget, ErrorListener errs)
        {
        for (int i = 0, c = aAnno.length; i < c; i++)
            {
            Annotation   anno      = aAnno[i];
            TypeConstant typeMixin = anno.getAnnotationType();
            if (typeMixin.getExplicitClassFormat() != Component.Format.MIXIN)
                {
                // no need to do anything; an error will be reported later
                findAnnotationExpression(anno, annotations).
                    log(errs, Severity.ERROR, org.xvm.compiler.Constants.VE_ANNOTATION_NOT_MIXIN,
                        anno.getValueString());
                return false;
                }

            TypeConstant typeInto = typeMixin.getExplicitClassInto(true);

            if (!typeTarget.isA(typeInto))
                {
                findAnnotationExpression(anno, annotations).
                    log(errs, Severity.ERROR, org.xvm.compiler.Constants.VE_ANNOTATION_INCOMPATIBLE,
                        typeTarget.getValueString(), typeMixin.getValueString(), typeInto.getValueString());
                return false;
                }

            for (int j = i+1; j < c; j++)
                {
                Annotation   anno2      = aAnno[j];
                TypeConstant typeMixin2 = anno2.getAnnotationType();
                if (typeMixin2.equals(typeMixin))
                    {
                    findAnnotationExpression(anno, annotations).
                        log(errs, Severity.ERROR, org.xvm.compiler.Constants.VE_ANNOTATION_REDUNDANT,
                            anno.getValueString());
                    return false;
                    }
                }
            }
        return true;
        }

    /**
     * If there are any undeclared formal parameters for the Annotation contributions of this class,
     * resolve the corresponding annotation types based on this class's formal type.
     *
     * @param component  the ClassStructure we're creating
     * @param errs       the error listener
     */
    private void resolveAnnotationTypes(ClassStructure component, ErrorListener errs)
        {
        for (Contribution contrib : component.getContributionsAsList())
            {
            switch (contrib.getComposition())
                {
                case Annotation:
                    break;

                default:
                    // disregard any other contribution
                    continue;
                }

            TypeConstant typeAnno = contrib.getTypeConstant();
            if (typeAnno.isParamsSpecified())
                {
                continue;
                }

            if (typeAnno.isSingleUnderlyingClass(false))
                {
                ClassStructure clzAnno = (ClassStructure)
                        typeAnno.getSingleUnderlyingClass(true).getComponent();

                // update the contribution
                contrib.narrowType(clzAnno.getFormalType().
                        resolveGenerics(pool(), component.getFormalType()));
                }
            else
                {
                log(errs, Severity.ERROR, Constants.VE_ANNOTATION_NOT_CLASS,
                        typeAnno.getValueString());
                }
            }
        }

    /**
     * Check if any contribution of the specified class has a non-trivial constraint for the
     * generic type of the specified name.
     */
    private Map<String, TypeConstant> findImplicitConstraint(ClassStructure clz, String sName,
                Map<String, TypeConstant> mapConstraints, ErrorListener errs)
        {
        ConstantPool pool = pool();

        for (Contribution contrib : clz.getContributionsAsList())
            {
            switch (contrib.getComposition())
                {
                case Extends:
                case Implements:
                case Incorporates:
                case Into:
                    break;

                default:
                    // disregard any other contribution
                    continue;
                }

            TypeConstant typeContrib = contrib.getTypeConstant();

            if (!typeContrib.isExplicitClassIdentity(true) || contrib.getTypeParams() != null)
                {
                // ignore any relational constraints or conditional incorporates
                continue;
                }

            ClassStructure clzContrib = (ClassStructure) typeContrib.
                    getSingleUnderlyingClass(true).getComponent();
            if (typeContrib.isParameterizedDeep())
                {
                TypeConstant typeConstraint = clzContrib.getTypeParams().
                        get(pool.ensureStringConstant(sName));
                if (typeConstraint != null)
                    {
                    if (typeConstraint.equals(pool.typeObject()))
                        {
                        // report errors only at the "top" level
                        mapConstraints = findImplicitConstraint(clzContrib, sName, mapConstraints,
                                            ErrorListener.BLACKHOLE);
                        }
                    else
                        {
                        if (mapConstraints == null)
                            {
                            mapConstraints = new ListMap<>();
                            }
                        else
                            {
                            TypeConstant typePrev = mapConstraints.get(sName);
                            if (typePrev != null)
                                {
                                typeConstraint = typePrev.combine(pool, typeConstraint);
                                }
                            }

                        mapConstraints.put(sName, typeConstraint);
                        return mapConstraints;
                        }
                    }
                }
            else if (clzContrib.isParameterizedDeep())
                {
                // the class has generic type parameters, but the contribution doesn't specify any
                log(errs, Severity.ERROR, Compiler.MISSING_TYPE_PARAMETERS,
                        typeContrib.getValueString());
                }
            }
        return mapConstraints;
        }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        return this;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        return true;
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

        ValidateShorthand:
        if (component.getFormat() != Format.INTERFACE)
            {
            MethodStructure constructor =
                    component.findMethod("construct", MethodStructure::isShorthandConstructor);
            if (constructor == null ||
                    !constructor.isSynthetic() || constructor.hasOps())
                {
                break ValidateShorthand;
                }

            RootContext ctxConstruct = createConstructorContext(constructor);
            Context     ctx          = ctxConstruct.validatingContext();

            if (constructorParams != null && !constructorParams.isEmpty())
                {
                // resolve the default values for constructor parameters
                if (!validateDefaultParameters(ctx, constructor, errs))
                    {
                    return;
                    }
                }

            ConstantPool           pool          = pool();
            List<Contribution>     listContribs  = null;
            List<TypeConstant>     listTypes     = null;
            List<MethodConstant>   listIds       = null;
            List<List<Expression>> listArgLists  = null;

            for (Map.Entry<Contribution, List<Expression>> entry : m_mapContribArgs.entrySet())
                {
                Contribution  contrib = entry.getKey();

                TypeConstant     typeSuper;
                List<Expression> listSuperArgs;

                switch (contrib.getComposition())
                    {
                    case Extends:
                        typeSuper = pool.ensureAccessTypeConstant(contrib.getTypeConstant(), Access.PROTECTED);
                        if (args == null)
                            {
                            // Examples:
                            //    const DeadlockException(String? text = null, Exception? cause = null)
                            //        extends Exception(text, cause);
                            //
                            //    const Point3d(Int x, Int y, Int z = 0)
                            //        extends Point(x, y);
                            listSuperArgs = entry.getValue();
                            }
                        else
                            {
                            // Examples:
                            //
                            //    enum Ordered(String symbol) { Lesser("<"), ... }
                            //
                            //    Entry<Key, Value> entry = new KeyBasedEntry<Key, Value>(key)
                            //          {
                            //          @Override
                            //          Value getValue()
                            //              {
                            //              ...
                            //              }
                            //          }
                            listSuperArgs = args;
                            }
                        break;

                    case Incorporates:
                        assert args == null; // no "incorporates" for anonymous classes or enums

                        typeSuper = (contrib.isConditional()
                                ? resolveConditionalMixin(ctx, contrib)
                                : contrib.getTypeConstant()).
                                    adjustAccess(component.getIdentityConstant());
                        listSuperArgs = entry.getValue();
                        break;

                    default:
                        typeSuper     = null;
                        listSuperArgs = null;
                        break;
                    }

                if (typeSuper != null)
                    {
                    MethodConstant idSuper =
                            findSuperConstructor(ctx, typeSuper, listSuperArgs, errs);
                    if (idSuper == null)
                        {
                        // error must have been reported
                        return;
                        }

                    MethodStructure constructSuper = typeSuper.ensureTypeInfo().
                            getMethodById(idSuper).getHead().getMethodStructure();

                    assert constructSuper != null;
                    if (!constructSuper.hasOps())
                        {
                        mgr.requestRevisit();
                        return;
                        }

                    if (!validateSuperParameters(ctx, constructor, idSuper, constructSuper,
                                listSuperArgs, errs))
                        {
                        // error must have been reported
                        return;
                        }

                    // replace a possibly synthetic (type dependant) identity with an absolute one
                    idSuper = constructSuper.getIdentityConstant();

                    (listContribs = ensureList(listContribs)).add(contrib);
                    (listTypes    = ensureList(listTypes))   .add(typeSuper);
                    (listIds      = ensureList(listIds))     .add(idSuper);
                    (listArgLists = ensureList(listArgLists)).add(listSuperArgs);
                    }
                }

            generateConstructor(ctxConstruct, constructor,
                    listContribs, listTypes, listIds, listArgLists, errs);
            }

        if (compositions == null)
            {
            return;
            }

        for (CompositionNode composition : compositions)
            {
            if (composition instanceof Delegates compDelegate)
                {
                String       sProp   = compDelegate.getPropertyName();
                TypeConstant typePri = pool().ensureAccessTypeConstant(
                                            component.getFormalType(), Access.PRIVATE);
                TypeInfo     infoThis = typePri.ensureTypeInfo(errs);
                PropertyInfo infoProp = infoThis.findProperty(sProp);
                if (infoProp == null)
                    {
                    composition.log(errs, Severity.ERROR, Compiler.DELEGATE_PROP_MISSING, sProp);
                    return;
                    }

                TypeConstant typeDelegate = composition.getType().ensureTypeConstant();
                TypeConstant typeProp     = infoProp.getType();
                if (!typeProp.isA(typeDelegate))
                    {
                    composition.log(errs, Severity.ERROR, Compiler.DELEGATE_PROP_WRONG_TYPE, sProp,
                        typeDelegate.getValueString(), typeProp.getValueString());
                    return;
                    }
                }
            }
        }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * A simple helper to create a new context for shorthand constructor processing.
     */
    private RootContext createConstructorContext(MethodStructure constructor)
        {
        StatementBlock blockBody = body;
        if (body == null)
            {
            blockBody = adopt(new StatementBlock(Collections.EMPTY_LIST));
            }
        return new RootContext(blockBody, constructor);
        }

    /**
     * Validate shorthand constructor's default parameters.
     */
    private boolean validateDefaultParameters(Context ctx, MethodStructure constructor,
                                              ErrorListener errs)
        {
        boolean         fValid     = true;
        List<Parameter> listParams = constructorParams;

        for (int i = 0, cParams = listParams.size(); i < cParams; ++i)
            {
            org.xvm.asm.Parameter param = constructor.getParam(i);
            if (!param.hasDefaultValue())
                {
                continue;
                }

            TypeConstant typeParam = param.getType();
            Expression   exprOld   = listParams.get(i).value;

            assert exprOld != null;
            if (exprOld.isValidated())
                {
                continue;
                }

            ctx = ctx.enterInferring(typeParam);

            Expression exprNew = exprOld.validate(ctx, typeParam, errs);

            ctx = ctx.exit();

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

        return fValid;
        }

    private <T> List<T> ensureList(List<T> list)
        {
        return list == null ? new ArrayList<>() : list;
        }

   /**
    * Given a contribution representing a conditional mixin, compute a "minimal" type that would
    * make the conditional incorporation applicable. Additionally, make the specified context aware
    * of the narrowed generic types, so it can be used for argument validation.
    */
    private TypeConstant resolveConditionalMixin(Context ctx, Contribution contrib)
        {
        Map<StringConstant, TypeConstant> mapConstraints = contrib.getTypeParams();
        assert mapConstraints != null;

        TypeConstant typeContrib = contrib.getTypeConstant();
        if (typeContrib instanceof ParameterizedTypeConstant)
            {
            typeContrib = typeContrib.getUnderlyingType();
            }

        ClassStructure clzThis       = ctx.getThisClass();
        TypeConstant[] atypeResolved = new TypeConstant[clzThis.getTypeParamCount()];
        int            ix            = 0;
        for (Map.Entry<StringConstant, TypeConstant> entry : clzThis.getTypeParams().entrySet())
            {
            StringConstant constName = entry.getKey();

            TypeConstant typeConstraint = mapConstraints.get(constName);
            if (typeConstraint == null)
                {
                atypeResolved[ix++] = entry.getValue();
                }
            else
                {
                atypeResolved[ix++] = typeConstraint;

                PropertyConstant idFormal = clzThis.getFormalType().ensureTypeInfo().
                        findProperty(constName.getValue()).getIdentity();
                ctx.replaceGenericType(idFormal, Branch.Always, typeConstraint.getType());
                }
            }
        return pool().ensureParameterizedTypeConstant(typeContrib, atypeResolved);
        }

    /**
     * @return a MethodConstant for the specified super type constructor; null if an error has
     *         been logged
     */
    private MethodConstant findSuperConstructor(Context ctx, TypeConstant typeSuper,
                                                 List<Expression> listSuperArgs, ErrorListener errs)
        {
        TypeInfo       infoSuper = typeSuper.ensureTypeInfo(errs);
        MethodConstant idSuper   = findMethod(ctx, typeSuper, infoSuper, "construct",
                                    listSuperArgs, MethodKind.Constructor, true, false, null, errs);
        if (idSuper == null)
            {
            // if an error has already been logged, this is an additional information
            log(errs, Severity.ERROR, Compiler.IMPLICIT_SUPER_CONSTRUCTOR_MISSING,
                    getComponent().getIdentityConstant().getValueString(), typeSuper.getValueString());
            return null;
            }

        return idSuper;
        }

    /**
     * Validate "extend" parameters and mark the constructor with constant arguments.
     *
     * @return the MethodConstant for the super constructor; null if the validation failed
     */
    private boolean validateSuperParameters(Context ctx, MethodStructure constructor,
                MethodConstant idSuper, MethodStructure constructSuper,
                List<Expression> listSuperArgs, ErrorListener errs)
        {
        // validate
        Constant[] aconstSuper = null;
        if (listSuperArgs != null && !listSuperArgs.isEmpty())
            {
            if (containsNamedArgs(listSuperArgs))
                {
                listSuperArgs = rearrangeNamedArgs(constructSuper, listSuperArgs, errs);
                if (listSuperArgs == null)
                    {
                    // invalid names encountered
                    return false;
                    }
                }

            boolean        fValid      = true;
            TypeConstant[] atypeArgs   = idSuper.getRawParams();
            for (int i = 0, c = listSuperArgs.size(); i < c; i++)
                {
                Expression exprOld = listSuperArgs.get(i);
                if (exprOld.isValidated())
                    {
                    // this could happen for anonymous inner classes, in which case arguments
                    // are validated by NewExpression
                    assert ctx.getThisClass().isAnonInnerClass();
                    continue;
                    }
                Expression exprNew = exprOld.validate(ctx, atypeArgs[i], errs);
                if (exprNew == null)
                    {
                    fValid = false;
                    continue;
                    }

                if (exprNew != exprOld)
                    {
                    listSuperArgs.set(i, exprNew);
                    }

                Constant constant = exprNew.toConstant();
                if (constant != null && !(constant instanceof RegisterConstant))
                    {
                    if (aconstSuper == null)
                        {
                        aconstSuper = new Constant[c];
                        }
                    aconstSuper[i] = constant;
                    }
                }

            if (!fValid)
                {
                return false;
                }
            }

        if (getComponent().getFormat() == Format.MIXIN)
            {
            // annotation mixins are applied to the underlying classes dynamically and need to
            // compute default values at run-time (see TypeConstant#mergeMixinTypeInfo)
            constructor.setShorthandInitialization(idSuper, aconstSuper);
            }
        return true;
        }

    private void disallowTypeParams(ErrorListener errs)
        {
        // type parameters are not permitted
        List<Parameter> listParams = typeParams;
        if (listParams != null && !listParams.isEmpty())
            {
            listParams.get(0).log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNEXPECTED);
            }
        }

    private void requireConstructorParamValues(ErrorListener errs)
        {
        // constructor parameters require default values
        List<Parameter> listParams = constructorParams;
        if (listParams != null && !listParams.isEmpty())
            {
            for (Parameter param : listParams)
                {
                if (param.value == null)
                    {
                    param.log(errs, Severity.ERROR, Compiler.CONSTRUCTOR_PARAM_DEFAULT_REQUIRED);
                    }
                }
            }
        }

    private AstNode findComposition(Contribution contrib)
        {
        for (CompositionNode composition : compositions)
            {
            if (composition.getContribution() == contrib)
                {
                return composition;
                }
            }
        return this;
        }

    /**
     * Emit the synthetic constructor's code.
     */
    private void generateConstructor(RootContext ctxConstruct, MethodStructure constructor,
                                     List<Contribution> listContribs, List<TypeConstant> listTypes,
                                     List<MethodConstant> listIds, List<List<Expression>> listArgLists,
                                     ErrorListener errs)
        {
        // the constructor has two responsibilities:
        // 1) set each property based on the parameter name and value passed in as an arg
        // 2) call the super constructors (for extended class and incorporated mixins)
        ConstantPool   pool      = pool();
        ClassStructure component = (ClassStructure) getComponent();
        Code           code      = constructor.ensureCode();
        Context        ctxEmit   = ctxConstruct.emittingContext(code);

        if (args == null)
            {
            // parameters that don't exist on the super and mixins must be properties on "this"
            // and we need to assign them
            for (int iParam = 0, cParams = constructor.getParamCount(); iParam < cParams; ++iParam)
                {
                var     param  = constructor.getParam(iParam);
                String  sParam = param.getName();
                boolean fSuper = false;
                if (listTypes != null)
                    {
                    for (TypeConstant typeSuper : listTypes)
                        {
                        PropertyInfo propSuper = typeSuper.ensureTypeInfo(errs).findProperty(sParam);
                        if (propSuper != null && !propSuper.isAbstract())
                            {
                            fSuper = true;
                            break;
                            }
                        }
                    }

                if (!fSuper)
                    {
                    // there must be a property by the same name on "this"
                    TypeInfo infoThis  = pool.ensureAccessTypeConstant(
                            component.getFormalType(), Access.PRIVATE).ensureTypeInfo(errs);
                    PropertyInfo propThis = infoThis.findProperty(sParam);
                    if (propThis == null || !propThis.isVar())
                        {
                        constructorParams.get(iParam).log(errs, Severity.ERROR,
                                    Compiler.IMPLICIT_PROP_MISSING, sParam);
                        }
                    else
                        {
                        TypeConstant typeProp = propThis.getType();
                        TypeConstant typeVal  = param.getType();
                        if (param.getType().isA(typeProp))
                            {
                            code.add(new L_Set(propThis.getIdentity(), ctxEmit.getVar(sParam)));
                            }
                        else
                            {
                            constructorParams.get(iParam).log(errs, Severity.ERROR,
                                    Compiler.IMPLICIT_PROP_WRONG_TYPE, sParam,
                                    typeVal.getValueString(), typeProp.getValueString());
                            }
                        }
                    }
                }
            }

        if (listContribs == null)
            {
            if (constructor.isAnonymousClassWrapperConstructor())
                {
                // call the default initializer
                code.add(new SynInit());
                }
            }
        else
            {
            for (int iSuper = 0, cSupers = listContribs.size(); iSuper < cSupers; iSuper++)
                {
                MethodConstant   idSuper        = listIds.get(iSuper);
                MethodStructure  constructSuper = (MethodStructure) idSuper.getComponent();

                // a no-op super constructor in the same module con be safely skipped
                if (constructSuper.isNoOp() &&
                        constructor   .getIdentityConstant().getModuleConstant().equals(
                        constructSuper.getIdentityConstant().getModuleConstant()))
                    {
                    continue;
                    }

                Contribution     contrib        = listContribs.get(iSuper);
                List<Expression> listSuperArgs  = listArgLists.get(iSuper);
                int              cSuperArgs     = constructSuper.getParamCount();
                Argument[]       aSuperArgs     = new Argument[cSuperArgs];
                int              cArgs          = listSuperArgs == null ? 0 : listSuperArgs.size();

                Label labelSkipSuper = null;
                if (contrib.getComposition() == Composition.Extends)
                    {
                    if (constructor.isAnonymousClassWrapperConstructor() &&
                            !constructSuper.isAnonymousClassWrapperConstructor())
                        {
                        // call the default initializer
                        code.add(new SynInit());
                        }
                    }
                else // Composition.Incorporates
                    {
                    Map<StringConstant, TypeConstant> mapConstraints = contrib.getTypeParams();
                    if (mapConstraints != null)
                        {
                        // generate a type check for "incorporates conditional M<T extends T0, ...>"
                        labelSkipSuper = new Label("skip_super");

                        TypeInfo infoThis = component.getFormalType().ensureTypeInfo(errs);
                        for (Map.Entry<StringConstant, TypeConstant> entry : mapConstraints.entrySet())
                            {
                            PropertyInfo propFormal = infoThis.findProperty(entry.getKey().getValue());
                            assert propFormal != null && propFormal.isFormalType();

                            TypeConstant typeConstraint = entry.getValue().getType();

                            Register regActualType = new Register(typeConstraint, Op.A_STACK);
                            code.add(new L_Get(propFormal.getIdentity(), regActualType));

                            Register regIs = new Register(pool.typeBoolean(), Op.A_STACK);
                            code.add(new IsType(regActualType, typeConstraint, regIs));
                            code.add(new JumpFalse(regIs, labelSkipSuper));
                            }
                        }
                    }

                // generate the super constructor arguments (filling in the default values)
                for (int i = 0; i < cSuperArgs; i++)
                    {
                    if (i < cArgs)
                        {
                        aSuperArgs[i] = listSuperArgs.get(i).generateArgument(ctxEmit, code, true, true, errs);
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

                if (labelSkipSuper != null)
                    {
                    code.add(labelSkipSuper);
                    }
                }
            }

        code.add(new Return_0());
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
        for (char ch : sDoc.substring(1).toCharArray())
            {
            switch (nState)
                {
                case 0: // leading whitespace expected
                    if (!isLineTerminator(ch))
                        {
                        if (isWhitespace(ch))
                            {
                            continue;
                            }

                        if (ch == '*')
                            {
                            nState = 1;
                            continue;
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
                            continue;
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
                        continue;
                        }
                    break;

                case 3:         // ate a CR, emitted an LF
                    if (ch == LF || isWhitespace(ch))
                        {
                        nState = 0;
                        continue;
                        }

                    if (ch == '*')
                        {
                        nState = 1;
                        continue;
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
                for (AnnotationExpression annotation : annotations)
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
                for (AnnotationExpression annotation : annotations)
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
            for (CompositionNode composition : this.compositions)
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
     * <li><b>{@code InProperty}</b> - declared within the body of a property.</li>
     * </ul>
     */
    public enum Zone
        {
        TopLevel, InClass, InMethod, InProperty;

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

    protected Source                     source;
    protected Expression                 condition;
    protected List<Token>                modifiers;
    protected List<AnnotationExpression> annotations;
    protected Token                      category;
    protected Token                      name;
    protected List<Token>                qualified;
    protected List<Parameter>            typeParams;
    protected List<Parameter>            constructorParams;
    protected List<TypeExpression>       typeArgs;
    protected List<Expression>           args;
    protected List<CompositionNode>      compositions;
    protected StatementBlock             body;
    protected Token                      doc;
    protected StatementBlock             enclosed;

    /**
     * True iff this is an anonymous inner class.
     */
    private boolean m_fAnon;

    /**
     * True iff this is a virtual child class.
     */
    private boolean m_fVirtChild;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TypeCompositionStatement.class,
            "condition", "annotations", "typeParams", "constructorParams", "typeArgs", "args",
            "compositions", "body");
    }