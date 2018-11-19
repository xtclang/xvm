package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.NewG_0;
import org.xvm.asm.op.NewG_1;
import org.xvm.asm.op.NewG_N;
import org.xvm.asm.op.New_0;
import org.xvm.asm.op.New_1;
import org.xvm.asm.op.New_N;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Constants;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

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

    /**
     * @return the type of the anonymous inner class
     */
    public TypeConstant getAnonymousInnerClassType()
        {
        if (anon == null)
            {
            ensureInnerClass(true, ErrorListener.BLACKHOLE);
            }

        // there must be an anonymous inner class skeleton by this point
        assert anon != null && anon.getComponent() != null;
        return anon.getComponent().getIdentityConstant().getType();
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
            return getAnonymousInnerClassType();
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
            ensureInnerClass(false, errs);
            }

        // validate the expression that occurs _before_ the new, e.g. x in "x.new Y()", which
        // specifies an "outer this" that provides support for virtual construction
        Expression   exprLeftOld = this.left;
        TypeConstant typeLeft    = null;
        if (exprLeftOld != null)
            {
            Expression exprLeftNew = exprLeftOld.validate(ctx, null, errs);
            if (exprLeftNew == null)
                {
                fValid = false;
                }
            else
                {
                this.left = exprLeftNew;
                typeLeft  = exprLeftNew.getType();
                }
            }

        // we intentionally don't pass the required type to the TypeExpression; instead, let's take
        // whatever type it produces and later validate the resulting type against the required type
        TypeExpression exprTypeOld = this.type;
        TypeExpression exprTypeNew = (TypeExpression) exprTypeOld.validate(ctx, null, errs);
        TypeConstant   typeTarget  = null;
        TypeInfo       infoTarget  = null;
        TypeConstant   typeSuper   = null;
        TypeInfo       infoSuper   = null;
        ConstantPool   pool        = pool();
        if (exprTypeNew == null)
            {
            fValid = false;
            }
        else
            {
            this.type = exprTypeNew;

            typeTarget = exprTypeNew.ensureTypeConstant();
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
                typeTarget = getAnonymousInnerClassType();
                // REVIEW GG - causes warning: "Suspicious assignment from: testSimple(?)#Object:1 to: testSimple(?)#Object:1:private"
                // typeTarget = pool.ensureAccessTypeConstant(getAnonymousInnerClassType(), Access.PRIVATE);
                }
            else if (fNestMate)
                {
                // since we are new-ing a class that is a nest-mate of the current class, we can
                // increase visibility from the public default all the way to private
                typeTarget = pool.ensureAccessTypeConstant(typeTarget, Access.PRIVATE);
                }

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
                    final int[] aiCount = new int[] {3}; // limit reporting to a small number of errors

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
            else if (left != null)
                {
                // TODO GG :-)
                // figure out the relationship between the type of "left" and the type being
                // constructed; they must both belong to the same "localized class tree", and the
                // type being instantiated must either be a static child class, the top level class,
                // or an instance class directly nested under the class specified by the "left" type
                // TODO detect & log errors: VE_NEW_REQUIRES_PARENT VE_NEW_DISALLOWS_PARENT VE_NEW_UNRELATED_PARENT
                log(errs, Severity.ERROR, Compiler.NOT_IMPLEMENTED, "Instantiation of child classes");
                fValid = false;
                }
            }

        if (fValid)
            {
            List<Expression> listArgs = this.args;
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
                idMethod = findMethod(ctx, infoTarget, "construct", listArgs, false, true, null, errsTarget);
                if (idMethod == null && !listArgs.isEmpty())
                    {
                    // the constructor that we're looking for is not on the anonymous inner class,
                    // so we need to find the specified constructor on the super class (which means
                    // that the super class must NOT be an interface), and we need to verify that
                    // there is a default constructor on the anon inner class, and it needs to be
                    // replaced by a constructor with the same signature as the super's constructor
                    // (note: the automatic creation of the synthetic no-arg constructor in the
                    // absence of any explicit constructor must do this same check)
                    MethodConstant idSuper = findMethod(ctx, infoSuper, "construct", listArgs, false, true, null, errs);
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
                        ClassStructure clzAnon        = (ClassStructure) anon.getComponent();
                        MethodConstant idDefault      = pool.ensureMethodConstant(
                                anon.getComponent().getIdentityConstant(), "construct",
                                TypeConstant.NO_TYPES, TypeConstant.NO_TYPES);
                        MethodStructure constrDefault = (MethodStructure) idDefault.getComponent();
                        if (constrDefault == null)
                            {
                            // TODO log error missing constructor
                            }
                        else
                            {
                            // remove synthetic construct() on the anonymous inner class
                            clzAnon.getChild("construct").removeChild(constrDefault);

                            // create a constructor that matches the one that we need to route to
                            // on the super class
//                            clzAnon.createMethod()
//                            MethodStructure method = container.createMethod(fFunction, access, aAnnotations,
//                                    aReturns, sName, aParams, body != null, fUsesSuper);

                            // TODO - replace synthetic construct() on the inner with construct(...)
                            notImplemented();

                            // since we just modified the component, flush the TypeInfo cache for
                            // the type of the anonymous inner class
                            typeTarget.clearTypeInfo();
                            }
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
                idMethod = findMethod(ctx, infoTarget, "construct", listArgs, false, true, null, errs);
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
                    if (args.size() == 1)
                        {
                        typeTuple = pool.ensureParameterizedTypeConstant(
                                pool.typeTuple(), atypeArgs);
                        if (!args.get(0).testFit(ctx, typeTuple).isFit())
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

                m_aconstDefault = collectDefaultArgs(m_constructor, listArgs.size());
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
            CaptureContext ctxAnon = new CaptureContext(ctx);
            anon.setCaptureContext(ctxAnon);

            // force a temp clone of the inner class to go through its validate() stage so that we
            // can determine what variables get captured (and if they are effectively final)
            ensureInnerClass(/* fTemp */ true, ErrorListener.BLACKHOLE);
            if (!new StageMgr(anon, Stage.Emitted, errs).fastForward(20))
                {
                fValid = false;
                }

            // collected VAS information from the lambda context
            ctx = ctxAnon.exit();

            // clean up temporary inner class and the capture context
            destroyTempInnerClass();
            anon.setCaptureContext(null);

            // at this point we have a fair bit of data about which variables get captured, but we
            // still lack the effectively final data that will only get reported when the various
            // nested contexts in which the captured variables were declared go through their exit()
            // logic (as the variables go out of scope in the method body that contains this
            // NewExpression); for now, store off the data from the capture context
            m_mapCapture     = ctxAnon.getCaptureMap();
            m_mapRegisters   = ctxAnon.ensureRegisterMap();
            m_fInstanceChild = ctxAnon.isInstanceChild();
            }

        return finishValidation(typeRequired, typeTarget, fValid ? TypeFit.Fit : TypeFit.NoFit, null, errs);
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
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        assert m_constructor != null;

        if (left != null)
            {
            // TODO construct child class
            notImplemented();
            }

        List<Expression> listArgs = args;
        int              cArgs    = listArgs.size();
        Argument[]       aArgs    = new Argument[cArgs];
        for (int i = 0; i < cArgs; ++i)
            {
            aArgs[i] = listArgs.get(i).generateArgument(ctx, code, true, true, errs);
            }

        Argument argResult = new Register(getType());

        generateNew(code, aArgs, argResult);

        return argResult;
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        assert m_constructor != null;

        if (left != null)
            {
            // TODO construct child class
            notImplemented();
            }

        List<Expression> listArgs = args;
        int              cArgs    = listArgs.size();
        Argument[]       aArgs    = new Argument[cArgs];
        for (int i = 0; i < cArgs; ++i)
            {
            aArgs[i] = listArgs.get(i).generateArgument(ctx, code, true, true, errs);
            }

        Argument argResult = LVal.isLocalArgument()
                ? LVal.getLocalArgument()
                : new Register(LVal.getType());

        generateNew(code, aArgs, argResult);

        if (!LVal.isLocalArgument())
            {
            LVal.assign(argResult, code, errs);
            }
        }


    // ----- compilation helpers -------------------------------------------------------------------

    /**
     * Generate the NEW_* op-code
     */
    private void generateNew(Code code, Argument[] aArgs, Argument argResult)
        {
        MethodConstant idConstruct   = m_constructor.getIdentityConstant();
        TypeConstant   typeTarget    = argResult.getType();
        Constant[]     aconstDefault = m_aconstDefault;
        int            cAll          = idConstruct.getRawParams().length;
        int            cArgs         = aArgs.length;
        int            cDefaults     = aconstDefault == null ? 0 : aconstDefault.length;

        if (m_fTupleArg)
            {
            notImplemented();
            }
        else
            {
            assert cArgs + cDefaults == cAll;
            if (cDefaults > 0)
                {
                if (cArgs == 0)
                    {
                    aArgs = aconstDefault;
                    }
                else
                    {
                    Argument[] aArgAll = new Argument[cAll];
                    System.arraycopy(aArgs, 0, aArgAll, 0, cArgs);
                    System.arraycopy(aconstDefault, 0, aArgAll, cArgs, cDefaults);
                    aArgs = aArgAll;
                    }
                }

            if (typeTarget.isParamsSpecified())
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
            }
        }

    /**
     * Create the necessary AST and Component nodes for the anonymous inner class.
     *
     * @param fTemp  true to specify that the inner class is only being created on a temporary basis
     * @param errs   the error listener to log any errors to
     */
    private void ensureInnerClass(boolean fTemp, ErrorListener errs)
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
                assert componentParent == getComponent();       // the parent should be this method

                ClassStructure clzTemp = (ClassStructure) clzActual.clone();
                componentParent.replaceChild(clzActual, clzTemp);

                anon.setComponent(clzTemp);
                }

            m_anonActual = anonActual;
            m_clzActual  = clzActual;
            }

        catchUpChildren(errs);
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
                    componentParent.replaceChild(clzTemp, clzReal);
                    }
                }

            anon         = m_anonActual;
            m_anonActual = null;
            m_clzActual  = null;
            }
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
    public class CaptureContext
            extends Context
        {
        /**
         * Construct a NewExpression CaptureContext.
         *
         * @param ctxOuter  the context within which this context is nested
         */
        public CaptureContext(Context ctxOuter)
            {
            super(ctxOuter, true);
            }

        @Override
        public Context exit()
            {
            Context ctxOuter = super.exit();

            // apply variable assignment information from the capture scope to the variables
            // captured from the outer scope
            Map<String, Boolean>  mapCapture = ensureCaptureMap();
            Map<String, Register> mapVars    = ensureRegisterMap();
            for (Entry<String, Boolean> entry : mapCapture.entrySet())
                {
                String  sName = entry.getKey();
                boolean fMod  = entry.getValue();
                if (!fMod && getDefiniteAssignments().containsKey(sName))
                    {
                    entry.setValue(true);
                    fMod = true;
                    }

                if (fMod)
                    {
                    Assignment asnOld = ctxOuter.getVarAssignment(sName);
                    Assignment asnNew = asnOld.applyAssignmentFromCapture();
                    ctxOuter.setVarAssignment(sName, asnNew);
                    }

                mapVars.put(sName, (Register) getVar(sName));
                }

            return ctxOuter;
            }

        @Override
        protected void markVarRead(boolean fNested, String sName, Token tokName, ErrorListener errs)
            {
            // variable capture will create a parameter (a variable in this scope) for the lambda,
            // so if the variable isn't already declared in this scope but it exists in the outer
            // scope, then capture it
            final Context ctxOuter = getOuterContext();
            if (!isVarDeclaredInThisScope(sName) && ctxOuter.isVarReadable(sName))
                {
                boolean fCapture = true;
                if (isReservedName(sName))
                    {
                    switch (sName)
                        {
                        case "this":
                        case "this:target":
                        case "this:public":
                        case "this:protected":
                        case "this:private":
                        case "this:struct":
                            // the only names that we capture _without_ a capture parameter are the
                            // various "this" references that refer to "this" object
                            if (ctxOuter.isMethod())
                                {
                                markInstanceChild(); // REVIEW
                                return;
                                }
                            break;

                        case "this:service":
                        case "this:module":
                            // these two are available globally, and are _not_ captured
                            return;
                        }
                    }

                if (fCapture)
                    {
                    // capture the variable
                    Map<String, Boolean> map = ensureCaptureMap();
                    if (!map.containsKey(sName))
                        {
                        map.put(sName, false);
                        }
                    }
                }

            super.markVarRead(fNested, sName, tokName, errs);
            }

        @Override
        protected void markVarWrite(String sName, Token tokName, ErrorListener errs)
            {
            // names in the name map but not in the capture map are lambda parameters; all other
            // names become captures
            if (!getNameMap().containsKey(sName) || getCaptureMap().containsKey(sName))
                {
                ensureCaptureMap().put(sName, true);
                }

            super.markVarWrite(sName, tokName, errs);
            }

        /**
         * @return a map of variable name to a Boolean representing if the capture is read-only
         *         (false) or read/write (true)
         */
        public Map<String, Boolean> getCaptureMap()
            {
            return m_mapCapture == null
                    ? Collections.EMPTY_MAP
                    : m_mapCapture;
            }

        /**
         * Obtain the map of names to registers, if it has been built.
         * <p/>
         * Note: built by exit()
         *
         * @return a non-null map of variable name to Register for all of variables to capture
         */
        public Map<String, Register> ensureRegisterMap()
            {
            Map<String, Register> map = m_mapRegisters;
            if (map == null)
                {
                if (getCaptureMap().isEmpty())
                    {
                    // there are never more capture-registers than there are captures
                    return Collections.EMPTY_MAP;
                    }

                m_mapRegisters = map = new HashMap<>();
                }

            return map;
            }

        /**
         * @return true iff the inner class captures the outer "this"
         */
        public boolean isInstanceChild()
            {
            return m_fCaptureThis;
            }

        @Override
        protected boolean hasInitialNames()
            {
            return true;
            }

        @Override
        protected void initNameMap(Map<String, Argument> mapByName)
            {
            TypeConstant[] atypeParams = m_atypeParams;
            String[]       asParams    = m_asParams;
            int            cParams     = atypeParams == null ? 0 : atypeParams.length;
            for (int i = 0; i < cParams; ++i)
                {
                TypeConstant type  = atypeParams[i];
                String       sName = asParams[i];
                if (!sName.equals(Id.IGNORED.TEXT) && type != null)
                    {
                    mapByName.put(sName, new Register(type));

                    // the variable has been definitely assigned, but not multiple times (i.e. it's
                    // still effectively final)
                    ensureDefiniteAssignments().put(sName, Assignment.AssignedOnce);
                    }
                }
            }

        /**
         * @return a map of variable name to a Boolean representing if the capture is read-only
         *         (false) or read/write (true)
         */
        private Map<String, Boolean> ensureCaptureMap()
            {
            Map<String, Boolean> map = m_mapCapture;
            if (map == null)
                {
                // use a tree map, as it will keep the captures in alphabetical order, which will
                // help to produce the lambdas with a "predictable" signature
                m_mapCapture = map = new TreeMap<>();
                }

            return map;
            }

        private TypeConstant[] m_atypeParams;
        private String[]       m_asParams;

        /**
         * A map from variable name to read/write flag (false is read-only, true is read-write) for
         * the variables to capture.
         */
        private Map<String, Boolean> m_mapCapture;

        /**
         * A map from variable name to register, built by exit().
         */
        private Map<String, Register> m_mapRegisters;

        /**
         * Set to true iff the lambda function has to actually be a method so that it can capture
         * "this".
         */
        private boolean m_fCaptureThis;
        }

    /**
     * @return a map of variable name to a Boolean representing if the capture is read-only
     *         (false) or read/write (true)
     */
    public Map<String, Boolean> getCaptureMap()
        {
        return m_mapCapture == null
                ? Collections.EMPTY_MAP
                : m_mapCapture;
        }

    /**
     * @return a map of variable name to a Boolean representing if the capture is read-only
     *         (false) or read/write (true)
     */
    Map<String, Boolean> ensureCaptureMap()
        {
        Map<String, Boolean> map = m_mapCapture;
        if (map == null)
            {
            // use a tree map, as it will keep the captures in alphabetical order, which will
            // help to produce the lambdas with a "predictable" signature
            m_mapCapture = map = new TreeMap<>();
            }

        return map;
        }

    /**
     * Obtain the map of names to registers, if it has been built.
     * <p/>
     * Note: built by exit()
     *
     * @return a non-null map of variable name to Register for all of variables to capture
     */
    Map<String, Register> ensureRegisterMap()
        {
        Map<String, Register> map = m_mapRegisters;
        if (map == null)
            {
            if (getCaptureMap().isEmpty())
                {
                // there are never more capture-registers than there are captures
                return Collections.EMPTY_MAP;
                }

            m_mapRegisters = map = new HashMap<>();
            }

        return map;
        }

    /**
     * Specify that the anonymous inner class has to capture "this".
     */
    void markInstanceChild()
        {
        m_fInstanceChild = true;
        }

    /**
     * @return true iff the anonymous inner class captures the containing "this"
     */
    public boolean isInstanceChild()
        {
        return m_fInstanceChild;
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
    private transient boolean         m_fTupleArg;     // indicates that arguments come from a tuple
    private transient Constant[]      m_aconstDefault; // default arguments

    private transient TypeCompositionStatement m_anonActual;
    private transient ClassStructure           m_clzActual;

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
     * True if the inner class captures "this" (i.e. not static).
     */
    private transient boolean               m_fInstanceChild;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NewExpression.class, "left", "type", "args", "anon");
    }
