/**
 * The UncheckedInt mixin is used with integer types to alter the default behavior for exceptional conditions such as
 * integer overflow. Specifically, the mixin is used to ignore the exceptions caused by the result exceeding the size
 * of the destination type; all operations are conducted as if in an arbitrarily-large-enough integer type, and then
 * truncated to the size of the original operands.
 */
mixin UncheckedInt
        into IntNumber
    {
    @Override
    @Op IntNumber nextValue()
        {
        try
            {
            return super().toUnchecked();
            }
        catch (OutOfBounds e)
            {
            return this.toIntN().nextValue().retainLSBits(bitLength).toUnchecked();
            }
        }

    @Override
    @Op IntNumber prevValue()
        {
        try
            {
            return super().toUnchecked();
            }
        catch (OutOfBounds e)
            {
            return this.toIntN().prevValue().retainLSBits(bitLength).toUnchecked();
            }
        }

    @Override
    @Op UncheckedInt add(UncheckedInt n)
        {
        try
            {
            return super(n).toUnchecked();
            }
        catch (OutOfBounds e)
            {
            return this.toIntN().add(n.toIntN()).retainLSBits(bitLength).toUnchecked();
            }
        }

    @Override
    @Op UncheckedInt sub(UncheckedInt n)
        {
        try
            {
            return super(n).toUnchecked();
            }
        catch (OutOfBounds e)
            {
            return this.toIntN().sub(n.toIntN()).retainLSBits(bitLength).toUnchecked();
            }
        }

    @Override
    @Op UncheckedInt mul(UncheckedInt n)
        {
        try
            {
            return super(n).toUnchecked();
            }
        catch (OutOfBounds e)
            {
            return this.toIntN().mul(n.toIntN()).retainLSBits(bitLength).toUnchecked();
            }
        }

    @Override
    UncheckedInt pow(UncheckedInt n)
        {
        try
            {
            return super(n).toUnchecked();
            }
        catch (OutOfBounds e)
            {
            return this.toIntN().pow(n.toIntN()).retainLSBits(bitLength).toUnchecked();
            }
        }

    @Override
    @Op UncheckedInt neg()
        {
        try
            {
            return super().toUnchecked();
            }
        catch (OutOfBounds e)
            {
            return this.toIntN().neg().retainLSBits(bitLength).toUnchecked();
            }
        }

    @Override
    UncheckedInt abs()
        {
        try
            {
            return super().toUnchecked();
            }
        catch (OutOfBounds e)
            {
            return this.toIntN().abs().retainLSBits(bitLength).toUnchecked();
            }
        }
    }
