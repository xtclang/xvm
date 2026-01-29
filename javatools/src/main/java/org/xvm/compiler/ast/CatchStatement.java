package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import org.xvm.asm.Assignment;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.CatchEnd;
import org.xvm.asm.op.CatchStart;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.indentLines;


/**
 * A "catch" statement. (Not actually a statement. It only occurs within a try.)
 */
public class CatchStatement
        extends Statement {
    // ----- constructors --------------------------------------------------------------------------

    public CatchStatement(VariableDeclarationStatement target, StatementBlock block, long lStartPos) {
        this.target    = target;
        this.block     = block;
        this.lStartPos = lStartPos;
    }

    /**
     * Copy constructor.
     * <p>
     * Master clone() semantics:
     * <ul>
     *   <li>CHILD_FIELDS: "target", "block" - deep copied by AstNode.clone()</li>
     *   <li>All transient fields: shallow copied via Object.clone() bitwise copy</li>
     * </ul>
     *
     * @param original  the CatchStatement to copy from
     */
    protected CatchStatement(@NotNull CatchStatement original) {
        super(Objects.requireNonNull(original));

        // Copy non-child structural fields
        this.lStartPos = original.lStartPos;

        // Deep copy child fields
        this.target = original.target == null ? null : original.target.copy();
        this.block  = original.block == null ? null : original.block.copy();

        // Adopt copied children
        adopt(this.target);
        adopt(this.block);

        // Shallow copy transient fields (matching Object.clone() semantics)
        this.m_opCatch      = original.m_opCatch;
        this.m_labelEndCatch = original.m_labelEndCatch;
    }

    @Override
    public CatchStatement copy() {
        return new CatchStatement(this);
    }


    // ----- visitor pattern -----------------------------------------------------------------------

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the exception variable declaration
     */
    public VariableDeclarationStatement getTarget() {
        return target;
    }

    /**
     * @return the catch block
     */
    public StatementBlock getBlock() {
        return block;
    }

    /**
     * @return the exception type
     */
    public TypeConstant getCatchType() {
        return target.getType();
    }

    /**
     * @return get the register used for the catch variable
     */
    public Register getCatchRegister() {
        return target.getRegister();
    }

    /**
     * @return the declared name of the catch variable
     */
    public String getCatchVariableName() {
        return target.getName();
    }

    public CatchStart ensureCatchStart() {
        CatchStart op = m_opCatch;
        if (op == null) {
            m_opCatch = op = new CatchStart(getCatchRegister(),
                    pool().ensureStringConstant(getCatchVariableName()));
        }
        return op;
    }

    public void setCatchLabel(Label label) {
        m_labelEndCatch = label;
    }

    @Override
    public long getStartPosition() {
        return lStartPos;
    }

    @Override
    public long getEndPosition() {
        return block.getEndPosition();
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs) {
        boolean fValid = true;

        ctx = ctx.enter();

        // validate the catch clause
        VariableDeclarationStatement targetNew = (VariableDeclarationStatement) target.validate(ctx, errs);
        if (targetNew == null) {
            fValid = false;
        } else {
            target = targetNew;

            // exception variable has a value at this point
            ctx.ensureDefiniteAssignments().put(target.getName(), Assignment.AssignedOnce);

            // make sure that the exception variable is of an exception type
            TypeConstant typeE = target.getType();
            if (!typeE.isA(pool().typeException())) {
                fValid = false;
                target.log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                        pool().typeException().getValueString(), typeE.getValueString());
            }

            // validate the block
            block.suppressScope();
            StatementBlock blockNew = (StatementBlock) block.validate(ctx, errs);
            if (blockNew != null) {
                block = blockNew;
            }
        }

        ctx.exit();

        return fValid ? this : null;
    }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs) {
        assert m_opCatch != null && m_labelEndCatch != null;

        code.add(m_opCatch);
        block.suppressScope();
        boolean fCompletes = block.completes(ctx, fReachable, code, errs);
        code.add(new CatchEnd(m_labelEndCatch));

        ctx.getHolder().setAst(this, ctx.getHolder().getAst(block));
        return fCompletes;
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return "catch (" + target + ")\n" + indentLines(block.toString(), "    ");
    }


    // ----- fields --------------------------------------------------------------------------------

    @ChildNode(index = 0, description = "Exception variable declaration")
    protected VariableDeclarationStatement target;
    @ChildNode(index = 1, description = "Catch body")
    protected StatementBlock               block;
    protected long                         lStartPos;

    @ComputedState("CatchStart op for code generation")
    private CatchStart m_opCatch;
    @ComputedState("End label for catch block")
    private Label      m_labelEndCatch;

    private static final Field[] CHILD_FIELDS = fieldsForNames(CatchStatement.class, "target", "block");
}