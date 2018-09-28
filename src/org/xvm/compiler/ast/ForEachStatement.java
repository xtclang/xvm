package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.IntervalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.IP_Dec;
import org.xvm.asm.op.IP_Inc;
import org.xvm.asm.op.Invoke_01;
import org.xvm.asm.op.Invoke_0N;
import org.xvm.asm.op.IsEq;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpFalse;
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
        ctx = ctx.enter();

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

        // validate the LValue declarations and any LValue sub-expressions
        AstNode condLVal = cond.getLValue();
        if (condLVal instanceof Statement)
            {
            Statement condOld = (Statement) condLVal;
            Statement condNew = condOld.validate(ctx, errs);
            if (condNew == null)
                {
                fValid = false;
                }
            else
                {
                condLVal = condNew;
                }
            }
        else
            {
            Expression condOld = (Expression) condLVal;
            Expression condNew = condOld.validate(ctx, null, errs);
            if (condNew == null)
                {
                fValid = false;
                }
            else
                {
                condLVal = condNew;
                }
            }
        Expression exprLVal = condLVal.getLValueExpression();
        if (!exprLVal.isValidated())
            {
            Expression exprNew = exprLVal.validate(ctx, null, errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else
                {
                exprLVal = exprNew;
                }
            }
        m_exprLValue = exprLVal;
        exprLVal.requireAssignable(ctx, errs);

        // figure out which category the R-Value should be
        Expression   exprRVal  = cond.getRValue();
        ConstantPool pool      = pool();
        int          nTypeRval = 0;
        TypeConstant typeRVal  = null;
        do
            {
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

                // TODO need to figure out if L.entry is ever referenced (also need the same for first, last, count)
                m_fUsesEntry = cLVals == 2;
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
                typeRVal = exprRVal.getType();

                TypeConstant[] aTypeLVals;
                switch (nTypeRval)
                    {
                    default:
                    case T_ITERATOR:
                    case T_RANGE:
                    case T_SEQUENCE:
                    case T_ITERABLE:
                        aTypeLVals = new TypeConstant[]
                            {
                            typeRVal.getGenericParamType("ElementType")
                            };
                        break;

                    case T_MAP:
                        aTypeLVals = new TypeConstant[]
                            {
                            typeRVal.getGenericParamType("KeyType"),
                            typeRVal.getGenericParamType("ValueType")
                            };
                        break;
                    }

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
        ctx = ctx.exit();

        return fValid
                ? this
                : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable;

        code.add(new Enter());

        // strip any declarations off of the LValues (we'll handle them separately)
        for (VariableDeclarationStatement stmt : cond.takeDeclarations())
            {
            fCompletes = stmt.completes(ctx, fCompletes, code, errs);
            }

        // code simplification for intrinsic sequential types
        boolean fEmitted = false;
        if (m_nPlan == T_RANGE && m_exprRValue.isConstant())
            {
            switch (m_exprRValue.getType().getParamTypesArray()[0].getEcstasyClassName())
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
                    fCompletes = emitConstantRangeLoop(ctx, fReachable, code, errs);
                    fEmitted   = true;
                    break;
                }
            }

        if (!fEmitted)
            {
            fCompletes = m_fUsesEntry
                    ? emitMapKVIterator(ctx, fReachable, code, errs)
                    : emitGenericIterator(ctx, fReachable, code, errs);
            }

        code.add(new Exit());

        return fCompletes;
        }

    private boolean emitGenericIterator(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean      fCompletes = fReachable;

        ConstantPool pool       = pool();
        Expression   exprLVal   = m_exprLValue;
        Expression   exprRVal   = m_exprRValue;
        String       sLabel     = getParent() instanceof LabeledStatement ?
                                  ((LabeledStatement) getParent()).getName() : null;

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

        Register   regIter  = null;
        Assignable lvalElem = null;
        switch (m_nPlan)
            {
            case T_ITERATOR:
                {
                // the type of the iterator is Iterator<LValType>
                TypeConstant typeElem = exprLVal.getType();
                TypeConstant typeIter = pool.ensureParameterizedTypeConstant(pool.typeIterator(), typeElem);
                code.add(new Var(typeIter));
                regIter = code.lastRegister();
                exprRVal.generateAssignment(ctx, code, exprRVal.new Assignable(regIter), errs);
                lvalElem = exprLVal.generateAssignable(ctx, code, errs);
                break;
                }

            case T_RANGE:
                // TODO .iterator()
                notImplemented();
                break;

            case T_SEQUENCE:
                // TODO .iterator()
//                notImplemented();
//                break;
//
            case T_ITERABLE:
                {
                // the type on the right is Iterable<LValType> (or can be assigned to it)
                // the type of the iterator is Iterator<LValType>
                TypeConstant typeElem = exprLVal.getType();
                TypeConstant typeIter = pool.ensureParameterizedTypeConstant(pool.typeIterator(), typeElem);
                code.add(new Var(typeIter));
                regIter = code.lastRegister();
                Argument argAble = exprRVal.generateArgument(ctx, code, true, true, errs);

                TypeInfo            infoAble = pool.typeIterable().ensureTypeInfo(errs);
                Set<MethodConstant> setId    = infoAble.findMethods("iterator", 0);
                assert setId.size() == 1;
                code.add(new Invoke_01(argAble, setId.iterator().next(), regIter));

                lvalElem = exprLVal.generateAssignable(ctx, code, errs);
                break;
                }

            case T_MAP:
                // the type of the iterator is Iterator<LValType> (key iterator)
                assert !m_fUsesEntry;
                // TODO Map.keys.iterator()
                notImplemented();
                break;

            default:
                throw new IllegalStateException();
            }

        code.add(new Var(pool.typeBoolean()));
        Register regCond = code.lastRegister();

        Register regFirst = null;
        Register regCount = null;
        if (sLabel != null)
            {
            code.add(new Var_IN(pool.typeBoolean(), pool.ensureStringConstant(sLabel + ".first"), pool.valTrue()));
            regFirst = code.lastRegister();

            code.add(new Var_IN(pool.typeInt(), pool.ensureStringConstant(sLabel + ".count"), pool.val0()));
            regCount = code.lastRegister();
            }

        Label labelRepeat = new Label("repeat_foreach_" + getLabelId());
        code.add(labelRepeat);

        boolean  fElemTemp = !lvalElem.isLocalArgument();
        Argument argElem   = fElemTemp
                ? exprLVal.createTempVar(code, lvalElem.getType(), true, errs).getLocalArgument()
                : lvalElem.getLocalArgument();

        TypeInfo            infoIter = pool.typeIterator().ensureTypeInfo(errs);
        Set<MethodConstant> setId    = infoIter.findMethods("next", 0);
        assert setId.size() == 1;
        code.add(new Invoke_0N(regIter, setId.iterator().next(), new Argument[] {regCond, argElem}));
        code.add(new JumpFalse(regCond, getEndLabel()));

        fCompletes = block.completes(ctx, fCompletes, code, errs);
        code.add(getContinueLabel());

        if (regFirst != null)
            {
            code.add(new Move(pool.valFalse(), regFirst));
            }
        if (regCount != null)
            {
            code.add(new IP_Inc(regCount));
            }
        code.add(new Jump(labelRepeat));

        return fCompletes;
        }

    private boolean emitMapKVIterator(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        // TODO
        throw notImplemented();
        }

    private boolean emitConstantRangeLoop(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean      fCompletes = fReachable;

        ConstantPool pool       = pool();
        Expression   exprLVal   = m_exprLValue;
        Expression   exprRVal   = m_exprRValue;
        TypeConstant typeSeq    = exprRVal.getType().getParamTypesArray()[0];

        LabeledStatement stmtLabel = getParent() instanceof LabeledStatement
                ? (LabeledStatement) getParent()
                : null;

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
        if (stmtLabel == null)
            {
            code.add(new Var(pool.typeBoolean()));
            regLast = code.lastRegister();
            }
        else
            {
            // TODO get rid of any of the following unused registers
            code.add(new Var_IN(pool.typeBoolean(), pool.ensureStringConstant(stmtLabel.getName() + ".first"), pool.valTrue()));
            regFirst = code.lastRegister();

            code.add(new Var_N(pool.typeBoolean(), pool.ensureStringConstant(stmtLabel.getName() + ".last")));
            regLast = code.lastRegister();

            code.add(new Var_N(pool.typeInt(), pool.ensureStringConstant(stmtLabel.getName() + ".count")));
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
    private transient boolean    m_fUsesEntry; // TODO also need L.first, L.last, L.count

    private static final Field[] CHILD_FIELDS = fieldsForNames(ForEachStatement.class, "cond", "block");
    }
