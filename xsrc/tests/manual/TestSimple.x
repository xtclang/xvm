module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        console.println(new Argument<Int>(21).check());
        console.println(new Argument<Int>(21).check(True));
        console.println(new Argument<Boolean>(True).check());
        console.println(new Argument<String>("hello").check());
        }

    const Argument<Referent extends immutable Const>(Referent value)
        {
        Int check(Boolean flag=False)
            {
            switch (Referent, flag)
                {
                case (Boolean, _):
                    return value | flag ? 1 : -1;

                case (Int, True):
                    return -value.toInt();

                case (IntNumber, _):
                    return value.toInt();

                case (String, _):
                    return value.size;

                default:
                    break;
                }
            return 0;
            }
        }
    }