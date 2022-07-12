module TestSimple
    {
    @Inject Console console;

    void run(String[] args=[])
        {
        function void(Int)? notifyOnClose = unregisterClient(_); // this used to fail to compile
        console.println(notifyOnClose);
        }

    void unregisterClient(Int id)
        {
        }
    }