package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * A labeled statement represents a statement that has a label.
 */
public class LabeledStatement
        extends Statement
        implements LabelAble
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


    // ----- LabelAble implementation --------------------------------------------------------------

    @Override
    public boolean hasLabelVar(String sName)
        {
        return stmt instanceof LabelAble && ((LabelAble) stmt).hasLabelVar(sName);
        }

    @Override
    public Register getLabelVar(Context ctx, String sName)
        {
        return ((LabelAble) stmt).getLabelVar(ctx, sName);
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        // make sure no other statement or variable has this label name
        String sName = label.getValueText();
        if (ctx.getVar(sName) != null)
            {
            label.log(errs, getSource(), Severity.ERROR, Compiler.DUPLICATE_LABEL, sName);
            }

        ctx.registerVar(label, new LabelVar(), errs);

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

        ctx.unregisterVar(label);

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


    // ----- inner class: LabelVar -----------------------------------------------------------------

    public class LabelVar
            extends Register
        {
        public LabelVar()
            {
            super(pool().ensureEcstasyTypeConstant("reflect.Label"), null, Op.A_LABEL);
            }

        /**
         * Determine if the specified property of the Label type is read-able.
         *
         * @param sProp  the property name
         *
         * @return true iff the specified "property" is made available by the label for which this
         *         pseudo-register exists
         */
        public boolean isPropReadable(String sProp)
            {
            Statement stmt = LabeledStatement.this.stmt;
            return stmt instanceof LabelAble
                    && ((LabelAble) stmt).hasLabelVar(sProp);
            }

        /**
         * Mark that the specified property is being accessed.
         *
         * @param ctx    the context
         * @param sProp  the property name
         */
        public void markPropRead(Context ctx, String sProp)
            {
            assert isPropReadable(sProp);
            ((LabelAble) LabeledStatement.this.stmt).getLabelVar(ctx, sProp);
            }

        /**
         * Obtain the register that represents the specified property.
         *
         * @param sProp  the property name
         */
        public Register getPropRegister(Context ctx, String sProp)
            {
            assert isPropReadable(sProp);
            return ((LabelAble) LabeledStatement.this.stmt).getLabelVar(ctx, sProp);
            }

        @Override
        public String toString()
            {
            return LabeledStatement.this.getName() + ':';
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token     label;
    protected Statement stmt;

    private static final Field[] CHILD_FIELDS = fieldsForNames(LabeledStatement.class, "stmt");
    }