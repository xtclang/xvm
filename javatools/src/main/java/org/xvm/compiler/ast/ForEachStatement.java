package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.ast.BinaryAST;
import org.xvm.asm.ast.BinaryAST.NodeType;
import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.ForEachStmtAST;
import org.xvm.asm.ast.RegAllocAST;
import org.xvm.asm.ast.StmtBlockAST;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.RangeConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodKind;

import org.xvm.asm.op.*;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Context.Branch;
import org.xvm.compiler.ast.Expression.Assignable;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.indentLines;


/**
 * An "Iterable"-based "for" statement.
 */
public class ForEachStatement
        extends ConditionalStatement
        implements LabelAble
    {
    // ----- constructors --------------------------------------------------------------------------

    public ForEachStatement(Token keyword, AssignmentStatement cond, StatementBlock block)
        {
        super(keyword, Collections.singletonList(cond));
        this.block = block;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public AssignmentStatement getCondition()
        {
        return (AssignmentStatement) conds.getFirst();
        }

    @Override
    public boolean isNaturalGotoStatementTarget()
        {
        return true;
        }

    @Override
    public Label ensureContinueLabel(AstNode nodeOrigin, Context ctxOrigin)
        {
        Context ctxDest = ensureValidationContext();

        // generate a delta of assignment information for the jump
        Map<String, Assignment> mapAsn = new HashMap<>();
        Map<String, Argument>   mapArg = new HashMap<>();

        ctxOrigin.prepareJump(ctxDest, mapAsn, mapArg);

        // record the jump that landed on this statement by recording its assignment impact
        if (m_listContinues == null)
            {
            m_listContinues = new ArrayList<>();
            }

        Label label = getContinueLabel();
        m_listContinues.add(new Break(this, mapAsn, mapArg, label));
        return label;
        }

    /**
     * @return true iff there is a "continue" label for this statement, which indicates that it has
     *         already been requested at least one time
     */
    public boolean hasContinueLabel()
        {
        return m_labelContinue != null;
        }

    /**
     * @return the "continue" label for this statement
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

    /**
     * @return the type of the Element type parameter if the plan is not "Map"
     */
    private TypeConstant getElementType()
        {
        assert m_plan != Plan.MAP;
        assert m_exprRValue != null;

        TypeConstant type = m_exprRValue.getType().resolveGenericType("Element");
        return type == null ? pool().typeObject() : type;
        }

    /**
     * @return the type of the Key type parameter if the plan is "Map"
     */
    private TypeConstant getKeyType()
        {
        return getFormalMapType("Key");
        }

    /**
     * @return the type of the Value type parameter if the plan is "Map"
     */
    private TypeConstant getValueType()
        {
        return getFormalMapType("Value");
        }

    /**
     * @return the type of the Value type parameter if the plan is "Map"
     */
    private TypeConstant getFormalMapType(String sProp)
        {
        assert m_plan == Plan.MAP;
        assert m_exprRValue != null;

        TypeConstant typeMap = m_exprRValue.getType();

        assert typeMap.isA(pool().typeMap());

        if (typeMap.isFormalType())
            {
            typeMap = ((FormalConstant) typeMap.getDefiningConstant()).getConstraintType();
            assert typeMap.isA(pool().typeMap());
            }

        TypeConstant type = typeMap.resolveGenericType(sProp);
        return type == null ? pool().typeObject() : type;
        }

    /**
     * @return the type of the Entry if the plan is "Map"
     */
    private TypeConstant getEntryType()
        {
        ConstantPool  pool    = pool();
        ClassConstant idEntry = pool.ensureEcstasyClassConstant("maps.Map.Entry");
        return pool.ensureClassTypeConstant(idEntry, null, getKeyType(), getValueType());
        }

    /**
     * @return true iff there is a label this "for" statement
     */
    private boolean isLabeled()
        {
        return getParent() instanceof LabeledStatement;
        }

    /**
     * @return the name of the label that labels this "for" statement
     */
    private String getLabelName()
        {
        return ((LabeledStatement) getParent()).getName();
        }

    /**
     * @return the StringConstant for the passed string
     */
    private StringConstant toConst(String s)
        {
        return pool().ensureStringConstant(s);
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
        return switch (sName)
            {
            case "first", "count"        -> true;
            case "last"                  -> m_plan == Plan.RANGE || m_plan == Plan.LIST;
            case "entry", "Key", "Value" -> m_plan == Plan.MAP;
            default                      -> false;
            };
        }

    @Override
    public Register getLabelVar(Context ctx, String sName)
        {
        assert isLabeled();
        assert hasLabelVar(sName);

        Register reg = switch (sName)
            {
            case "first" -> m_regFirst;
            case "last"  -> m_regLast;
            case "count" -> m_regCount;
            case "entry" -> m_regEntry;
            case "Key"   -> m_regKeyType;
            case "Value" -> m_regValType;
            default      -> throw new IllegalStateException();
            };

        if (reg == null)
            {
            // this occurs only during validate()
            assert m_ctxLabelVars != null;

            String       sLabel = ((LabeledStatement) getParent()).getName();
            Token        tok    = new Token(keyword.getStartPosition(), keyword.getEndPosition(), Id.IDENTIFIER, sLabel + '.' + sName);
            ConstantPool pool   = pool();

            TypeConstant type = switch (sName)
                {
                case "first", "last" -> pool.typeBoolean();
                case "count"         -> pool.typeInt64();
                case "entry"         -> getEntryType();
                case "Key"           -> getKeyType().getType();
                case "Value"         -> getValueType().getType();
                default              -> throw new IllegalStateException();
                };

            reg = ctx.createRegister(type, getLabelName() + '.' + sName);
            m_ctxLabelVars.registerVar(tok, reg, m_errsLabelVars);

            switch (sName)
                {
                case "first": m_regFirst   = reg; break;
                case "last" : m_regLast    = reg; break;
                case "count": m_regCount   = reg; break;
                case "entry": m_regEntry   = reg; break;
                case "Key"  : m_regKeyType = reg; break;
                case "Value": m_regValType = reg; break;
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
        // each attempt to validate the loop will log errors into a temporary error list; whichever
        // run is the "keeper" will have its temporary errors moved over (relogged) into the
        // original error listener
        ErrorListener errsOrig = errs;

        // by holding on to the original context, we can determine the impact on the incoming
        // context that would occur by the beginning of the second iteration of the loop, and
        // use that information to correctly provide forward-looking assignment information to
        // AST nodes nested further down the tree, including e.g. lambdas that may make different
        // capture decisions based on that data
        Context                 ctxOrig    = ctx;
        Map<String, Assignment> mapLoopAsn = new HashMap<>();
        Map<String, Argument>   mapLoopArg = new HashMap<>();

        StatementBlock      blockOrig = block;
        AssignmentStatement condOrig  = getCondition();

        // don't let this repeat ad nauseam
        int cTries = 0;

        while (true)
            {
            boolean fValid = true;

            // clone the condition(s) and the body
            conds = Collections.singletonList(condOrig.clone());
            block = (StatementBlock) blockOrig.clone();

            // create a temporary error list
            errs = errsOrig.branch(this);

            // we use a potentially unnecessary context here as a place to jam in any assumptions
            // that we learned on a previous trial run through the loop
            ctx = ctxOrig.enter();
            ctx.merge(mapLoopAsn, mapLoopArg);
            ctx.setReachable(true);

            // save off the current context and errors, in case we have to lazily create some loop vars
            m_ctxLabelVars  = ctx;
            m_errsLabelVars = errs;

            // ultimately, the condition has to be re-written, because it is inevitably shorthand for
            // a measure of syntactic sugar; in order of precedence, the condition can be:
            //
            //   1) iterator   :  L: for (T value          : container.as(Iterator<T>)) {...}
            //   2) range      :  L: for (T value          : container.as(Range<T>   )) {...}
            //   3) list       :  L: for (T value          : container.as(List<T>    )) {...}
            //   4) map keys   :  L: for (K key            : container.as(Map<K,T>   )) {...}
            //   5) map entries:  L: for ((K key, T value) : container.as(Map<K,T>   )) {...}
            //   6) iterable   :  L: for (T value          : container.as(Iterable<T>)) {...}
            AssignmentStatement cond = getCondition();
            assert cond.isForEachCondition();

            // create an IfContext in case there are short-circuiting conditions that result in
            // narrowing inferences (see comment in SwitchStatement.validateImpl)
            ctx = ctx.enterIf();

            // validate the LValue declarations and any LValue sub-expressions
            AstNode condLVal = cond.getLValue();
            if (condLVal instanceof Statement condOld)
                {
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
            TypeConstant typeLVal  = fValid ? exprLVal.getType() : null;
            TypeConstant typeRVal  = null;
            int          cLVals    = 0;
            boolean      fConvert  = false;

            if (typeLVal != null)
                {
                ctx = ctx.enterInferring(typeLVal);
                }

            TypeConstant[] atypeLVals = null;
            for (int i = Plan.ITERATOR.ordinal(); i <= Plan.ITERABLE.ordinal(); ++i)
                {
                plan     = Plan.valueOf(i);
                typeRVal = switch (plan)
                    {
                    case ITERATOR -> pool.typeIterator();
                    case RANGE    -> pool.typeRange();
                    case LIST     -> pool.typeList();
                    case MAP      -> pool.typeMap();
                    case ITERABLE -> pool.typeIterable();
                    };

                if (exprRVal.testFit(ctx, typeRVal, false, null).isFit())
                    {
                    atypeLVals = exprLVal.getTypes();
                    break;
                    }
                }
            // if none of the above matched, we leave it on Iterable, so that the expression will fail
            // to validate with that required type (i.e. treat Iterable as the default required type)
            m_plan = plan;

            if (fValid && atypeLVals != null)
                {
                // get as specific as possible with the required type for the R-Value
                TypeConstant typeRValExact = null;
                int          cMaxLVals     = plan == Plan.MAP ? 2 : 1;

                cLVals = atypeLVals.length;
                if (cLVals < 1 || cLVals > cMaxLVals)
                    {
                    if (cLVals > 1 && cMaxLVals == 1)
                        {
                        m_fTupleLValue = true;
                        typeRValExact = pool.ensureParameterizedTypeConstant(typeRVal,
                                                pool.ensureTupleType(atypeLVals));
                        }
                    else
                        {
                        condLVal.log(errs, Severity.ERROR, Compiler.INVALID_LVALUE_COUNT, 1, cMaxLVals);
                        fValid = false;
                        }
                    }
                else
                    {
                    typeRValExact = pool.ensureParameterizedTypeConstant(typeRVal, atypeLVals);
                    }

                if (exprRVal.testFit(ctx, typeRValExact, false, null).isFit())
                    {
                    typeRVal = typeRValExact;
                    }
                else
                    {
                    // the specific container type didn't fit; proceed with the basic type,
                    // may need to convert r-values
                    fConvert = true;
                    }
                }

            Expression exprRValNew = exprRVal.validate(ctx, typeRVal, errs);
            if (exprRValNew == null)
                {
                fValid = false;
                }
            else
                {
                m_exprRValue = exprRValNew;

                if (fValid)
                    {
                    // update "var"/"val" type declarations on the LValues with their actual types
                    TypeConstant[] atypeRVals;
                    switch (plan)
                        {
                        default:
                        case ITERATOR:
                        case RANGE:
                        case LIST:
                        case ITERABLE:
                            {
                            TypeConstant typeEl = getElementType();
                            if (m_fTupleLValue)
                                {
                                if (!typeEl.isTuple())
                                    {
                                    condLVal.log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                                                pool.typeTuple(), typeEl);
                                    atypeRVals = null;
                                    fValid     = false;
                                    break;
                                    }

                                int cRVals = typeEl.getParamsCount();
                                if (cLVals <= cRVals)
                                    {
                                    atypeRVals = typeEl.getParamTypesArray();
                                    }
                                else
                                    {
                                    condLVal.log(errs, Severity.ERROR, Compiler.INVALID_LVALUE_COUNT,
                                                1, cRVals);
                                    atypeRVals = null;
                                    fValid     = false;
                                    }
                                }
                            else
                                {
                                atypeRVals = new TypeConstant[] {typeEl};
                                }
                            break;
                            }
                        case MAP:
                            atypeRVals = new TypeConstant[] { getKeyType(), getValueType() };

                            if (isLabeled())
                                {
                                Register regLabel = (Register) ctx.getVar(getLabelName());
                                if (regLabel != null)
                                    {
                                    regLabel.specifyActualType(pool.ensureParameterizedTypeConstant(
                                            regLabel.getType(), atypeRVals));
                                    }
                                }
                            break;
                        }

                    if (fValid)
                        {
                        assert atypeRVals.length >= cLVals;

                        if (fConvert)
                            {
                            atypeRVals = atypeRVals.clone();
                            for (int i = 0; i < cLVals; i++)
                                {
                                TypeConstant typeR = atypeRVals[i];
                                TypeConstant typeL = atypeLVals[i];
                                if (!typeL.isA(typeR))
                                    {
                                    atypeRVals[i] = typeL; // remove the inference
                                    MethodConstant idConv =
                                            typeR.ensureTypeInfo(errs).findConversion(typeL);
                                    if (idConv == null)
                                        {
                                        // cannot provide the required type
                                        exprRVal.log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                                                typeL.getValueString(), typeR.getValueString());
                                        fValid = false;
                                        }
                                    else
                                        {
                                        if (m_aidConvKey == null)
                                            {
                                            m_aidConvKey = new MethodConstant[cLVals];
                                            m_atypeConv  = new TypeConstant[cLVals];
                                            }
                                        m_aidConvKey[i] = idConv;
                                        m_atypeConv[i]  = typeR;
                                        }
                                    }
                                }
                            }
                        exprLVal.updateLValueFromRValueTypes(ctx, Branch.Always, false, atypeRVals);
                        }
                    }
                }

            if (typeLVal != null)
                {
                ctx = ctx.exit();
                }

            // regardless of the validity of the R-Value let's mark the L-Value as assigned
            exprLVal.markAssignment(ctx, false, errs);

            StatementBlock blockOld = block;

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

            List<Break> listContinues = m_listContinues;
            if (listContinues != null)
                {
                for (Break continueInfo : listContinues)
                    {
                    ctx.merge(continueInfo.mapAssign(), continueInfo.mapNarrow());
                    }
                ctx.setReachable(true);
                }

            // see if there are any assignments that would change our starting assumptions
            Map<String, Assignment> mapAsnAfter = new HashMap<>();
            Map<String, Argument>   mapArgAfter = new HashMap<>();
            ctx.prepareJump(ctxOrig, mapAsnAfter, mapArgAfter);

            if (!mapAsnAfter.equals(mapLoopAsn))
                {
                // don't let this repeat forever
                if (++cTries < 10)
                    {
                    mapLoopAsn = mapAsnAfter;
                    mapLoopArg = mapArgAfter;

                    // discard the clones created in this pass
                    cond.discard(true);
                    block.discard(true);
                    continue; // repeat
                    }

                if (!errs.hasSeriousErrors())
                    {
                    log(errs, Severity.ERROR, Compiler.FATAL_ERROR);
                    }
                fValid = false;
                }

            // discard the original nodes (we cloned them already)
            condOrig.discard(true);
            blockOrig.discard(true);

            // leaving the "true" fork of the condition
            ctx = ctx.exit();

            // leaving the "if" scope
            ctx = ctx.exit();

            // leaving the scope of the for() statement
            ctx = ctx.exit();

            // lazily created loop vars are only created inside the validation of this statement
            m_ctxLabelVars  = null;
            m_errsLabelVars = null;

            errs.merge();
            return fValid ? this : null;
            }
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean   fCompletes = fReachable;
        AstHolder holder     = ctx.getHolder();

        code.add(new Enter());

        List<RegAllocAST> listSpecial = new ArrayList<>();
        if (isLabeled())
            {
            ConstantPool pool = pool();
            if (m_regFirst != null)
                {
                code.add(new Var_IN(m_regFirst, toConst(m_regFirst.getName()), pool.valTrue()));
                listSpecial.add(m_regFirst.getRegAllocAST());
                }
            if (m_regCount != null)
                {
                code.add(new Var_IN(m_regCount, toConst(m_regCount.getName()), pool.val0()));
                listSpecial.add(m_regCount.getRegAllocAST());
                }
            if (m_regLast != null)
                {
                // no need to initialize; we always compute it
                code.add(new Var_N(m_regLast, toConst(m_regLast.getName())));
                listSpecial.add(m_regLast.getRegAllocAST());
                }
            if (m_regEntry != null)
                {
                code.add(new Var_N(m_regEntry, toConst(m_regEntry.getName())));
                listSpecial.add(m_regEntry.getRegAllocAST());
                }
            if (m_regKeyType != null)
                {
                code.add(new Var_N(m_regKeyType, toConst(m_regKeyType.getName())));
                listSpecial.add(m_regKeyType.getRegAllocAST());
                }
            if (m_regValType != null)
                {
                code.add(new Var_N(m_regValType, toConst(m_regValType.getName())));
                listSpecial.add(m_regValType.getRegAllocAST());
                }
            }

        ExprAST astLVal = m_exprLValue.getExprAST(ctx);
        if (getCondition().getLValue() instanceof Statement stmt)
            {
            fCompletes = stmt.completes(ctx, fCompletes, code, errs);
            astLVal    = AssignmentStatement.combineLValueAST(
                (ExprAST) ctx.getHolder().getAst(stmt), astLVal);
            }

        NodeType nodeType;
        switch (m_plan)
            {
            case ITERATOR:
                fCompletes = emitIterator(ctx, fCompletes, code, errs);
                nodeType   = NodeType.ForIterableStmt;
                break;
            case RANGE:
                fCompletes = emitRange(ctx, fCompletes, code, errs);
                nodeType   = NodeType.ForRangeStmt;
                break;
            case LIST:
                fCompletes = emitList(ctx, fCompletes, code, errs);
                nodeType   = NodeType.ForListStmt;
                break;
            case MAP:
                fCompletes = emitMap(ctx, fCompletes, code, errs);
                nodeType   = NodeType.ForMapStmt;
                break;
            case ITERABLE:
                fCompletes = emitIterable(ctx, fCompletes, code, errs);
                nodeType   = NodeType.ForIterableStmt;
                break;
            default:
                throw new IllegalStateException();
            }

        code.add(new Exit());

        if (fCompletes)
            {
            holder.setAst(this, new ForEachStmtAST(nodeType, listSpecial.toArray(BinaryAST.NO_ALLOCS),
                    astLVal, m_exprRValue.getExprAST(ctx), holder.getAst(block)));
            }
        return fCompletes;
        }

    /**
     * Handle code generation for the Iterator type.
     */
    private boolean emitIterator(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        ConstantPool pool     = pool();
        TypeConstant typeIter = pool.ensureParameterizedTypeConstant(pool.typeIterator(), getElementType());

        Register regIter = code.createRegister(typeIter);
        code.add(new Var(regIter));
        m_exprRValue.generateAssignment(ctx, code, m_exprRValue.new Assignable(regIter), errs);

        return emitAnyIterator(ctx, fReachable, code, regIter, errs);
        }

    /**
     * Helper that generates code using the passed Iterator register.
     */
    private boolean emitAnyIterator(Context ctx, boolean fReachable, Code code, Register regIter,
                                    ErrorListener errs)
        {
        ConstantPool pool = pool();

        TypeInfo       infoIter = pool.typeIterator().ensureTypeInfo(errs);
        MethodConstant idNext   = findWellKnownMethod(infoIter, "next", errs);
        if (idNext == null)
            {
            return false;
            }

        if (m_regLast != null)
            {
            log(errs, Severity.ERROR, Compiler.LABEL_VARIABLE_ILLEGAL, "last", getLabelName());
            return false;
            }

        // VAR_I   iter Iterator<T>         ; (passed in) hidden variable that holds the Iterator
        // MOV     xxx iter                 ; (passed in) however the iterator got assigned
        //
        // VAR     cond Boolean             ; hidden variable that holds the conditional result
        // LOOP
        // NVOK_0N iter Iterator.next() -> cond, val ; assign the conditional result and the value
        //                                             (with optional conversion)
        // JMP_F   cond, Exit               ; exit when the conditional result is false
        // {...}                            ; body
        // Continue:
        // MOV False first                  ; (optional) no longer the L.first
        // IP_INC count                     ; (optional) increment the L.count
        // LOOP_END
        // Exit:

        Register regCond = code.createRegister(pool.typeBoolean());
        code.add(new Var(regCond));

        Assignable lvalVal  = m_exprLValue.generateAssignable(ctx, code, errs);
        boolean    fTempVal = !lvalVal.isLocalArgument();
        Argument   argVal   = fTempVal
                ? new Register(lvalVal.getType(), null, Op.A_STACK)
                : lvalVal.getLocalArgument();

        code.add(new Loop());

        MethodConstant idConv = m_aidConvKey == null ? null : m_aidConvKey[0];
        if (idConv == null)
            {
            code.add(new Invoke_0N(regIter, idNext, new Argument[] {regCond, argVal}));
            code.add(new JumpFalse(regCond, getEndLabel()));
            }
        else
            {
            Register regTemp = new Register(m_atypeConv[0], null, Op.A_STACK);
            code.add(new Invoke_0N(regIter, idNext, new Argument[] {regCond, regTemp}));
            code.add(new JumpFalse(regCond, getEndLabel()));
            code.add(new Invoke_01(regTemp, idConv, argVal));
            }
        if (fTempVal)
            {
            lvalVal.assign(argVal, code, errs);
            }

        // we explicitly do NOT check the block completion, since our completion is not dependent on
        // the block's ability to complete (since the loop may execute zero times)
        block.completes(ctx, fReachable, code, errs);

        if (hasContinueLabel())
            {
            code.add(getContinueLabel());
            }
        if (m_regFirst != null)
            {
            code.add(new Move(pool.valFalse(), m_regFirst));
            }
        if (m_regCount != null)
            {
            code.add(new IP_Inc(m_regCount));
            }
        code.add(new LoopEnd());

        return fReachable;
        }

    /**
     * Handle code generation for the Range (Interval) type.
     */
    private boolean emitRange(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        TypeConstant typeElement = getElementType().removeAutoNarrowing();
        if (m_exprRValue.toConstant() instanceof RangeConstant)
            {
            // code simplification for intrinsic sequential types
            switch (m_exprRValue.getType().getParamType(0).removeAutoNarrowing().getEcstasyClassName())
                {
                case "numbers.Bit":
                case "numbers.Nibble":
                case "text.Char":
                case "numbers.Int8":
                case "numbers.Int16":
                case "numbers.Int32":
                case "numbers.Int64":
                case "numbers.Int128":
                case "numbers.IntN":
                case "numbers.UInt8":
                case "numbers.UInt16":
                case "numbers.UInt32":
                case "numbers.UInt64":
                case "numbers.UInt128":
                case "numbers.UIntN":
                    return emitConstantRange(ctx, fReachable, code, typeElement, errs);
                }

            if (typeElement.isExplicitClassIdentity(false) &&
                typeElement.getExplicitClassFormat() == Component.Format.ENUM)
                {
                return emitConstantRange(ctx, fReachable, code, typeElement, errs);
                }
            }

        if (!typeElement.isA(pool().typeSequential()))
            {
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                    pool().typeSequential().getValueString(), typeElement.getValueString());
            return false;
            }

        return emitVariableRange(ctx, fReachable, code, typeElement, errs);
        }

    /**
     * Handle optimized code generation for the Interval type when the Range is a constant value.
     */
    private boolean emitConstantRange(Context ctx, boolean fReachable, Code code,
                                      TypeConstant typeSeq, ErrorListener errs)
        {
        ConstantPool  pool  = pool();
        RangeConstant range = (RangeConstant) m_exprRValue.toConstant();

        if (range.size() < 1)
            {
            // block is unreachable
            block.completes(ctx, false, code, errs);
            ctx.getHolder().setAst(block, StmtBlockAST.EMPTY);
            return fReachable;
            }

        // VAR_IN "cur"  T _start_          ; initialize the current value to the Range "start"
        // VAR   "last" Boolean             ; (optional) if no label.last exists, create a temp for it
        // LOOP
        // IS_EQ cur _end_ -> last          ; compare current value to last value
        // MOV cur lval                     ; whatever code Assignable generates for "lval=cur"
        // {...}                            ; body
        // Continue:
        // JMP_T last Exit                  ; exit after last iteration
        // IP_INC/IP_DEC cur                ; increment (or decrement) the current value
        // MOV False first                  ; (optional) no longer the L.first
        // IP_INC count                     ; (optional) increment the L.count
        // LOOP_END
        // Exit:

        Assignable LVal   = m_exprLValue.generateAssignable(ctx, code, errs);
        Register   regVal = code.createRegister(typeSeq);
        code.add(new Var_IN(regVal,
                pool.ensureStringConstant(getLoopPrefix() + "current"), range.getEffectiveFirst()));

        Register regLast = m_regLast;
        if (regLast == null)
            {
            regLast = code.createRegister(pool.typeBoolean());
            code.add(new Var_N(regLast,
                    pool.ensureStringConstant(getLoopPrefix() + "last")));
            }

        code.add(new Loop());
        code.add(new IsEq(regVal.getType(), regVal, range.getEffectiveLast(), regLast));
        LVal.assign(regVal, code, errs);

        // we explicitly do NOT check the block completion, since our completion is not dependent on
        // the block's ability to complete (since the loop may execute zero times)
        block.completes(ctx, fReachable, code, errs);

        code.add(getContinueLabel());
        code.add(new JumpTrue(regLast, getEndLabel()));
        code.add(range.isReverse() ? new IP_Dec(regVal) : new IP_Inc(regVal));
        if (m_regFirst != null)
            {
            code.add(new Move(pool.valFalse(), m_regFirst));
            }
        if (m_regCount != null)
            {
            code.add(new IP_Inc(m_regCount));
            }
        code.add(new LoopEnd());

        return fReachable;
        }

    /**
     * Handle optimized code generation for the Interval type when the Range is not a constant.
     */
    private boolean emitVariableRange(Context ctx, boolean fReachable, Code code,
                                      TypeConstant typeSeq, ErrorListener errs)
        {
        ConstantPool pool = pool();

        Argument argRange = m_exprRValue.generateArgument(ctx, code, true, false, errs);

        TypeInfo         infoRange = pool.ensureRangeType(typeSeq).ensureTypeInfo(errs);
        MethodConstant   idLimits  = findWellKnownMethod(infoRange, "effectiveLimits", errs);
        PropertyConstant idDescend = findWellKnownProperty(infoRange, "descending", errs);

        if (idLimits == null || idDescend == null)
            {
            return false;
            }

        Register regCond       = code.createRegister(pool.typeBoolean());
        Register regFirstValue = code.createRegister(typeSeq);
        Register regLastValue  = code.createRegister(typeSeq);
        code.add(new Var(regCond));
        code.add(new Var(regFirstValue));
        code.add(new Var(regLastValue));
        code.add(new Invoke_0N(argRange, idLimits,
                    new Argument[] {regCond, regFirstValue, regLastValue}));

        // check if the interval is empty
        code.add(new JumpFalse(regCond, getEndLabel()));

        Register regDescend = code.createRegister(pool.typeBoolean());
        code.add(new Var(regDescend));
        code.add(new P_Get(idDescend, argRange, regDescend));

        // from here down - almost identical to the emitConstantRange logic

        // VAR_I "cur"  T _start_           ; initialize the current value to the Range "start"
        // VAR   "last" Boolean             ; (optional) if no label.last exists, create a temp for it
        // LOOP
        // IS_EQ cur _end_ -> last          ; compare current value to last value
        // MOV cur lval                     ; whatever code Assignable generates for "lval=cur"
        // {...}                            ; body
        // Continue:
        // JMP_T last Exit                  ; exit after last iteration
        // IP_INC/IP_DEC cur                ; increment (or decrement) the current value
        // MOV False first                  ; (optional) no longer the L.first
        // IP_INC count                     ; (optional) increment the L.count
        // LOOP_END
        // Exit:

        Assignable LVal = m_exprLValue.generateAssignable(ctx, code, errs);

        Register regVal = code.createRegister(typeSeq);
        code.add(new Var_IN(regVal,
                pool.ensureStringConstant(getLoopPrefix() + "current"), regFirstValue));

        Register regLast = m_regLast;
        if (regLast == null)
            {
            regLast = code.createRegister(pool.typeBoolean());
            code.add(new Var_N(regLast,
                    pool.ensureStringConstant(getLoopPrefix() + "last")));
            }

        code.add(new Loop());
        code.add(new IsEq(regVal.getType(), regVal, regLastValue, regLast));
        LVal.assign(regVal, code, errs);

        // we explicitly do NOT check the block completion, since our completion is not dependent on
        // the block's ability to complete (since the loop may execute zero times)
        block.completes(ctx, fReachable, code, errs);

        code.add(getContinueLabel());
        code.add(new JumpTrue(regLast, getEndLabel()));

        Label labelDecrement  = new Label("decrement" + getLabelId());
        Label labelUpdateVars = new Label("update_vars" + getLabelId());

        code.add(new JumpTrue(regDescend, labelDecrement));
        code.add(new IP_Inc(regVal));
        code.add(new Jump(labelUpdateVars));
        code.add(labelDecrement);
        code.add(new IP_Dec(regVal));
        code.add(labelUpdateVars);

        if (m_regFirst != null)
            {
            code.add(new Move(pool.valFalse(), m_regFirst));
            }
        if (m_regCount != null)
            {
            code.add(new IP_Inc(m_regCount));
            }
        code.add(new LoopEnd());

        return fReachable;
        }

    /**
     * Handle code generation for the List type.
     */
    private boolean emitList(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        ConstantPool     pool     = pool();
        TypeConstant     typeElem = getElementType();
        TypeConstant     typeList = pool.ensureParameterizedTypeConstant(pool.typeList(), typeElem);
        TypeInfo         infoList = typeList.ensureTypeInfo(errs);
        PropertyConstant idSize   = infoList.findProperty("size").getIdentity();

        // VAR_I   "count" Int 0            ; (optional) if no label.count exists, create a temp for it
        // P_GET   list.size -> "end" Int   ; get the size of the list
        // JMP_GTE count end -> Exit        ; skip everything if the list is empty
        // IP_DEC  end                      ; now "end" is the last index to iterate
        // VAR     "last" Boolean           ; (optional) if no label.last exists, create a temp for it
        // LOOP
        // IS_EQ   count end -> last        ; compare current index to end index
        // I_GET   list [count] -> lval     ; whatever code Assignable generates for "lval=list[count]"
        //                                    (with optional conversion)
        // {...}                            ; body
        // Continue:
        // JMP_T last Exit                  ; exit after last iteration
        // IP_INC count                     ; increment the current index
        // MOV False first                  ; (optional) no longer the L.first
        // LOOP_END
        // Exit:

        Register regCount = m_regCount;
        if (regCount == null)
            {
            regCount = code.createRegister(pool.typeInt64());
            code.add(new Var_IN(regCount,
                    pool.ensureStringConstant(getLoopPrefix() + "count"), pool.val0()));
            }

        Register regEnd = code.createRegister(pool.typeInt64());
        code.add((new Var(regEnd)));

        Register regList = code.createRegister(typeList);
        code.add(new Var(regList));
        m_exprRValue.generateAssignment(ctx, code, m_exprRValue.new Assignable(regList), errs);

        code.add(new P_Get(idSize, regList, regEnd));
        code.add(new JumpGte(pool.typeInt64(), regCount, regEnd, getEndLabel()));
        code.add(new IP_Dec(regEnd));

        Assignable   lvalVal  = null;
        Assignable[] alvalVal = null;
        boolean      fTempVal = false;
        Argument     argVal;

        if (m_fTupleLValue)
            {
            assert typeElem.isTuple();

            alvalVal = m_exprLValue.generateAssignables(ctx, code, errs);

            Register regElem = code.createRegister(typeElem);
            code.add(new Var(regElem));
            argVal = regElem;
            }
        else
            {
            lvalVal  = m_exprLValue.generateAssignable(ctx, code, errs);
            fTempVal = !lvalVal.isLocalArgument();
            argVal   = fTempVal
                    ? new Register(typeElem, null, Op.A_STACK)
                    : lvalVal.getLocalArgument();
            }

        Register regLast = m_regLast;
        if (regLast == null)
            {
            regLast = code.createRegister(pool.typeBoolean());
            code.add(new Var_N(regLast, pool.ensureStringConstant(getLoopPrefix() + "last")));
            }

        code.add(new Loop());
        code.add(new IsEq(pool.typeInt64(), regCount, regEnd, regLast));

        MethodConstant idConv = m_aidConvKey == null ? null : m_aidConvKey[0];
        if (idConv == null)
            {
            code.add(new I_Get(regList, regCount, argVal));
            }
        else
            {
            Register regTemp = new Register(m_atypeConv[0], null, Op.A_STACK);
            code.add(new I_Get(regList, regCount, regTemp));
            code.add(new Invoke_01(regTemp, idConv, argVal));
            }

        if (m_fTupleLValue)
            {
            for (int i = 0, c = alvalVal.length; i < c; i++)
                {
                Assignable  lval  = alvalVal[i];
                IntConstant index = pool.ensureIntConstant(i);
                if (lval.isLocalArgument())
                    {
                    code.add(new I_Get(argVal, index, lval.getLocalArgument()));
                    }
                else
                    {
                    Register regTemp = code.createRegister(lval.getType());
                    code.add(new Var(regTemp));
                    code.add(new I_Get(argVal, index, regTemp));
                    lvalVal.assign(regTemp, code, errs);
                    }
                }
            }
        else
            {
            if (fTempVal)
                {
                lvalVal.assign(argVal, code, errs);
                }
            }

        // we explicitly do NOT check the block completion, since our completion is not dependent on
        // the block's ability to complete (since the loop may execute zero times)
        block.completes(ctx, fReachable, code, errs);

        code.add(getContinueLabel());
        code.add(new JumpTrue(regLast, getEndLabel()));
        code.add(new IP_Inc(regCount));
        if (m_regFirst != null)
            {
            code.add(new Move(pool.valFalse(), m_regFirst));
            }
        code.add(new LoopEnd());

        return fReachable;
        }

    /**
     * Handle code generation for the Map type.
     */
    private boolean emitMap(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        ConstantPool pool      = pool();
        TypeConstant typeKey   = getKeyType();
        TypeConstant typeValue = getValueType();
        TypeConstant typeMap   = pool.ensureMapType(typeKey, typeValue);
        TypeConstant typeEntry = getEntryType();
        TypeInfo     infoMap   = typeMap.ensureTypeInfo(errs);

        boolean      fKeysOnly = m_exprLValue.getValueCount() == 1 && m_regEntry == null;
        TypeConstant typeEach  = fKeysOnly ? typeKey : typeEntry;
        TypeConstant typeIter  = pool.ensureParameterizedTypeConstant(pool.typeIterator(), typeEach);

        MethodConstant idNext  = findWellKnownMethod(typeIter.ensureTypeInfo(errs), "next", errs);
        if (idNext == null)
            {
            return false;
            }

        Argument argMap = m_exprRValue.generateArgument(ctx, code, true, true, errs);

        if (m_exprLValue.getValueCount() == 2 || m_regEntry != null)
            {
            MethodConstant idIterator = findWellKnownMethod(infoMap, "iterator", errs);
            if (idIterator == null)
                {
                return false;
                }

            // VAR     iter Iterator<Entry>     ; the entry Iterator
            // NVOK_01 Map.iterator() -> iter   ; get the iterator
            //
            // VAR     cond Boolean             ; hidden variable that holds the conditional result
            // VAR     entry Entry              ; the entry
            // VAR     key Key                  ; the key
            // VAR     value Value              ; the value
            // LOOP
            // NVOK_0N iter Iterator.next() -> cond, entry ; assign the conditional result and the value
            // JMP_F   cond, Exit               ; exit when the conditional result is false
            // P_GET   Entry.key, entry -> key
            // (optional conversion)
            // P_GET   Entry.value, entry -> value
            // (optional conversion)
            // {...}                            ; body
            // Continue:
            // MOV False first                  ; (optional) no longer the L.first
            // IP_INC count                     ; (optional) increment the L.count
            // LOOP_END
            // Exit:

            TypeInfo         infoEntry = typeEntry.ensureTypeInfo(errs);
            PropertyConstant idKey     = infoEntry.findProperty("key").getIdentity();
            PropertyConstant idValue   = infoEntry.findProperty("value").getIdentity();

            Register regIter = code.createRegister(typeIter);
            code.add(new Var(regIter));
            code.add(new Invoke_01(argMap, idIterator, regIter));

            Register regCond = code.createRegister(pool.typeBoolean());
            code.add(new Var(regCond));

            code.add(new Loop());

            Register regEntry = m_regEntry;
            if (regEntry == null)
                {
                // the "entry" label is not used; create a temp var
                regEntry = code.createRegister(typeEntry);
                code.add(new Var_N(regEntry, pool.ensureStringConstant(getLoopPrefix() + "entry")));
                }

            code.add(new Invoke_0N(regIter, idNext, new Argument[] {regCond, regEntry}));
            code.add(new JumpFalse(regCond, getEndLabel()));

            Assignable[] aLVal = m_exprLValue.generateAssignables(ctx, code, errs);

            Assignable lvalKey  = aLVal[0];
            boolean    fTempKey = !lvalKey.isLocalArgument();
            Argument   argKey   = fTempKey
                    ? new Register(typeKey, null, Op.A_STACK)
                    : lvalKey.getLocalArgument();

            MethodConstant idConvKey = m_aidConvKey == null ? null : m_aidConvKey[0];
            if (idConvKey == null)
                {
                code.add(new P_Get(idKey, regEntry, argKey));
                }
            else
                {
                Register regTemp = new Register(m_atypeConv[0], null, Op.A_STACK);
                code.add(new P_Get(idKey, regEntry, regTemp));
                code.add(new Invoke_01(regTemp, idConvKey, argKey));
                }

            if (fTempKey)
                {
                lvalKey.assign(argKey, code, errs);
                }

            if (aLVal.length == 2)
                {
                Assignable lvalVal  = aLVal[1];
                boolean    fTempVal = !lvalVal.isLocalArgument();
                Argument   argVal   = fTempVal
                        ? new Register(typeValue, null, Op.A_STACK)
                        : lvalVal.getLocalArgument();

                MethodConstant idConvVal = m_aidConvKey == null ? null : m_aidConvKey[1];
                if (idConvVal == null)
                    {
                    code.add(new P_Get(idValue, regEntry, argVal));
                    }
                else
                    {
                    Register regTemp = new Register(m_atypeConv[1], null, Op.A_STACK);
                    code.add(new P_Get(idValue, regEntry, regTemp));
                    code.add(new Invoke_01(regTemp, idConvVal, argVal));
                    }

                if (fTempVal)
                    {
                    lvalVal.assign(argVal, code, errs);
                    }
                }
            }
        else
            {
            TypeConstant   typeSet    = pool.ensureParameterizedTypeConstant(pool.typeSet(), typeEach);
            MethodConstant idIterator = findWellKnownMethod(typeSet.ensureTypeInfo(errs), "iterator", errs);
            if (idIterator == null)
                {
                return false;
                }

            // VAR     iter Iterator<Key>       ; the key Iterator
            // P_GET   Map.keys, map -> set     ; get the "keys" property from the [RValue] map
            // NVOK_01 set iterator() -> iter   ; get the iterator
            //
            // VAR     cond Boolean             ; hidden variable that holds the conditional result
            // VAR     key Key                  ; the key
            // LOOP
            // NVOK_0N iter Iterator.next() -> cond, key ; assign the conditional result and the value
            //                                            (with optional conversion)
            // JMP_F   cond, Exit               ; exit when the conditional result is false
            // P_GET   Entry.key, entry -> key
            // {...}                            ; body
            // Continue:
            // MOV False first                  ; (optional) no longer the L.first
            // IP_INC count                     ; (optional) increment the L.count
            // LOOP_END
            // Exit:
            PropertyConstant idKeys = infoMap.findProperty("keys").getIdentity();
            Register         regSet = new Register(typeSet, null, Op.A_STACK);
            code.add(new P_Get(idKeys, argMap, regSet));

            Register regIter = code.createRegister(typeIter);
            code.add(new Var(regIter));
            code.add(new Invoke_01(regSet, idIterator, regIter));

            Register regCond = code.createRegister(pool.typeBoolean());
            code.add(new Var(regCond));

            code.add(new Loop());

            Assignable lvalKey = m_exprLValue.generateAssignable(ctx, code, errs);

            boolean    fTempKey = !lvalKey.isLocalArgument();
            Argument   argKey   = fTempKey
                    ? new Register(typeKey, null, Op.A_STACK)
                    : lvalKey.getLocalArgument();

            MethodConstant idConv = m_aidConvKey == null ? null : m_aidConvKey[0];
            if (idConv == null)
                {
                code.add(new Invoke_0N(regIter, idNext, new Argument[] {regCond, argKey}));
                code.add(new JumpFalse(regCond, getEndLabel()));
                }
            else
                {
                Register regTemp = new Register(m_atypeConv[0], null, Op.A_STACK);
                code.add(new Invoke_0N(regIter, idNext, new Argument[] {regCond, regTemp}));
                code.add(new JumpFalse(regCond, getEndLabel()));
                code.add(new Invoke_01(regTemp, idConv, argKey));
                }

            if (fTempKey)
                {
                lvalKey.assign(argKey, code, errs);
                }
            }

        // we explicitly do NOT check the block completion, since our completion is not dependent on
        // the block's ability to complete (since the loop may execute zero times)
        block.completes(ctx, fReachable, code, errs);

        if (hasContinueLabel())
            {
            code.add(getContinueLabel());
            }
        if (m_regFirst != null)
            {
            code.add(new Move(pool.valFalse(), m_regFirst));
            }
        if (m_regCount != null)
            {
            code.add(new IP_Inc(m_regCount));
            }
        code.add(new LoopEnd());

        return fReachable;
        }

    /**
     * Handle code generation for the Iterable type.
     */
    private boolean emitIterable(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        ConstantPool pool        = pool();
        TypeConstant typeElement = getElementType();
        TypeConstant typeAble    = pool.ensureParameterizedTypeConstant(pool.typeIterable(), typeElement);
        TypeConstant typeIter    = pool.ensureParameterizedTypeConstant(pool.typeIterator(), getElementType());

        Register regIter = code.createRegister(typeIter);
        code.add(new Var(regIter));

        Argument       argAble    = m_exprRValue.generateArgument(ctx, code, true, true, errs);
        TypeInfo       infoAble   = typeAble.ensureTypeInfo(errs);
        MethodConstant idIterator = findWellKnownMethod(infoAble, "iterator", errs);
        if (idIterator == null)
            {
            return false;
            }

        code.add(new Invoke_01(argAble, idIterator, regIter));

        return emitAnyIterator(ctx, fReachable, code, regIter, errs);
        }

    /**
     * Trivial helper.
     */
    private MethodConstant findWellKnownMethod(TypeInfo infoTarget, String sMethodName, ErrorListener errs)
        {
        Set<MethodConstant> setId = infoTarget.findMethods(sMethodName, 0, MethodKind.Method);

        if (setId.isEmpty())
            {
            log(errs, Severity.ERROR, Compiler.MISSING_METHOD,
                    sMethodName, infoTarget.getType().getValueString());
            return null;
            }

        Map<MethodConstant, MethodStructure> mapMethods = new HashMap<>();
        for (MethodConstant id : setId)
            {
            MethodInfo      info   = infoTarget.getMethodById(id);
            MethodStructure method = info.getTopmostMethodStructure(infoTarget);
            mapMethods.put(id, method);
            }
        return chooseBest(setId, infoTarget.getType(), mapMethods, errs);
        }

    /**
     * Trivial helper.
     */
    private PropertyConstant findWellKnownProperty(TypeInfo info, String sPropName, ErrorListener errs)
        {
        PropertyInfo prop = info.findProperty(sPropName);

        if (prop == null)
            {
            log(errs, Severity.ERROR, Compiler.MISSING_METHOD,
                    sPropName + ".get()", info.getType().getValueString());
            return null;
            }
        return prop.getIdentity();
        }

    /**
     * @return a prefix to use for auto-generated loop variables
     */
    private String getLoopPrefix()
        {
        return "loop#" + Source.calculateLine(getStartPosition()) + '.';
        }

    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return keyword.getId().TEXT +
            " (" +
            getCondition() +
            ")\n" +
            indentLines(block.toString(), "    ");
        }


    // ----- inner class: Plan ---------------------------------------------------------------------

    /**
     * Compilation plan for the for-each statement.
     */
    enum Plan
        {
        ITERATOR, RANGE, LIST, MAP, ITERABLE;

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

    protected StatementBlock           block;

    private transient Label            m_labelContinue;
    private transient Expression       m_exprLValue;
    private transient Expression       m_exprRValue;
    private transient Plan             m_plan;
    private transient Context          m_ctxLabelVars;
    private transient ErrorListener    m_errsLabelVars;
    private transient Register         m_regFirst;
    private transient Register         m_regLast;
    private transient Register         m_regCount;
    private transient Register         m_regEntry;
    private transient Register         m_regKeyType;
    private transient Register         m_regValType;
    private transient boolean          m_fTupleLValue;
    private transient MethodConstant[] m_aidConvKey;
    private transient TypeConstant[]   m_atypeConv;

    /**
     * Generally null, unless there is a "continue" that jumps to this statement.
     */
    private transient List<Break> m_listContinues;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ForEachStatement.class, "conds", "block");
    }