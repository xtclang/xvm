/**
 * The AtomicIntNumber mixin adds atomic capabilities such as increment and decrement to every
 * atomic reference to any of the integer number types.
 */
@auto mixin AtomicIntNumber<RefType>
        into AtomicRef<IntNumber>
    {
    @op RefType preIncrement()
        {
        RefType oldValue = get();
        RefType newValue;
        do
            {
            newValue = oldValue + 1;
            }
        while (oldValue : replaceFailed(oldValue, newValue))
        return newValue;
        }

    @op RefType preDecrement()
        {
        RefType oldValue = get();
        RefType newValue;
        do
            {
            newValue = oldValue - 1;
            }
        while (oldValue : replaceFailed(oldValue, newValue))
        return newValue;
        }

    @op RefType postIncrement()
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue + 1) {}
        return oldValue;
        }

    @op RefType postDecrement()
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue - 1) {}
        return oldValue;
        }

    @op Void addAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue + n) {}
        }

    @op Void subAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue - n) {}
        }

    @op Void mulAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue * n) {}
        }

    @op Void divAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue / n) {}
        }

    @op Void modAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue % n) {}
        }

    @op Void andAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue & n) {}
        }

    @op Void orAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue | n) {}
        }

    @op Void xorAssign(RefType n)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue ^ n) {}
        }

    @op Void shiftLeftAssign(Int count)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue << count) {}
        }

    @op Void shiftRightAssign(Int count)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue >> count) {}
        }

    @op Void shiftAllRightAssign(Int count)
        {
        RefType oldValue = get();
        while (oldValue : replaceFailed(oldValue, oldValue >>> count) {}
        }
    }
