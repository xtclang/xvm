module TestSimple
    {
    @Inject Console console;

    import ecstasy.text.Log;
    import ecstasy.text.SimpleLog;

    void run(String[] args=[])
        {
        ErrorLog log = new ErrorLog();
        log.add("hello");
        log.add("world");
        console.println(log.toString()); // this used to print "ErrorLog" (by Service.toString())
        }

    service ErrorLog
            extends SimpleLog
        {
        }
    }