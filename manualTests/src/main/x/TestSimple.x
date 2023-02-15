module TestSimple
    {
    @Inject Console console;

    void run()
        {
        String[] names = ["a", "b", "c", "d", "e"].reify(Mutable);
        String[] slice = names[2..3];
        slice = slice.reify(); // this used to produce a Constant array
        slice[1] = "x";
        console.print(slice);
        }
    }