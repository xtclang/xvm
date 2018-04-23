package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.ErrorListener.SilentErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * A name expression specifies a name. This handles both a simple name, a qualified name, and a name
 * with type parameters.
 * <p/>
 * A simple name can refer to:
 * <ul>
 * <li>A method parameter from the current method;</li>
 * <li>A local variable (register) available from the Context;</li>
 * <li>In the case of a lambda, a capturable variable from outside of the current MethodStructure
 *     (but within the compiler context)  that must be captured (added as an implicit parameter to
 *     the lambda);</li>
 * <li>A constant value available from the Context;</li>
 * <li>A capturable name available to the current method, if the method is a lambda;</li>
 * <li>A property name;</li>
 * <li>A class identity;</li>
 * <li>A typedef identity;</li>
 * <li>A multi-method identity;</li>
 * <li>The "construct" keyword (indicating a call to a constructor function on this type);</li>
 * <li>A parameter name preceding the lambda operator ("->").</li>
 * </ul>
 *
 * <p/>
 * A name with params can only be one of the following:
 * <ul>
 * <li>A method name with "redundant return" disambiguation; or</li>
 * <li>A type.</li>
 * </ul>
 *
 * <p/>
 * Subsequent simple names can refer to:
 * <ul>
 * <li>A nested class name ... ending with "construct"</li>
 * <li></li>
 * <li></li>
 * <li></li>
 * </ul>
 */
