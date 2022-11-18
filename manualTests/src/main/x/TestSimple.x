module TestSimple
    {
    @Inject Console console;

    void run()
        {
        test1('A', Null);
        }

    void test1(Char ch, String? error)
        {
        Int  factor;

        if (error != Null)
            {
            console.println(error);
            return;
            }

        for (Int i : 0..4)
            {
            switch (ch)
                {
                case 'A':
                    continue;
                case 'a':
                    factor = 1;
                    Int q = 0;
                    break;

                case 'B':
                    continue;
                case 'b':
                    factor   = 2;
                    break;

                case 'C':
                    // commenting either one below used to allow to compile
                    factor   = 6;
                    (_, error) = decodeEscape(error);
                    break;

                default:
                    error ?:= "Illegal character";
                    factor   = 100;
                    break;
                }
            console.println(factor);
            assert ch := ch.next();
            }
        }

    (Boolean, String?) decodeEscape(String? error)
        {
        return True, error;
        }
    }