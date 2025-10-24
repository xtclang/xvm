package org.xtclang.ecstasy.collections;

import java.util.Arrays;

import org.xtclang.ecstasy.Range$Int64;
import org.xtclang.ecstasy.xObj;
import org.xtclang.ecstasy.xType;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.javajit.Ctx;

import static java.lang.Math.max;
import static java.lang.System.arraycopy;

public class xArray$Object extends Array {

    public xArray$Object(Ctx ctx, TypeConstant type) {
        super(ctx, type);
        $type = type;
    }

    public TypeConstant $type;
    public xArray$Object $delegate;
    public xObj[] $storage;

    // ----- xObj API ------------------------------------------------------------------------------

    @Override public xType $type() {
        // TODO in makeImmutable() or when mutability changes to Constant, remember to do: $type = $type.freeze()
        return (xType) $type.ensureXType(Ctx.get().container);
    }

    // ----- Array API -----------------------------------------------------------------------------

    /**
     * Array Constructor: construct(Int capacity = 0)
     */
    public static xArray$Object $new$0$p(Ctx ctx, TypeConstant type, long capacity) {
        assert !type.isImmutable();

        ctx.alloc(64); // REVIEW how big?
        xArray$Object array = new xArray$Object(ctx, type);
        array.$capCfg(ctx, capacity);
        return array;
    }

    public static xArray$Object $new$1$p(Ctx ctx, TypeConstant type, long size, xObj supply) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public static xArray$Object $new$2$p(Ctx ctx, TypeConstant type, Mutability mutability, org.xtclang.ecstasy.Iterable elements) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public static xArray$Object $new$3$p(Ctx ctx, TypeConstant type, xArray$Object that) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override public long capacity$get$p(Ctx ctx) {
        return $delegate == null
                ? $storage == null ? $capCfg(ctx) : $storage.length
                : $delegate.capacity$get$p(ctx);
    }

    @Override public long size$get$p(Ctx ctx) {
        // for virtual calls coded against xArray$Object, this avoids the virtual field accessor
        return $delegate == null ? ($sizeEtc & $SIZE_MASK) : $delegate.size$get$p(ctx);
    }

