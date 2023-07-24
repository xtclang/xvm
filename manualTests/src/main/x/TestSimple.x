module TestSimple {
    @Inject Console console;

    void run() {

        Int128 i = 128;
        Byte[] bytes = i.toByteArray();
        console.print(i.toByteArray());

        IntN i2 = Int:2.as(IntN).pow(129) + 2;

        console.print(i2.toByteArray());
        console.print(i2.toBitArray());
        console.print($"{i2.bitLength=}");
        console.print($"{i2.leftmostBit=}");
        console.print($"{i2.rightmostBit=}");
        console.print($"{i2.trailingZeroCount=}");
    }
}