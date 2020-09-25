package org.xvm.compiler.ast;


import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.CompositeComponent;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.TypedefStructure;
import org.xvm.asm.XvmStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PseudoConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeParameterConstant;

import org.xvm.compiler.Compiler;

import org.xvm.util.Severity;


/**
 * This represents the progress toward resolution of a name for a particular AstNode.
 */
public class NameResolver
        implements ResolutionCollector
    {
    /**
     * Create a resolver for a single name, for the purpose of resolving a reference to a value
     * (including a type, which can be treated as if it were a value) or a multi-method (which can
     * also be treated as if it were a value).
     *
     * @param node   the node which is requesting the resolution of the name
     * @param sName  the name to resolve
     */
    public NameResolver(AstNode node, String sName)
        {
        m_node      = node;
        m_iter      = Collections.emptyIterator();
        m_sName     = sName;
        m_fTypeGoal = false;
        }

    /**
     * Create a resolver used during the resolveNames() process that can evaluate a sequence of
     * names, for the purpose of resolving a "type name".
     *
     * @param node       the NameResolving AstNode for which the resolution is occurring
     * @param iterNames  the iterator of the sequence of names
     */
    public NameResolver(AstNode node, Iterator<String> iterNames)
        {
        assert node instanceof NameResolving;
        assert iterNames != null && iterNames.hasNext();

        m_node      = node;
        m_iter      = iterNames;
        m_sName     = m_iter.next();
        m_fTypeGoal = true;
        }

    /**
     * @return the AstNode for which this NameResolver exists
     */
    public AstNode getNode()
        {
        return m_node;
        }

    /**
     * If the compilation stage is past the stage in which deferral can occur, then just force the
     * completion of the resolution and treat anything else as an error.
     *
     * @param errs  the error list to log any errors to
     *
     * @return the constant representing the name, or null if the name could not be resolved
     */
    public Constant forceResolve(ErrorListener errs)
        {
        switch (resolve(errs))
            {
            case RESOLVED:
                return m_constant;

            case DEFERRED:
                m_node.log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, m_sName);
                m_stage = Stage.ERROR;
                // fall through
            case ERROR:
            default:
                return null;
            }
        }

    /**
     * @return true iff the NameResolver has not begun the process of resolving the name
     */
    public boolean isFirstTime()
        {
        return m_stage == Stage.CHECK_IMPORTS;
        }

    /**
     * Resolve the name, or at least try to make progress doing so.
     *
     * @param errs   the error list to log any errors etc. to
     *
     * @return one of {@link Result#DEFERRED} to indicate that the NameResolver needs to be
     *         revisited, {@link Result#ERROR} to indicate that the NameResolver has
     *         logged an error to {@code errs} and given up all hope of resolving the name, or
     *         {@link Result#RESOLVED} to indicate that the name has been successfully resolved
     */
    public Result resolve(ErrorListener errs)
        {
        // store off the error list for use by call backs
        // (note: there's no attempt to clean this up later)
        m_errs = errs;

        switch (m_stage)
            {
            case CHECK_IMPORTS:
                // the first name could be an import, in which case that needs to be evaluated right
                // away (because the imports will continue to be registered as the AST is resolved,
                // so the answers to the questions about the imports will change if we don't ask now
                // and store off the result); even if we find the name in the imports, we do NOT use
                // it at this point -- it is held in case we work our way up to the point where the
                // import is registered, at which point we will use the result that we found here
                m_stmtImport = m_node.resolveImportBySingleName(m_sName);
                if (m_stmtImport != null)
                    {
                    AstNode parent = m_stmtImport.getParent();
                    while (!(parent instanceof StatementBlock))
                        {
                        parent = parent.getParent();
                        }
                    m_blockImport = (StatementBlock) parent;
                    }
                m_stage = Stage.RESOLVE_FIRST_NAME;
                // fall through

            case RESOLVE_FIRST_NAME:
                // resolve the first name if possible; otherwise defer (to come back to this point).
                // remember to check the import statement if it exists (but only use it when we make
                // our way up to the node that the import was registered with). if no one knows what
                // the first name is, then check if it is an implicitly imported identity.

                // start with the current node, and one by one walk up to the root, asking at
                // each level for the node to resolve the name
                boolean          fPossibleFormal = false;
                AstNode          node            = m_node;
                Access           access          = Access.PRIVATE;
                IdentityConstant idOuter         = null;
                WalkUpToTheRoot: while (node != null)
                    {
                    // if the first name refers to an import, then ask that import to figure out
                    // what the corresponding qualified name refers to (i.e. delegate!)
                    if (node == m_blockImport)
                        {
                        NameResolver resolver = m_stmtImport.getNameResolver();
                        switch (resolver.resolve(errs))
                            {
                            case RESOLVED:
                                if (m_stmtImport.isWildcard())
                                    {
                                    m_component = ((IdentityConstant) resolver.getConstant()).getComponent().getChild(m_sName);
                                    assert m_component instanceof ClassStructure;
                                    m_constant  = m_component.getIdentityConstant();
                                    }
                                else
                                    {
                                    m_constant  = resolver.m_constant;
                                    m_component = resolver.m_component;
                                    }
                                break WalkUpToTheRoot;

                            case DEFERRED:
                                // dependent on a node that is deferred, so this is deferred
                                return Result.DEFERRED;

                            default:
                            case ERROR:
                                // no need to log an error; the import already should have
                                m_stage = Stage.ERROR;
                                return Result.ERROR;
                            }
                        }

                    // otherwise, if the node has a component associated with it that is
                    // prepared to resolve names, then ask it to resolve the name, and if it
                    // isn't ready, we'll come back later
                    if (node.isComponentNode())
                        {
                        if (!node.canResolveNames())
                            {
                            // not ready yet
                            return Result.DEFERRED;
                            }

                        Component componentResolver = node.getComponent();
                        if (componentResolver == null)
                            {
                            // corresponding component isn't available (yet?)
                            return Result.DEFERRED;
                            }

                        // the identity of the component corresponding to the current node as
                        // we "WalkUpToTheRoot"
                        IdentityConstant id = componentResolver.getIdentityConstant();

                        // first time through, figure out the "outermost" class, which is the
                        // boundary where we will transition from looking at all (including
                        // private) members, to looking at only public members
                        IdentityConstant idClz = id.getClassIdentity();
                        if (idOuter == null)
                            {
                            idOuter = idClz instanceof ClassConstant ? ((ClassConstant) idClz).getOutermost() : idClz;
                            }

                        // ask the component to resolve the name
                        switch (componentResolver.resolveName(m_sName, access, this))
                            {
                            case POSSIBLE:
                                // formal types could not be resolved; keep walking up
                                fPossibleFormal = true;
                                // fall-through
                            case UNKNOWN:
                                break;

                            case RESOLVED:
                                // the component resolved the first name
                                break WalkUpToTheRoot;

                            case ERROR:
                                m_stage = Stage.ERROR;
                                return Result.ERROR;

                            default:
                                throw new IllegalStateException();
                            }

                        // see if this was the last step on the "WalkUpToTheRoot" that had
                        // private access to all members
                        if (id == idOuter)
                            {
                            // in the top-most-class down, there is private access
                            // above the top-most-class, there is public access
                            access = Access.PUBLIC;
                            }
                        }

                    // walk up towards the root
                    node = node.getParent();
                    }

                // last chance: check the implicitly imported names
                if (m_constant == null)
                    {
                    Component component = getPool().getImplicitlyImportedComponent(m_sName);
                    if (component == null)
                        {
                        if (fPossibleFormal)
                            {
                            return Result.DEFERRED;
                            }
                        m_node.log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, m_sName);
                        m_stage = Stage.ERROR;
                        return Result.ERROR;
                        }
                    else
                        {
                        if (resolvedComponent(component) != ResolutionResult.RESOLVED)
                            {
                            return Result.ERROR;
                            }
                        assert m_constant != null;
                        }
                    }
                m_constantFirst = m_constant;

                // first name has been resolved
                m_stage = Stage.RESOLVE_DOT_NAME;
                m_sName = m_iter.hasNext() ? m_iter.next() : null;
                // fall through

            case RESOLVE_DOT_NAME:
                // at this point, we have a component (or other identity) to work from, so the next
                // name has to be relative to that component
                while (m_sName != null)
                    {
                    XvmStructure structure = ensurePartiallyResolvedComponent();
                    if (structure == null)
                        {
                        return getResult();
                        }

                    if (structure instanceof PropertyStructure)
                        {
                        PropertyStructure prop = (PropertyStructure) structure;
                        if (!prop.isGenericTypeParameter())
                            {
                            m_node.log(errs, Severity.ERROR, Compiler.NOT_CLASS_TYPE, m_sName);
                            return Result.ERROR;
                            }

                        Result result = resolveFormalDotName(prop.getType(), errs);
                        if (result != Result.RESOLVED)
                            {
                            return result;
                            }
                        }
                    else if (structure instanceof Parameter)
                        {
                        Parameter parameter = (Parameter) structure;
                        assert parameter.isTypeParameter();

                        TypeConstant typeParam = parameter.getType();
                        assert typeParam.isTypeOfType();

                        Result result = resolveFormalDotName(typeParam.getParamType(0), errs);
                        if (result != Result.RESOLVED)
                            {
                            return result;
                            }
                        }
                    else
                        {
                        Component component = (Component) structure;
                        switch (component.resolveName(m_sName, Access.PRIVATE, this))
                            {
                            case UNKNOWN:
                                // the component didn't know the name
                                m_node.log(errs, Severity.ERROR, Compiler.NAME_MISSING, m_sName, m_constant);
                                m_stage = Stage.ERROR;
                                return Result.ERROR;

                            case RESOLVED:
                                // the component resolved the name; advance to the next one
                                m_sName = m_iter.hasNext() ? m_iter.next() : null;
                                break;

                            case POSSIBLE:
                                // should not be possible (no pun intended)
                            case ERROR:
                                m_stage = Stage.ERROR;
                                return Result.ERROR;

                            default:
                                throw new IllegalStateException();
                            }
                        }
                    }

                // no names left to resolve, but what we resolved to has not yet been resolved
                m_stage = Stage.RESOLVE_TURTLES;
                // fall through

            case RESOLVE_TURTLES:
                // stay in this stage until the constant that we have resolved to is itself resolved
                if (m_constant.canResolve())
                    {
                    // no turtles left to resolve
                    m_stage = Stage.RESOLVED;
                    }
                else
                    {
                    return Result.DEFERRED;
                    }
                // fall through

            case RESOLVED:
                // already resolved
                return Result.RESOLVED;

            default:
            case ERROR:
                // already determined to be unresolvable
                return Result.ERROR;
            }
        }

    /**
     * @return the component that is responsible for resolving the next name or null if an error
     *         has been reported
     */
    private XvmStructure ensurePartiallyResolvedComponent()
        {
        Component component = m_component;
        if (m_typeMode == null)
            {
            if (component.getFormat().isDeadEnd())
                {
                // for methods (and multi-methods), it is not possible to further resolve the name,
                // because methods are opaque from the outside, and multi-methods can only be
                // resolved by analyzing signatures (not names)
                m_node.log(m_errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, m_sName);
                m_stage = Stage.ERROR;
                return null;
                }
            else
                {
                return component;
                }
            }

        // once we get into the domain of type parameters, the "resolving component to use next"
        // is not pre-loaded. the quintessential example is the type parameter "MapType extends
        // Map", and then resolving "MapType.Key", where there is no actual type (or
        // component) for MapType, but the "Map" component is used instead
        Constant id = m_constant;
        while (true)
            {
            TypeConstant type;
            switch (id.getFormat())
                {
                case Module:
                case Package:
                case Class:
                    return component;

                case Property:
                    if (component instanceof PropertyStructure)
                        {
                        return component;
                        }

                    if (component instanceof CompositeComponent)
                        {
                        List<Component>   listProps = ((CompositeComponent) component).components();
                        PropertyStructure prop0     = (PropertyStructure) listProps.get(0);
                        TypeConstant      type0     = prop0.getType();
                        for (int i = 1, c = listProps.size(); i < c; ++i)
                            {
                            TypeConstant typeN = ((PropertyStructure) listProps.get(i)).getType();
                            if (!type0.equals(typeN))
                                {
                                // eventual To-Do: we need to handle cases where composite
                                // components differ in substantial ways, such as type, but for
                                // now this is just an assertion that the type does not vary
                                throw new UnsupportedOperationException("non-uniform composite property type: "
                                        + id + "; 0=" + type0 + ", " + i + "=" + typeN);
                                }
                            }
                        return prop0;
                        }
                    else
                        {
                        throw new IllegalStateException("id=" + id + ", prop=" + component);
                        }

                case Typedef:
                    if (component instanceof TypedefStructure)
                        {
                        type = ((TypedefStructure) component).getType();
                        }
                    else if (component instanceof CompositeComponent)
                        {
                        List<Component> listTypedefs = ((CompositeComponent) component).components();
                        type = ((TypedefStructure) listTypedefs.get(0)).getType();
                        for (int i = 1, c = listTypedefs.size(); i < c; ++i)
                            {
                            TypeConstant constTypeN = ((TypedefStructure) listTypedefs.get(i)).getType();
                            if (!type.equals(constTypeN))
                                {
                                // eventual To-Do: we need to handle cases where composite
                                // components differ in substantial ways, such as type, but for
                                // now this is just an assertion that the type does not vary
                                throw new UnsupportedOperationException("non-uniform composite typedef type: "
                                        + id + "; 0=" + type + ", " + i + "=" + constTypeN);
                                }
                            }
                        }
                    else
                        {
                        throw new IllegalStateException("id=" + id + ", typedef=" + component);
                        }
                    break;

                case TypeParameter:
                    {
                    TypeParameterConstant constTypeParam = (TypeParameterConstant) id;
                    return ((MethodStructure) constTypeParam.getMethod().getComponent()).
                            getParam(constTypeParam.getRegister());
                    }

                case ThisClass:
                case ChildClass:
                case ParentClass:
                    {
                    PseudoConstant constClass = (PseudoConstant) id;
                    return constClass.getDeclarationLevelClass().getComponent();
                    }

                default:
                    throw new IllegalStateException("illegal type param constant id: " + id);
                }

            if (!type.isTypeOfType())
                {
                m_errs.log(Severity.ERROR, Compiler.NOT_CLASS_TYPE,
                        new Object[] {id.getValueString()}, component);
                m_stage = Stage.ERROR;
                return null;
                }

            TypeConstant typeParam = type.getParamType(0);
            if (typeParam.isSingleDefiningConstant())
                {
                id        = typeParam.getDefiningConstant();
                component = id instanceof IdentityConstant ? ((IdentityConstant) id).getComponent() : null;
                }
            else
                {
                throw new IllegalStateException("not a single defining constant: " + typeParam);
                }
            }
        }

    /**
     * Resolve the next name for a formal partially resolved component that represents either
     * a generic type (Property) or a formal type parameter (Parameter).
     *
     * @param typeConstraint  the constraint type of the formal type
     * @param errs            the error list to log any errors to
     *
     * @return the resolution result
     */
    private Result resolveFormalDotName(TypeConstant typeConstraint, ErrorListener errs)
        {
        ResolutionResult result = ResolutionResult.UNKNOWN;
        if (typeConstraint.isSingleDefiningConstant())
            {
            // try to use the constraint type
            // (e.g. CompileType.Key where CompileType is known to be a Map)
            Constant id         = typeConstraint.getDefiningConstant();
            Component component = id instanceof IdentityConstant
                    ? ((IdentityConstant) id).getComponent()
                    : id instanceof PseudoConstant
                        ? ((PseudoConstant) id).getDeclarationLevelClass().getComponent()
                        : null;
            if (component != null)
                {
                result = component.resolveName(m_sName, Access.PRIVATE, this);
                }
            }

        if (result == ResolutionResult.UNKNOWN)
            {
            // since the formal type is a Type, now try the Type's children;
            // (e.g. CompileType.OuterType )
            //
            // REVIEW: we should strongly consider creating a dedicated constant for this,
            //         so there is no ambiguity at compile- or run-time resolution
            result = getPool().clzType().getComponent().resolveName(m_sName, Access.PUBLIC, this);
            }

        switch (result)
            {
            case UNKNOWN:
                // the component didn't know the name
                m_node.log(errs, Severity.ERROR, Compiler.NAME_MISSING, m_sName, m_constant);
                // break through
            case POSSIBLE:
                // should not be possible
            case ERROR:
                m_stage = Stage.ERROR;
                return Result.ERROR;

            case RESOLVED:
                // the component resolved the name; advance to the next one
                m_sName = m_iter.hasNext() ? m_iter.next() : null;
                return Result.RESOLVED;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * @return the result of the NameResolver thus far; the ERROR and RESOLVED states are the
     *         terminal states
     */
    public Result getResult()
        {
        switch (m_stage)
            {
            case CHECK_IMPORTS:
            case RESOLVE_FIRST_NAME:
            case RESOLVE_DOT_NAME:
                // not done yet
                return Result.DEFERRED;

            case RESOLVED:
                // completed successfully
                return Result.RESOLVED;

            default:
            case ERROR:
                // cannot complete successfully
                return Result.ERROR;
            }
        }

    /**
     * @return the Constant that the NameResolver has resolved the first part of the name to;
     *         this value can only be depended on after stage value is RESOLVE_FIRST_NAME
     */
    public Constant getBaseConstant()
        {
        return m_constantFirst;
        }

    /**
     * @return the Constant that the NameResolver has resolved to thus far; this value can only be
     *         depended on after the NameResolver result is RESOLVED
     */
    public Constant getConstant()
        {
        return m_constant;
        }

    /**
     * @return the ConstantPool
     */
    private ConstantPool getPool()
        {
        return m_node.pool();
        }


    // ----- ResolutionCollector -------------------------------------------------------------------

    @Override
    public ResolutionResult resolvedComponent(Component component)
        {
        // it is possible that the name "resolved to" an ambiguous component, which is an error
        IdentityConstant id = component.getIdentityConstant();
        if (component instanceof CompositeComponent && ((CompositeComponent) component).isAmbiguous())
            {
            m_node.log(m_errs, Severity.ERROR, Compiler.NAME_AMBIGUOUS, m_sName);
            m_stage = Stage.ERROR;
            return ResolutionResult.ERROR;
            }

        if (m_typeMode != null)
            {
            switch (component.getFormat())
                {
                case TYPEDEF:
                    // typedef is allowed in type mode, but not in formal type mode
                    if (m_typeMode == TypeMode.FORMAL_TYPE)
                        {
                        m_node.log(m_errs, Severity.ERROR, Compiler.TYPEDEF_UNEXPECTED);
                        m_stage = Stage.ERROR;
                        return ResolutionResult.ERROR;
                        }
                    break;

                case PROPERTY:
                    // type params are allowed in type modes
                    if (((PropertyStructure) component).isGenericTypeParameter())
                        {
                        if (m_typeMode == TypeMode.FORMAL_TYPE)
                            {
                            m_component = null;
                            m_constant  = getPool().ensureFormalTypeChildConstant(
                                    (IdentityConstant) m_constant, m_sName);
                            return ResolutionResult.RESOLVED;
                            }

                        m_typeMode = TypeMode.FORMAL_TYPE;
                        break;
                        }
                    // fall through
                default:
                    // nothing else is allowed in type mode (can't switch back to a value mode, i.e.
                    // an "identity mode")
                    m_node.log(m_errs, Severity.ERROR, Compiler.NAME_MISSING, component.getName(), m_constant);
                    m_stage = Stage.ERROR;
                    return ResolutionResult.ERROR;
                }
            }
        else if (m_fTypeGoal)
            {
            // when resolving for a type name, encountering a typedef or a type parameter
            // transitions the NameResolver into a "type mode", which is a one-way transition
            switch (component.getFormat())
                {
                case PROPERTY:
                    if (((PropertyStructure) component).isGenericTypeParameter())
                        {
                        m_typeMode = TypeMode.FORMAL_TYPE;
                        }
                    break;

                case TYPEDEF:
                    m_typeMode = TypeMode.TYPE;
                    break;
                }
            }

        m_component = component;
        m_constant  = id;
        return ResolutionResult.RESOLVED;
        }

    @Override
    public ResolutionResult resolvedConstant(Constant constant)
        {
        if (constant == null)
            {
            return ResolutionResult.UNKNOWN;
            }

        if (constant instanceof IdentityConstant)
            {
            Component component = ((IdentityConstant) constant).getComponent();
            if (component != null)
                {
                return resolvedComponent(component);
                }
            }

        m_constant  = constant;
        m_component = null;
        m_typeMode = constant instanceof TypeParameterConstant
                ? TypeMode.FORMAL_TYPE
                : m_fTypeGoal ? TypeMode.TYPE : null;

        return ResolutionResult.RESOLVED;
        }

    @Override
    public ErrorListener getErrorListener()
        {
        return m_errs;
        }


    // ----- inner classes -------------------------------------------------------------------------

    /**
     * Classes that can provide a NameResolver should implement this interface.
     */
    public interface NameResolving
        {
        NameResolver getNameResolver();
        }

    /**
     * The result of an attempt to resolve the name.
     */
    public enum Result {DEFERRED, RESOLVED, ERROR}

    /**
     * The possible internal states for the resolver.
     */
    private enum Stage {CHECK_IMPORTS, RESOLVE_FIRST_NAME, RESOLVE_DOT_NAME, RESOLVE_TURTLES, RESOLVED, ERROR}

    /**
     * The possible internal type resolution modes for the resolver.
     */
    private enum TypeMode {TYPE, FORMAL_TYPE};


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The node that this NameResolver is working for.
     */
    private AstNode m_node;

    /**
     * The sequence of names to resolve.
     */
    private Iterator<String> m_iter;

    /**
     * The current simple name to resolve.
     */
    private String m_sName;

    /**
     * The current internal status of the name resolution.
     */
    private Stage m_stage = Stage.CHECK_IMPORTS;

    /**
     * The import statement selected by the import-checking phase, if any possible match was found.
     */
    private ImportStatement m_stmtImport;

    /**
     * The node that the import was registered with, if any possible import match was found.
     */
    private StatementBlock m_blockImport;

    /**
     * The constant representing what the first node has resolved to. This is information
     * is used to determine whether or not a virtual child name was fully qualified.
     */
    private Constant m_constantFirst;

    /**
     * The constant representing what the node has thus far resolved to.
     */
    private Constant m_constant;

    /**
     * The component representing what the node has thus far resolved to.
     */
    private Component m_component;

    /**
     * The goal of the NameResolver is either a type or a more general value (which itself might be
     * a type).
     * <p/>
     * Name resolution can be completely generic ("I need any value, including a type or a
     * multi-method"), or can be a bit more specific ("I am resolving for a type").
     */
    private boolean m_fTypeGoal;

    /**
     * Set to TYPE or FORMAL_TYPE mode once a type definition or a formal type parameter have been
     * encountered.
     */
    private TypeMode m_typeMode;

    /**
     * The ErrorListener to log errors to.
     */
    private ErrorListener m_errs;
    }
