module TestSimple
    {
    @Inject Console console;

    void run(String[] args=[])
        {
        Int  n  = 0;
        Char ch = 'q';
        n := ch.asciiDigit();
        }
    }