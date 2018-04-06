/**
 * The AtomicIntNumber mixin adds atomic capabilities such as increment and decrement to every
 * atomic reference to any of the integer number types.
 *
 * TODO this is AtomicIntNumber, but sub-pieces are needed, like AtomicSequential
 */
mixin AtomicIntNumber<RefType extends IntNumber>
        into AtomicVar<RefType>
    {
    @Op Void increment()
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue.nextValue())) {}
        }

    @Op Void decrement()
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue.prevValue())) {}
        }

    @Op RefType preIncrement()
        {
        RefType oldValue = get();
        RefType newValue;
        do
            {
            newValue = oldValue.nextValue();
            }
        while (oldValue : replaceFailed(oldValue, newValue));
        return newValue;
        }

    @Op RefType preDecrement()
        {
        RefType oldValue = get();
        RefType newValue;
        do
            {
            newValue = oldValue.prevValue();
            }
        while (oldValue : replaceFailed(oldValue, newValue));
        return newValue;
        }

    @Op RefType postIncrement()
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue.nextValue())) {}
        return oldValue;
        }

    @Op RefType postDecrement()
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue.prevValue())) {}
        return oldValue;
        }

    @Op Void addAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue + n)) {}
        }

    @Op Void subAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue - n)) {}
        }

    @Op Void mulAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue * n)) {}
        }

    @Op Void divAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue / n)) {}
        }

    @Op Void modAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue % n)) {}
        }

    @Op Void andAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue & n)) {}
        }

    @Op Void orAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue | n)) {}
        }

    @Op Void xorAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue ^ n)) {}
        }

    @Op Void shiftLeftAssign(Int count)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue << count)) {}
        }

    @Op Void shiftRightAssign(Int count)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue >> count)) {}
        }

    @Op Void shiftAllRightAssign(Int count)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue >>> count)) {}
        }
    }