public class NameExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public NameExpression(Token name)
        {
        this(null, Collections.singletonList(name), null, name.getEndPosition());
        }

    public NameExpression(Token amp, List<Token> names, List<TypeExpression> params, long lEndPos)
        {
        this.amp     = amp;
        this.names   = names;
        this.params  = params;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected boolean usesSuper()
        {
        for (Token token : names)
            {
            if (token.getId() == Id.SUPER)
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        return isDotNameWithNoParams("present") || super.validateCondition(errs);
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        if (validateCondition(null))
            {
            ConstantPool pool = pool();
            return pool.ensurePresentCondition(new UnresolvedNameConstant(pool, getUpToDotName(), false));
            }

        return super.toConditionalConstant();
        }

    /**
     * Determine if the expression is a multi-part, dot-delimited name that has no type params.
     *
     * @param sName  the last name of the expression must match this name
     *
     * @return true iff the expression is a multi-part, dot-delimited name that has no type params,
     *         with the last part of the name matching the specified name
     */
    protected boolean isDotNameWithNoParams(String sName)
        {
        List<Token> names  = this.names;
        int         cNames = names.size();
        return cNames > 1 && names.get(cNames-1).getValue().equals(sName) && (params == null || params.isEmpty());
        }

    /**
     * Get all of the names in the expression except the last one.
     *
     * @return an array of names
     */
    protected String[] getUpToDotName()
        {
        List<Token> listNames = this.names;
        int         cNames    = listNames.size() - 1;
        String[]    aNames    = new String[cNames];
        for (int i = 0; i < cNames; ++i)
            {
            aNames[i] = (String) listNames.get(i).getValue();
            }
        return aNames;
        }

    @Override
    public boolean isSuppressDeref()
        {
        return amp != null;
        }

    public List<Token> getNames()
        {
        return names;
        }

    public List<TypeExpression> getParams()
        {
        return params;
        }

    /**
     * @return the number of dot-delimited names in the expression
     */
    public int getNameCount()
        {
        return names.size();
        }

    /**
     * @param i  the index of the name to obtain from the dot-delimited names in the expression
     *
     * @return the i-th name in the expression
     */
    String getName(int i)
        {
        return (String) names.get(i).getValue();
        }

    @Override
    public long getStartPosition()
        {
        return names.get(0).getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    public boolean isSpecial()
        {
        for (Token name : names)
            {
            if (name.isSpecial())
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public TypeExpression toTypeExpression()
        {
        return new NamedTypeExpression(null, names, null, null, params, lEndPos);
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    protected Argument resolveNames(Context ctx, ErrorListener errs)
        {
        // already did the first (0) name, so start with the second (1) and go to the last,
        // resolving the name incrementally
        Argument arg = null;
        for (int i = 0, iLast = names.size() - 1; i <= iLast; ++i)
            {
            arg = i == 0
                    ? resolveFirstName(ctx, isSuppressDeref(), names.get(i), i == iLast ? params : null, errs)
                    : resolveNextName(arg, false, names.get(i), i == iLast ? params : null, errs);
            if (arg == null)
                {
                return null;
                }
            }

        return arg;
        }

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        Argument arg = resolveNames(ctx, ErrorListener.BLACKHOLE);
        return arg == null
                ? null
                : arg.getRefType();
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, TuplePref pref)
        {
        Argument arg = resolveNames(ctx, ErrorListener.BLACKHOLE);
        if (arg == null)
            {
            return TypeFit.NoFit;
            }

        if (typeRequired == null || arg.getRefType().isA(typeRequired))
            {
            return pref == TuplePref.Required
                    ? TypeFit.Pack
                    : TypeFit.Fit;
            }

        if (arg.getRefType().getConverterTo(typeRequired) != null)
            {
            return pref == TuplePref.Required
                    ? TypeFit.ConvPack
                    : TypeFit.Conv;
            }

        return TypeFit.NoFit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, TuplePref pref, ErrorListener errs)
        {
        boolean fValid = true;

        // a name expression has params from the construct:
        //      QualifiedNameName TypeParameterTypeList-opt
        ConstantPool pool = pool();
        if (params != null)
            {
            for (int i = 0, c = params.size(); i < c; ++i)
                {
                TypeExpression typeOld = params.get(i);
                TypeExpression typeNew = (TypeExpression) typeOld.validate(
                        ctx, pool.typeType(), TuplePref.Rejected, errs);
                fValid &= typeNew != null;
                if (typeNew != typeOld && typeNew != null)
                    {
                    params.set(i, typeNew);
                    }
                }
            }

        // resolve the initial name
        Token        name  = names.get(0);
        String       sName = name.getValue().toString();
        Argument     arg   = ctx.resolveName(name, errs);
        if (arg == null)
            {
            log(errs, Severity.ERROR, org.xvm.compiler.Compiler.NAME_MISSING,
                    sName, ctx.getMethod().getIdentityConstant().getSignature());
            fValid = false;
            }

        // if anything has failed already, we won't be able to complete the validation
        if (!fValid)
            {
            finishValidation(TypeFit.NoFit, typeRequired, arg instanceof Constant ? (Constant) arg : null);
            return null;
            }

        // resolve the name to an argument, and determine assignability
        if (names.size() == 1)
            {
            // TODO incorporate params, if any
            m_arg         = arg;
            m_fAssignable = ctx.isVarWritable(sName); // TODO: handle properties
            }
        else
            {
            // TODO resolve subsequent names (will return another expression type at any point that a name is not resolving to an arg)
            notImplemented();
            }

        // validate that the expression can be of the required type
        TypeConstant type = arg.getRefType();
        TypeFit      fit  = TypeFit.Fit;
        if (fValid && typeRequired != null && !type.isA(typeRequired))
            {
            // check if conversion in required
            MethodConstant idMethod = type.ensureTypeInfo().findConversion(typeRequired);
            if (idMethod == null)
                {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, typeRequired, arg.getRefType());
                fValid = false;
                }
            else
                {
                // use the return value from the conversion function to figure out what type the
                // literal should be converted to, and then do the conversion here in the
                // compiler (eventually, once boot-strapped into Ecstasy, the compiler will be
                // able to rely on the runtime itself to do conversions, and using containers,
                // can even do so for user code)
                type = idMethod.getSignature().getRawReturns()[0];
                fit  = fit.addConversion();
                }
            }

        if (!fValid)
            {
            // if there's any problem computing the type, and the expression is already invalid,
            // then just agree to whatever was asked
            if (typeRequired != null)
                {
                type = typeRequired;
                }

            fit = TypeFit.NoFit;
            }
        else if (pref == TuplePref.Required)
            {
            fit = fit.addPack();
            }

        boolean fConstant = m_arg != null && m_arg instanceof Constant && !m_fAssignable;
        finishValidation(fit, type, fConstant ? (Constant) m_arg : null);

        return fValid
                ? this
                : null;
        }

    @Override
    public boolean isAssignable()
        {
        return m_fAssignable;
        }

    @Override
    public Argument generateArgument(Code code, boolean fPack, ErrorListener errs)
        {
        return m_arg == null
                ? generateBlackHole(getType())
                : m_arg;
        }


    @Override
    public Assignable generateAssignable(Code code, ErrorListener errs)
        {
        Assignable LVal = m_LVal;
        if (LVal == null && isAssignable())
            {
            if (m_arg instanceof Register)
                {
                LVal = new Assignable((Register) m_arg);
                }
            else if (m_arg instanceof PropertyConstant)
                {
                // TODO: use getThisClass().toTypeConstant() for a type
                LVal = new Assignable(
                    new Register(pool().typeObject(), Op.A_TARGET),
                    (PropertyConstant) m_arg);
                }
            else
                {
                LVal = super.generateAssignable(code, errs);
                }
            m_LVal = LVal;
            }

        return LVal;
        }

    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        boolean first = true;
        for (Token token : names)
            {
            if (first)
                {
                first = false;
                if (amp != null)
                    {
                    sb.append('&');
                    }
                }
            else
                {
                sb.append('.');
                }
            sb.append(token.getValue());
            }

        if (params != null)
            {
            sb.append('<');
            first = true;
            for (Expression param : params)
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
            sb.append('>');
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token                amp;
    protected List<Token>          names;
    protected List<TypeExpression> params;
    protected long                 lEndPos;

    private Argument     m_arg;
    private Assignable   m_LVal;
    private boolean      m_fAssignable;
    private TypeConstant m_type;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NameExpression.class, "params");
    }
