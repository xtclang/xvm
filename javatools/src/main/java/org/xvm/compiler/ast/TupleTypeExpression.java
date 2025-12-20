package org.xvm.compiler.ast;


import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.constants.TypeConstant;


/**
 * A type expression that represents a list of type expressions.
 */
public class TupleTypeExpression
        extends TypeExpression {
    // ----- constructors --------------------------------------------------------------------------

    public TupleTypeExpression(@NotNull List<TypeExpression> params, long lStartPos, long lEndPos) {
        this.paramTypes   = Objects.requireNonNull(params);
        this.lStartPos    = lStartPos;
        this.lEndPos      = lEndPos;
    }


    // ----- accessors -----------------------------------------------------------------------------

    public List<TypeExpression> getParamTypes() {
        return paramTypes;
    }

    @Override
    public long getStartPosition() {
        return lStartPos;
    }

    @Override
    public long getEndPosition() {
        return  lEndPos;
    }

    @Override
    public <T> T forEachChild(Function<AstNode, T> visitor) {
        T result;
        for (TypeExpression param : paramTypes) {
            if ((result = visitor.apply(param)) != null) {
                return result;
            }
        }
        return null;
    }



    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant(Context ctx, ErrorListener errs) {
        return pool().ensureTupleType(FunctionTypeExpression.toTypeConstantArray(paramTypes));
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
            } else if (exprNew != exprOld) {
                listTypes.set(i, exprNew);
            }
        }

        return fValid
                ? super.validate(ctx, typeRequired, errs)
                : null;
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return "<" + paramTypes.stream().map(Object::toString).collect(Collectors.joining(", ")) + ">";
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }


    // ----- AstNode methods -----------------------------------------------------------------------

    @Override
    protected AstNode withChildren(List<AstNode> children) {
        var c = new ChildList(children);
        return new TupleTypeExpression(c.nextList(paramTypes.size()), lStartPos, lEndPos);
    }


    // ----- fields --------------------------------------------------------------------------------

    protected List<TypeExpression> paramTypes;
    protected final long           lStartPos;
    protected final long           lEndPos;
}
