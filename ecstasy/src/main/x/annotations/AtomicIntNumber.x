/**
 * The AtomicIntNumber mixin adds atomic capabilities such as increment and decrement to every
 * atomic reference to any of the integer number types.
 *
 * TODO this is AtomicIntNumber, but sub-pieces are needed, like AtomicSequential
 */
@Concurrent
mixin AtomicIntNumber<Referent extends IntNumber>
        into AtomicVar<Referent>
    {
    @Op void increment()
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue.nextValue())) {}
        }

    @Op void decrement()
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue.prevValue())) {}
        }

    @Op Referent preIncrement()
        {
        Referent oldValue = get();
        Referent newValue;
        do
            {
            newValue = oldValue.nextValue();
            }
        while (oldValue := replaceFailed(oldValue, newValue));
        return newValue;
        }

    @Op Referent preDecrement()
        {
        Referent oldValue = get();
        Referent newValue;
        do
            {
            newValue = oldValue.prevValue();
            }
        while (oldValue := replaceFailed(oldValue, newValue));
        return newValue;
        }

    @Op Referent postIncrement()
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue.nextValue())) {}
        return oldValue;
        }

    @Op Referent postDecrement()
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue.prevValue())) {}
        return oldValue;
        }

    @Op void addAssign(Referent n)
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue + n)) {}
        }

    @Op void subAssign(Referent n)
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue - n)) {}
        }

    @Op void mulAssign(Referent n)
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue * n)) {}
        }

    @Op void divAssign(Referent n)
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue / n)) {}
        }

    @Op void modAssign(Referent n)
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue % n)) {}
        }

    @Op void andAssign(Referent n)
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue & n)) {}
        }

    @Op void orAssign(Referent n)
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue | n)) {}
        }

    @Op void xorAssign(Referent n)
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue ^ n)) {}
        }

    @Op void shiftLeftAssign(Int count)
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue << count)) {}
        }

    @Op void shiftRightAssign(Int count)
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue >> count)) {}
        }

    @Op void shiftAllRightAssign(Int count)
        {
        Referent oldValue = get();
        while (oldValue := replaceFailed(oldValue, oldValue >>> count)) {}
        }
    }
