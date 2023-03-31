module TestSimple
    {
    @Inject Console console;

    void run()
        {
        }

    static <WideType, NarrowType extends WideType>
            WideType[] copy(Iterable<NarrowType> iterable, Array.Mutability mutability)
        {
        WideType[] result = new WideType[](iterable.size);

        loop: for (NarrowType element : iterable)
            {
            result[loop.count] = element; // this used to fail to compile
            }

        return result.toArray(mutability, True);
        }
    }