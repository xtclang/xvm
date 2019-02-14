/**
 * The UncheckedInt mixin is used with integer types to alter the default behavior for exceptional conditions such as
 * integer overflow. Specifically, the mixin is used to ignore the exceptions caused by the result exceeding the size
 * of the destination type; all operations are conducted as if in an arbitrarily-large-enough integer type, and then
 * truncated to the size of the original operands.
 */
mixin UncheckedInt
        into IntNumber
    {
    static const MathException extends Exception {}

    @Override
    @Op IntNumber nextValue()
        {
        try
            {
            return super();
            }
        catch (MathException e)
            {
            return this.to<VarInt>().nextValue().truncate(bitLength).to<UncheckedInt>();
            }
        }

    @Override
    @Op IntNumber prevValue()
        {
        try
            {
            return super();
            }
        catch (MathException e)
            {
            return this.to<VarInt>().prevValue().truncate(bitLength).to<UncheckedInt>();
            }
        }

    @Override
    @Op UncheckedInt add(UncheckedInt n)
        {
        try
            {
            return super(n);
            }
        catch (MathException e)
            {
            return this.to<VarInt>().add(n.to<VarInt>()).truncate(bitLength).to<UncheckedInt>();
            }
        }

    @Override
    @Op UncheckedInt sub(UncheckedInt n)
        {
        try
            {
            return super(n);
            }
        catch (MathException e)
            {
            return this.to<VarInt>().sub(n.to<VarInt>()).truncate(bitLength).to<UncheckedInt>();
            }
        }

    @Override
    @Op UncheckedInt mul(UncheckedInt n)
        {
        try
            {
            return super(n);
            }
        catch (MathException e)
            {
            return this.to<VarInt>().mul(n.to<VarInt>()).truncate(bitLength).to<UncheckedInt>();
            }
        }

    @Override
    UncheckedInt pow(UncheckedInt n)
        {
        try
            {
            return super(n);
            }
        catch (MathException e)
            {
            return this.to<VarInt>().pow(n.to<VarInt>()).truncate(bitLength).to<UncheckedInt>();
            }
        }

    @Override
    @Op UncheckedInt neg()
        {
        try
            {
            return super();
            }
        catch (MathException e)
            {
            return this.to<VarInt>().neg().truncate(bitLength).to<UncheckedInt>();
            }
        }

    @Override
    UncheckedInt abs()
        {
        try
            {
            return super();
            }
        catch (MathException e)
            {
            return this.to<VarInt>().abs().truncate(bitLength).to<UncheckedInt>();
            }
        }
    }
