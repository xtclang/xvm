module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Int[] ints = new Int[];
        ints.add(1);
        ints.add(0);
        ints.add(2);
        ints.add(0);
        ints.add(3);

        Int[] ints2 = ints.freeze(False);

        console.println(ints.removeAll(i -> i == 0));
        console.println(ints);

        console.println(ints2.removeAll(i -> i == 0));
        console.println(ints2);
        }
    }