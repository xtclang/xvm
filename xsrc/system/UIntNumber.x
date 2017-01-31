interface UIntNumber
        extends IntNumber
    {
    UnsignedIntNumber abs()
        {
        return this;
        }

    UnsignedIntNumber neg()
        {
        throw new UnsupportedOperationException();
        }
    }