    @Override public xObj getElement$p(Ctx ctx, long index) {
        if ($delegate != null) {
            return $delegate.getElement$p(ctx, index);
        }

        if (index >= ($sizeEtc & $SIZE_MASK)) {
            throw $oob(ctx, index);
        }

        try {
            return $storage[(int) index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw $oob(ctx, index);
        }
    }

    @Override public void setElement$p(Ctx ctx, long index, xObj value) {
        if ($delegate != null) {
            $delegate.setElement$p(ctx, index, value);
            return;
        }

        int size = $sizeEtc & $SIZE_MASK;
        if (index >= size) {
            if (index == size && ($sizeEtc >>> $MUT_SHIFT) == $MUTABLE) {
                // index==size up to 2^30 is an append operation within this array's storage;
                // anything over that requires a transition to the "huge" model
                if (($storage == null || index >= $storage.length) && !$growInPlace(ctx, index+1)) {
                    // we just transitioned to the "huge model"
                    $delegate.setElement$p(ctx, index, value);
                    return;
                }
            } else {
                throw $oob(ctx, index);
            }
        }

        try {
            $storage[(int) index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw $oob(ctx, index);
        }
    }

    @Override
    public Array add(Ctx ctx, xObj element) {
        if ($delegate != null) {
            $delegate.add(ctx, element);
            return this;
        }

        if (($sizeEtc >>> $MUT_SHIFT) != $MUTABLE) {
            // TODO: persistent
            throw $ro(ctx);
        }

        int size = $sizeEtc & $SIZE_MASK;
        if (($storage == null || $storage.length == size) && !$growInPlace(ctx, size+1)) {
            // we just transitioned to the "huge model"
            $delegate.add(ctx, element);
            return this;
        }

        try {
            $storage[size+1] = element;
            return this;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw $oob(ctx, size);
        }
    }

    // TODO CP
//
//    @Override public Array$Object clear(Ctx ctx) {
//        if (empty$get$p(ctx)) {
//            return this;
//        }
//
//        Array delegate = $delegate();
//        if (delegate != null) {
//            return delegate.clear(ctx);
//        }
//
//        switch ($mut()) {
//        case $CONSTANT -> return
//        }
//    }

    @Override public xArray$Object slice$p(Ctx ctx, long n1, long n2) {
        // slice must be in-range
        // TODO check lower bound as well
        long upper = Range$Int64.$effectiveUpperBound(ctx, n1, n2);
        if (size$get$p(ctx) > upper) {
            throw $oob(ctx, upper);
        }

        // optimized empty slice
        if (Range$Int64.$empty(ctx, n1, n2)) {
            // return TODO empty Array$Object
        }

        // return TODO Array$Object$Slice
        throw $oob(ctx, upper);
    }

    // ----- Array internals -----------------------------------------------------------------------

    @Override protected xArray$Object $delegate() {
        return $delegate;
    }

    @Override protected Object $storage() {
        return $storage;
    }

    /**
     * Ensure the specified capacity.
     *
     * @param ctx     the runtime context
     * @param minCap  the minimum capacity requirement
     *
     * @return true iff this array has able to grow "in place" to support the capacity requirement;
     *         false indicates that the array transitioned to the "huge" model
     */
    @Override protected boolean $growInPlace(Ctx ctx, long minCap) {
        // the caller is responsible for passing valid args; this will not be checked at runtime
        assert minCap > 0 && minCap <= $SIZE_MASK+1 && ($storage == null || minCap > $storage.length);

        if ($storage == null) {
            long cap = max($capCfg(ctx), minCap);
            if (cap > $SIZE_MASK) {
                $growHuge(ctx, cap);
                return false;
            } else {
                $storage = new xObj[(int) cap];
                return true;
            }
        }

        // calculate power of 2 growth
        long cap = max($MIN_CAP, Long.highestOneBit(-1 + minCap + minCap)); // TODO current cap, not requested cap
        if (cap > $SIZE_MASK) {
            $growHuge(ctx, cap);
            return false;
        }

        $storage = Arrays.copyOf($storage, (int) cap);
        return true;
    }

    @Override protected void $growHuge(Ctx ctx, long cap) {
        // TODO new Array$Object$Huge
        throw $oob(ctx, cap);
    }

    @Override protected void $shrinkToSize(Ctx ctx) {
        int size = $sizeEtc & $MUT_MASK;
        if ($storage != null && $storage.length > size) {
            xObj[] newArray = new xObj[size];
            arraycopy($storage, 0, newArray, 0, size);
            $storage = newArray;
        }
    }

    @Override protected void $insert(Ctx ctx, long index, long count) {
        if ($delegate != null) {
            $delegate.$insert(ctx, index, count);
            return;
        }

        // the caller is responsible for passing valid args; this will not be checked at runtime
        assert index >= 0 && index <= size$get$p(ctx) && count > 0;

        // only mutable arrays can insert
        if (($sizeEtc >>> $MUT_SHIFT) != $MUTABLE) {
            throw $ro(ctx);
        }

        // need to compare size and capacity to see if we can insert without running out of room
        int  size = $sizeEtc & $SIZE_MASK;
        long req  = size + count;
        if (($storage == null || req > $storage.length) && !$growInPlace(ctx, req)) {
            $delegate.$insert(ctx, index, count);
            return;
        }

        if (index < size) {
            // move elements [index..size)
            arraycopy($storage, (int) index, $storage, (int) (index+count), size-((int) index));
            Arrays.fill($storage, (int) index, (int) (index+count), null); // <-- might be safe to remove this code (after we test)
        }
    }

    @Override protected void $delete(Ctx ctx, long index, long count) {
        if ($delegate != null) {
            $delegate.$delete(ctx, index, count);
            return;
        }

        // only mutable arrays can delete
        if (($sizeEtc >>> $MUT_SHIFT) != $MUTABLE) {
            throw $ro(ctx);
        }

        int oldSize  = $sizeEtc & $SIZE_MASK;
        int newSize  = oldSize - (int) count;
        int moveFrom = (int) (index + count);
        int moveTo   = (int) index;

        // the caller is responsible for passing valid args; this will not be checked at runtime
        assert index >= 0 && count > 0 && moveFrom <= oldSize;

        if (newSize == 0) {
            if ($storage.length > $MIN_CAP) {
                // array is now empty; discard the old storage altogether
                $storage = null;
            } else {
                Arrays.fill($storage, 0, oldSize, null);
            }
        } else if (max(newSize, $MIN_CAP) < ($storage.length >> 2)) {
            // wasting >75%, so resize (future tuning required)
            int newCap = max(Integer.highestOneBit(-1 + newSize + newSize), $MIN_CAP);
            xObj[] newArray = new xObj[newCap];
            if (moveTo > 0) {
                arraycopy($storage, 0, newArray, 0, moveTo);
            }
            if (oldSize > moveFrom) {
                arraycopy($storage, moveFrom, newArray, moveTo, oldSize-moveFrom);
            }
            $storage = newArray;
        } else {
            if (oldSize > moveFrom) {
                // move the data
                arraycopy($storage, moveFrom, $storage, moveTo, oldSize-moveFrom);
            }
            // clear the newly "abandoned" portion of the array
            Arrays.fill($storage, newSize, oldSize, null);
        }
        $sizeEtc = $sizeEtc & $MUT_MASK | newSize;
    }
}
