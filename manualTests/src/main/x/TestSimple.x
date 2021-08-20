module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Iterator<String> iter =  iter("");
        }

    Iterator<String> iter(String s)
        {
        // that used to blow up
        return (s.size > 0 ? [s] : []).iterator();
        }
    }
