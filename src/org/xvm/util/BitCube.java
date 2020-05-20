package org.xvm.util;

import java.util.BitSet;

/**
 * BitCube is a multi-dimensional set of bits. For every multi-dimensional point (N0, N1, ...) the
 * corresponding bit's id is calculated as:
 *  <pre><code>
 *    N0 + D0*(N1 + D1*(N2 + ...))
 *  </pre></code>
 * where <i>Di</i> is a size of dimension <i>i</i>.
 */
public class BitCube
    {
    /**
     * Construct a BitCube of given dimension sizes.
     *
     * @param acSize  the dimension sizes of the cube
     */
    public BitCube(int[] acSize)
        {
        f_cDims  = acSize.length;
        f_acSize = acSize;

        assert f_cDims >= 2;

        int cTotal = 1;
        for (int i = 0; i < f_cDims; i++)
            {
            cTotal *= acSize[i];
            }
        f_cTotal = cTotal;
        f_bs     = new BitSet(cTotal);
        }


    // ----- BitCube API ---------------------------------------------------------------------------

    /**
     * @return the number of cube dimensions
     */
    public int getDimensions()
        {
        return f_cDims;
        }

    /**
     * @return the size of the specified dimension
     */
    public int getSize(int nDim)
        {
        return f_acSize[nDim];
        }

    /**
     * Set a bit for the specified multi-dimensional point.
     *
     * @param an  the point coordinates
     */
    public void set(int[] an)
        {
        f_bs.set(toId(an));
        }

    /**
     * @return true iff all the bits are set
     */
    public boolean isFull()
        {
        return f_bs.cardinality() == f_cTotal;
        }


    // ---- helpers --------------------------------------------------------------------------------

    /**
     * Convert a multi-dimensional point to a bit id.
     */
    private int toId(int[] an)
        {
        int n = an[f_cDims - 1];
        for (int i = f_cDims - 2; i >= 0; i--)
            {
            n *= f_acSize[i];
            n += an[i];
            }
        return n;
        }

    /**
     * Convert a bit id to a multi-dimensional point.
     */
    private int[] fromId(int n)
        {
        int[] an = new int[f_cDims];
        for (int i = 0; i < f_cDims; i++)
            {
            int c = f_acSize[i];

            an[i] = n % c;
            n     = n / c;
            }
        return an;
        }


    // ----- fields --------------------------------------------------------------------------------

    final private int    f_cDims;
    final private int    f_cTotal;
    final private int[]  f_acSize;
    final private BitSet f_bs;
    }

