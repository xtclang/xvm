package org.xvm.util;


import java.util.BitSet;
import java.util.Random;


/**
 * A test for ConstBitSet.
 */
public class ConstBitSetTest
    {
    public static void main(String[] args)
        {
        BitSet bs = new BitSet();

        for (int i = 0; i < 1000; ++i)
            {
            ConstBitSet cbs = new ConstBitSet(bs);
            validate(bs, cbs, i, "");
            BitSet bs2 = cbs.toBitSet();
            assert bs.equals(bs2);

            BitSet bsI = ConstBitSet.invert(bs);
            ConstBitSet cbsI = new ConstBitSet(bsI);
            validate(bsI, cbsI, i, " (inverse)");
            BitSet bs2I = cbsI.toBitSet();
            assert bsI.equals(bs2I);

            bs.set((i + 1) * 1000);
            bs.set(s_rnd.nextInt((i + 1) * 1000));
            }
        }

    static void validate(BitSet bs, ConstBitSet cbs, int iTest, String sTest)
        {
        System.out.println("Test #" + iTest + sTest
            + ", size=" + cbs.size() + " (" + (cbs.size()/8) + " bytes)"
            + ", length=" + cbs.length()
            + ", isEmpty=" + cbs.isEmpty()
            + ", cardinality=" + cbs.cardinality()
            + ", compression=" + calcPct(bs.size() + 32, cbs.size()));

        assert bs.isEmpty() == cbs.isEmpty();
        assert bs.cardinality() == cbs.cardinality();
        assert bs.length() == cbs.length();

        int iLast = bs.length();
        for (int iBit = bs.nextSetBit(0); iBit >= 0; iBit = bs.nextSetBit(iBit + 1))
            {
            assert cbs.get(iBit);
            }

        for (int i = 0; i < 100; ++i)
            {
            int iBit = s_rnd.nextInt(iLast + 100);
            assert bs.get(iBit) == cbs.get(iBit);
            }

// code for comparing to:
//  - Roaring Bitmap - https://github.com/RoaringBitmap/RoaringBitmap
//  - EWAH - https://github.com/lemire/javaewah
//
//        org.roaringbitmap.RoaringBitmap rbm = new org.roaringbitmap.RoaringBitmap();
//        com.googlecode.javaewah.EWAHCompressedBitmap ecb = new com.googlecode.javaewah.EWAHCompressedBitmap();
//        for (int iBit = bs.nextSetBit(0); iBit >= 0; iBit = bs.nextSetBit(iBit + 1))
//            {
//            rbm.add(iBit);
//            ecb.add(iBit);
//            }
//        int cbRoar = rbm.getSizeInBytes();
//        int cbEWAH = ecb.sizeInBytes();
//        System.out.println("  RoaringBitmap size=" + cbRoar + " (" + calcPct(cbs.size()/8, cbRoar) + ")"
//                + ", EWAHCompressedBitmap size=" + cbEWAH + " (" + calcPct(cbs.size()/8, cbEWAH) + ")");
//
// results:
//  - Roaring bitmap generally 60% smaller on sparse populations, and 10x the size on mostly-full
//  - EWAH is 50% larger on sparse populations, and 750x the size on mostly-full
// conclusion:
//  - both libs degrade poorly and need an inverting optimization
//  - Roaring looks great for super sparse data sets, but it is an enormous amount of code to port
//    at this time, and its performance is not optimized for the simple, read-only case that we have
//    (so we should put it on our list of "libraries that would be handy to have ported")
        }

    static String calcPct(int nFrom, int nTo)
        {
        int nPct = (int) ((((double) nTo) - nFrom) / nFrom * 100);
        return nPct < 0 ? nPct + "%" : "+" + nPct + "%";
        }

    private static final Random s_rnd = new Random();
    }