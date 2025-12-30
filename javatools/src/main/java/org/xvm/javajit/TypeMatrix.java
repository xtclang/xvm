package org.xvm.javajit;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.TypeConstant;

/**
 * The matrix that can answer type questions for a given register at a given op address.
 */
public class TypeMatrix {

    public TypeMatrix(BuildContext bctx) {
        MethodStructure method = bctx.methodStruct;

        this.bctx  = bctx;
        this.views = new OpView[method.hasCode() ? method.getOps().length : 0];
    }

    private final BuildContext bctx;
    private final OpView[]     views;

    public record OpView(Map<Integer, TypeConstant> types, boolean isImmutable) {
        /**
         * @return a mutable copy of this view
         */
        public OpView copy() {
            return new OpView(new HashMap<>(types), false);
        }

        /**
         * @return an immutable copy of this view
         */
        public OpView freeze() {
            return isImmutable ? this : new OpView(types, true);
        }
    }

    // ----- collection phase ----------------------------------------------------------------------

    /**
     * Propagate all register types from current op to the next op.
     *
     * @return the set of registers that have widened their types
     */
    public Set<Integer> follow(int currAddr) {
        return follow(currAddr, currAddr + 1, -1);
    }

    /**
     * Propagate all register types from current op to the destination op.
     *
     * @param exceptId  if not negative, indicates the register id **not** to propagate
     *
     * @return the set of registers that have widened their types
     */
    public Set<Integer> follow(int currAddr, int nextAddr, int exceptId) {
        OpView currView = views[currAddr];
        OpView nextView = views[nextAddr];

        Set<Integer> changeSet = Collections.emptySet();
        if (currView != null) {
            if (nextView == null) {
                if (exceptId >= 0 && currView.types.containsKey(exceptId)) {
                    nextView = views[nextAddr] = currView.copy();
                    nextView.types.remove(exceptId);
                } else {
                    views[nextAddr] = currView.freeze();
                }
            } else {
                // merge the views
                changeSet = mergeTypes(currView, nextAddr, exceptId);
            }
        }
        return changeSet;
    }

    /**
     * Propagate all types from current op to the next op and set the specified register's type.
     */
    public void declare(int currAddr, int regId, TypeConstant type) {
        declare(currAddr, currAddr + 1, regId, type);
    }

    /**
     * Propagate all types from current op to the destination op and set the specified register's
     * type.
     */
    public void declare(int currAddr, int nextAddr, int regId, TypeConstant type) {
        if (currAddr != -1) {
            follow(currAddr, nextAddr, regId);
        }

        ensureMutableView(nextAddr).types.put(regId, type);
    }

    /**
     * Propagate all register types from current op to the next op and merge the type for the
     * specified register with any previously known type for that register.
     */
    public void merge(int currAddr, int regId, TypeConstant type) {
        merge(currAddr, currAddr + 1, regId, type);
    }

    /**
     * Propagate all register types from current op to the destination op and merge the type for the
     * specified register with any previously known type for that register.
     */
    public void merge(int currAddr, int nextAddr, int regId, TypeConstant type) {
        follow(currAddr, nextAddr, regId);

        OpView nextView = views[nextAddr];
        if (nextView == null) {
            nextView = ensureMutableView(nextAddr);
            nextView.types.put(regId, type);
        } else {
            TypeConstant nextType = nextView.types.get(regId);
            if (!type.equals(nextType)) {
                nextView = ensureMutableView(nextAddr);
                mergeType(nextView.types, regId, nextType, type);
            }
        }
    }

    /**
     * Remove all the type information for registers at the current address that are higher that
     * the specified register id.
     */
    public void removeRegisters(int currAddr, int topRegId) {
        ensureMutableView(currAddr).types.entrySet().
            removeIf(entry -> entry.getKey() >= topRegId);
    }

    // ----- collection phase helpers --------------------------------------------------------------

    /**
     * @return true iff there is an OpView for the specified address
     */
    public boolean isReached(int addr) {
        return views[addr] != null;
    }

    /**
     * Ensure a mutable view at the specified address.
     */
    protected OpView ensureMutableView(int addr) {
        OpView view = views[addr];
        return view == null
            ? views[addr] = new OpView(new HashMap<>(), false)
            : view.isImmutable
                ? views[addr] = view.copy()
                : view;
    }

    /**
     * Merge the `currView` into the view corresponding to the `nextAddr` with an exception to
     * the specified register.
     *
     * @return the set of registers that have widened their types
     */
    protected Set<Integer> mergeTypes(OpView currView, int nextAddr, int exceptId) {
        OpView nextView = views[nextAddr];
        assert nextView != null;

        Set<Integer> changeSet = Collections.emptySet();
        for (var entry : currView.types.entrySet()) {
            Integer regId = entry.getKey();
            if (regId < 0 || regId == exceptId) {
                continue;
            }

            TypeConstant currType = entry.getValue();
            TypeConstant nextType = nextView.types.get(regId);
            if (currType.equals(nextType)) {
                continue;
            }

            if (nextView.isImmutable) {
                views[nextAddr] = nextView = nextView.copy();
            }

            if (mergeType(nextView.types, regId, entry.getValue(), nextType)) {
                if (changeSet.isEmpty()) {
                    changeSet = new HashSet<>();
                }
                changeSet.add(regId);
            }
        }
        return changeSet;
    }

    /**
     * Merge the `currType` with the `mergeType` in the `types` map.
     *
     * @return true iff the register type has been widened
     */
    private boolean mergeType(Map<Integer, TypeConstant> types, Integer regId,
                           TypeConstant currType, TypeConstant mergeType) {
        if (mergeType == null) {
            types.put(regId, currType);
        } else if (!currType.equals(mergeType)) {
            types.put(regId, currType.union(bctx.pool(), mergeType));
            return true;
        }
        return false;
    }

    // ----- retrieval phase -----------------------------------------------------------------------

    /**
     * @return the type for the specified register at the specified address
     */
    public TypeConstant getType(int regId, int addr) {
        OpView view = views[addr];
        return view == null ? null : view.types.get(regId);
    }
}
