module TestSimple
    {
    @Inject Console console;

    typedef immutable Byte[] as Hash;

    void run()
        {
        HashMap<Hash, Hash>[] array = new HashMap[5](_ -> new HashMap());

        console.print(array); // this used to produce run-time "WARNING: suspicious assignment"
        }
    }