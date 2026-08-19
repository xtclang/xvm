import ecstasy.numbers.UIntNumber;

class IntNumberTests {

    void run() {
        testLeftmostBit();
        testRightmostBit();
        testLeadingZeroCount();
        testTrailingZeroCount();
    }

    void testLeftmostBit() {
        Int8 i8 = 0x34;
        assert i8.leftmostBit == 0x20;
        testLeftmostBitIntNumber(i8, 0x20);
        Int8 i8Zero = 0;
        assert i8Zero.leftmostBit == 0;
        testLeftmostBitIntNumber(i8Zero, 0);
        Int8 i8Max = Int8.MaxValue;
        assert i8Max.leftmostBit == 0x40;
        testLeftmostBitIntNumber(i8Max, 0x40);
        Int8 i8Neg = Int8.MinValue;
        assert i8Neg.leftmostBit == Int8.MinValue;
        testLeftmostBitIntNumber(i8Neg, Int8.MinValue);

        Int16 i16 = 0x348;
        assert i16.leftmostBit == 0x200;
        testLeftmostBitIntNumber(i16, 0x200);
        Int16 i16Zero = 0;
        assert i16Zero.leftmostBit == 0;
        testLeftmostBitIntNumber(i16Zero, 0);
        Int16 i16Max = Int16.MaxValue;
        assert i16Max.leftmostBit == 0x4000;
        testLeftmostBitIntNumber(i16Max, 0x4000);
        Int16 i16Neg = -130;
        assert i16Neg.leftmostBit == -0x8000;
        testLeftmostBitIntNumber(i16Neg, -0x8000);

        Int32 i32a = 0x34;
        assert i32a.leftmostBit == 0x20;
        testLeftmostBitIntNumber(i32a, 0x20);
        Int32 i32b = 0x3_4891;
        assert i32b.leftmostBit == 0x2_0000;
        testLeftmostBitIntNumber(i32b, 0x2_0000);
        Int32 i32Zero = 0;
        assert i32Zero.leftmostBit == 0;
        testLeftmostBitIntNumber(i32Zero, 0);
        Int32 i32Max = Int32.MaxValue;
        assert i32Max.leftmostBit == 0x4000_0000;
        testLeftmostBitIntNumber(i32Max, 0x4000_0000);
        Int32 i32Neg = -18920;
        assert i32Neg.leftmostBit == -0x8000_0000;
        testLeftmostBitIntNumber(i32Neg, -0x8000_0000);

        Int64 i64a = 0x3_4891;
        assert i64a.leftmostBit == 0x2_0000;
        testLeftmostBitIntNumber(i64a, 0x2_0000);
        Int64 i64b = 0x3_4891_1234_9876;
        assert i64b.leftmostBit == 0x2_0000_0000_0000;
        testLeftmostBitIntNumber(i64b, 0x2_0000_0000_0000);
        Int64 i64Zero = 0;
        assert i64Zero.leftmostBit == 0;
        testLeftmostBitIntNumber(i64Zero, 0);
        Int64 i64Max = Int64.MaxValue;
        assert i64Max.leftmostBit == 0x4000_0000_0000_0000;
        testLeftmostBitIntNumber(i64Max, 0x4000_0000_0000_0000);
        Int64 i64Neg = -18920;
        assert i64Neg.leftmostBit == -0x8000_0000_0000_0000;
        testLeftmostBitIntNumber(i64Neg, -0x8000_0000_0000_0000);

        Int128 i128a = 0x3_4891;
        assert i128a.leftmostBit == 0x2_0000;
        testLeftmostBitIntNumber(i128a, 0x2_0000);
        Int128 i128b = 0x3_4891_1234_9876;
        assert i128b.leftmostBit == 0x2_0000_0000_0000;
        testLeftmostBitIntNumber(i128b, 0x2_0000_0000_0000);
        Int128 i128c = 0x3_4891_1234_9876_1234_9876_1234_9876;
        assert i128c.leftmostBit == 0x2_0000_0000_0000_0000_0000_0000_0000;
        testLeftmostBitIntNumber(i128c, 0x2_0000_0000_0000_0000_0000_0000_0000);
        Int128 i128Zero = 0;
        assert i128Zero.leftmostBit == 0;
        testLeftmostBitIntNumber(i128Zero, 0);
        Int128 i128Max = Int128.MaxValue;
        assert i128Max.leftmostBit == 0x4000_0000_0000_0000_0000_0000_0000_0000;
        testLeftmostBitIntNumber(i128Max, 0x4000_0000_0000_0000_0000_0000_0000_0000);
        Int128 i128Neg = -18920;
        assert i128Neg.leftmostBit == -0x8000_0000_0000_0000_0000_0000_0000_0000;
        testLeftmostBitIntNumber(i128Neg, -0x8000_0000_0000_0000_0000_0000_0000_0000);


        Nibble n = 0x3;
        assert n.leftmostBit == 0x2;
        testLeftmostBitUIntNumber(n, 0x2);
        Nibble nZero = 0;
        assert nZero.leftmostBit == 0;
        testLeftmostBitUIntNumber(nZero, 0);
        Nibble nMax = Nibble.MaxValue;
        assert nMax.leftmostBit == 0x8;
        testLeftmostBitUIntNumber(nMax, 0x8);


        UInt8 u8 = 0x34;
        assert u8.leftmostBit == 0x20;
        testLeftmostBitUIntNumber(u8, 0x20);
        UInt8 u8Zero = 0;
        assert u8Zero.leftmostBit == 0;
        testLeftmostBitUIntNumber(u8Zero, 0);
        UInt8 u8Max = UInt8.MaxValue;
        assert u8Max.leftmostBit == 0x80;
        testLeftmostBitUIntNumber(u8Max, 0x80);

        UInt16 u16 = 0x348;
        assert u16.leftmostBit == 0x200;
        testLeftmostBitUIntNumber(u16, 0x200);
        UInt16 u16Zero = 0;
        assert u16Zero.leftmostBit == 0;
        testLeftmostBitUIntNumber(u16Zero, 0);
        UInt16 u16Max = UInt16.MaxValue;
        assert u16Max.leftmostBit == 0x8000;
        testLeftmostBitUIntNumber(u16Max, 0x8000);

        UInt32 u32a = 0x34;
        assert u32a.leftmostBit == 0x20;
        testLeftmostBitUIntNumber(u32a, 0x20);
        UInt32 u32b = 0x3_4891;
        assert u32b.leftmostBit == 0x2_0000;
        testLeftmostBitUIntNumber(u32b, 0x2_0000);
        UInt32 u32Zero = 0;
        assert u32Zero.leftmostBit == 0;
        testLeftmostBitUIntNumber(u32Zero, 0);
        UInt32 u32Max = UInt32.MaxValue;
        assert u32Max.leftmostBit == 0x8000_0000;
        testLeftmostBitUIntNumber(u32Max, 0x8000_0000);

        UInt64 u64a = 0x3_4891;
        assert u64a.leftmostBit == 0x2_0000;
        testLeftmostBitUIntNumber(u64a, 0x2_0000);
        UInt64 u64b = 0x3_4891_1234_9876;
        assert u64b.leftmostBit == 0x2_0000_0000_0000;
        testLeftmostBitUIntNumber(u64b, 0x2_0000_0000_0000);
        UInt64 u64Zero = 0;
        assert u64Zero.leftmostBit == 0;
        testLeftmostBitUIntNumber(u64Zero, 0);
        UInt64 u64Max = UInt64.MaxValue;
        assert u64Max.leftmostBit == 0x8000_0000_0000_0000;
        testLeftmostBitUIntNumber(u64Max, 0x8000_0000_0000_0000);

        UInt128 u128a = 0x3_4891;
        assert u128a.leftmostBit == 0x2_0000;
        testLeftmostBitUIntNumber(u128a, 0x2_0000);
        UInt128 u128b = 0x3_4891_1234_9876;
        assert u128b.leftmostBit == 0x2_0000_0000_0000;
        testLeftmostBitUIntNumber(u128b, 0x2_0000_0000_0000);
        UInt128 u128c = 0x3_4891_1234_9876_1234_9876_1234_9876;
        assert u128c.leftmostBit == 0x2_0000_0000_0000_0000_0000_0000_0000;
        testLeftmostBitUIntNumber(u128c, 0x2_0000_0000_0000_0000_0000_0000_0000);
        UInt128 u128Zero = 0;
        assert u128Zero.leftmostBit == 0;
        testLeftmostBitUIntNumber(u128Zero, 0);
        UInt128 u128Max = UInt128.MaxValue;
        assert u128Max.leftmostBit == 0x8000_0000_0000_0000_0000_0000_0000_0000;
        testLeftmostBitUIntNumber(u128Max, 0x8000_0000_0000_0000_0000_0000_0000_0000);

    }

