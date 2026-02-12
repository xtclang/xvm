package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;
import org.xvm.asm.Scope;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.RegisterInfo;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.ClassfileShape;

import org.xvm.runtime.Frame;

import static org.xvm.javajit.Builder.CD_Exception;


/**
 * CATCH ; begin an exception handler (implicit ENTER and VAR_IN)
 * <p/>
 * The CATCH op indicates the beginning of an exception handler. The exception handler concludes
 * with a matching CATCH_END op.
 */
public class CatchStart
        extends OpVar {
    /**
     * Construct a CATCH op.
     *
     * @param reg        the register that will hold the caught exception
     * @param constName  the name constant for the catch exception variable
     */
    public CatchStart(Register reg, StringConstant constName) {
        super(reg);

        if (!reg.getType().isA(reg.getType().getConstantPool().typeException())) {
            throw new IllegalArgumentException("catch type must be an exception type");
        }

        if (constName == null) {
            throw new IllegalArgumentException("name required");
        }

        m_constName = constName;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public CatchStart(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst, false);
    }

    void preWrite(ConstantRegistry registry) {
        m_nType = encodeArgument(getRegisterType(), registry);

        if (m_constName != null) {
            m_nNameId = encodeArgument(m_constName, registry);
        }
    }

    int getTypeId() {
        return m_nType;
    }

    void setTypeId(int nType) {
        m_nType = nType;
    }

    int getNameId() {
        return m_nNameId;
    }

    void setNameId(int nName) {
        m_nNameId = nName;
    }

    @Override
    protected boolean isTypeAware() {
        return false;
    }

    @Override
    public int getOpCode() {
        return OP_CATCH;
    }

    @Override
    public boolean isEnter() {
        return true;
    }

    @Override
    public int process(Frame frame, int iPC) {
        // all the logic is actually implemented by Frame.findGuard()
        return iPC + 1;
    }

    @Override
    public void markReachable(Op[] aop) {
        super.markReachable(aop);
        findCorrespondingOp(aop, OP_CATCH_END).markNecessary();
    }

    @Override
    public void simulate(Scope scope) {
        scope.enter(this);
        super.simulate(scope);
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        super.registerConstants(registry);

        m_constName = (StringConstant) registerArgument(m_constName, registry);
    }

    @Override
    public String getName(Constant[] aconst) {
        return getName(aconst, m_constName, m_nNameId);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void computeTypes(BuildContext bctx) {
        bctx.enterScope(null);

        // we could be called for dead code to correctly compute the scopes, but should not
        // compute types further
        if (bctx.typeMatrix.isReached(getAddress())) {
            bctx.typeMatrix.assign(getAddress(), m_nVar, bctx.getTypeConstant(m_nType));
        }
    }

    /**
     * Called by the {@link Guarded} "label" op,
     *
     * @param scopeGuarded  the guarded scope (not the current one)
     */
    public void build(BuildContext bctx, CodeBuilder code, org.xvm.javajit.Scope scopeGuarded) {
        org.xvm.javajit.Scope scopeThis = bctx.enterScope(code);

        TypeSystem   ts     = bctx.typeSystem;
        RegisterInfo regEx  = bctx.introduceVar(code, m_nVar, m_nType, m_nNameId);
        TypeConstant typeEx = regEx.type();
        assert typeEx.isA(ts.pool().typeException());

        ClassDesc cdEx = Builder.getShapeDesc(typeEx.ensureJitClassName(ts), ClassfileShape.Exception);

        code.getfield(cdEx, "exception", CD_Exception);
        bctx.storeValue(code, regEx);

        // add to the exception table
        code.exceptionCatch(scopeGuarded.startLabel, scopeGuarded.endLabel, scopeThis.startLabel, cdEx);
    }

    // ----- fields --------------------------------------------------------------------------------

    private int m_nNameId;

    private StringConstant m_constName;
}