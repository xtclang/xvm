mixin UncheckedInt
        into IntNumber
    {
    @op IntNumber increment()
        {
        try
            {
            return super();
            }
        catch (MathException e)
            {
            return this.to<VarInt>().increment().truncate(bitLength).to<this:type>();
            }
        }

    @op IntNumber decrement()
        {
        try
            {
            return super();
            }
        catch (MathException e)
            {
            return this.to<VarInt>().decrement().truncate(bitLength).to<this:type>();
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