    void testLeftmostBitIntNumber(IntNumber i, IntLiteral expected) {
        assert i.leftmostBit.toIntN() == expected.toIntN();
    }

    void testLeftmostBitUIntNumber(UIntNumber u, IntLiteral expected) {
        assert u.leftmostBit.toIntN() == expected.toIntN();
        testLeftmostBitIntNumber(u, expected);
    }


    void testRightmostBit() {
        Int8 i8 = 0x36;
        assert i8.rightmostBit == 0x02;
        testRightmostBitIntNumber(i8, 0x02);
        Int8 i8Zero = 0;
        assert i8Zero.rightmostBit == 0;
        testRightmostBitIntNumber(i8Zero, 0);
        Int8 i8Max = Int8.MaxValue;
        assert i8Max.rightmostBit == 0x01;
        testRightmostBitIntNumber(i8Max, 0x01);
        Int8 i8Min = Int8.MinValue;
        assert i8Min.rightmostBit == Int8.MinValue;
        testRightmostBitIntNumber(i8Min, Int8.MinValue);

        Int16 i16 = 0x0346;
        assert i16.rightmostBit == 0x0002;
        testRightmostBitIntNumber(i16, 0x0002);
        Int16 i16Zero = 0;
        assert i16Zero.rightmostBit == 0;
        testRightmostBitIntNumber(i16Zero, 0);
        Int16 i16Max = Int16.MaxValue;
        assert i16Max.rightmostBit == 0x01;
        testRightmostBitIntNumber(i16Max, 0x01);
        Int16 i16Min = Int16.MinValue;
        assert i16Min.rightmostBit == Int16.MinValue;
        testRightmostBitIntNumber(i16Min, Int16.MinValue);

        Int32 i32a = 0x36;
        assert i32a.rightmostBit == 0x02;
        testRightmostBitIntNumber(i32a, 0x02);
        Int32 i32b = 0x36_0000;
        assert i32b.rightmostBit == 0x02_0000;
        testRightmostBitIntNumber(i32b, 0x02_0000);
        Int32 i32Zero = 0;
        assert i32Zero.rightmostBit == 0;
        testRightmostBitIntNumber(i32Zero, 0);
        Int32 i32Max = Int32.MaxValue;
        assert i32Max.rightmostBit == 0x01;
        testRightmostBitIntNumber(i32Max, 0x01);
        Int32 i32Min = Int32.MinValue;
        assert i32Min.rightmostBit == Int32.MinValue;
        testRightmostBitIntNumber(i32Min, Int32.MinValue);

        Int64 i64a = 0x36_0000;
        assert i64a.rightmostBit == 0x02_0000;
        testRightmostBitIntNumber(i64a, 0x02_0000);
        Int64 i64b = 0x3_4891_1234_0000;
        assert i64b.rightmostBit == 0x0004_0000;
        testRightmostBitIntNumber(i64b, 0x0004_0000);
        Int64 i64Zero = 0;
        assert i64Zero.rightmostBit == 0;
        testRightmostBitIntNumber(i64Zero, 0);
        Int64 i64Max = Int64.MaxValue;
        assert i64Max.rightmostBit == 0x01;
        testRightmostBitIntNumber(i64Max, 0x01);
        Int64 i64Min = Int64.MinValue;
        assert i64Min.rightmostBit == Int64.MinValue;
        testRightmostBitIntNumber(i64Min, Int64.MinValue);

        Int128 i128a = 0x36_0000;
        assert i128a.rightmostBit == 0x2_0000;
        testRightmostBitIntNumber(i128a, 0x2_0000);
        Int128 i128b = 0x3_4891_0000_0000;
        assert i128b.rightmostBit == 0x1_0000_0000;
        testRightmostBitIntNumber(i128b, 0x1_0000_0000);
        Int128 i128c = 0x3_4891_1234_9876_0000_0000_0000_0000;
        assert i128c.rightmostBit == 0x2_0000_0000_0000_0000;
        testRightmostBitIntNumber(i128c, 0x2_0000_0000_0000_0000);
        Int128 i128Zero = 0;
        assert i128Zero.rightmostBit == 0;
        testRightmostBitIntNumber(i128Zero, 0);
        Int128 i128Max = Int128.MaxValue;
        assert i128Max.rightmostBit == 0x01;
        testRightmostBitIntNumber(i128Max, 0x01);
        Int128 i128Min = Int128.MinValue;
        assert i128Min.rightmostBit == Int128.MinValue;
        testRightmostBitIntNumber(i128Min, Int128.MinValue);


        Nibble n = 0xC;
        assert n.rightmostBit == 0x4;
        testRightmostBitUIntNumber(n, 0x4);
        Nibble nZero = 0;
        assert nZero.rightmostBit == 0;
        testRightmostBitUIntNumber(nZero, 0);
        Nibble nMax = Nibble.MaxValue;
        assert nMax.rightmostBit == 0x1;
        testRightmostBitUIntNumber(nMax, 0x1);

        UInt8 u8 = 0x34;
        assert u8.rightmostBit == 0x04;
        testRightmostBitUIntNumber(u8, 0x04);
        UInt8 u8Zero = 0;
        assert u8Zero.rightmostBit == 0;
        testRightmostBitUIntNumber(u8Zero, 0);
        UInt8 u8Max = UInt8.MaxValue;
        assert u8Max.rightmostBit == 0x01;
        testRightmostBitUIntNumber(u8Max, 0x01);

        UInt16 u16 = 0x0348;
        assert u16.rightmostBit == 0x0008;
        testRightmostBitUIntNumber(u16, 0x0008);
        UInt16 u16Zero = 0;
        assert u16Zero.rightmostBit == 0;
        testRightmostBitUIntNumber(u16Zero, 0);
        UInt16 u16Max = UInt16.MaxValue;
        assert u16Max.rightmostBit == 0x01;
        testRightmostBitUIntNumber(u16Max, 0x01);

        UInt32 u32a = 0x34;
        assert u32a.rightmostBit == 0x04;
        testRightmostBitUIntNumber(u32a, 0x04);
        UInt32 u32b = 0x3_0000;
        assert u32b.rightmostBit == 0x1_0000;
        testRightmostBitUIntNumber(u32b, 0x1_0000);
        UInt32 u32Zero = 0;
        assert u32Zero.rightmostBit == 0;
        testRightmostBitUIntNumber(u32Zero, 0);
        UInt32 u32Max = UInt32.MaxValue;
        assert u32Max.rightmostBit == 0x01;
        testRightmostBitUIntNumber(u32Max, 0x01);

        UInt64 u64a = 0xAB93_0000;
        assert u64a.rightmostBit == 0x0001_0000;
        testRightmostBitUIntNumber(u64a, 0x0001_0000);
        UInt64 u64b = 0x3_4891_0000_0000;
        assert u64b.rightmostBit == 0x1_0000_0000;
        testRightmostBitUIntNumber(u64b, 0x1_0000_0000);
        UInt64 u64Zero = 0;
        assert u64Zero.rightmostBit == 0;
        testRightmostBitUIntNumber(u64Zero, 0);
        UInt64 u64Max = UInt64.MaxValue;
        assert u64Max.rightmostBit == 0x01;
        testRightmostBitUIntNumber(u64Max, 0x01);

        UInt128 u128a = 0xACF3_0000;
        assert u128a.rightmostBit == 0x1_0000;
        testRightmostBitUIntNumber(u128a, 0x1_0000);
        UInt128 u128b = 0x3_4891_0000_0000;
        assert u128b.rightmostBit == 0x1_0000_0000;
        testRightmostBitUIntNumber(u128b, 0x1_0000_0000);
        UInt128 u128c = 0x3_4891_1234_9876_0000_0000_0000_0000;
        assert u128c.rightmostBit == 0x2_0000_0000_0000_0000;
        testRightmostBitUIntNumber(u128c, 0x2_0000_0000_0000_0000);
        UInt128 u128Zero = 0;
        assert u128Zero.rightmostBit == 0;
        testRightmostBitUIntNumber(u128Zero, 0);
        UInt128 u128Max = UInt128.MaxValue;
        assert u128Max.rightmostBit == 0x01;
        testRightmostBitUIntNumber(u128Max, 0x01);

    }

