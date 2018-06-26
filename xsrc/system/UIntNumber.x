interface UIntNumber
        extends IntNumber
    {
    @Override
    UIntNumber abs()
        {
        return this;
        }

    @Override
    UIntNumber neg()
        {
        throw new UnsupportedOperationException();
        }
    }
