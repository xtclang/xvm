package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;
import java.util.stream.Collectors;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.constants.TypeConstant;


/**
 * A type expression that represents a list of type expressions.
 */
public class TupleTypeExpression
        extends TypeExpression {
    // ----- constructors --------------------------------------------------------------------------

    public TupleTypeExpression(List<TypeExpression> params, long lStartPos, long lEndPos) {
        this.paramTypes   = params;
        this.lStartPos    = lStartPos;
        this.lEndPos      = lEndPos;
    }

    /**
     * Copy constructor.
     */
    protected TupleTypeExpression(TupleTypeExpression original) {
        super(original);

        this.lStartPos = original.lStartPos;
        this.lEndPos   = original.lEndPos;

        // Deep copy children
        this.paramTypes = copyNodes(original.paramTypes);
        adopt(this.paramTypes);
    }

    @Override
    public TupleTypeExpression copy() {
        return new TupleTypeExpression(this);
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
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
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
        return paramTypes.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ", "<", ">"));
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }


    // ----- fields --------------------------------------------------------------------------------

    protected List<TypeExpression> paramTypes;
    protected long                 lStartPos;
    protected long                 lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TupleTypeExpression.class, "paramTypes");
}
