package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Token;


/**
 * An sequence type expression is a type expression followed by an ellipsis.
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


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant()
        {
        // build an Array type
        ConstantPool pool = pool();
        return pool.ensureClassTypeConstant(pool.clzSequence(), null, type.ensureTypeConstant());
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public AstNode resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        if (!alreadyReached(Stage.Resolved))
            {
            setStage(Stage.Resolving);

            // resolve the sub-type
            type.resolveNames(listRevisit, errs);

            // store off the type that is an array of the sub-type
            ensureTypeConstant();
            }

        return super.resolveNames(listRevisit, errs);
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
