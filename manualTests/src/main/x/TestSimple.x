module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        }

    void tes(Boolean flag, String? pre = Null, String? post = Null)
        {
        (pre, post) = flag
                    ? ("{", "}")
                    : ("[", "]");

        String s = pre; // this used to fail to compile
        }
    }