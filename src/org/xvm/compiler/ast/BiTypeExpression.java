package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;


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


    // ----- inner class compilation support -------------------------------------------------------

    @Override
    public Format getInnerClassFormat()
        {
        Format fmt1 = type1.getInnerClassFormat();
        Format fmt2 = type2.getInnerClassFormat();
        if (fmt1 == null || fmt2 == null)
            {
            return null;
            }

        switch (operator.getId())
            {
            case ADD:
                switch (fmt1.toString() + operator.getId().TEXT + fmt2.toString())
                    {
                    case "CLASS+INTERFACE":
                    case "INTERFACE+CLASS":
                    case "CLASS+MIXIN":
                    case "MIXIN+CLASS":
                    case "INTERFACE+MIXIN":
                    case "MIXIN+INTERFACE":
                        return Format.CLASS;

                    case "CONST+MIXIN":
                    case "MIXIN+CONST":
                    case "CONST+INTERFACE":
                    case "INTERFACE+CONST":
                        return Format.CONST;

                    case "SERVICE+MIXIN":
                    case "MIXIN+SERVICE":
                    case "SERVICE+INTERFACE":
                    case "INTERFACE+SERVICE":
                        return Format.SERVICE;

                    case "MIXIN+MIXIN":
                        return Format.MIXIN;

                    case "INTERFACE+INTERFACE":
                        return Format.INTERFACE;

                    default:
                        return null;
                    }

            case BIT_OR:
                return Format.INTERFACE;

            case SUB:
                return Format.INTERFACE;

            default:
                throw new IllegalStateException("unsupported operator: " + operator);
            }
        }

    @Override
    public String getInnerClassName()
        {
        String sName1 = type1.getInnerClassName();
        String sName2 = type2.getInnerClassName();
        if (sName1 == null || sName2 == null)
            {
            return null;
            }

        if (operator.getId() == Id.ADD)
            {
            Format fmt1 = type1.getInnerClassFormat();
            Format fmt2 = type2.getInnerClassFormat();
            if (fmt1 == null || fmt2 == null)
                {
                return null;
                }

            switch (fmt1.toString() + operator.getId().TEXT + fmt2.toString())
                {
                case "CLASS+INTERFACE":
                case "CLASS+MIXIN":
                case "MIXIN+INTERFACE":
                case "CONST+MIXIN":
                case "CONST+INTERFACE":
                case "SERVICE+MIXIN":
                case "SERVICE+INTERFACE":
                    return sName1;

                case "MIXIN+CLASS":
                case "INTERFACE+CLASS":
                case "INTERFACE+MIXIN":
                case "MIXIN+CONST":
                case "INTERFACE+CONST":
                case "MIXIN+SERVICE":
                case "INTERFACE+SERVICE":
                    return sName2;

                case "MIXIN+MIXIN":
                case "INTERFACE+INTERFACE":
                    break;

                default:
                    return null;
                }
            }

        return sName1 + operator.getId().TEXT + sName2;
        }

    @Override
    public TypeExpression collectContributions(List<Annotation> listAnnos,
            List<Contribution> listContribs)
        {
        return this;
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
