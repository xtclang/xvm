package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Token;


/**
 * An bi type expression is a type expression composed of two type expressions. For example, union
 * or intersection types.
 */
public class BiTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public BiTypeExpression(TypeExpression type1, Token operator, TypeExpression type2)
        {
        this.type1    = type1;
        this.operator = operator;
        this.type2    = type2;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected boolean canResolveNames()
        {
        return super.canResolveNames() ||
                (type1.canResolveNames() && type2.canResolveNames());
        }

    @Override
    public long getStartPosition()
        {
        return type1.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return type2.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant()
        {
        TypeConstant constType1 = type1.ensureTypeConstant();
        TypeConstant constType2 = type2.ensureTypeConstant();

        final ConstantPool pool = pool();
        switch (operator.getId())
            {
            case ADD:
                return pool.ensureUnionTypeConstant(constType1, constType2);

            case BIT_OR:
                return pool.ensureIntersectionTypeConstant(constType1, constType2);

            case SUB:
                return pool.ensureDifferenceTypeConstant(constType1, constType2);

            default:
                throw new IllegalStateException("unsupported operator: " + operator);
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type1)
          .append(' ')
          .append(operator.getId().TEXT)
          .append(' ')
          .append(type2);

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression type1;
    protected Token          operator;
    protected TypeExpression type2;

    private static final Field[] CHILD_FIELDS = fieldsForNames(BiTypeExpression.class, "type1", "type2");
    }
