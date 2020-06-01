module TestSimple
    {
    @Inject Console console;
    @Inject Random  random;

    void run()
        {
        for (Int i : 0..100)
            {
            Bit [] bits  = new Bit [random.int(5)]; random.fill(bits );
            Byte[] bytes = new Byte[random.int(3)]; random.fill(bytes);
            console.println($"[{i}] bit={random.bit()}, bits={bits}, boolean={random.boolean()} byte={random.byte()}, bytes={bytes}, int={random.int()}, int(17)={random.int(17)}, uint={random.uint()}, uint(23)={random.uint(23)}, dec={random.dec()}, float={random.float()}");
            }
        }
    }