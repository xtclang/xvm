package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.op.FinallyEnd;

/**
 * Scope information for the JIT compiler.
 */
public class Scope {

    /**
     * Construct the initial scope.
     */
    public Scope(BuildContext bctx, CodeBuilder code) {
        this(bctx, code, null, -1, -1, 0, 0, 0, false);
    }

    /**
     * The internal constructor.
     */
    private Scope(BuildContext bctx, CodeBuilder code, Scope parent,
                  int startLocal, int topLocal, int topReg, int depth, int startAddr,
                  boolean preprocess) {
        this.bctx       = bctx;
        this.code       = code;
        this.parent     = parent;
        this.startLocal = startLocal;
        this.topLocal   = topLocal;
        this.topReg     = topReg;
        this.depth      = depth;
        this.startAddr  = startAddr;
        this.preprocess = preprocess;
        this.startLabel = code.newLabel();
        this.endLabel   = code.newLabel();
    }

    private final BuildContext bctx;
    private final CodeBuilder code;

    /**
     * The parent Scope.
     */
    public Scope parent;

    /**
     * The start-of-scope label.
     */
    public final Label startLabel;

    /**
     * The end-of-scope label.
     */
    public final Label endLabel;

    /**
     * The start index of the Java stack for this scope.
     */
    private int startLocal;

    /**
     * The top index of the Java stack for this scope (exclusive).
     */
    private int topLocal;

    /**
     * The top index of the XVM register for this scope (exclusive).
     */
    public int topReg;

    /**
     * The depth of this scope. Used for debugging.
     */
    public final int depth;

    /**
     * The address of the starting op for this scope. Used for debugging.
     */
    public final int startAddr;

    /**
     * If `true`, indicates a pre-processing phase.
     */
    public final boolean preprocess;

    /**
     * The list of jumps addresses the "finally" block may need to conditionally jump to.
     */
    public List<Integer> jumps;

    /**
     * A map of synthetic variables declared in this scope.
     */
    private Map<String, Integer> synthetics;

    /**
     * Create a clone of the Scope that could be used for preprocessing.
     */
    public Scope startPreprocessing() {
        assert depth == 0;
        return new Scope(bctx, code, null, startLocal, topLocal, topReg,
            0, startAddr, true);
    }

    /**
     * Enter a new Scope.
     *
     * @param startAddr  the address of the corresponding op
     */
    public Scope enter(int startAddr) {
        return new Scope(bctx, code, this, startLocal, topLocal, topReg,
            depth + 1, startAddr, preprocess);
    }

    /**
     * Allocate Java slot(s) for an XVM register of the specified ClassDesc. Note, that there could
     * be multiple Java slots for the same XVM variable.
     *
     * @return the Java slot for the newly allocated local variable
     */
    public int allocateLocal(int regId, ClassDesc cd) {
        return allocateLocal(regId, Builder.toTypeKind(cd));
    }

    /**
     * Allocate Java slot(s) for an XVM register of the specified kind. Note, that there could be
     * multiple Java slots for the same XVM variable.
     *
     * @return the Java slot for the newly allocated local variable
     */
    public int allocateLocal(int regId, TypeKind kind) {
        assert !preprocess;

        int slot = allocateJavaSlot(kind);
        declareRegister(regId);
        return slot;
    }

    /**
     * Declare the specified register at this scope.
     */
    public void declareRegister(int regId) {
        assert parent == null || regId >= parent.topReg;
        topReg = Math.max(topReg, regId + 1);
    }

    /**
     * Allocate a slot for an exception to be rethrown by the {@link FinallyEnd}.
     */
    public void allocateRethrow(CodeBuilder code) {
        assert !preprocess;

        // at the moment, the name doesn't have to be unique across the scopes since we don't
        // register the variable with the method, but we can always augment it by the "depth"
        int slot = allocateSynthetic("$rethrow", TypeKind.REFERENCE);
        code.aconst_null()
            .astore(slot);
    }

    /**
     * Retrieve the Java slot for an exception to be rethrown by the {@link FinallyEnd}.
     */
    public int getRethrow() {
        return getSynthetic("$rethrow", false);
    }

    /**
     * Allocate Java slots for conditional jumps by the {@link FinallyEnd}.
     */
    public void allocateJumps(CodeBuilder code, List<Integer> jumps) {
        assert !preprocess;

        for (Integer jump : jumps) {
            // $jumpN = false;
            int slot = allocateSynthetic("$jump" + jump, TypeKind.BOOLEAN);
            code.iconst_0()
                .istore(slot);
        }
        this.jumps = jumps;
    }

    /**
     * Allocate Java slot(s) for a synthetic variable of the specified name and kind.
     *
     * @return the Java slot for the newly allocated synthetic variable
     */
    public int allocateSynthetic(String name, TypeKind kind) {
        assert !preprocess;

        int slot = allocateJavaSlot(kind);
        if (synthetics == null) {
            synthetics = new HashMap<>();
        } else {
            assert !synthetics.containsKey(name);
        }
        synthetics.put(name, slot);
        return slot;
    }

    /**
     * @return a Java slot(s) of the specified class within this scope
     */
    public int allocateJavaSlot(ClassDesc cd) {
        return allocateJavaSlot(Builder.toTypeKind(cd));
    }

    /**
     * @return a Java slot(s) of the specified kind within this scope
     */
    public int allocateJavaSlot(TypeKind kind) {
        assert !preprocess;

        int slot;
        if (topLocal >= bctx.maxLocal || startLocal == -1) {
            slot = code.allocateLocal(kind);
            if (startLocal == -1) {
                startLocal = slot;
            }
            bctx.maxLocal = topLocal = slot + kind.slotSize();
        } else { // topLocal < bctx.maxLocal
            slot      = topLocal;
            topLocal += kind.slotSize();
            if (topLocal > bctx.maxLocal) {
                bctx.maxLocal = code.allocateLocal(TypeKind.REFERENCE) + 1; // bump the code counter
                assert bctx.maxLocal == topLocal;
            }
        }
        return slot;
    }

    /**
     * @return a Java slot for the specified synthetic variable; -1 if not found
     */
    public int getSynthetic(String name, boolean recurse) {
        Integer slot = synthetics == null ? null : synthetics.get(name);
        return  slot == null
            ? recurse && parent != null
                ? parent.getSynthetic(name, true)
                : -1
            : slot.intValue();
    }

    /**
     * Exit this Scope.
     */
    public Scope exit() {
        if (parent == null) {
            throw new IllegalStateException();
        }
        if (parent.startLocal == -1) {
            // the top scope hasn't allocated anything; use this slot's start slot index
            parent.startLocal = this.startLocal;
            parent.topLocal   = this.topLocal;
        }
        if (!preprocess) {
            code.labelBinding(endLabel);
        }
        return parent;
    }
}
