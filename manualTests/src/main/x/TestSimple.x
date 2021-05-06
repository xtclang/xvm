module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Bit b0 = Bit:0;
        Bit b1 = Bit:1;
//        Bit b2 = Bit:2;
        Nibble n0 = Nibble:0;
        Nibble nf = Nibble:0xF;
//        Nibble n1f = Nibble:0x1F;
        console.println($"b0={b0}, b1={b1}, n0={n0}, nf={nf}");
//        console.println($"b0={b0}, b1={b1}, b2={b2}, n0={n0}, nf={nf}, n1f={n1f}");
        }
    }