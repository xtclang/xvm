module OO
    {
    @Inject Console console;

    enum Color{Red, Orange, Yellow, Green, Blue, Violet, Brown, Black}

    const Frog(Color color)
            implements Closeable
        {
        void speak()
            {
            console.println($"{color} frog says \"Ribbit!\"");
            }
        void jump(Int cm)
            {
            assert:arg cm > 0 && cm < 1000;
            console.println($"{color} frog jumps {cm} centimeteres!");
            }
        @Override
        void close(Exception? cause = Null)
            {
            }
        }

    void run()
        {
        using ( Frog frog1 = new Frog(Black),
                Frog frog2 = new Frog(Brown),
                Frog frog3 = new Frog(Green))
            {
            frog1.speak();
            frog2.jump(10);
            frog3.speak();
            }
        }
    }