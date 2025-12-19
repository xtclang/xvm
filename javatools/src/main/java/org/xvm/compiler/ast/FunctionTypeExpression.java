package org.xvm.compiler.ast;


import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * A type expression for a function. This corresponds to the "function" keyword.
 */
public class FunctionTypeExpression
        extends TypeExpression {
    // ----- constructors --------------------------------------------------------------------------

    public FunctionTypeExpression(Token function, Token conditional, List<Parameter> returnValues,
            List<TypeExpression> params, long lEndPos) {
        this.function     = function;
        this.conditional  = conditional;
        this.returnValues = returnValues;
        this.paramTypes   = params;
        this.lEndPos      = lEndPos;
    }


    // ----- accessors -----------------------------------------------------------------------------

    public boolean isConditional() {
        return conditional != null;
    }

    public List<Parameter> getReturnValues() {
        return returnValues;
    }

    public List<TypeExpression> getParamTypes() {
        return paramTypes;
    }

    @Override
    public long getStartPosition() {
        return function.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return  lEndPos;
    }

    @Override
    public <T> T forEachChild(Function<AstNode, T> visitor) {
        T result;
        for (Parameter param : returnValues) {
            if ((result = visitor.apply(param)) != null) {
                return result;
            }
        }
        for (TypeExpression param : paramTypes) {
            if ((result = visitor.apply(param)) != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    public List<AstNode> children() {
        return childList(returnValues, paramTypes);
    }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant(Context ctx, ErrorListener errs) {
        ConstantPool pool = pool();
        return pool.ensureClassTypeConstant(pool.clzFunction(), null,
                toTupleType(toTypeConstantArray(paramTypes)),
                isConditional()
                        ? toConditionalTupleType(toParamTypeConstantArray(returnValues))
                        : toTupleType(toParamTypeConstantArray(returnValues)));
    }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs) {
        List<TypeExpression> listTypes = paramTypes;
        boolean              fValid    = true;
        for (int i = 0, c = listTypes.size(); i < c; i++) {
            TypeExpression exprOld = listTypes.get(i);
            TypeExpression exprNew = (TypeExpression) exprOld.validate(ctx, null, errs);
            if (exprNew == null) {
                fValid = false;
            } else {
                if (exprNew.isDynamic()) {
                    log(errs, Severity.ERROR, Compiler.UNSUPPORTED_DYNAMIC_TYPE_PARAMS);
                    fValid = false;
                }

                if (exprNew != exprOld) {
                    listTypes.set(i, exprNew);
                }
            }
        }

        return fValid
                ? super.validate(ctx, typeRequired, errs)
                : null;
    }

    private TypeConstant toTupleType(TypeConstant[] atypes) {
        return pool().ensureTupleType(atypes);
    }

    private TypeConstant toConditionalTupleType(TypeConstant[] atypes) {
        ConstantPool   pool       = pool();
        int            cTypes     = atypes.length;
        TypeConstant[] aconstCond = new TypeConstant[cTypes+1];

        aconstCond[0] = pool.typeBoolean();
        System.arraycopy(atypes, 0, aconstCond, 1, cTypes);

        return pool.ensureParameterizedTypeConstant(pool.typeCondTuple(), aconstCond);
    }

    static TypeConstant[] toTypeConstantArray(List<TypeExpression> list) {
        int            c      = list.size();
        TypeConstant[] aconst = new TypeConstant[c];
        for (int i = 0; i < c; ++i) {
            aconst[i] = list.get(i).ensureTypeConstant();
        }
        return aconst;
    }

    private static TypeConstant[] toParamTypeConstantArray(List<Parameter> list) {
        int            c      = list.size();
        TypeConstant[] aconst = new TypeConstant[c];
        for (int i = 0; i < c; ++i) {
            aconst[i] = list.get(i).getType().ensureTypeConstant();
        }
        return aconst;
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        String retVals = returnValues.isEmpty() ? "void"
                       : returnValues.stream().map(Object::toString).collect(Collectors.joining(", "));
        return "function "
             + (isConditional() ? "conditional " : "")
             + retVals
             + " ("
             + paramTypes.stream().map(Object::toString).collect(Collectors.joining(", "))
             + ")";
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }


    // ----- fields --------------------------------------------------------------------------------

    protected Token                function;
    protected Token                conditional;
    protected List<Parameter>      returnValues;
    protected List<TypeExpression> paramTypes;
    protected long                 lEndPos;
}
