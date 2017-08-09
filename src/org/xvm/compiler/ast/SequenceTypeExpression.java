package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Constants;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;


/**
 * An sequence type expression is a type expression followed by an ellipsis.
 *
 * @author cp 2017.03.31
 */
public class SequenceTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public SequenceTypeExpression(TypeExpression type, Token tokDots)
        {
        this.type    = type;
        this.lEndPos = tokDots.getEndPosition();
        }


    // ----- accessors -----------------------------------------------------------------------------

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
    public void resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        if (getStage().ordinal() < org.xvm.compiler.Compiler.Stage.Resolved.ordinal())
            {
            // resolve the sub-type
            type.resolveNames(listRevisit, errs);
            TypeConstant constSub = type.ensureTypeConstant();

            // obtain the Array type
            ConstantPool pool       = getConstantPool();
            TypeConstant constArray = pool.ensureClassTypeConstant(
                    pool.ensureEcstasyClassConstant(Constants.X_CLASS_SEQUENCE),
                    Constants.Access.PUBLIC, constSub);

            // store off the type that is an array of the sub-type
            setTypeConstant(constArray);

            super.resolveNames(listRevisit, errs);
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append("...");

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression type;
    protected long           lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SequenceTypeExpression.class, "type");
    }
