/**
 * Tests for Decimal numbers.
 *
 * @see http://www.cplusplus.com/reference/cmath/round
 */
class Decimals {

    static Dec64[] numbers  = [2.3, 3.8, 5.5, -2.3, -3.8, -5.5];
    static Int[]   rounds   = [2,   4,   6,   -2,   -4,   -6  ];
    static Int[]   floors   = [2,   3,   5,   -3,   -4,   -6  ];
    static Int[]   ceilings = [3,   4,   6,   -2,   -3,   -5  ];
    static Int[]   toZeros  = [2,   3,   5,   -2,   -3,   -5  ];

    @Test
    void round() {
        for (Int i : 0 ..< numbers.size) {
            assert numbers[i].round() == rounds[i];
        }
    }

    @Test
    void floor() {
        for (Int i : 0 ..< numbers.size) {
            assert numbers[i].floor() == floors[i];
        }
    }

    @Test
    void ceiling() {
        for (Int i : 0 ..< numbers.size) {
            assert numbers[i].ceil() == ceilings[i];
        }
    }

    @Test
    void toZero() {
        for (Int i : 0 ..< numbers.size) {
            assert numbers[i].round(TowardZero) == toZeros[i];
        }
    }

    @Test
    void fromBytes() {
        for (Int i : 0 ..< numbers.size) {
            Dec64  n0 = numbers[i];
            Dec64  n1 = new Dec64(n0.toByteArray());
            assert n1 == n0;
        }
    }

    @Test
    void fromBits() {
        for (Int i : 0 ..< numbers.size) {
            Dec64  n0 = numbers[i];
            Dec64  n1 = new Dec64(n0.toBitArray());
            assert n1 == n0;
        }
    }

    @Test
    void literals() {
        Dec64 pi64a = FPNumber.PI;
        Dec64 pi64b = FPNumber.PI.toDec64();
        assert pi64a.toString() == pi64b.toString();
    }
}