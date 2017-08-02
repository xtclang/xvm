interface UIntNumber
        extends IntNumber
    {
    UIntNumber abs()
        {
        return this;
        }

    UIntNumber neg()
        {
        throw new UnsupportedOperationException();
        }
    }
