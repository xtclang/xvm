package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodType;

import org.xvm.asm.op.Construct_0;
import org.xvm.asm.op.Construct_1;
import org.xvm.asm.op.Construct_N;
import org.xvm.asm.op.L_Set;
import org.xvm.asm.op.MoveRef;
import org.xvm.asm.op.MoveVar;
import org.xvm.asm.op.NewCG_0;
import org.xvm.asm.op.NewCG_1;
import org.xvm.asm.op.NewCG_N;
import org.xvm.asm.op.NewC_0;
import org.xvm.asm.op.NewC_1;
import org.xvm.asm.op.NewC_N;
import org.xvm.asm.op.NewG_0;
import org.xvm.asm.op.NewG_1;
import org.xvm.asm.op.NewG_N;
import org.xvm.asm.op.New_0;
import org.xvm.asm.op.New_1;
import org.xvm.asm.op.New_N;
import org.xvm.asm.op.Return_0;

import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Constants;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Context.CaptureContext;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.indentLines;


/**
 * "New object" expression.
 *
 * <p/> TODO constructor - create the specified constructor (same sig as super class by default)
 * <p/> TODO implicit captures - will alter the constructor that we create (add each as a constructor param)
 * <p/> TODO pass the arguments to the constructor, including the implicit captures
 * <p/> TODO no other constructors allowed (either the default created one or any explicit ones) on the inner class
 * <p/> TODO capture of outer "this" means that the inner class is non-static
 */