    void testRightmostBitIntNumber(IntNumber i, IntLiteral expected) {
        assert i.rightmostBit.toIntN() == expected.toIntN();
    }

    void testRightmostBitUIntNumber(UIntNumber u, IntLiteral expected) {
        assert u.rightmostBit.toIntN() == expected.toIntN();
        testRightmostBitIntNumber(u, expected);
    }


    void testLeadingZeroCount() {
        Int8 i8 = 0x35;
        assert i8.leadingZeroCount == 2;
        testLeadingZeroCountIntNumber(i8, 2);
        Int8 i8Zero = 0;
        assert i8Zero.leadingZeroCount == 8;
        testLeadingZeroCountIntNumber(i8Zero, 8);
        Int8 i8Max = Int8.MaxValue;
        assert i8Max.leadingZeroCount == 1;
        testLeadingZeroCountIntNumber(i8Max, 1);
        Int8 i8Neg = -10;
        assert i8Neg.leadingZeroCount == 0;
        testLeadingZeroCountIntNumber(i8Neg, 0);

        Int16 i16a = 0x3500;
        assert i16a.leadingZeroCount == 2;
        testLeadingZeroCountIntNumber(i16a, 2);
        Int16 i16b = 0x35;
        assert i16b.leadingZeroCount == 10;
        testLeadingZeroCountIntNumber(i16b, 10);
        Int16 i16Zero = 0;
        assert i16Zero.leadingZeroCount == 16;
        testLeadingZeroCountIntNumber(i16Zero, 16);
        Int16 i16Max = Int16.MaxValue;
        assert i16Max.leadingZeroCount == 1;
        testLeadingZeroCountIntNumber(i16Max, 1);
        Int16 i16Neg = -10;
        assert i16Neg.leadingZeroCount == 0;
        testLeadingZeroCountIntNumber(i16Neg, 0);

        Int32 i32a = 0x3500_0000;
        assert i32a.leadingZeroCount == 2;
        testLeadingZeroCountIntNumber(i32a, 2);
        Int32 i32b = 0x3500;
        assert i32b.leadingZeroCount == 18;
        testLeadingZeroCountIntNumber(i32b, 18);
        Int32 i32c = 0x35;
        assert i32c.leadingZeroCount == 26;
        testLeadingZeroCountIntNumber(i32c, 26);
        Int32 i32Zero = 0;
        assert i32Zero.leadingZeroCount == 32;
        testLeadingZeroCountIntNumber(i32Zero, 32);
        Int32 i32Max = Int32.MaxValue;
        assert i32Max.leadingZeroCount == 1;
        testLeadingZeroCountIntNumber(i32Max, 1);
        Int32 i32Neg = -10;
        assert i32Neg.leadingZeroCount == 0;
        testLeadingZeroCountIntNumber(i32Neg, 0);

        Int64 i64a = 0x3500_0000_0000_0000;
        assert i64a.leadingZeroCount == 2;
        testLeadingZeroCountIntNumber(i64a, 2);
        Int64 i64b = 0x3500;
        assert i64b.leadingZeroCount == 50;
        testLeadingZeroCountIntNumber(i64b, 50);
        Int64 i64c = 0x35;
        assert i64c.leadingZeroCount == 58;
        testLeadingZeroCountIntNumber(i64c, 58);
        Int64 i64Zero = 0;
        assert i64Zero.leadingZeroCount == 64;
        testLeadingZeroCountIntNumber(i64Zero, 64);
        Int64 i64Max = Int64.MaxValue;
        assert i64Max.leadingZeroCount == 1;
        testLeadingZeroCountIntNumber(i64Max, 1);
        Int64 i64Neg = -10;
        assert i64Neg.leadingZeroCount == 0;
        testLeadingZeroCountIntNumber(i64Neg, 0);

        Int128 i128a = 0x3500_0000_0000_0000_0000_0000_0000_0000;
        assert i128a.leadingZeroCount == 2;
        testLeadingZeroCountIntNumber(i128a, 2);
        Int128 i128b = 0x3500_0000_0000_0000_0000;
        assert i128b.leadingZeroCount == 50;
        testLeadingZeroCountIntNumber(i128b, 50);
        Int128 i128c = 0x35;
        assert i128c.leadingZeroCount == 122;
        testLeadingZeroCountIntNumber(i128c, 122);
        Int128 i128Zero = 0;
        assert i128Zero.leadingZeroCount == 128;
        testLeadingZeroCountIntNumber(i128Zero, 128);
        Int128 i128Max = Int128.MaxValue;
        assert i128Max.leadingZeroCount == 1;
        testLeadingZeroCountIntNumber(i128Max, 1);
        Int128 i128Neg = -10;
        assert i128Neg.leadingZeroCount == 0;
        testLeadingZeroCountIntNumber(i128Neg, 0);


        Nibble u8 = 0x3;
        assert u8.leadingZeroCount == 2;
        testLeadingZeroCountUIntNumber(u8, 2);
        Nibble u8Zero = 0;
        assert u8Zero.leadingZeroCount == 4;
        testLeadingZeroCountUIntNumber(u8Zero, 4);
        Nibble u8Max = Nibble.MaxValue;
        assert u8Max.leadingZeroCount == 0;
        testLeadingZeroCountUIntNumber(u8Max, 0);


        UInt8 n = 0x35;
        assert n.leadingZeroCount == 2;
        testLeadingZeroCountUIntNumber(n, 2);
        UInt8 nZero = 0;
        assert nZero.leadingZeroCount == 8;
        testLeadingZeroCountUIntNumber(nZero, 8);
        UInt8 nMax = UInt8.MaxValue;
        assert nMax.leadingZeroCount == 0;
        testLeadingZeroCountUIntNumber(nMax, 0);

        UInt16 u16a = 0x3500;
        assert u16a.leadingZeroCount == 2;
        testLeadingZeroCountUIntNumber(u16a, 2);
        UInt16 u16b = 0x35;
        assert u16b.leadingZeroCount == 10;
        testLeadingZeroCountUIntNumber(u16b, 10);
        UInt16 u16Zero = 0;
        assert u16Zero.leadingZeroCount == 16;
        testLeadingZeroCountUIntNumber(u16Zero, 16);
        UInt16 u16Max = UInt16.MaxValue;
        assert u16Max.leadingZeroCount == 0;
        testLeadingZeroCountUIntNumber(u16Max, 0);

        UInt32 u32a = 0x3500_0000;
        assert u32a.leadingZeroCount == 2;
        testLeadingZeroCountUIntNumber(u32a, 2);
        UInt32 u32b = 0x3500;
        assert u32b.leadingZeroCount == 18;
        testLeadingZeroCountUIntNumber(u32b, 18);
        UInt32 u32c = 0x35;
        assert u32c.leadingZeroCount == 26;
        testLeadingZeroCountUIntNumber(u32c, 26);
        UInt32 u32Zero = 0;
        assert u32Zero.leadingZeroCount == 32;
        testLeadingZeroCountUIntNumber(u32Zero, 32);
        UInt32 u32Max = UInt32.MaxValue;
        assert u32Max.leadingZeroCount == 0;
        testLeadingZeroCountUIntNumber(u32Max, 0);

        UInt64 u64a = 0x3500_0000_0000_0000;
        assert u64a.leadingZeroCount == 2;
        testLeadingZeroCountUIntNumber(u64a, 2);
        UInt64 u64b = 0x3500;
        assert u64b.leadingZeroCount == 50;
        testLeadingZeroCountUIntNumber(u64b, 50);
        UInt64 u64c = 0x35;
        assert u64c.leadingZeroCount == 58;
        testLeadingZeroCountUIntNumber(u64c, 58);
        UInt64 u64Zero = 0;
        assert u64Zero.leadingZeroCount == 64;
        testLeadingZeroCountUIntNumber(u64Zero, 64);
        UInt64 u64Max = UInt64.MaxValue;
        assert u64Max.leadingZeroCount == 0;
        testLeadingZeroCountUIntNumber(u64Max, 0);

        UInt128 u128a = 0x3500_0000_0000_0000_0000_0000_0000_0000;
        assert u128a.leadingZeroCount == 2;
        testLeadingZeroCountUIntNumber(u128a, 2);
        UInt128 u128b = 0x3500_0000_0000_0000_0000;
        assert u128b.leadingZeroCount == 50;
        testLeadingZeroCountUIntNumber(u128b, 50);
        UInt128 u128c = 0x35;
        assert u128c.leadingZeroCount == 122;
        testLeadingZeroCountUIntNumber(u128c, 122);
        UInt128 u128Zero = 0;
        assert u128Zero.leadingZeroCount == 128;
        testLeadingZeroCountUIntNumber(u128Zero, 128);
        UInt128 u128Max = UInt128.MaxValue;
        assert u128Max.leadingZeroCount == 0;
        testLeadingZeroCountUIntNumber(u128Max, 0);
    }

