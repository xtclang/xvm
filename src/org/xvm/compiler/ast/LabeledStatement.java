package org.xvm.compiler.ast;


import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * A labeled statement represents a statement that has a label.
 */
public class LabeledStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public LabeledStatement(Token label, Statement stmt)
        {
        this.label = label;
        this.stmt  = stmt;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the label name
     */
    public String getName()
        {
        return label.getValueText();
        }

    @Override
    public long getStartPosition()
        {
        return label.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return stmt.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        // make sure no other statement has this label name
        // TODO

        // make sure no variable has this label name
        // TODO

        Statement stmtOld = stmt;
        Statement stmtNew = stmtOld.validate(ctx, errs);
        if (stmtNew == null)
            {
            return null;
            }
        else
            {
            stmt = stmtNew;
            }

        return this;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        return stmt.completes(ctx, fReachable, code, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return label.getValue() + ": " + stmt;
        }

    @Override
    public String getDumpDesc()
        {
        return label.getValue() + ":";
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token     label;
    protected Statement stmt;

    private static final Field[] CHILD_FIELDS = fieldsForNames(LabeledStatement.class, "stmt");
    }
