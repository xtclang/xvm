/**
 * The AtomicIntNumber mixin adds atomic capabilities such as increment and decrement to every
 * atomic reference to any of the integer number types.
 *
 * TODO this is AtomicIntNumber, but sub-pieces are needed, like AtomicSequential
 */
mixin AtomicIntNumber<RefType extends IntNumber>
        into AtomicVar<RefType>
    {
    @Op void increment()
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue.nextValue())) {}
        }

    @Op void decrement()
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

    @Op void addAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue + n)) {}
        }

    @Op void subAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue - n)) {}
        }

    @Op void mulAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue * n)) {}
        }

    @Op void divAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue / n)) {}
        }

    @Op void modAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue % n)) {}
        }

    @Op void andAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue & n)) {}
        }

    @Op void orAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue | n)) {}
        }

    @Op void xorAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue ^ n)) {}
        }

    @Op void shiftLeftAssign(Int count)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue << count)) {}
        }

    @Op void shiftRightAssign(Int count)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue >> count)) {}
        }

    @Op void shiftAllRightAssign(Int count)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue >>> count)) {}
        }
    }
