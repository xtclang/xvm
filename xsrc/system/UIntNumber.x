const UIntNumber
        extends IntNumber
    {
    protected construct(Bit[] bits)
        {
        construct IntNumber(bits);
        }

    @Override
    UIntNumber abs()
        {
        return this;
        }

    @Override
    UIntNumber neg()
        {
        throw new UnsupportedOperation();
        }
    }
