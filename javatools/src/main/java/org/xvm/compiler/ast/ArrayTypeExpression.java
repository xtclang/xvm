package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;


/**
 * An array type expression is a type expression followed by an array indicator. Because an array
 * type can be used to (e.g.) "new" an array, it also has to support actual index extents, in
 * addition to just supporting the number of dimensions.
 */
public class ArrayTypeExpression
        extends TypeExpression {
    // ----- constructors --------------------------------------------------------------------------

    public ArrayTypeExpression(TypeExpression type, int dims, long lEndPos) {
        this(type, dims, List.of(), lEndPos);
    }

    public ArrayTypeExpression(TypeExpression type, @NotNull List<Expression> indexes, long lEndPos) {
        this(type, indexes.size(), indexes, lEndPos);
    }

    private ArrayTypeExpression(TypeExpression type, int dims, @NotNull List<Expression> indexes, long lEndPos) {
        this.type    = type;
        this.dims    = dims;
        this.indexes = Objects.requireNonNull(indexes);
        this.lEndPos = lEndPos;
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the number of array dimensions, 0 or more
     */
    public int getDimensions() {
        return dims;
    }

    @Override
    protected boolean canResolveNames() {
        return super.canResolveNames() || type.canResolveNames();
    }

    @Override
    public long getStartPosition() {
        return type.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return lEndPos;
    }

    @Override
    public <T> T forEachChild(Function<AstNode, T> visitor) {
        T result;
        if ((result = visitor.apply(type)) != null) {
            return result;
        }
        for (Expression index : indexes) {
            if ((result = visitor.apply(index)) != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    protected AstNode withChildren(List<AstNode> children) {
        if (children.isEmpty()) {
            return new ArrayTypeExpression(type, dims, List.of(), lEndPos);
        }
        TypeExpression newType = (TypeExpression) children.get(0);
        List<Expression> newIndexes = new ArrayList<>();
        for (int i = 1; i < children.size(); i++) {
            newIndexes.add((Expression) children.get(i));
        }
        return new ArrayTypeExpression(newType, newIndexes, lEndPos);
    }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive, ErrorListener errs) {
        TypeConstant typeEl = type.ensureTypeConstant(ctx, errs);
        return typeEl.containsUnresolved()
                ? TypeFit.NoFit
                : super.testFit(ctx, typeRequired, fExhaustive, errs);
    }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs) {
        TypeExpression exprTypeOld = type;
        TypeExpression exprTypeNew = (TypeExpression) exprTypeOld.validate(ctx, pool().typeType(), errs);
        if (exprTypeNew == null) {
            return null;
        }
        type = exprTypeNew;

        return super.validate(ctx, typeRequired, errs);
    }

    /**
     * @return the id of the two-argument array constructor with the following signature:
     *         "construct(Int size, Element | function Element (Int) supply)"
     */
    public MethodConstant getSupplyConstructor() {
        ClassConstant  idArray  = pool().clzArray();
        ClassStructure clzArray = (ClassStructure) idArray.getComponent();

        return clzArray.findMethod("construct", 2, pool().typeInt64()).getIdentityConstant();
    }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant(Context ctx, ErrorListener errs) {
        ConstantPool pool = pool();
        return pool.ensureClassTypeConstant(pool.clzArray(), null, type.ensureTypeConstant(ctx, errs));
    }

    @Override
    protected void collectAnonInnerClassInfo(AnonInnerClass info) {
        info.addContribution(this);
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append('[');

        for (int i = 0; i < dims; ++i) {
            if (i > 0) {
                sb.append(',');
            }

            if (indexes.isEmpty()) {
                sb.append('?');
            } else {
                sb.append(indexes.get(i));
            }
        }

          sb.append(']');

        return sb.toString();
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression   type;
    protected int              dims;
    protected List<Expression> indexes;
    protected long             lEndPos;
}