public class NewExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a "new" expression.
     *
     * @param operator  presumably, the "new" operator
     * @param type      the type being instantiated
     * @param args      a list of constructor arguments for the type being instantiated
     * @param body      the body of the anonymous inner class being instantiated, or null
     * @param lEndPos   the expression's end position in the source code
     */
    public NewExpression(Token operator, TypeExpression type, List<Expression> args, StatementBlock body, long lEndPos)
        {
        assert operator != null;
        assert type != null;
        assert args != null;

        this.left     = null;
        this.operator = operator;
        this.type     = type;
        this.args     = args;
        this.body     = body;
        this.lEndPos  = lEndPos;
        }

    /**
     * Construct a ".new" expression.
     *
     * @param left      the "left" expression
     * @param operator  presumably, the "new" operator
     * @param type      the type being instantiated
     * @param args      a list of constructor arguments for the type being instantiated, or null
     * @param lEndPos   the expression's end position in the source code
     */
    public NewExpression(Expression left, Token operator, TypeExpression type, List<Expression> args, long lEndPos)
        {
        assert left != null;
        assert operator != null;
        assert type != null;
        assert args != null;

        this.left     = left;
        this.operator = operator;
        this.type     = type;
        this.args     = args;
        this.body     = null;
        this.lEndPos  = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean isComponentNode()
        {
        return body != null;
        }

    /**
     * @return the parent of the object being new'd, for example "parent.new Child()", or null if
     *         the object being new'd is of a top level class
     */
    public Expression getLeftExpression()
        {
        return left;
        }

    /**
     * @return the type of the object being new'd; never null
     */
    public TypeExpression getTypeExpression()
        {
        return type;
        }

    /**
     * @return return a list of expressions that are passed to the constructor of the object being
     *         instantiated, or null if there is no argument list specified
     */
    public List<Expression> getConstructorArguments()
        {
        return args;
        }

    /**
     * @return the body of the anonymous inner class, or null if this "new" is not instantiating an
     *         anonymous inner class
     */
    public StatementBlock getAnonymousInnerClassBody()
        {
        return body;
        }

    @Override
    public boolean isAutoNarrowingAllowed(TypeExpression type)
        {
        // anonymous inner class declaration cannot auto-narrow its contributions
        return !isComponentNode();
        }

    @Override
    public long getStartPosition()
        {
        return left == null ? operator.getStartPosition() : left.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- Code Container methods ----------------------------------------------------------------

    @Override
    protected RuntimeException notCodeContainer()
        {
        // while an inner class is technically a code container, it is not directly a code container
        // in the same sense that a method is, because it cannot directly contain a "return"
        throw new IllegalStateException("invalid return from an anonymous inner class: " + this);
        }

    /**
     * @return the AnonInnerClassContext, if captures are available from this expression, or null
     */
    public AnonInnerClassContext getCaptureContext()
        {
        return m_ctxCapture;
        }


    // ----- compilation (Expression) --------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        if (body == null)
            {
            TypeConstant typeTarget = type.ensureTypeConstant();
            if (typeTarget.containsUnresolved() || !typeTarget.isSingleUnderlyingClass(false))
                {
                // unknown or not a class; someone will report an error later
                return null;
                }
            return typeTarget;
            }
        else
            {
            return getAnonymousInnerClassType(ctx);
            }
        }

    @Override
    protected TypeFit calcFit(Context ctx, TypeConstant typeIn, TypeConstant typeOut)
        {
        if (typeIn != null && typeOut != null)
            {
            // right-to-left inference to match the "validate" logic
            TypeConstant typeInferred = inferTypeFromRequired(typeIn, typeOut);
            if (typeInferred != null)
                {
                typeIn = typeInferred;
                }
            }
        return super.calcFit(ctx, typeIn, typeOut);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        boolean fValid = true;
        boolean fAnon  = anon != null;
        if (fAnon)
            {
            ensureInnerClass(ctx, /*fTemp*/ false, /*fRO*/ true, errs);
            assert left == null;
            }

        ConstantPool pool       = pool();
        TypeConstant typeTarget = null;
        TypeInfo     infoSuper  = null;
        if (left == null)
            {
            // we intentionally don't pass the required type to the TypeExpression; instead, let's take
            // whatever type it produces and later validate the resulting type against the required type
            TypeExpression exprTypeOld = this.type;
            TypeExpression exprTypeNew = (TypeExpression) exprTypeOld.validate(ctx, null, errs);
            TypeConstant   typeSuper   = null;
            if (exprTypeNew == null)
                {
                fValid = false;
                }
            else
                {
                this.type = exprTypeNew;

                typeTarget = exprTypeNew.ensureTypeConstant().resolveAutoNarrowingBase(pool);
                if (typeRequired != null)
                    {
                    TypeConstant typeInferred = inferTypeFromRequired(typeTarget, typeRequired);
                    if (typeInferred != null)
                        {
                        typeTarget = typeInferred;
                        }
                    }

                boolean fNestMate = isNestMate(ctx, typeTarget);
                if (fAnon)
                    {
                    // since we are going to be extending the specified type, increase visibility from
                    // the public default to protected, which we get when a class "extends" another;
                    // the real target, though, is not the specified type being "new'd", but rather the
                    // anonymous inner class
                    typeSuper  = pool.ensureAccessTypeConstant(typeTarget, fNestMate ? Access.PRIVATE : Access.PROTECTED);
                    infoSuper  = typeSuper.ensureTypeInfo(errs);
                    typeTarget = pool.ensureAccessTypeConstant(getAnonymousInnerClassType(ctx), Access.PRIVATE);
                    }
                else if (fNestMate)
                    {
                    ClassStructure clzTarget = (ClassStructure)
                        typeTarget.getSingleUnderlyingClass(false).getComponent();
                    m_fVirtualChild = clzTarget.isVirtualChild();

                    // since we are new-ing a class that is a nest-mate of the current class, we can
                    // increase visibility from the public default all the way to private
                    typeTarget = pool.ensureAccessTypeConstant(typeTarget, Access.PRIVATE);
                    }
                }
            }
        else // left != null
            {
            // validate the expression that occurs _before_ the new, e.g. x in "x.new Y()", which
            // specifies an "outer this" that provides support for virtual construction
            Expression exprLeftOld = this.left;
            Expression exprLeftNew = exprLeftOld.validate(ctx, null, errs);
            if (exprLeftNew == null)
                {
                fValid = false;
                }
            else
                {
                this.left = exprLeftNew;

                TypeExpression exprTypeOld = this.type;
                TypeExpression exprTypeNew = (TypeExpression) exprTypeOld.validate(ctx, null, errs);
                if (exprTypeNew == null)
                    {
                    fValid = false;
                    }
                else
                    {
                    this.type       = exprTypeNew;
                    m_fVirtualChild = true;
                    typeTarget      = exprTypeNew.ensureTypeConstant();

                    if (isNestMate(ctx, typeTarget))
                        {
                        typeTarget = pool.ensureAccessTypeConstant(typeTarget, Access.PRIVATE);
                        }
                    }
                }
            }

        TypeInfo infoTarget = null;
        if (fValid)
            {
            // the target type must be new-able
            infoTarget = typeTarget.ensureTypeInfo(errs);
            if (!infoTarget.isNewable())
                {
                String sType = typeTarget.getValueString();
                if (infoTarget.isExplicitlyAbstract())
                    {
                    log(errs, Severity.ERROR, Constants.VE_NEW_ABSTRACT_TYPE, sType);
                    }
                else if (infoTarget.isSingleton())
                    {
                    log(errs, Severity.ERROR, Constants.VE_NEW_SINGLETON_TYPE, sType);
                    }
                else
                    {
                    final int[] aiCount = new int[] {1}; // limit reporting to a small number of errors

                    infoTarget.getProperties().values().stream()
                            .filter(PropertyInfo::isExplicitlyAbstract)
                            .forEach(info ->
                                {
                                if (--aiCount[0] >= 0)
                                    {
                                    log(errs, Severity.ERROR, Constants.VE_NEW_ABSTRACT_PROPERTY,
                                            sType, info.getName());
                                    }
                                });

                    infoTarget.getMethods().values().stream()
                            .filter(MethodInfo::isAbstract)
                            .forEach(info ->
                                {
                                if (--aiCount[0] >= 0)
                                    {
                                    log(errs, Severity.ERROR, Constants.VE_NEW_ABSTRACT_METHOD,
                                            sType, info.getSignature());
                                    }
                                });
                    }

                fValid = false;
                }
            }

        if (fValid)
            {
            List<Expression> listArgs = this.args;
            int              cArgs    = listArgs == null ? 0 : listArgs.size();
            MethodConstant   idMethod = null;
            if (fAnon)
                {
                // first, see if the constructor that we're looking for is on the anonymous
                // inner class (which -- other than the zero-args case -- will be rare, but it
                // is still supported); however, since it's not an error for the constructor to
                // be missing, trap the errors in a temporary list. if we do find the constructor
                // that we need on the anonymous inner class, then we will simply use that one (and
                // any required dependency that it has one a super class constructor will be handled
                // as if this were any other normal class)
                ErrorList errsTarget = new ErrorList(10);
                idMethod = findMethod(ctx, infoTarget, "construct", listArgs, MethodType.Constructor, null, errsTarget);
                if (idMethod == null && !listArgs.isEmpty())
                    {
                    // the constructor that we're looking for is not on the anonymous inner class,
                    // so we need to find the specified constructor on the super class (which means
                    // that the super class must NOT be an interface), and we need to verify that
                    // there is a default constructor on the anon inner class, and it needs to be
                    // replaced by a constructor with the same signature as the super's constructor
                    // (note: the automatic creation of the synthetic no-arg constructor in the
                    // absence of any explicit constructor must do this same check)
                    MethodConstant idSuper = findMethod(ctx, infoSuper, "construct", listArgs, MethodType.Constructor, null, errs);
                    if (idSuper == null)
                        {
                        fValid = false;
                        }
                    else
                        {
                        // we found a super constructor that needs to get called from the
                        // constructor on the inner class; find the no-parameter synthetic
                        // "construct()" constructor on the inner class, and remove it, replacing it
                        // with a constructor that matches the super class constructor, so that we
                        // correctly invoke it
                        destroyDefaultConstructor();
                        idMethod = createPassThroughConstructor(idSuper);

                        // since we just modified the component, flush the TypeInfo cache for
                        // the type of the anonymous inner class
                        typeTarget.invalidateTypeInfo();
                        }
                    }
                else
                    {
                    // we did find a constructor; there were probably no errors, but just in case
                    // something got logged, transfer it to the real error list
                    errsTarget.logTo(errs);
                    }
                }
            else
                {
                idMethod = findMethod(ctx, infoTarget, "construct", listArgs, MethodType.Constructor, null, errs);
                }

            if (idMethod == null)
                {
                fValid = false;
                }
            else if (fValid)
                {
                m_constructor = (MethodStructure) idMethod.getComponent();
                if (m_constructor == null)
                    {
                    m_constructor = infoTarget.getMethodById(idMethod).getTopmostMethodStructure(infoTarget);
                    assert m_constructor != null;
                    }

                TypeConstant[] atypeArgs = idMethod.getRawParams();

                // test the "regular fit" first and Tuple afterwards
                TypeConstant typeTuple = null;
                if (!testExpressions(ctx, listArgs, atypeArgs).isFit())
                    {
                    // otherwise, check the tuple based invoke (see Expression.findMethod)
                    if (cArgs == 1)
                        {
                        typeTuple = pool.ensureParameterizedTypeConstant(
                                pool.typeTuple(), atypeArgs);
                        if (!listArgs.get(0).testFit(ctx, typeTuple).isFit())
                            {
                            // the regular "validateExpressions" call will report an error
                            typeTuple = null;
                            }
                        }
                    }

                if (typeTuple == null)
                    {
                    fValid = validateExpressions(ctx, listArgs, atypeArgs, errs) != null;
                    }
                else
                    {
                    fValid = validateExpressionsFromTuple(ctx, listArgs, typeTuple, errs) != null;
                    m_fTupleArg = true;
                    }

                m_cDefaults = m_constructor.getParamCount() - cArgs;
                }
            }

        if (fAnon && fValid)
            {
            // at this point, we need to create a temporary copy of the anonymous inner class for
            // the purpose of determining which local variables from this context will be "captured"
            // by the code in the anonymous inner class; to determine the captures, we need to go
            // much further in the compilation of the inner class, to the point of the emit stage
            // in which the expression validations are done (which means that the inner class will
            // end up emitting code); fortunately, all of that work can be done with temporary
            // structures, such that we can revert it after we collect the information about the
            // captures
            destroyTempInnerClass();

            // force a temp clone of the inner class to go through its validate() stage so that we
            // can determine what variables get captured (and if they are effectively final)
            ensureInnerClass(ctx, /*fTemp*/ true, /*fRO*/ false, ErrorListener.BLACKHOLE);

            if (!new StageMgr(anon, Stage.Emitted, errs).fastForward(20))
                {
                fValid = false;
                }

            // clean up temporary inner class and transfer the capture context information
            destroyTempInnerClass();

            AnonInnerClassContext ctxAnon = m_ctxCapture;
            ctxAnon.exit();

            // at this point we have a fair bit of data about which variables get captured, but we
            // still lack the effectively final data that will only get reported when the various
            // nested contexts in which the captured variables were declared go through their exit()
            // logic (as the variables go out of scope in the method body that contains this
            // NewExpression); for now, store off the data from the capture context
            m_mapCapture     = ctxAnon.getCaptureMap();
            m_mapRegisters   = ctxAnon.ensureRegisterMap();
            m_fInstanceChild = ctxAnon.isInstanceChild();
            m_ctxCapture     = null;
            }

        // remove the access modifier
        if (typeTarget != null && typeTarget.isAccessSpecified())
            {
            typeTarget = typeTarget.getUnderlyingType();
            }

        Expression exprResult = finishValidation(typeRequired, typeTarget,
                fValid ? TypeFit.Fit : TypeFit.NoFit, null, errs);
        clearAnonTypeInfos();
        return exprResult;
        }

    @Override
    public boolean isCompletable()
        {
        for (Expression expr : args)
            {
            if (!expr.isCompletable())
                {
                return false;
                }
            }

        return true;
        }

    @Override
    public boolean isShortCircuiting()
        {
        for (Expression expr : args)
            {
            if (expr.isShortCircuiting())
                {
                return true;
                }
            }

        return false;
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        // 1. To avoid an out-of-order execution, we cannot allow the use of local properties
        //    except for the parent when there are no arguments
        // 2. The arguments are allowed to be pushed on the stack since the run-time knows to load
        //    them up in the inverse order; however, the parent (for the NEWC_* ops should not be
        //    put on the stack unless there are no arguments

        assert m_constructor != null;

        if (LVal.isLocalArgument())
            {
            List<Expression> listArgs = args;
            int              cArgs    = listArgs.size();
            Argument[]       aArgs    = new Argument[cArgs];
            for (int i = 0; i < cArgs; ++i)
                {
                aArgs[i] = listArgs.get(i).generateArgument(ctx, code, false, true, errs);
                }

            if (anon != null)
                {
                aArgs = addCaptures(code, aArgs);
                }

            generateNew(ctx, code, aArgs, LVal.getLocalArgument(), errs);
            }
        else
            {
            super.generateAssignment(ctx, code, LVal, errs);
            }
        }


    // ----- compilation helpers -------------------------------------------------------------------

    /**
     * Generate the NEW_* op-code
     */
    private void generateNew(Context ctx, Code code, Argument[] aArgs, Argument argResult,
                             ErrorListener errs)
        {
        MethodConstant idConstruct = m_constructor.getIdentityConstant();
        TypeConstant   typeTarget  = getType();
        int            cAll        = idConstruct.getRawParams().length;
        int            cArgs       = aArgs.length;
        int            cDefaults   = m_cDefaults;

        if (m_fTupleArg)
            {
            throw notImplemented();
            }
        else
            {
            assert cArgs + cDefaults == cAll;
            if (cDefaults > 0)
                {
                Argument[] aArgAll = new Argument[cAll];
                System.arraycopy(aArgs, 0, aArgAll, 0, cArgs);
                aArgs = aArgAll;

                for (int i = 0; i < cDefaults; i++)
                    {
                    aArgs[cArgs + i] = Register.DEFAULT;
                    }
                }

            Argument argOuter = null;
            if (m_fVirtualChild)
                {
                if (left == null)
                    {
                    argOuter = new Register(ctx.getThisType(), Op.A_THIS);
                    }
                else
                    {
                    argOuter = left.generateArgument(ctx, code, true, true, errs);
                    }
                }

            if (typeTarget.isParamsSpecified())
                {
                if (argOuter == null)
                    {
                    switch (cAll)
                        {
                        case 0:
                            code.add(new NewG_0(idConstruct, typeTarget, argResult));
                            break;

                        case 1:
                            code.add(new NewG_1(idConstruct, typeTarget, aArgs[0], argResult));
                            break;

                        default:
                            code.add(new NewG_N(idConstruct, typeTarget, aArgs, argResult));
                            break;
                        }
                    }
                else
                    {
                    switch (cAll)
                        {
                        case 0:
                            code.add(new NewCG_0(idConstruct, argOuter, typeTarget, argResult));
                            break;

                        case 1:
                            code.add(new NewCG_1(idConstruct, argOuter, typeTarget, aArgs[0], argResult));
                            break;

                        default:
                            code.add(new NewCG_N(idConstruct, argOuter, typeTarget, aArgs, argResult));
                            break;
                        }
                    }
                }
            else
                {
                if (argOuter == null)
                    {
                    switch (cAll)
                        {
                        case 0:
                            code.add(new New_0(idConstruct, argResult));
                            break;

                        case 1:
                            code.add(new New_1(idConstruct, aArgs[0], argResult));
                            break;

                        default:
                            code.add(new New_N(idConstruct, aArgs, argResult));
                            break;
                        }
                    }
                else
                    {
                    switch (cAll)
                        {
                        case 0:
                            code.add(new NewC_0(idConstruct, argOuter, argResult));
                            break;

                        case 1:
                            code.add(new NewC_1(idConstruct, argOuter, aArgs[0], argResult));
                            break;

                        default:
                            code.add(new NewC_N(idConstruct, argOuter, aArgs, argResult));
                            break;
                        }
                    }
                }
            }
        }

    /**
     * @return the type of the anonymous inner class
     */
    private TypeConstant getAnonymousInnerClassType(Context ctx)
        {
        if (anon == null)
            {
            ensureInnerClass(ctx, /*fTemp*/ true, /*fRO*/ true, ErrorListener.BLACKHOLE);
            }

        // there must be an anonymous inner class skeleton by this point
        assert anon != null && anon.getComponent() != null;
        return anon.getComponent().getIdentityConstant().getType();
        }

    /**
     * Create the necessary AST and Component nodes for the anonymous inner class.
     *
     * @param ctx    the current compilation context
     * @param fTemp  true to specify that the inner class is only being created on a temporary basis
     * @param fRO    true to specify that any changes to the capture context should be discarded
     * @param errs   the error listener to log any errors to
     */
    private void ensureInnerClass(Context ctx, boolean fTemp, boolean fRO, ErrorListener errs)
        {
        assert body != null;

        // check if there is already a temp copy of the anonymous inner class floating around, and
        // if so, get rid of it
        if (m_anonActual != null)
            {
            destroyTempInnerClass();
            }

        // select a unique (and purposefully syntactically illegal) name for the anonymous inner
        // class
        if (anon == null)
            {
            AnonInnerClass info     = type.inferAnonInnerClass(errs);
            Component      parent   = getComponent();
            String         sDefault = info.getDefaultName();
            int            nSuffix  = 1;
            String         sName;
            while (parent.getChild(sName = sDefault + ":" + nSuffix) != null)
                {
                ++nSuffix;
                }
            Token tokName = new Token(type.getStartPosition(), type.getEndPosition(), Id.IDENTIFIER, sName);

            this.anon = adopt(new TypeCompositionStatement(
                    this,
                    info.getAnnotations(),
                    info.getCategory(),
                    tokName,
                    info.getCompositions(),
                    args,
                    body,
                    type.getStartPosition(),
                    body.getEndPosition()));
            }

        if (fTemp)
            {
            TypeCompositionStatement anonActual = anon;
            ClassStructure           clzActual  = (ClassStructure) anon.getComponent();

            anon = adopt((TypeCompositionStatement) anonActual.clone());
            if (clzActual != null)
                {
                Component componentParent = clzActual.getParent();
                assert componentParent == getComponent(); // the parent should be this method

                anon.setComponent(clzActual.replaceWithTemporary());
                }

            m_anonActual = anonActual;
            m_clzActual  = clzActual;
            }

        AnonInnerClassContext ctxAnon = new AnonInnerClassContext(ctx);
        m_ctxCapture = ctxAnon;

        catchUpChildren(errs);

        if (fRO)
            {
            // we intentionally don't call "ctxAnon.exit()"
            m_ctxCapture = null;
            }
        }

    /**
     * If the inner class was created on a temporary basis, then clean up the temporary data and
     * objects.
     */
    private void destroyTempInnerClass()
        {
        if (m_anonActual != null)
            {
            ClassStructure clzTemp = (ClassStructure) anon.getComponent();
            if (clzTemp != null)
                {
                Component componentParent = clzTemp.getParent();
                assert componentParent == getComponent();       // the parent should be this method

                ClassStructure clzReal = m_clzActual;
                if (clzReal == null)
                    {
                    componentParent.removeChild(clzTemp);
                    }
                else
                    {
                    clzTemp.replaceTemporaryWith(clzReal);
                    }
                }

            anon         = m_anonActual;
            m_anonActual = null;
            m_clzActual  = null;
            }
        }

    /**
     * Remove synthetic default constructor on the anonymous inner class.
     */
    private void destroyDefaultConstructor()
        {
        ClassStructure clz = (ClassStructure) anon.getComponent();
        MethodConstant id  = pool().ensureMethodConstant(clz.getIdentityConstant(), "construct",
                TypeConstant.NO_TYPES, TypeConstant.NO_TYPES);
        MethodStructure constrDefault = (MethodStructure) id.getComponent();
        if (constrDefault != null)
            {
            clz.getChild("construct").removeChild(constrDefault);
            }
        }

    /**
     * Create a synthetic constructor on the inner class that calls the specified super constructor.
     *
     * @param idSuper  the super constructor
     *
     * @return the identity of the new constructor on the anonymous inner class
     */
    private MethodConstant createPassThroughConstructor(MethodConstant idSuper)
        {
        // create a constructor that matches the one that we need to route to on the super class
        Parameter[]     aParams     = ((MethodStructure) idSuper.getComponent()).getParamArray();
        int             cParams     = aParams.length;
        ClassStructure  clzAnon     = (ClassStructure) anon.getComponent();
        MethodStructure constrThis  = clzAnon.createMethod(true, Access.PUBLIC, null,
                Parameter.NO_PARAMS, "construct", aParams, true, false);
        constrThis.setSynthetic(true);

        Code code = constrThis.createCode();
        if (cParams == 1)
            {
            code.add(new Construct_1(idSuper, new Register(aParams[0].getType(), 0)));
            }
        else
            {
            assert cParams > 1;
            Register[] aArgs = new Register[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                aArgs[i] = new Register(aParams[i].getType(), i);
                }
            code.add(new Construct_N(idSuper, aArgs));
            }
        code.add(new Return_0());

        return constrThis.getIdentityConstant();
        }

    /**
     * Apply information that was collected by analyzing the capture behavior of the anonymous inner
     * class.
     *
     * @param code  the code being emitted for the site of the NewExpression
     */
    private Argument[] addCaptures(Code code, Argument[] aOldArgs)
        {
        // we're going to be making changes, so get rid of any cached TypeInfo
        clearAnonTypeInfos();

        // if the anonymous inner class captures the "outer this", then it has to be an instance
        // child
        anon.getComponent().setStatic(!m_fInstanceChild);

        // if nothing else is captured, then we're done
        Map<String, Boolean>  mapCapture   = m_mapCapture;
        Map<String, Register> mapRegisters = m_mapRegisters;
        if (mapCapture == null || mapCapture.isEmpty())
            {
            return aOldArgs;
            }

        // we're going to replace the constructor by creating a new constructor that calls the old
        // one, but that first stores off all of the passed-in binding values
        ConstantPool pool       = pool();
        Parameter[]  aOldParams = m_constructor.getParamArray();
        int          cOldParams = aOldParams.length;
        int          cCaptures  = mapCapture.size();
        int          cNewParams = cOldParams + cCaptures;
        Parameter[]  aNewParams = new Parameter[cNewParams];
        int          iNewParam  = cOldParams;
        Argument[]   aNewArgs   = new Argument[cNewParams];
        assert cOldParams == aOldArgs.length;
        System.arraycopy(aOldParams, 0, aNewParams, 0, cOldParams);
        System.arraycopy(aOldArgs  , 0, aNewArgs  , 0, cOldParams);
        for (Entry<String, Boolean> entry : mapCapture.entrySet())
            {
            String       sName = entry.getKey();
            Register     reg   = mapRegisters.get(sName);
            Boolean      FVar  = entry.getValue();
            TypeConstant type  = reg.getType();
            Register     arg   = reg;
            if (FVar)
                {
                // it's a read/write capture; obtain the Var of the capture
                type = pool.ensureParameterizedTypeConstant(pool.typeVar(), type);
                arg  = new Register(type, Op.A_STACK);
                code.add(new MoveVar(reg, arg));
                }
            else if (!reg.isEffectivelyFinal())
                {
                // it's a read-only capture, but since we were unable to prove that the
                // register was effectively final, we need to capture the Ref
                type = pool.ensureParameterizedTypeConstant(pool.typeRef(), type);
                arg  = new Register(type, Op.A_STACK);
                code.add(new MoveRef(reg, arg));
                }

            // the new constructor will have the value/Ref/Var passed in as an additional parameter
            aNewParams[iNewParam] = new Parameter(pool, type, sName, null, false, iNewParam, false);
            aNewArgs  [iNewParam] = arg;
            ++iNewParam;
            }

        // create a wrapper constructor that takes the additional capture values and then delegates
        // to the original constructor
        ClassStructure  clzAnon   = (ClassStructure) anon.getComponent();
        MethodStructure constrOld = m_constructor;
        MethodStructure constrNew = clzAnon.createMethod(true, Access.PUBLIC, null,
                Parameter.NO_PARAMS, "construct", aNewParams, true, false);
        constrNew.setSynthetic(true);
        Code codeConstr = constrNew.createCode();

        // for each capture variable needed by the anonymous inner class, create a property that
        // will hold it, and store the value (which is being passed into the new constructor) into
        // that property
        for (int iCapture = 0; iCapture < cCaptures; ++iCapture)
            {
            iNewParam = cOldParams + iCapture;
            Parameter    param = aNewParams[iNewParam];
            String       sName = param.getName();
            TypeConstant type  = param.getType();
            Register     reg   = new Register(type, iNewParam);

            // create the property as a private synthetic
            PropertyStructure prop = clzAnon.createProperty(
                    false, Access.PRIVATE, Access.PRIVATE, type, sName);   // TODO @Final
            prop.setSynthetic(true);

            // store the constructor parameter into the property
            codeConstr.add(new L_Set(prop.getIdentityConstant(), reg));
            }

        // call the previous constructor
        MethodConstant idOld = constrOld.getIdentityConstant();
        switch (cOldParams)
            {
            case 0:
                codeConstr.add(new Construct_0(idOld));
                break;
            case 1:
                codeConstr.add(new Construct_1(idOld, new Register(aNewParams[0].getType(), 0)));
                break;
            default:
                Register[] aArgs = new Register[cOldParams];
                for (int i = 0; i < cOldParams; ++i)
                    {
                    aArgs[i] = new Register(aOldParams[i].getType(), i);
                    }
                codeConstr.add(new Construct_N(constrOld.getIdentityConstant(), aArgs));
                break;
            }
        codeConstr.add(new Return_0());

        // the new constructor calls the old constructor, so we need to call the new constructor
        m_constructor = constrNew;

        return aNewArgs;
        }

    private void clearAnonTypeInfos()
        {
        if (anon != null)
            {
            getType().invalidateTypeInfo();
            }
        }

    /**
     * @return true iff the name specifies a captured variable
     */
    boolean isCapture(String sCaptureName)
        {
        return m_mapCapture != null && m_mapCapture.containsKey(sCaptureName);
        }

    /**
     * @return the type of the value (not the Ref or Var, if implicit deref is used)
     */
    TypeConstant getCaptureType(String sCaptureName)
        {
        Register reg = m_mapRegisters.get(sCaptureName);
        return reg.getType();
        }

    /**
     * @return true iff a capture variable needs to be implicitly de-ref'd (via a CVAR)
     */
    boolean isImplicitDeref(String sCaptureName)
        {
        assert m_mapCapture.containsKey(sCaptureName);

        Register reg    = m_mapRegisters.get(sCaptureName);
        Boolean  FVar   = m_mapCapture  .get(sCaptureName);
        return FVar || !reg.isEffectivelyFinal();
        }


    // ----- debugging assistance ------------------------------------------------------------------

    /**
     * @return the signature of the constructor invocation
     */
    public String toSignatureString()
        {
        StringBuilder sb = new StringBuilder();

        if (left != null)
            {
            sb.append(left)
              .append('.');
            }

        sb.append(operator.getId().TEXT)
          .append(' ')
          .append(type);

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

        return sb.toString();
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(toSignatureString());

        if (body != null)
            {
            sb.append('\n')
              .append(indentLines(body.toString(), "        "));
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        String s = toSignatureString();

        return body == null
                ? s
                : s + "{..}";
        }


    // ----- inner class: CaptureContext -----------------------------------------------------------

    /**
     * A context for compiling new expressions that define an anonymous inner class.
     * <p/>TODO capture "this" (makes a lambda into a method, or a static anonymous class into an instance anonymous class)
     * <p/>TODO refactor for shared base class with LambdaExpression.CaptureContext
     */
    public class AnonInnerClassContext
            extends CaptureContext
        {
        /**
         * Construct a NewExpression CaptureContext.
         *
         * @param ctxOuter  the context within which this context is nested
         */
        public AnonInnerClassContext(Context ctxOuter)
            {
            super(ctxOuter);
            }

        /**
         * @return true iff the inner class captures the outer "this"
         */
        public boolean isInstanceChild()
            {
            return isThisCaptured() | true; // TODO it's not immediately obvious how to capture "outer this" (GG?)
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression               left;
    protected Token                    operator;
    protected TypeExpression           type;
    protected List<Expression>         args;
    protected StatementBlock           body;        // NOT a child
    protected TypeCompositionStatement anon;        // synthetic, added as a child
    protected long                     lEndPos;

    private transient MethodStructure m_constructor;
    private transient boolean         m_fTupleArg;  // indicates that arguments come from a tuple
    private transient int             m_cDefaults;  // number of default arguments

    private transient TypeCompositionStatement m_anonActual;
    private transient ClassStructure           m_clzActual;

    /**
     * The capture context, while it is active.
     */
    private transient AnonInnerClassContext m_ctxCapture;
    /**
     * The variables captured by the anonymous inner class, with an associated "true" flag if the
     * inner class needs to capture the variable in a read/write mode.
     */
    private transient Map<String, Boolean>  m_mapCapture;
    /**
     * A map from variable name to register, built by the anonymous inner class context.
     */
    private transient Map<String, Register> m_mapRegisters;
    /**
     * True if the class is a virtual child and needs to be constructed using a NEWC_ op-code.
     */
    private transient boolean               m_fVirtualChild;
    /**
     * True if the inner class captures "this" (i.e. not static).
     */
    private transient boolean               m_fInstanceChild;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NewExpression.class, "left", "type", "args", "anon");
    }
