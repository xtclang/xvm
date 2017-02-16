/**
 * The AtomicIntNumber mixin adds atomic capabilities such as increment and decrement to an atomic
 * reference to an integer number.
 */
@auto mixin AtomicIntNumber
        into AtomicRef<IntNumber>
    {
    @op IntNumber increment()
        {
        IntNumber oldValue = get();
        while (oldValue : casFailed(oldValue, oldValue + 1) {}
        }

    @op IntNumber decrement()
        {
        IntNumber oldValue = get();
        while (oldValue : casFailed(oldValue, oldValue - 1) {}
        }

    // TODO lots of other += -= *= etc. etc. etc.
    }
