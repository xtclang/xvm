package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.ast.ConstantExprAST;
import org.xvm.asm.ast.LanguageAST.ExprAST;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;

import org.xvm.util.Severity;


/**
 * An array type expression is a type expression followed by an array indicator. Because an array
 * type can be used to (e.g.) "new" an array, it also has to support actual index extents, in
 * addition to just supporting the number of dimensions.
 */
public class ArrayTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ArrayTypeExpression(TypeExpression type, int dims, long lEndPos)
        {
        this(type, dims, null, lEndPos);
        }

    public ArrayTypeExpression(TypeExpression type, List<Expression> indexes, long lEndPos)
        {
        this(type, indexes.size(), indexes, lEndPos);
        }

    private ArrayTypeExpression(TypeExpression type, int dims, List<Expression> indexes, long lEndPos)
        {
        this.type    = type;
        this.dims    = dims;
        this.indexes = indexes;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the number of array dimensions, 0 or more
     */
    public int getDimensions()
        {
        return dims;
        }

    @Override
    protected boolean canResolveNames()
        {
        return super.canResolveNames() || type.canResolveNames();
        }

    @Override
    public long getStartPosition()
        {
        return type.getStartPosition();
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


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive, ErrorListener errs)
        {
        TypeConstant typeEl = type.ensureTypeConstant(ctx, errs);
        return typeEl.containsUnresolved()
                ? TypeFit.NoFit
                : super.testFit(ctx, typeRequired, fExhaustive, errs);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeExpression exprTypeOld = type;
        TypeExpression exprTypeNew = (TypeExpression) exprTypeOld.validate(ctx, pool().typeType(), errs);
        if (exprTypeNew == null)
            {
            return null;
            }
        type = exprTypeNew;

        ArrayTypeExpression exprNew = (ArrayTypeExpression) super.validate(ctx, typeRequired, errs);
        if (exprNew == null || dims <= 1)
            {
            return exprNew;
            }

        // array[c1, c2, ...] is a fixed size matrix and is allowed only for types with default values
        TypeConstant typeArray   = exprNew.ensureTypeConstant(ctx, errs);
        TypeConstant typeElement = typeArray.getParamType(0);

        if (typeElement.getDefaultValue() == null)
            {
            log(errs, Severity.ERROR, Compiler.NO_DEFAULT_VALUE, typeElement.getValueString());
            return null;
            }
        return exprNew;
        }

    /**
     * @return the id of the two-argument array constructor with the following signature:
     *         "construct(Int size, Element | function Element (Int) supply)"
     */
    public MethodConstant getSupplyConstructor()
        {
        ClassConstant  idArray  = pool().clzArray();
        ClassStructure clzArray = (ClassStructure) idArray.getComponent();

        return clzArray.findMethod("construct", 2, pool().typeInt64()).getIdentityConstant();
        }

    @Override
    public ExprAST<Constant> getExprAST()
        {
        if (indexes == null && dims == 0)
            {
            TypeConstant typeArray = pool().ensureArrayType(getType());
            return new ConstantExprAST<>(typeArray, typeArray);
            }
        throw notImplemented(); // MatrixTypeExprAST
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant(Context ctx, ErrorListener errs)
        {
        ConstantPool pool = pool();
        return pool.ensureClassTypeConstant(pool.clzArray(), null, type.ensureTypeConstant(ctx, errs));
        }

    @Override
    protected void collectAnonInnerClassInfo(AnonInnerClass info)
        {
        info.addContribution(this);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append('[');

        for (int i = 0; i < dims; ++i)
            {
            if (i > 0)
                {
                sb.append(',');
                }

            if (indexes == null)
                {
                sb.append('?');
                }
            else
                {
                sb.append(indexes.get(i));
                }
            }

          sb.append(']');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression   type;
    protected int              dims;
    protected List<Expression> indexes;
    protected long             lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ArrayTypeExpression.class, "type", "indexes");
    }