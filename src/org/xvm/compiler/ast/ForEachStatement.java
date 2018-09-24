package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.IntervalConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.IP_Dec;
import org.xvm.asm.op.IP_Inc;
import org.xvm.asm.op.IsEq;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Move;
import org.xvm.asm.op.Var;
import org.xvm.asm.op.Var_I;
import org.xvm.asm.op.Var_IN;
import org.xvm.asm.op.Var_N;

import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Expression.Assignable;

import static org.xvm.util.Handy.indentLines;


/**
 * An "Iterable"-based "for" statement.
 */
public class ForEachStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ForEachStatement(Token keyword, AssignmentStatement cond, StatementBlock block)
        {
        this.keyword = keyword;
        this.cond    = cond;
        this.block   = block;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean canBreak()
        {
        return true;
        }

    @Override
    public boolean canContinue()
        {
        return true;
        }

    @Override
    public Label getContinueLabel()
        {
        Label label = m_labelContinue;
        if (label == null)
            {
            m_labelContinue = label = new Label("continue_foreach_" + getLabelId());
            }
        return label;
        }

    private int getLabelId()
        {
        int n = m_nLabel;
        if (n == 0)
            {
            m_nLabel = n = ++s_nLabelCounter;
            }
        return n;
        }

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return block.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // the for() statement has its own scope
        ctx = ctx.enterScope();

        // ultimately, the condition has to be re-written, because it is inevitably short-hand for
        // a measure of syntactic sugar; in order of precedence, the condition can be:
        //
        //   1) iterator   :  L: for (T value : container.as(Iterator<T>)) {...}
        //   2) range      :  L: for (T value : container.as(Range<T>)) {...}
        //   3) sequence   :  L: for (T value : container.as(Sequence<T>)) {...}
        //   4) map keys   :  L: for (K key : container.as(Map<K,T>)) {...}
        //   5) map entries:  L: for ((K key, T value) : container.as(Map<K,T>)) {...}
        //   6) iterable   :  L: for (T value : container.as(Iterator<T>)) {...}
        assert cond.isConditional();

        // validate the LValue expression
        Expression exprLVal    = cond.getLValue().getLValueExpression();
        Expression exprLValNew = exprLVal.validate(ctx, null, errs);
        if (exprLValNew == null)
            {
            fValid = false;
            }
        else
            {
            m_exprLValue = exprLVal = exprLValNew;
            }

        // figure out which category the R-Value should be
        Expression   exprRVal  = cond.getRValue();
        ConstantPool pool      = pool();
        int          nTypeRval = 0;
        TypeConstant typeRVal  = null;
        do
            {
            TypeConstant typeTest;
            switch (++nTypeRval)
                {
                default:
                case T_ITERATOR: typeRVal = pool.typeIterator(); break;
                case T_RANGE   : typeRVal = pool.typeRange();    break;
                case T_SEQUENCE: typeRVal = pool.typeSequence(); break;
                case T_MAP     : typeRVal = pool.typeMap();      break;
                case T_ITERABLE: typeRVal = pool.typeIterable(); break;
                }
            }
        while (!exprRVal.testFit(ctx, typeRVal).isFit() && nTypeRval < T_ITERABLE);
        // if none of the above matched, we leave it on Iterable, so that the expression will fail
        // to validate with that required type (i.e. treat Iterable as the default required type)
        m_nPlan = nTypeRval;

        if (fValid)
            {
            // get as specific as possible with the required type for the R-Value
            TypeConstant[] atypeLVals = exprLVal.getTypes();
            int            cLVals     = atypeLVals.length;
            int            cMaxLVals  = nTypeRval == T_MAP ? 2 : 1;
            if (cLVals > cMaxLVals)
                {
                // TODO log error
                throw new IllegalStateException();
                }
            else if (cLVals < 1)
                {
                // TODO log error
                throw new IllegalStateException();
                }
            else
                {
                typeRVal = pool.ensureParameterizedTypeConstant(typeRVal, atypeLVals);
                }
            }

        Expression exprRValNew = exprRVal.validate(ctx, typeRVal, errs);
        if (exprRValNew == null)
            {
            fValid = false;
            }
        else
            {
            m_exprRValue = exprRVal = exprRValNew;

            if (fValid)
                {
                // update "var"/"val" type declarations on the LValues with their actual types
                TypeConstant[] aTypeLVals = exprRVal.getType().getParamTypesArray();
                assert aTypeLVals.length >= exprLVal.getValueCount();
                exprLVal.updateLValueFromRValueTypes(aTypeLVals);
                }
            }

        // the statement block does not need its own scope (because the for() statement is a scope)
        StatementBlock blockOld = block;
        blockOld.suppressScope();
        StatementBlock blockNew = (StatementBlock) blockOld.validate(ctx, errs);
        if (blockNew != blockOld)
            {
            if (blockNew == null)
                {
                fValid = false;
                }
            else
                {
                block = blockNew;
                }
            }

        // leaving the scope of the for() statement
        ctx = ctx.exitScope();

        return fValid
                ? this
                : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean      fCompletes = fReachable;

        ConstantPool pool       = pool();
        Expression   exprLVal   = m_exprLValue;
        Expression   exprRVal   = m_exprRValue;
        int          nPlan      = m_nPlan;

        code.add(new Enter());

        // strip any declarations off of the LValues (we'll handle them separately)
        VariableDeclarationStatement[] aDecls = cond.takeDeclarations();
        for (VariableDeclarationStatement stmt : aDecls)
            {
            fCompletes = stmt.completes(ctx, fCompletes, code, errs);
            }

        LabeledStatement label = getParent() instanceof LabeledStatement
                ? (LabeledStatement) getParent()
                : null;

        // code simplification for intrinsic sequential types
        TypeConstant typeSeq = null;
        if (nPlan == T_RANGE && exprRVal.isConstant())
            {
            typeSeq = exprRVal.getType().getParamTypesArray()[0];
            switch (typeSeq.getEcstasyClassName())
                {
                // TODO
                // case "Bit":
                // case "Nibble":
                case "Char":
                case "Int8":
                case "Int16":
                case "Int32":
                case "Int64":
                case "Int128":
                case "VarInt":
                case "UInt8":
                case "UInt16":
                case "UInt32":
                case "UInt64":
                case "UInt128":
                case "VarUInt":
                    break;

                default:
                    typeSeq = null;
                    break;
                }
            }

        if (typeSeq == null)
            {
            // TODO
            // nothing / .iterator() / Map.keys.iterator() / Map.entries.iterator()

            //      VAR_I  #0 Iterator<T> ...       ; hidden variable that holds the Iterator
            //      VAR    #1 Boolean               ; hidden variable that holds the conditional result
            //      VAR_N  #2 T value;              ; the value
            //      VAR_IN #3 Boolean L.first True  ; optional label variable
            //      VAR_IN #4 Int L.count 0         ; optional label variable
            //      Repeat:
            //      Iterator.next() #0 -> #1 #2     ; assign the conditional result and the value
            //      JMP_F #1 Exit                   ; exit when the conditional result is false
            //      {...}
            //      Continue:
            //      MOV False #3                    ; no longer the L.first
            //      IP_INC #4                       ; increment the L.count
            //      JMP Repeat                      ; loop
            //      Exit:


            // TODO decl goes here

            fCompletes = decl.completes(ctx, fCompletes, code, errs);

            Label labelRepeat = new Label("loop_for_" + getLabelId());
            code.add(labelRepeat);

            fCompletes = block.completes(ctx, fCompletes, code, errs);

            code.add(getContinueLabel());

            List<Statement> listUpdate = update;
            int             cUpdate    = listUpdate.size();
            for (int i = 0; i < cUpdate; ++i)
                {
                fCompletes = listUpdate.get(i).completes(ctx, fCompletes, code, errs);
                }

            code.add(new Jump(labelRepeat));
            }
        else
            {
            // VAR_I #0 T _start_;             ; initialize the current value to the Range "start"
            // VAR_IN #1 Boolean L.first True  ; (optional) label variable
            // VAR(_N) #2 Boolean (L.last)     ; (optionally named if a label exists)
            // VAR_IN #3 Int L.count 0         ; (optional) label variable
            // Repeat:
            // IS_EQ #0 _end_ -> #2            ; compare current value to last value
            // {...}
            // Continue:
            // JMP_T #2 Exit                   ; exit after the L.last
            // IP_INC/IP_DEC #0                ; increment (or decrement) the current value
            // MOV False #1                    ; (optional) no longer the L.first
            // IP_INC #3                       ; (optional) increment the L.count
            // JMP Repeat                      ; loop
            // Exit:
            IntervalConstant range  = (IntervalConstant) exprRVal.toConstant();
            Assignable       asnV   = exprLVal.generateAssignable(ctx, code, errs);
            boolean          fTempV = !asnV.isNormalVariable();
            Register         regV;
            if (fTempV)
                {
                code.add(new Var_I(typeSeq, range.getFirst()));
                regV = code.lastRegister();
                }
            else
                {
                asnV.assign(range.getFirst(), code, errs);
                regV = asnV.getRegister();
                }

            Register regFirst = null;
            Register regLast  = null;
            Register regCount = null;
            if (label == null)
                {
                code.add(new Var(pool.typeBoolean()));
                regLast = code.lastRegister();
                }
            else
                {
                // TODO get rid of any of the following unused registers
                code.add(new Var_IN(pool.typeBoolean(), pool.ensureStringConstant(label.getName() + ".first"), pool.valTrue()));
                regFirst = code.lastRegister();

                code.add(new Var_N(pool.typeBoolean(), pool.ensureStringConstant(label.getName() + ".last")));
                regLast = code.lastRegister();

                code.add(new Var_N(pool.typeInt(), pool.ensureStringConstant(label.getName() + ".count")));
                regCount = code.lastRegister();
                }

            Label lblRepeat = new Label("repeat_foreach_" + getLabelId());
            code.add(lblRepeat);

            code.add(new IsEq(regV, range.getLast(), regLast));
            fCompletes = block.completes(ctx, fCompletes, code, errs);
            code.add(getContinueLabel());
            code.add(new JumpTrue(regLast, getEndLabel()));
            code.add(range.isReverse() ? new IP_Dec(regV) : new IP_Inc(regV));
            if (regFirst != null)
                {
                code.add(new Move(pool.valFalse(), regFirst));
                }
            if (regCount != null)
                {
                code.add(new IP_Inc(regCount));
                }
            code.add(new Jump(lblRepeat));
            }

        code.add(new Exit());

        return fCompletes;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT)
          .append(" (")
          .append(cond)
          .append(")\n")
          .append(indentLines(block.toString(), "    "));

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    public static final int T_ITERATOR = 1;
    public static final int T_RANGE    = 2;
    public static final int T_SEQUENCE = 3;
    public static final int T_MAP      = 4;
    public static final int T_ITERABLE = 5;

    protected Token               keyword;
    protected AssignmentStatement cond;
    protected StatementBlock      block;

    private static    int   s_nLabelCounter;
    private transient int   m_nLabel;
    private transient Label m_labelContinue;

    private transient Expression m_exprLValue;
    private transient Expression m_exprRValue;
    private transient int        m_nPlan;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ForEachStatement.class, "cond", "block");
    }
