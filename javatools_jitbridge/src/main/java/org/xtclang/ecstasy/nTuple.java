package org.xtclang.ecstasy;

import java.util.Arrays;

import org.xtclang.ecstasy.collections.Tuple;

import org.xtclang.ecstasy.reflect.nRef;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Native Tuple support.
 */
public class nTuple
        extends nObject
        implements Tuple {
    public nTuple(Ctx ctx, TypeConstant type, nObject[] values) {
        super(ctx);

        $type   = type;
        $values = values;
    }

    public final TypeConstant $type;
    public final nObject[]    $values;

    @Override
    public TypeConstant $xvmType(Ctx ctx) {
        return $type;
    }

    /**
     * Native implementation of:
     *
     *   Int size;
     */
    public long size$get$p(Ctx ctx) {
        return $values.length;
    }

    /**
     * Native implementation of:
     *
     *   Object getElement(Int index)
     */
    public Object getElement$p(Ctx ctx, long index) {
        return $values[checkIndex(ctx, index)];
    }

    /**
     * Native implementation of:
     *
     *   Ref<Object> elementAt(Int index)
     */
    public nRef elementAt$p(Ctx ctx, long index) {
        int            i    = checkIndex(ctx, index);
        TypeConstant[] types = $type.getParamTypesArray();
        TypeConstant   type  = i < types.length
                ? types[i]
                : ctx.pool().typeObject();
        return new nRef(ctx, $values[i], type, false);
    }

    /**
     * Native implementation of:
     *
     *   <Element> Tuple!<> add(Element value)
     */
    public nTuple add(Ctx ctx, nType elementType, Object value) {
        int       count     = $values.length;
        nObject[] valuesNew = Arrays.copyOf($values, count + 1);
        valuesNew[count] = (nObject) value;

        TypeConstant[] types    = $type.getParamTypesArray();
        TypeConstant[] typesNew = TypeConstant.NO_TYPES;
        if (types.length == count) {
            typesNew        = Arrays.copyOf(types, count + 1);
            typesNew[count] = elementType.$dataType;
        }

        TypeConstant typeNew = ctx.pool().ensureTupleType(typesNew);
        return new nTuple(ctx, typeNew, valuesNew);
    }

    /**
     * Native implementation of:
     *
     *   Tuple!<> addAll(Tuple!<> that)
     */
    public nTuple addAll(Ctx ctx, nTuple that) {
        int countThis = $values.length;
        int countThat = that.$values.length;
        if (countThis == 0) {
            return that;
        }
        if (countThat == 0) {
            return this;
        }

        nObject[] valuesNew = Arrays.copyOf($values, countThis + countThat);
        System.arraycopy(that.$values, 0, valuesNew, countThis, countThat);

        TypeConstant[] typesThis = $type.getParamTypesArray();
        TypeConstant[] typesThat = that.$type.getParamTypesArray();
        TypeConstant[] typesNew  = TypeConstant.NO_TYPES;
        if (typesThis.length == countThis && typesThat.length == countThat) {
            typesNew = Arrays.copyOf(typesThis, countThis + countThat);
            System.arraycopy(typesThat, 0, typesNew, countThis, countThat);
        }

        TypeConstant typeNew = ctx.pool().ensureTupleType(typesNew);
        return new nTuple(ctx, typeNew, valuesNew);
    }

    /**
     * Native implementation of:
     *
     *   Tuple replace(Int index, Object value)
     */
    public nTuple replace$p(Ctx ctx, long index, Object value) {
        int            i     = checkIndex(ctx, index);
        TypeConstant[] types = $type.getParamTypesArray();
        if (i >= types.length) {
            throw Exception.$unsupported(ctx, "Tuple element type is unavailable");
        }

        nObject valueNew = (nObject) value;
        if (!valueNew.$xvmType(ctx).isA(types[i])) {
            throw Exception.$typeMismatch(ctx, "Tuple element [" + index + "]");
        }

        nObject[] valuesNew = $values.clone();
        valuesNew[i] = valueNew;
        return new nTuple(ctx, $type, valuesNew);
    }

    /**
     * Native implementation of:
     *
     *   Tuple!<> slice(Range<Int> interval)
     */
    public nTuple slice(Ctx ctx, nRangeᐸInt64ᐳ interval) {
        long lower = interval.$lowerBound + (interval.$lowerExclusive ? 1 : 0);
        long upper = interval.$upperBound + (interval.$upperExclusive ? 0 : 1);
        if (lower < 0 || upper > $values.length) {
            throw Exception.$oob(ctx, "Tuple interval out of range");
        }

        if (lower >= upper) {
            TypeConstant typeNew = ctx.pool().ensureTupleType(TypeConstant.NO_TYPES);
            return new nTuple(ctx, typeNew, new nObject[0]);
        }

        int       from      = (int) lower;
        int       to        = (int) upper;
        int       count     = to - from;
        nObject[] valuesNew = new nObject[count];
        if (interval.$descending) {
            for (int i = 0; i < count; i++) {
                valuesNew[i] = $values[to - i - 1];
            }
        } else {
            System.arraycopy($values, from, valuesNew, 0, count);
        }

        TypeConstant[] types    = $type.getParamTypesArray();
        TypeConstant[] typesNew = TypeConstant.NO_TYPES;
        if (types.length == $values.length) {
            typesNew = new TypeConstant[count];
            if (interval.$descending) {
                for (int i = 0; i < count; i++) {
                    typesNew[i] = types[to - i - 1];
                }
            } else {
                System.arraycopy(types, from, typesNew, 0, count);
            }
        }

        TypeConstant typeNew = ctx.pool().ensureTupleType(typesNew);
        return new nTuple(ctx, typeNew, valuesNew);
    }

    /**
     * Native implementation of:
     *
     *   Tuple!<> remove(Int index)
     */
    public nTuple remove$p(Ctx ctx, long index) {
        throw Exception.$unsupported(ctx, "Tuple.remove()");
    }

    /**
     * Native implementation of:
     *
     *   Tuple!<> removeAll(Interval<Int> interval)
     */
    public nTuple removeAll(Ctx ctx, nRangeᐸInt64ᐳ interval) {
        throw Exception.$unsupported(ctx, "Tuple.removeAll()");
    }

    /**
     * Native implementation of:
     *
     *   immutable Tuple freeze(Boolean inPlace = False)
     */
    public nTuple freeze$p(Ctx ctx, boolean inPlace, boolean inPlace$dflt) {
        throw Exception.$unsupported(ctx, "Tuple.freeze()");
    }

    /**
     * Native implementation of:
     *
     *   static <CompileType extends Tuple> Boolean equals( CompileType value1, CompileType value2)
     */
    public static boolean equals$p(Ctx ctx, nType type, nTuple value1, nTuple value2) {
        return Tuple.equals$p(ctx, type, value1, value2);
    }

    /**
     * Used by {@link #getElement$p(Ctx, long)}, {@link #elementAt$p(Ctx, long)}, and
     * {@link #replace$p(Ctx, long, Object)}.
     */
    private int checkIndex(Ctx ctx, long index) {
        if (index < 0 || index >= $values.length) {
            throw Exception.$oob(ctx, "Tuple index out of range: " + index);
        }
        return (int) index;
    }
}
