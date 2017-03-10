/**
 * The UncheckedInt mixin is used with integer types to alter the default behavior for exceptional conditions such as
 * integer overflow. Specifically, the mixin is used to ignore the exceptions caused by the result exceeding the size
 * of the destination type; all operations are conducted as if in an arbitrarily-large-enough integer type, and then
 * truncated to the size of the original operands.
 */
mixin UncheckedInt
        into IntNumber
    {
    @op IntNumber nextValue()
        {
        try
            {
            return super();
            }
        catch (MathException e)
            {
            return this.to<VarInt>().nextValue().truncate(bitLength).to<this:type>();
            }
        }

    @op IntNumber prevValue()
        {
        try
            {
            return super();
            }
        catch (MathException e)
            {
            return this.to<VarInt>().prevValue().truncate(bitLength).to<this:type>();
            }
        }

    @op UncheckedInt add(UncheckedInt n)
        {
        try
            {
            return super(n);
            }
        catch (MathException e)
            {
            return this.to<VarInt>().add(n.to<VarInt>()).truncate(bitLength).to<this:type>();
            }
        }

    @op UncheckedInt sub(UncheckedInt n)
        {
        try
            {
            return super(n);
            }
        catch (MathException e)
            {
            return this.to<VarInt>().sub(n.to<VarInt>()).truncate(bitLength).to<this:type>();
            }
        }

    @op UncheckedInt mul(UncheckedInt n)
        {
        try
            {
            return super(n);
            }
        catch (MathException e)
            {
            return this.to<VarInt>().mul(n.to<VarInt>()).truncate(bitLength).to<this:type>();
            }
        }

    UncheckedInt pow(UncheckedInt n)
        {
        try
            {
            return super(n);
            }
        catch (MathException e)
            {
            return this.to<VarInt>().pow(n.to<VarInt>()).truncate(bitLength).to<this:type>();
            }
        }

    @op UncheckedInt neg()
        {
        try
            {
            return super();
            }
        catch (MathException e)
            {
            return this.to<VarInt>().neg().truncate(bitLength).to<this:type>();
            }
        }

    UncheckedInt abs()
        {
        try
            {
            return super();
            }
        catch (MathException e)
            {
            return this.to<VarInt>().abs().truncate(bitLength).to<this:type>();
            }
        }
    }
