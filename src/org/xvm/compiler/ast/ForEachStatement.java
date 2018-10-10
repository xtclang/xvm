package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
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

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Expression.Assignable;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.indentLines;


/**
 * An "Iterable"-based "for" statement.
 */
public class ForEachStatement
        extends Statement
        implements LabelAble
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
    public boolean isNaturalShortCircuitStatementTarget()
        {
        return true;
        }

    @Override
    public Label ensureContinueLabel(Context ctxOrigin)
        {
        Context ctxDest = getValidationContext();
        assert ctxDest != null;

        // generate a delta of assignment information for the long-jump
        Map<String, Assignment> mapAsn = ctxOrigin.prepareJump(ctxDest);

        // record the long-jump that landed on this statement by recording its assignment impact
        if (m_listContinues == null)
            {
            m_listContinues = new ArrayList<>();
            }
        m_listContinues.add(mapAsn);

        return getContinueLabel();
        }

    /**
     * @return true iff there is a continue label for this statement, which indicates that it has
     *         already been requested at least one time
     */
    public boolean hasContinueLabel()
        {
        return m_labelContinue != null;
        }

    /**
     * @return the continue label for this statement
     */
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

    /**
     * @return the type of the ElementType type parameter if the plan is not "Map"
     */
    private TypeConstant getElementType()
        {
        assert m_plan != Plan.MAP;
        assert m_exprRValue != null;

        TypeConstant type = m_exprRValue.getType().getGenericParamType("ElementType");
        return type == null ? pool().typeObject() : type;
        }

    /**
     * @return the type of the KeyType type parameter if the plan is "Map"
     */
    private TypeConstant getKeyType()
        {
        assert m_plan == Plan.MAP;
        assert m_exprRValue != null;
        assert m_exprRValue.getType().isA(pool().typeMap());

        TypeConstant type = m_exprRValue.getType().getGenericParamType("KeyType");
        return type == null ? pool().typeObject() : type;
        }

    /**
     * @return the type of the ValueType type parameter if the plan is "Map"
     */
    private TypeConstant getValueType()
        {
        assert m_plan == Plan.MAP;
        assert m_exprRValue != null;
        assert m_exprRValue.getType().isA(pool().typeMap());

        TypeConstant type = m_exprRValue.getType().getGenericParamType("ValueType");
        return type == null ? pool().typeObject() : type;
        }

    /**
     * @return the type of the Entry if the plan is "Map"
     */
    private TypeConstant getEntryType()
        {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(pool.typeEntry(), getKeyType(), getValueType());
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


    // ----- LabelAble methods ---------------------------------------------------------------------

    @Override
    public boolean hasLabelVar(String sName)
        {
        switch (sName)
            {
            case "first":
            case "count":
                return true;

            case "last":
                return m_plan == Plan.RANGE || m_plan == Plan.SEQUENCE;

            case "entry":
            case "KeyType":
            case "ValueType":
                return m_plan == Plan.MAP;

            default:
                return false;
            }
        }

    @Override
    public Register getLabelVar(String sName)
        {
        assert hasLabelVar(sName);

        Register reg;
        switch (sName)
            {
            case "first"    : reg = m_regFirst  ; break;
            case "last"     : reg = m_regLast   ; break;
            case "count"    : reg = m_regCount  ; break;
            case "entry"    : reg = m_regEntry  ; break;
            case "KeyType"  : reg = m_regKeyType; break;
            case "ValueType": reg = m_regValType; break;
            default:
                throw new IllegalStateException();
            }

        if (reg == null)
            {
            // this occurs only during validate()
            assert m_ctxLabelVars != null;

            String       sLabel = ((LabeledStatement) getParent()).getName();
            Token        tok    = new Token(keyword.getStartPosition(), keyword.getEndPosition(), Id.IDENTIFIER, sLabel + '.' + sName);
            ConstantPool pool   = pool();

            TypeConstant type;
            switch (sName)
                {
                case "first"    : type = pool.typeBoolean()      ; break;
                case "last"     : type = pool.typeBoolean()      ; break;
                case "count"    : type = pool.typeInt()          ; break;
                case "entry"    : type = getEntryType()          ; break;
                case "KeyType"  : type = getKeyType().getType()  ; break;
                case "ValueType": type = getValueType().getType(); break;
                default:
                    throw new IllegalStateException();
                }

            reg = new Register(type);
            m_ctxLabelVars.registerVar(tok, reg, m_errsLabelVars);

            switch (sName)
                {
                case "first"    : m_regFirst   = reg; break;
                case "last"     : m_regLast    = reg; break;
                case "count"    : m_regCount   = reg; break;
                case "entry"    : m_regEntry   = reg; break;
                case "KeyType"  : m_regKeyType = reg; break;
                case "ValueType": m_regValType = reg; break;
                default:
                    throw new IllegalStateException();
                }
            }

        return reg;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // the for() statement has its own scope
        ctx = ctx.enter();

        // save off the current context and errors, in case we have to lazily create some loop vars
        m_ctxLabelVars  = ctx;
        m_errsLabelVars = errs;

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
        Plan         plan      = null;
        TypeConstant typeRVal  = null;
        for (int i = Plan.ITERATOR.ordinal(); i <= Plan.ITERABLE.ordinal(); ++i)
            {
            plan = Plan.valueOf(i);
            switch (plan)
                {
                case ITERATOR: typeRVal = pool.typeIterator(); break;
                case RANGE   : typeRVal = pool.typeRange();    break;
                case SEQUENCE: typeRVal = pool.typeSequence(); break;
                case MAP     : typeRVal = pool.typeMap();      break;
                case ITERABLE: typeRVal = pool.typeIterable(); break;
                default:
                    throw new IllegalStateException();
                }

            if (exprRVal.testFit(ctx, typeRVal).isFit())
                {
                // found something that fits!
                break;
                }
            }
        // if none of the above matched, we leave it on Iterable, so that the expression will fail
        // to validate with that required type (i.e. treat Iterable as the default required type)
        m_plan = plan;

        if (fValid)
            {
            // get as specific as possible with the required type for the R-Value
            TypeConstant[] atypeLVals = exprLVal.getTypes();
            int            cLVals     = atypeLVals.length;
            int            cMaxLVals  = plan == Plan.MAP ? 2 : 1;
            if (cLVals < 1 || cLVals > cMaxLVals)
                {
                condLVal.log(errs, Severity.ERROR, Compiler.INVALID_LVALUE_COUNT, 1, cMaxLVals);
                fValid = false;
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
                typeRVal = exprRVal.getType();

                TypeConstant[] aTypeLVals;
                switch (plan)
                    {
                    default:
                    case ITERATOR:
                    case RANGE:
                    case SEQUENCE:
                    case ITERABLE:
                        aTypeLVals = new TypeConstant[] { getElementType() };
                        break;

                    case MAP:
                        aTypeLVals = new TypeConstant[] { getKeyType(), getValueType() };
                        break;
                    }

                assert aTypeLVals.length >= exprLVal.getValueCount();
                exprLVal.updateLValueFromRValueTypes(aTypeLVals);
                }
            }

        // regardless of the validity of the R-Value let's mark the L-Value as assigned
        exprLVal.markAssignment(ctx, true, errs);

        // the statement block does not need its own scope (because the for() statement is a scope)
        StatementBlock blockOld = block;
        blockOld.suppressScope();

        // while the block doesn't get its own scope, it only sees the "true" fork of the condition
        ctx = ctx.enterFork(true);

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

        List<Map<String, Assignment>> listContinues = m_listContinues;
        if (listContinues != null)
            {
            for (Map<String, Assignment> mapAsn : listContinues)
                {
                ctx.merge(mapAsn);
                }
            }

        // leaving the "true" fork of the condition
        ctx = ctx.exit();

        // leaving the scope of the for() statement
        ctx = ctx.exit();

        // lazily created loop vars are only created inside the validation of this statement
        m_ctxLabelVars  = null;
        m_errsLabelVars = null;

        return fValid
                ? this
                : null;
        }

    // TODO TODO TODO

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
        if (m_plan == T_RANGE && m_exprRValue.isConstant())
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
        switch (m_plan)
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
                Set<MethodConstant> setId    = infoAble.findMethods("iterator", 0, true, false);
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
        Set<MethodConstant> setId    = infoIter.findMethods("next", 0, true, false);
        assert setId.size() == 1;
        code.add(new Invoke_0N(regIter, setId.iterator().next(), new Argument[] {regCond, argElem}));
        code.add(new JumpFalse(regCond, getEndLabel()));

        fCompletes = block.completes(ctx, fCompletes, code, errs);

        if (hasContinueLabel())
            {
            code.add(getContinueLabel());
            }
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


    // ----- inner class: Plan ---------------------------------------------------------------------

    enum Plan
        {
        ITERATOR, RANGE, SEQUENCE, MAP, ITERABLE;

        /**
         * Look up a Plan enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Plan enum for the specified ordinal
         */
        public static Plan valueOf(int i)
            {
            return BY_ORDINAL[i];
            }

        /**
         * All of the Plan enums, by ordinal.
         */
        private static final Plan[] BY_ORDINAL = Plan.values();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token               keyword;
    protected AssignmentStatement cond;
    protected StatementBlock      block;

    private static    int           s_nLabelCounter;
    private transient int           m_nLabel;
    private transient Label         m_labelContinue;

    private transient Expression    m_exprLValue;
    private transient Expression    m_exprRValue;
    private transient Plan          m_plan;

    private transient Context       m_ctxLabelVars;
    private transient ErrorListener m_errsLabelVars;
    private transient Register      m_regFirst;
    private transient Register      m_regLast;
    private transient Register      m_regCount;
    private transient Register      m_regEntry;
    private transient Register      m_regKeyType;
    private transient Register      m_regValType;

    /**
     * Generally null, unless there is a "continue" that long-jumps to this statement.
     */
    private transient List<Map<String, Assignment>> m_listContinues;

    private static final Field[] CHILD_FIELDS =
            fieldsForNames(ForEachStatement.class, "cond", "block");
    }
