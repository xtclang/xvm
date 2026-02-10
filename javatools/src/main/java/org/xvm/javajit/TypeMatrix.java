package org.xvm.javajit;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.CastTypeConstant;
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
        Set<Integer> changeSet = Collections.emptySet();

        if (nextAddr >= views.length) {
            return changeSet;
        }

        OpView currView = views[currAddr];
        OpView nextView = views[nextAddr];

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
     * Propagate all types from current op to the next op and declare the specified register's type.
     */
    public void declare(int currAddr, int regId, TypeConstant type) {
        assert type != null;

        if (type.containsFormalType(true)) {
            type = type.resolveGenerics(bctx.pool(), bctx.typeInfo.getType());
        }

        if (currAddr != -1) {
            follow(currAddr, currAddr + 1, regId);
        }

        ensureMutableView(currAddr + 1).types.put(regId, type);

        if (regId >= 0) {
            bctx.scope.declareRegister(regId);
        }
    }

    /**
     * Propagate all types from current op to the next op and assign the specified register's type.
     */
    public void assign(int currAddr, int regId, TypeConstant type) {
        assign(currAddr, currAddr + 1, regId, type);
    }

    /**
     * Propagate all types from current op to the destination op and assign the specified register's
     * type.
     */
    public void assign(int currAddr, int nextAddr, int regId, TypeConstant type) {
        assert currAddr >= 0 && type != null;

        if (type.containsFormalType(true)) {
            type = type.resolveGenerics(bctx.pool(), bctx.typeInfo.getType());
        }

        follow(currAddr, nextAddr, -1);

        OpView       nextView = ensureMutableView(nextAddr);
        TypeConstant nextType = nextView.types.get(regId);

        ComputeType:
        if (nextType == null) {
            if (regId >= 0) {
                bctx.scope.declareRegister(regId);
            }
        } else {
            if (type.equals(nextType)) {
                return;
            }

            if (nextType instanceof CastTypeConstant inferredType) {
                nextType = inferredType.getBaseType();
            }

            // use CastTypeConstant to remember the original type
            assert type.isA(nextType) || type.containsFormalType(true);

            if (!type.equals(nextType)) {
                if (type instanceof CastTypeConstant inferredType) {
                    TypeConstant baseType = inferredType.getBaseType();
                    if (baseType.equals(nextType)) {
                        // take as is
                        break ComputeType;
                    }
                    type = inferredType.getUnderlyingType2();
                }
                type = new CastTypeConstant(bctx.pool(), nextType, type);
            }
        }
        nextView.types.put(regId, type);
    }

    /**
     * Remove all the type information for registers at the current address that are higher that
     * the specified register id.
     */
    public void removeRegisters(int currAddr, int topRegId) {
        if (currAddr < views.length) {
            ensureMutableView(currAddr).types.entrySet().
                removeIf(entry -> entry.getKey() >= topRegId);
        }
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
        } else if (!mergeType.equals(currType)) {
            if (mergeType.isA(currType)) {
                types.put(regId, currType);
            } else if (currType.isA(mergeType)) {
                types.put(regId, mergeType);
            } else {
                TypeConstant baseType = null;
                if (currType instanceof CastTypeConstant inferredType) {
                    baseType = inferredType.getBaseType();
                    currType = inferredType.getUnderlyingType2();
                    assert mergeType.isA(baseType);
                }

                if (mergeType instanceof CastTypeConstant inferredType) {
                    baseType  = inferredType.getBaseType();
                    mergeType = inferredType.getUnderlyingType2();
                }

                ConstantPool pool      = bctx.pool();
                TypeConstant unionType = currType.union(pool, mergeType);
                types.put(regId, baseType == null // this usually means an "out-of-scope" var
                    ? unionType
                    : new CastTypeConstant(pool, baseType, unionType));
            }
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
