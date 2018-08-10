package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.MultiMethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.Statement.CaptureContext;
import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.indentLines;


/**
 * Lambda expression is an inlined function. This version uses parameters that are assumed to be
 * names only.
 */
public class LambdaExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     *
     * @param params     either a list of Expression objects or a list of Parameter objects
     * @param operator
     * @param body
     * @param lStartPos
     */
    public LambdaExpression(List params, Token operator, StatementBlock body, long lStartPos)
        {
        if (!params.isEmpty() && params.get(0) instanceof Expression)
            {
            assert params.stream().allMatch(Expression.class::isInstance);
            this.paramNames = params;
            }
        else
            {
            assert params.stream().allMatch(org.xvm.asm.Parameter.class::isInstance);
            this.params = params;
            }

        this.operator  = operator;
        this.body      = body;
        this.lStartPos = lStartPos;
        }

    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public Component getComponent()
        {
        MethodStructure method = m_lambda;
        return method == null
                ? super.getComponent()
                : method;
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return body.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected void registerStructures(StageMgr mgr, ErrorListener errs)
        {
        // just like the MethodDeclarationStatement, lambda expressions are considered to be
        // completely opaque, and so a lambda defers the processing of its children at this point,
        // because it wants everything around its children to be set up by the time those children
        // need to be able to answer all of the questions about names and types and so on
        if (m_lambda == null)
            {
            mgr.deferChildren();
            }
        }

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        // see note above
        if (m_lambda == null)
            {
            mgr.deferChildren();
            }
        }

    @Override
    public void validateExpressions(StageMgr mgr, ErrorListener errs)
        {
        // see note above
        if (m_lambda == null)
            {
            mgr.deferChildren();
            }
        }

    @Override
    public void generateCode(StageMgr mgr, ErrorListener errs)
        {
        MethodStructure method = m_lambda;

        // if the lambda was somehow determined to produce a constant value with no side-effects,
        // then there shouldn't be a lambda body and we're already done here
        if (isConstant())
            {
            assert method == null;
            mgr.deferChildren();
            return;
            }

        // the method body containing this must validate the lambda expression, which then will
        // create the lambda body (the method structure), and that has to happen before we try to
        // spit out any code
        if (method == null)
            {
            mgr.requestRevisit();
            return;
            }

        // this is where the magic happens:
        // - by this point in time, the method body containing the lambda has already been validated
        // - the validation included validating (a temp copy of) this expression in order to
        //   determine definite assignment information (VAS data) which is used to itemize the
        //   captures by the lambda, and whether the read-only captures should use values or
        //   references
        // - when the expression was validated, it started by creating the MethodStructure for the
        //   lambda (m_lambda)
        // - when the expression was subsequently asked to generate code that invokes the lambda
        //   (via generateAssignment), it was then able to use that VAS information to build the
        //   final signature for the lambda, including all of the parameters necessary to capture
        //   the various variables in the lexical scope of the lambda declaration that needed to be
        //   passed to the lambda (via fbind)
        // - so now, at this point, we have the signature, we have the method structure, and we just
        //   have to emit the code corresponding to the lambda
        if (catchUpChildren(errs))
            {
            body.compileMethod(method.createCode(), errs);
            }
        }

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        if (isValidated())
            {
            return getType();
            }

        checkDebug(); // TODO remove
        assert m_typeRequired == null && m_listRetTypes == null;

        if
        // clone the body (to avoid damaging the original) and validate it to calculate its type
        StatementBlock blockTemp = (StatementBlock) body.clone();

        // the resulting returned types come back in m_listRetTypes
        if (blockTemp.validate(ctx.createCaptureContext(blockTemp), ErrorListener.BLACKHOLE) == null
                || m_listRetTypes == null)
            {
            m_listRetTypes = null;
            return null;
            }

        TypeConstant[] aTypes = m_listRetTypes.toArray(new TypeConstant[m_listRetTypes.size()]);
        m_listRetTypes = null;
        return ListExpression.inferCommonType(aTypes);
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired)
        {
        if (isValidated())
            {
            return getType().isA(typeRequired)
                    ? TypeFit.Fit
                    : TypeFit.NoFit;
            }

        // short-circuit for simple cases
        TypeConstant typeFunction = pool().typeFunction();
        if (typeFunction.isA(typeRequired))
            {
            return TypeFit.Fit;
            }
        else if (typeFunction.isAssignableTo(typeRequired))
            {
            return TypeFit.Conv;
            }

        checkDebug(); // TODO remove
        assert m_typeRequired == null && m_listRetTypes == null;

        // clone the body and validate it using the requested type to test if that type will work
        m_typeRequired = typeRequired;
        StatementBlock blockTemp = (StatementBlock) body.clone();
        blockTemp.validate(ctx.createCaptureContext(blockTemp), ErrorListener.BLACKHOLE);
        m_typeRequired = null;

        // the resulting returned types come back in m_listRetTypes
        if (m_listRetTypes == null)
            {
            return TypeFit.NoFit;
            }
        else
            {
            TypeConstant[] aTypes = m_listRetTypes.toArray(new TypeConstant[m_listRetTypes.size()]);
            m_listRetTypes = null;

            // calculate the resulting type
            TypeConstant typeResult = ListExpression.inferCommonType(aTypes);
            return typeResult != null && typeResult.isA(typeRequired)
                    ? TypeFit.Fit
                    : TypeFit.NoFit;
            }
        }


    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        checkDebug(); // TODO remove

        // validation only occurs once, but we'll put some extra checks in up front, because we do
        // weird stuff on lambdas like cloning the AST so that we can pass over it once for the
        // expression validation, but use another copy of the body for the lambda method/function
        // compilation itself
        assert m_typeRequired == null && m_listRetTypes == null && m_lambda == null;

        if (typeRequired != null)
            {

            }
        if (paramNames != null)
            {
            for (Expression expr : paramNames)
                {
                if (!(expr instanceof NameExpression))
                    {
                    expr.log()
                    }
                }
            // TODO check paramNames - if it isn't null, then each one must be a name expression or an ignore name or a
            }

        m_typeRequired = typeRequired;
        m_lambda       = instantiateLambda(errs);

        TypeFit        fit       = TypeFit.Fit;
        StatementBlock blockTemp = (StatementBlock) body.clone();
        CaptureContext ctxLambda = ctx.createCaptureContext(blockTemp);
        StatementBlock blockNew  = (StatementBlock) blockTemp.validate(ctxLambda, errs);
        if (blockNew == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            // we do NOT store off the validated block; the block does NOT belong to the lambda
            // expression; rather, it belongs to the function (m_lambda) that we created, and the
            // real (not temp) block will get validated and compiled by generateCode() above
            }

        TypeConstant typeActual = null;
        if (m_listRetTypes != null)
            {
            TypeConstant[] aTypes = m_listRetTypes.toArray(new TypeConstant[m_listRetTypes.size()]);
            m_listRetTypes = null;
            typeActual = ListExpression.inferCommonType(aTypes);
            }
        if (typeActual == null)
            {
            fit = TypeFit.NoFit;
            }

        // while we have enough info at this point to build a signature, what we lack is the
        // effectively final data that will only get reported (via exitScope() on the context) as
        // the variables go out of scope in the method body that contains this lambda, so we need
        // to store off the data from the capture context, and defer the signature creation to the
        // generateAssignment() method
        ctxLambda.getCaptureMap();

        return finishValidation(typeRequired, typeActual, fit, null, errs);
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        checkDebug();

        // this is the first time that we have a chance to put together the signature, because
        // this is the first time that we called after validate

        // TODO somehow, at the end of validate (after all the various things that could happen could happen, i.e. all assignments before & after this),
        // TODO ... we build the lambda signature, and set it on the MethodConstant
        // TODO
        super.generateAssignment(ctx, code, LVal, errs);
        }


    // TODO temp
    static LambdaExpression exprDebug;
    void checkDebug()
        {
        if (exprDebug == null && !getComponent().getIdentityConstant().getModuleConstant().toString().contains("Ecstasy"))
            {
            exprDebug = this;
            }

        if (this == exprDebug)
            {
            String s = toString();
            }
        }

    // ----- compilation helpers -------------------------------------------------------------------

    /**
     * @return the required type, which is the specified required type during validation, or the
     *         actual type once the expression is validatd
     */
    TypeConstant getRequiredType()
        {
        return isValidated() ? getType() : m_typeRequired;
        }

    void addReturnType(TypeConstant typeRet)
        {
        List<TypeConstant> list = m_listRetTypes;
        if (list == null)
            {
            m_listRetTypes = list = new ArrayList<>();
            }
        list.add(typeRet);
        }

    TypeConstant[] inferParamTypes(TypeConstant typeFunction)
        {

        }

    TypeConstant inferReturnType(TypeConstant typeFunction)
        {
        if (typeFunction.isA(pool().typeFunction()) && typeFunction.isParamsSpecified()
                && typeFunction.getParamsCount() > )
            {
            typeFunction.getParamTypesArray()
            }
        }

    MethodStructure instantiateLambda(ErrorListener errs)
        {
        TypeConstant[] atypes   = null;
        String[]       asParams = null;
        if (paramNames == null)
            {
            // build an array of types and an array of names
            int cParams = params == null ? 0 : params.size();
            atypes   = new TypeConstant[cParams];
            asParams = new String[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                Parameter param = params.get(i);
                atypes  [i] = param.getType().ensureTypeConstant();
                asParams[i] = param.getName();
                }
            }
        else
            {
            // build an array of names
            int cParams = paramNames.size();
            asParams = new String[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                Expression expr = paramNames.get(i);
                if (expr instanceof NameExpression)
                    {
                    // note: could also be an IgnoredNameExpression
                    asParams[i] = ((NameExpression) expr).getName();
                    }
                else
                    {
                    expr.log(errs, Severity.ERROR, Compiler.NAME_REQUIRED);
                    asParams[i] = Id.IGNORED.TEXT;
                    }
                }
            }

        Component            container = getParent().getComponent();
        MultiMethodStructure structMM  = container.ensureMultiMethodStructure(METHOD_NAME);
        MethodStructure      lambda    = structMM.createLambda(atypes, asParams);

        return lambda;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    public String toSignatureString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('(');
        boolean first = true;
        for (Object param : (params == null ? paramNames : params))
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

        sb.append(')')
          .append(' ')
          .append(operator.getId().TEXT);

        return sb.toString();
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(toSignatureString());

        String s = body.toString();
        if (s.indexOf('\n') >= 0)
            {
            sb.append('\n')
              .append(indentLines(s, "    "));
            }
        else
            {
            sb.append(' ')
              .append(s);
            }

        return sb.toString();
        }

    @Override
    public String toDumpString()
        {
        return toSignatureString() + " {...}";
        }


    // ----- fields --------------------------------------------------------------------------------

    public static final String METHOD_NAME = "->";

    protected List<Parameter>  params;
    protected List<Expression> paramNames;
    protected Token            operator;
    protected StatementBlock   body;
    protected long             lStartPos;

    private MethodStructure m_lambda;

    private transient TypeConstant       m_typeRequired;
    private transient List<TypeConstant> m_listRetTypes;

    private static final Field[] CHILD_FIELDS = fieldsForNames(LambdaExpression.class, "params", "paramNames", "body");
    }