    void testLeadingZeroCountIntNumber(IntNumber i, Int expected) {
        assert i.leadingZeroCount == expected;
    }

    void testLeadingZeroCountUIntNumber(UIntNumber u, Int expected) {
        assert u.leadingZeroCount == expected;
        testLeadingZeroCountIntNumber(u, expected);
    }


    void testTrailingZeroCount() {
        Int8 i8 = 0x74;
        assert i8.trailingZeroCount == 2;
        testTrailingZeroCountIntNumber(i8, 2);
        Int8 i8Zero = 0;
        assert i8Zero.trailingZeroCount == 8;
        testTrailingZeroCountIntNumber(i8Zero, 8);
        Int8 i8Max = Int8.MaxValue;
        assert i8Max.trailingZeroCount == 0;
        testTrailingZeroCountIntNumber(i8Max, 0);

        Int16 i16a = 0x7400;
        assert i16a.trailingZeroCount == 10;
        testTrailingZeroCountIntNumber(i16a, 10);
        Int16 i16b = 0x74;
        assert i16b.trailingZeroCount == 2;
        testTrailingZeroCountIntNumber(i16b, 2);
        Int16 i16Zero = 0;
        assert i16Zero.trailingZeroCount == 16;
        testTrailingZeroCountIntNumber(i16Zero, 16);
        Int16 i16Max = Int16.MaxValue;
        assert i16Max.trailingZeroCount == 0;
        testTrailingZeroCountIntNumber(i16Max, 0);

        Int32 i32a = 0x7400_0000;
        assert i32a.trailingZeroCount == 26;
        testTrailingZeroCountIntNumber(i32a, 26);
        Int32 i32b = 0x7400;
        assert i32b.trailingZeroCount == 10;
        testTrailingZeroCountIntNumber(i32b, 10);
        Int32 i32c = 0x74;
        assert i32c.trailingZeroCount == 2;
        testTrailingZeroCountIntNumber(i32c, 2);
        Int32 i32Zero = 0;
        assert i32Zero.trailingZeroCount == 32;
        testTrailingZeroCountIntNumber(i32Zero, 32);
        Int32 i32Max = Int32.MaxValue;
        assert i32Max.trailingZeroCount == 0;
        testTrailingZeroCountIntNumber(i32Max, 0);

        Int64 i64a = 0x7400_0000_0000_0000;
        assert i64a.trailingZeroCount == 58;
        testTrailingZeroCountIntNumber(i64a, 58);
        Int64 i64b = 0x7400;
        assert i64b.trailingZeroCount == 10;
        testTrailingZeroCountIntNumber(i64b, 10);
        Int64 i64c = 0x74;
        assert i64c.trailingZeroCount == 2;
        testTrailingZeroCountIntNumber(i64c, 2);
        Int64 i64Zero = 0;
        assert i64Zero.trailingZeroCount == 64;
        testTrailingZeroCountIntNumber(i64Zero, 64);
        Int64 i64Max = Int64.MaxValue;
        assert i64Max.trailingZeroCount == 0;
        testTrailingZeroCountIntNumber(i64Max, 0);

        Int128 i128a = 0x7400_0000_0000_0000_0000_0000_0000_0000;
        assert i128a.trailingZeroCount == 122;
        testTrailingZeroCountIntNumber(i128a, 122);
        Int128 i128b = 0x7400_0000_0000_0000_0000;
        assert i128b.trailingZeroCount == 74;
        testTrailingZeroCountIntNumber(i128b, 74);
        Int128 i128c = 0x74;
        assert i128c.trailingZeroCount == 2;
        testTrailingZeroCountIntNumber(i128c, 2);
        Int128 i128Zero = 0;
        assert i128Zero.trailingZeroCount == 128;
        testTrailingZeroCountIntNumber(i128Zero, 128);
        Int128 i128Max = Int128.MaxValue;
        assert i128Max.trailingZeroCount == 0;
        testTrailingZeroCountIntNumber(i128Max, 0);


        Nibble u8 = 0x4;
        assert u8.trailingZeroCount == 2;
        testTrailingZeroCountUIntNumber(u8, 2);
        Nibble u8Zero = 0;
        assert u8Zero.trailingZeroCount == 4;
        testTrailingZeroCountUIntNumber(u8Zero, 4);
        Nibble u8Max = Nibble.MaxValue;
        assert u8Max.trailingZeroCount == 0;
        testTrailingZeroCountUIntNumber(u8Max, 0);


        UInt8 n = 0x74;
        assert n.trailingZeroCount == 2;
        testTrailingZeroCountUIntNumber(n, 2);
        UInt8 nZero = 0;
        assert nZero.trailingZeroCount == 8;
        testTrailingZeroCountUIntNumber(nZero, 8);
        UInt8 nMax = UInt8.MaxValue;
        assert nMax.trailingZeroCount == 0;
        testTrailingZeroCountUIntNumber(nMax, 0);

        UInt16 u16a = 0x7400;
        assert u16a.trailingZeroCount == 10;
        testTrailingZeroCountUIntNumber(u16a, 10);
        UInt16 u16b = 0x74;
        assert u16b.trailingZeroCount == 2;
        testTrailingZeroCountUIntNumber(u16b, 2);
        UInt16 u16Zero = 0;
        assert u16Zero.trailingZeroCount == 16;
        testTrailingZeroCountUIntNumber(u16Zero, 16);
        UInt16 u16Max = UInt16.MaxValue;
        assert u16Max.trailingZeroCount == 0;
        testTrailingZeroCountUIntNumber(u16Max, 0);

        UInt32 u32a = 0x7400_0000;
        assert u32a.trailingZeroCount == 26;
        testTrailingZeroCountUIntNumber(u32a, 26);
        UInt32 u32b = 0x7400;
        assert u32b.trailingZeroCount == 10;
        testTrailingZeroCountUIntNumber(u32b, 10);
        UInt32 u32c = 0x74;
        assert u32c.trailingZeroCount == 2;
        testTrailingZeroCountUIntNumber(u32c, 2);
        UInt32 u32Zero = 0;
        assert u32Zero.trailingZeroCount == 32;
        testTrailingZeroCountUIntNumber(u32Zero, 32);
        UInt32 u32Max = UInt32.MaxValue;
        assert u32Max.trailingZeroCount == 0;
        testTrailingZeroCountUIntNumber(u32Max, 0);

        UInt64 u64a = 0x7400_0000_0000_0000;
        assert u64a.trailingZeroCount == 58;
        testTrailingZeroCountUIntNumber(u64a, 58);
        UInt64 u64b = 0x7400;
        assert u64b.trailingZeroCount == 10;
        testTrailingZeroCountUIntNumber(u64b, 10);
        UInt64 u64c = 0x74;
        assert u64c.trailingZeroCount == 2;
        testTrailingZeroCountUIntNumber(u64c, 2);
        UInt64 u64Zero = 0;
        assert u64Zero.trailingZeroCount == 64;
        testTrailingZeroCountUIntNumber(u64Zero, 64);
        UInt64 u64Max = UInt64.MaxValue;
        assert u64Max.trailingZeroCount == 0;
        testTrailingZeroCountUIntNumber(u64Max, 0);

        UInt128 u128a = 0x7400_0000_0000_0000_0000_0000_0000_0000;
        assert u128a.trailingZeroCount == 122;
        testTrailingZeroCountUIntNumber(u128a, 122);
        UInt128 u128b = 0x7400_0000_0000_0000_0000;
        assert u128b.trailingZeroCount == 74;
        testTrailingZeroCountUIntNumber(u128b, 74);
        UInt128 u128c = 0x74;
        assert u128c.trailingZeroCount == 2;
        testTrailingZeroCountUIntNumber(u128c, 2);
        UInt128 u128Zero = 0;
        assert u128Zero.trailingZeroCount == 128;
        testTrailingZeroCountUIntNumber(u128Zero, 128);
        UInt128 u128Max = UInt128.MaxValue;
        assert u128Max.trailingZeroCount == 0;
        testTrailingZeroCountUIntNumber(u128Max, 0);
    }

    void testTrailingZeroCountIntNumber(IntNumber i, Int expected) {
        assert i.trailingZeroCount == expected;
    }

    void testTrailingZeroCountUIntNumber(UIntNumber u, Int expected) {
        assert u.trailingZeroCount == expected;
        testTrailingZeroCountIntNumber(u, expected);
    }
}
