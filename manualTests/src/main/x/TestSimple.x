module TestSimple
    {
    @Inject Console console;
    void run()
        {
        (_, _, String? error) = decodeEscape(Null); // this used to blow the compiler
        }

    (Boolean, String?) decodeEscape(String? error)
        {
        return True, error;
        }
    }