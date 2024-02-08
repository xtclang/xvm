package org.xvm.util;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.math.BigInteger;


/**
 * A PackedInteger represents a signed, 2's-complement integer of 1 byte to 64KB (512Kb) in size.
 * <p/>
 * Signed values up to 8 bytes can be written and read as a Java <tt>long</tt> value; values up to the maximum size can
 * written and red as a Java BigInteger.
 * <p/>
 * The storage format (XVM Integer Packing, or "XIP") uses a variable length compression scheme. It defines four
 * internal formats for XIP'd integers:
 * <ul><li>
 * <b>Small</b>: For a value in the range {@code -64..127}, the value is encoded as the least significant byte of the
 * integer, as is. When reading in a packed integer, if the leftmost bits of the first byte are <b>not</b> {@code 10},
 * then the XIP'd integer is in small format, and simply requires sign extension of the first byte to the desired
 * integer size in order to form the integer value.
 * </li><li>
 * <b>Medium</b>: For a value in the range {@code -4096..4095} (13 bits), the value is encoded in two bytes. The first
 * byte contains the binary value {@code 100} in the most significant 3 bits, indicating the medium format. The 2's
 * complement integer value is formed from the least significant 5 bits of the first byte, sign extended to the desired
 * integer size and then left-shifted 8 bits, or'd with the bits of the second byte.
 * </li><li>
 * <b>Large</b>: For any 2's complement integer value from {@code 2..32} bytes (in the range {@code -(2^255)..2^255-1}),
 * let {@code b} be that number of bytes, with the XIP'd value encoded in {@code 1+b} bytes. The first byte contains the
 * binary value {@code 101} in the most significant 3 bits, and the remaining 5 bits are the unsigned value {@code b-1},
 * indicating the large format. (Note: Since {@code b} is at least {@code 2}, the value {@code b-1} is always non-zero.)
 * The following {@code b} bytes hold the 2's complement integer value.
 * </li><li>
 * <b>Huge</b>: For any integer value {@code n} larger than 32 bytes, let {@code b} be that number of bytes. The first
 * byte of the XIP'd integer is {@code 0xA0}, which indicates the huge format. The following bytes contain the value
 * {@code b}, encoded as a XIP'd integer. The following {@code b} bytes form the 2's complement integer value {@code n}.
 * </li></ul>
 * <p/>
 * To maximize density, the algorithms in this file use the smallest possible encoding for each value.
 */
public class PackedInteger
        implements Comparable<PackedInteger>
{
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an uninitialized PackedInteger.
     */
    public PackedInteger()
    {
    }

    /**
     * Construct a PackedInteger using a <tt>long</tt> value.
     *
     * @param lVal  the <tt>long</tt> value for the PackedInteger
     */
    public PackedInteger(long lVal)
    {
        setLong(lVal);
    }

    /**
     * Construct a PackedInteger using a <tt>BigInteger</tt> value.
     *
     * @param bigint  the <tt>BigInteger</tt> value for the PackedInteger
     */
    public PackedInteger(BigInteger bigint)
    {
        setBigInteger(bigint);
    }

    /**
     * Construct a PackedInteger by reading the packed value from a DataInput
     * stream.
     *
     * @param in  a DataInput stream to read from
     *
     * @throws IOException  if an I/O exception occurs while reading the data
     */
    public PackedInteger(DataInput in)
            throws IOException
    {
        readObject(in);
    }


    // ----- public methods ------------------------------------------------------------------------

    /**
     * Obtain a PackedInteger that has the specified <tt>long</tt> value. This
     * method is useful for taking advantage of the built-in "cache" of
     * commonly-used PackedInteger instances.
     *
     * @param lVal  the <tt>long</tt> value for the PackedInteger
     */
    public static PackedInteger valueOf(long lVal)
    {
        if (lVal >= CACHE_MIN & lVal <= CACHE_MAX)
        {
            final int iCache = (int) (lVal - CACHE_MIN);

            PackedInteger pint = CACHE[iCache];
            if (pint == null)
            {
                pint = new PackedInteger(lVal);
                CACHE[iCache] = pint;
            }

            return pint;
        }

        return new PackedInteger(lVal);
    }

    /**
     * The size of the "native" 2's-complement signed integer that would be
     * necessary to hold the value.
     *
     * @return a value between 1 and 32 inclusive
     */
    public int getSignedByteSize()
    {
        verifyInitialized();

        int nBytes = m_fBig
                ? calculateSignedByteCount(m_bigint)
                : Math.max(1, (((64 - Long.numberOfLeadingZeros(Math.max(m_lValue, ~m_lValue))) & 0x3F) + 7) / 8);

        assert nBytes >= 1 && nBytes <= 8192; // arbitrary limit
        return nBytes;
    }

    /**
     * The size of the unsigned integer that would be necessary to hold the value.
     *
     * @return a value between 1 and 32 inclusive
     */
    public int getUnsignedByteSize()
    {
        verifyInitialized();
        if (m_fBig && m_bigint.signum() < 0)
        {
            throw new IllegalStateException("negative value");
        }

        int nBytes = m_fBig
                ? calculateUnsignedByteCount(m_bigint)
                : Math.max(1, (((64 - Long.numberOfLeadingZeros(m_lValue)) + 7) / 8));

        assert nBytes >= 1 && nBytes <= 32; // arbitrary limit of 32 for the prototype
        return nBytes;
    }

    /**
     * Determine if the value of the PackedInteger is "big". The value is
     * considered to be "big" if it cannot fit into a <tt>long</tt>.
     *
     * @return true if the value of the PackedInteger does not fit into a long
     */
    public boolean isBig()
    {
        verifyInitialized();
        return m_fBig;
    }

    /**
     * Range check the PackedInteger.
     *
     * @param nLo  the low end of the range (inclusive)
     * @param nHi  the high end of the range (inclusive)
     *
     * @return true iff the PackedInteger is in the specified range
     */
    public boolean checkRange(long nLo, long nHi)
    {
        if (isBig())
        {
            return false;
        }

        return m_lValue >= nLo && m_lValue <= nHi;
    }

    /**
     * @return true iff the value is less than zero
     */
    public boolean isNegative()
    {
        verifyInitialized();
        return m_fBig
                ? m_bigint.signum() < 0
                : m_lValue < 0;
    }

    /**
     * Helper to grab the value as an int, with a range check to be safe.
     *
     * @return the value as a 32-bit signed int
     */
    public int getInt()
    {
        long n = getLong();
        if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE)
        {
            throw new IllegalStateException(n + " exceeds the 32-bit integer range ("
                    + Integer.MIN_VALUE + ".." + Integer.MAX_VALUE + ")");
        }

        return (int) n;
    }

    /**
     * Obtain the <tt>long</tt> value of the PackedInteger. If the PackedInteger
     * is "big", i.e. if the {@link #isBig} method returns <tt>true</tt>, then
     * this method will throw an IllegalStateException, because the value cannot
     * be expressed as a <tt>long</tt> without losing data.
     *
     * @return the <tt>long</tt> value of this PackedInteger
     *
     * @throws IllegalStateException if the value of this PackedInteger does not
     *         fit into a long
     */
    public long getLong()
    {
        verifyInitialized();
        if (m_fBig && m_lValue == 0)
        {
            throw new IllegalStateException("too big!");
        }

        return m_lValue;
    }

    /**
     * Initialize the PackedInteger using a <tt>long</tt> value.
     *
     * @param lVal  the <tt>long</tt> value for the PackedInteger
     */
    public void setLong(long lVal)
    {
        verifyUninitialized();
        m_lValue       = lVal;
        m_fInitialized = true;
    }

    /**
     * Obtain the BigInteger value of the PackedInteger. Whether or not the
     * PackedInteger value is too large to be held in a <tt>long</tt>, the
     * caller can request the BigInteger value; one will be lazily instantiated
     * (and subsequently cached) if necessary.
     *
     * @return the BigInteger that represents the integer value of this
     *         PackedInteger object
     */
    public BigInteger getBigInteger()
    {
        verifyInitialized();
        BigInteger bigint = m_bigint;
        if (bigint == null)
        {
            bigint = BigInteger.valueOf(m_lValue);
            m_bigint = bigint;
        }
        return bigint;
    }

    /**
     * Initialize the PackedInteger using a BigInteger value.
     *
     * @param bigint  the BigInteger value for the PackedInteger
     */
    public void setBigInteger(BigInteger bigint)
    {
        verifyUninitialized();

        if (bigint == null)
        {
            throw new IllegalArgumentException("big integer value required");
        }

        // determine if the number of bytes allows the BigInteger value to be
        // stored in a long value
        if (!(m_fBig = calculateSignedByteCount(bigint) > 8))
        {
            m_lValue = bigint.longValue();
        }
        else if (calculateUnsignedByteCount(bigint) <= 8)
        {
            // the unsigned value still fits the long
            m_lValue = bigint.longValue();
        }
        else
        {
            m_lValue = 0;
        }

        m_bigint       = bigint;
        m_fInitialized = true;
    }

    /**
     * @return the negated form of this packed integer
     */
    public PackedInteger negate()
    {
        return new PackedInteger(this.getBigInteger().negate());
    }

    /**
     * @return the complemented form of this packed integer
     */
    public PackedInteger complement()
    {
        return new PackedInteger(this.getBigInteger().not());
    }

    /**
     * @return the packed integer that is one less than this packed integer
     */
    public PackedInteger previous()
    {
        return sub(ONE);
    }

    /**
     * @return the packed integer that is one more than this packed integer
     */
    public PackedInteger next()
    {
        return add(ONE);
    }

    /**
     * Add the value of a specified PackedInteger to this PackedInteger, resulting in a new
     * PackedInteger.
     *
     * @param that  a second PackedInteger to add to this
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger add(PackedInteger that)
    {
        return new PackedInteger(this.getBigInteger().add(that.getBigInteger()));
    }

    /**
     * Subtract the value of a specified PackedInteger from this PackedInteger, resulting in a new
     * PackedInteger.
     *
     * @param that  a second PackedInteger to subtract from this
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger sub(PackedInteger that)
    {
        return new PackedInteger(this.getBigInteger().subtract(that.getBigInteger()));
    }

    /**
     * Multiply the value of a specified PackedInteger by this PackedInteger, resulting in a new
     * PackedInteger.
     *
     * @param that  a second PackedInteger to multiply this by
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger mul(PackedInteger that)
    {
        return new PackedInteger(this.getBigInteger().multiply(that.getBigInteger()));
    }

    /**
     * Divide the value of this PackedInteger by the specified PackedInteger, resulting in a new
     * PackedInteger.
     *
     * @param that  a second PackedInteger to divide this by
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger div(PackedInteger that)
    {
        return new PackedInteger(this.getBigInteger().divide(that.getBigInteger()));
    }

    /**
     * Calculate the modulo of the value of this PackedInteger and the value of a specified
     * PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger to divide this by, resulting in a modulo
     *
     * @return the resulting PackedInteger (never negative - modulo not remainder!)
     */
    public PackedInteger mod(PackedInteger that)
    {
        return new PackedInteger(this.getBigInteger().mod(that.getBigInteger()));
    }

    /**
     * Divide the value of this PackedInteger by the specified PackedInteger, resulting in a new
     * PackedInteger quotient and a new PackedInteger remainder.
     *
     * @param that  a second PackedInteger to divide this by
     *
     * @return an array of the resulting PackedInteger quotient and remainder
     */
    public PackedInteger[] divrem(PackedInteger that)
    {
        BigInteger   [] aBigInt = this.getBigInteger().divideAndRemainder(that.getBigInteger());
        PackedInteger[] aPacked = new PackedInteger[2];
        aPacked[0] = new PackedInteger(aBigInt[0]);
        aPacked[1] = new PackedInteger(aBigInt[1]);
        return aPacked;
    }

    /**
     * Calculate the bit-and of the value of this PackedInteger and the value of a specified
     * PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger to bit-and with this PackedInteger
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger and(PackedInteger that)
    {
        return new PackedInteger(this.getBigInteger().and(that.getBigInteger()));
    }

    /**
     * Calculate the bit-or of the value of this PackedInteger and the value of a specified
     * PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger to bit-or with this PackedInteger
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger or(PackedInteger that)
    {
        return new PackedInteger(this.getBigInteger().or(that.getBigInteger()));
    }

    /**
     * Calculate the bit-xor of the value of this PackedInteger and the value of a specified
     * PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger to bit-xor with this PackedInteger
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger xor(PackedInteger that)
    {
        return new PackedInteger(this.getBigInteger().xor(that.getBigInteger()));
    }

    /**
     * Shift left the bits in the value of this PackedInteger by the value of a specified
     * PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger specifying the shift amount
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger shl(PackedInteger that)
    {
        return shl(that.getInt());
    }

    /**
     * Shift left the bits in the value of this PackedInteger by the specified count,
     * resulting in a new PackedInteger.
     *
     * @param count  the shift count
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger shl(int count)
    {
        return new PackedInteger(this.getBigInteger().shiftLeft(count));
    }

    /**
     * Logical shift right the bits in the value of this PackedInteger by the value of a specified
     * PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger specifying the shift amount
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger shr(PackedInteger that)
    {
        return shr(that.getInt());
    }

    /**
     * Logical shift right the bits in the value of this PackedInteger by the specified count,
     * resulting in a new PackedInteger.
     *
     * @param count  the shift count
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger shr(int count)
    {
        return count <= 0
                ? this
                : new PackedInteger(this.getBigInteger().shiftRight(count));
    }

    /**
     * Arithmetic (aka "unsigned") shift right the bits in the value of this PackedInteger by the
     * value of a specified PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger specifying the shift amount
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger ushr(PackedInteger that)
    {
        return shr(that.getInt());
    }

    /**
     * Arithmetic (aka "unsigned") shift right the bits in the value of this PackedInteger by the
     * specified count, resulting in a new PackedInteger.
     *
     * @param count  the shift count
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger ushr(int count)
    {
        return shr(count);
    }

    /**
     * Compare the value of a specified PackedInteger with the value of this PackedInteger.
     *
     * @param that  a second PackedInteger to compare to
     *
     * @return -1, 0, or 1 if this is less than, equal to, or greater than that
     */
    public int cmp(PackedInteger that)
    {
        return this.getBigInteger().compareTo(that.getBigInteger());
    }

    /**
     * Format the PackedInteger as a String of the specified radix, including a radix prefix for
     * any non-decimal radix.
     *
     * @param radix  2, 8, 10, or 16
     *
     * @return the String value of this PackedInteger in the format of the specified radix
     */
    public String toString(int radix)
    {
        if (radix == 10)
        {
            return toString();
        }

        StringBuilder sb = new StringBuilder();
        if (isNegative())
        {
            sb.append('-');
        }

        switch (radix)
        {
            case 2:
                sb.append("0b");
                break;

            case 8:
                sb.append("0o");
                break;

            case 16:
                sb.append("0x");
                break;

            default:
                throw new IllegalArgumentException("radix=" + radix);
        }

        sb.append(isBig()
                ? getBigInteger().abs().toString(radix)
                : Long.toUnsignedString(Math.abs(getLong()), radix));

        return sb.toString();
    }

    /**
     * Read a PackedInteger from a stream.
     *
     * @param in  a DataInput stream to read from
     *
     * @throws IOException  if an I/O exception occurs while reading the data
     */
    public void readObject(DataInput in)
            throws IOException
    {
        verifyUninitialized();

        // check for large or huge format with more than 8 trailing bytes (needs BigInteger)
        final int b = in.readByte();
        if ((b & 0xE0) == 0xA0)
        {
            int cBytes = 1 + (b & 0x1F);
            if (cBytes == 1)
            {
                // huge format
                long len = readLong(in, in.readByte(), true);
                if (len < 33)
                {
                    throw new IOException("huge integer size of " + len + " bytes; minimum is 33");
                }
                if (len > 8192)
                {
                    throw new IOException("huge integer size of " + len + " bytes; maximum is 8192");
                }
                cBytes = (int) len;
            }

            if (cBytes > 8)
            {
                final byte[] ab = new byte[cBytes];
                in.readFully(ab);
                setBigInteger(new BigInteger(ab));
                return;
            }
        }

        // small, medium, and large (up to 8 trailing bytes) format values fit into
        // a Java long
        setLong(readLong(in, b, false));
    }

    /**
     * Write a PackedInteger to a stream.
     *
     * @param out  a DataOutput stream to write to
     *
     * @throws IOException  if an I/O exception occurs while writing the data
     */
    public void writeObject(DataOutput out)
            throws IOException
    {
        verifyInitialized();

        if (isBig())
        {
            // the value is supposed to be "big", so figure out how many bytes
            // (minimum) it needs to hold its significant bits
            byte[] ab = m_bigint.toByteArray();
            int    cb = ab.length;
            int    of = 0;

            // truncate any redundant bytes
            boolean fNeg  = (ab[0] & 0x80) != 0;
            int     bSkip = fNeg ? 0xFF : 0x00;
            while (of < cb-1 && ab[of] == bSkip && (ab[of+1] & 0x80) == (bSkip & 0x80))
            {
                ++of;
            }
            cb -= of;
            assert cb > 8 && cb <= 8192;

            if (cb <= 32)
            {
                // large format: length encoded in 6 MSBs of first byte, then the bytes of the int
                out.writeByte(0xA0 | cb-1);
            }
            else
            {
                // huge format: first byte is 0, then the length as a packed int, then the bytes
                out.writeByte(0xA0);
                writeLong(out, cb);
            }
            out.write(ab, of, cb);
        }
        else
        {
            writeLong(out, m_lValue);
        }
    }

    /**
     * Verify that the PackedInteger is still uninitialized (has no integer
     * value).
     *
     * @throws IllegalStateException if the value of the PackedInteger has
     *         already been set
     */
    public void verifyUninitialized()
    {
        if (m_fInitialized)
        {
            throw new IllegalStateException("already initialized");
        }
    }

    /**
     * Verify that the PackedInteger has been successfully initialized to an
     * integer value.
     *
     * @throws IllegalStateException if the value of the PackedInteger has
     *         <b>not</b> yet been set
     */
    public void verifyInitialized()
    {
        if (!m_fInitialized)
        {
            throw new IllegalStateException("not yet initialized");
        }
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
    {
        return isBig() ? m_bigint.hashCode() : Long.hashCode(m_lValue);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof PackedInteger that))
        {
            return false;
        }

        return this.isBig()
                ? that.isBig() && this.getBigInteger().equals(that.getBigInteger())
                : !that.isBig() && this.getLong() == that.getLong();
    }

    @Override
    public String toString()
    {
        return isBig() ? m_bigint.toString() : Long.toString(m_lValue);
    }


    // ----- Comparable methods --------------------------------------------------------------------

    @Override
    public int compareTo(PackedInteger that)
    {
        if (this.isBig() || that.isBig())
        {
            return this.getBigInteger().compareTo(that.getBigInteger());
        }

        long lThis = this.m_lValue;
        long lThat = that.m_lValue;
        return Long.compare(lThis, lThat);
    }


    // ----- public helpers ------------------------------------------------------------------------

    /**
     * Write a signed 64-bit integer to a stream using variable-length
     * encoding.
     *
     * @param out  the <tt>DataOutput</tt> stream to write to
     * @param l    the <tt>long</tt> value to write
     *
     * @throws IOException  if an I/O exception occurs
     */
    public static void writeLong(DataOutput out, long l)
            throws IOException
    {
        // test for small (1-byte format)
        if (l <= 127 && l >= -64)
        {
            out.writeByte((int) l);
            return;
        }

        // test for medium (2-byte format)
        final int i = (int) l;
        final int cBits = 65 - Long.numberOfLeadingZeros(Math.max(l, ~l));
        if (cBits <= 13)
        {
            // 0x100xxxxx marker in first byte, followed by 13 bit number
            out.writeShort(0x8000 | (i & 0x1FFF));
            return;
        }

        final int cBytes = (cBits + 7) >>> 3;   // in the range of 2..8 bytes
        out.writeByte(0xA0 | cBytes-1);
        switch (cBytes)
        {
            case 2:
                out.writeShort(i);
                break;
            case 3:
                out.writeByte(i >>> 16);
                out.writeShort(i);
                break;
            case 4:
                out.writeInt(i);
                break;
            case 5:
                out.writeByte((int) (l >>> 32));
                out.writeInt(i);
                break;
            case 6:
                out.writeShort((int) (l >>> 32));
                out.writeInt(i);
                break;
            case 7:
                out.writeByte((int) (l >>> 48));
                out.writeShort((int) (l >>> 32));
                out.writeInt(i);
                break;
            case 8:
                out.writeLong(l);
                break;
            default:
                throw new IllegalStateException("n=" + l);
        }
    }

    /**
     * Read a variable-length encoded integer value from a stream.
     *
     * @param in  a <tt>DataInput</tt> stream to read from
     *
     * @return a <tt>long</tt> value
     *
     * @throws IOException  if an I/O exception occurs
     * @throws NumberFormatException  if the integer does not fit into
     *         a <tt>long</tt> value
     */
    public static long readLong(DataInput in)
            throws IOException
    {
        return readLong(in, in.readByte(), false);
    }

    /**
     * Determine the number of bytes of the packed integer at the specified offset in the provided
     * byte array.
     *
     * @param ab  the byte array containing a packed integer
     * @param of  the byte offset at which the packed integer is located
     *
     * @return the number of bytes used to encode the packed integer
     */
    public static int packedLength(byte[] ab, int of)
    {
        int b = ab[of];
        if ((b & 0xC0) != 0x80)
        {
            // small format
            return 1;
        }

        if ((b & 0x20) == 0)
        {
            // medium format
            return 2;
        }

        int cb = 1 + (b & 0x1F);
        if (cb > 1)
        {
            // large format
            return 1 + cb;
        }

        // huge format
        long sizeAndValue = unpackInt(ab, of+1);
        return 1 + (int) (sizeAndValue >>> 32) + (int) sizeAndValue;
    }

    /**
     * Determine the number of bytes that the specified value would use if it were packed.
     *
     * @param n  the long integer value to estimate a packed length for
     *
     * @return the smallest number of bytes necessary to encode the packed integer
     */
    public static int packedLength(long n)
    {
        // test for small (1-byte format)
        if (n <= 127 && n >= -64)
        {
            return 1;
        }

        // test for medium (2-byte format)
        final int cBits = 65 - Long.numberOfLeadingZeros(Math.max(n, ~n));
        if (cBits <= 13)
        {
            return 2;
        }

        final int cBytes = (cBits + 7) >>> 3;   // in the range of 2..8 bytes
        return 1 + cBytes;
    }

    /**
     * Extract an integer from a byte array, and report back both the integer value and its size in
     * terms of the number of bytes.
     *
     * @param ab  the byte array to unpack an integer from
     * @param of  the byte offset at which the integer is located
     *
     * @return the int value encoded as bits 0..31, and the number of bytes used by the value
     *         encoded as bits 32..63
     */
    public static long unpackInt(byte[] ab, int of)
    {
        int n;
        int cb;

        int b = ab[of];
        if ((b & 0xC0) != 0x80)
        {
            // small format: 1 byte value -64..127
            n  = b;
            cb = 1;
        }
        else if ((b & 0x20) == 0)
        {
            // medium format: 13 bit int, combines 5 bits + next byte (and sign extend)
            n = b << 27 >> 19 | ab[of+1] & 0xFF;
            cb = 2;
        }
        else
        {
            // large format: trail mode: next x+1 (2-32) bytes
            final int cBytes = 1 + (b & 0x1F);
            cb = 1 + cBytes;
            ++of;

            switch (cBytes)
            {
                case 2:
                    n  = ab[of] << 8 | ab[of+1] & 0xFF;
                    break;
                case 3:
                    n  = ab[of] << 16 | (ab[of+1] & 0xFF) << 8 | ab[of+2] & 0xFF;
                    break;
                case 4:
                    n  = ab[of] << 24 | (ab[of+1] & 0xFF) << 16 | (ab[of+2] & 0xFF) << 8 | ab[of+3] & 0xFF;
                    break;
                default:
                    throw new IllegalStateException("# trailing bytes=" + cBytes);
            }
        }

        return ((long) cb) << 32 | n & 0xFFFFFFFFL;
    }


    // ----- internal ------------------------------------------------------------------------------

    private static long readLong(DataInput in, int b, boolean recursion)
            throws IOException
    {
        // small format: 1 byte value -64..127
        if ((b & 0xC0) != 0x80)
        {
            return b;
        }

        // medium format: 13 bit int, combines 5 bits + next byte (and sign extend)
        if ((b & 0x20) == 0)
        {
            b = b << 27 >> 19 | in.readUnsignedByte();
            return b;
        }

        // large format: trail mode: next x+1 (2-32) bytes
        int size = 1 + (b & 0x1F);
        if (size == 1)
        {
            // huge format: the actual byte length comes next in the stream
            if (recursion)
            {
                throw new IOException("illegal recursive format");
            }
            long nestedSize = readLong(in, in.readUnsignedByte(), true);
            if (nestedSize < 1)
            {
                throw new IOException("huge integer length (" + nestedSize + " bytes) below minimum (1 bytes)");
            }
            if (nestedSize > 8)
            {
                throw new IOException("huge integer length (" + nestedSize + " bytes) exceeds maximum (8 bytes)");
            }
            size = (int) nestedSize;
        }

        switch (size)
        {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readByte() << 16 | in.readUnsignedShort();
            case 4:
                return in.readInt();
            case 5:
                return ((long) in.readByte()) << 32 | readUnsignedInt(in);
            case 6:
                return ((long) in.readShort()) << 32 | readUnsignedInt(in);
            case 7:
                return ((long) in.readByte()) << 48
                        | ((long) in.readUnsignedShort()) << 32
                        | readUnsignedInt(in);
            case 8:
                return in.readLong();
            default:
                throw new IllegalStateException("# trailing bytes=" + size);
        }
    }

    private static long readUnsignedInt(DataInput in)
            throws IOException
    {
        return in.readInt() & 0xFFFFFFFFL;
    }

    /**
     * Determine how many bytes is necessary to hold the specified BigInteger.
     *
     * @return the minimum number of bytes to hold the integer value in
     *         2s-complement form
     *
     * @throws IllegalArgumentException if the BigInteger is out of range
     */
    private static int calculateSignedByteCount(BigInteger bigint)
    {
        return bigint.bitLength() / 8 + 1;
    }

    /**
     * Determine how many bytes is necessary to hold the specified BigInteger.
     *
     * @return the minimum number of bytes to hold the integer value in
     *         2s-complement form
     *
     * @throws IllegalArgumentException if the BigInteger is out of range
     */
    private static int calculateUnsignedByteCount(BigInteger bigint)
    {
        return (bigint.bitLength() + 7) / 8;
    }


    // ----- data members --------------------------------------------------------------------------

    /**
     * Set to true once the value has been set.
     */
    private boolean m_fInitialized;

    /**
     * Set to true if the value is too large to fit into a <tt>long</tt> (for signed values).
     */
    private boolean m_fBig;

    /**
     * The <tt>long</tt> value if the value fits into a <tt>long</tt>. Note, the value could be "big",
     * as a signed one but still fit the long as unsigned.
     */
    private long m_lValue;

    /**
     * The <tt>BigInteger</tt> value, which is non-null in several different
     * cases, including if the PackedInteger was constructed with a BigInteger
     * value, if the value was set to a BigInteger, or if the BigInteger was
     * lazily instantiated by a call to {@link #getBigInteger}.
     */
    private BigInteger m_bigint;

    /**
     * Cache of PackedInteger instances for small integers.
     */
    private static final PackedInteger[] CACHE = new PackedInteger[0x1000];


    // ----- constants -----------------------------------------------------------------------------

    /**
     * Smallest integer value to cache. One quarter of the cache size is
     * reserved for small negative integer values.
     */
    private static final long CACHE_MIN = -(CACHE.length >> 2);

    /**
     * Largest integer value to cache. Three-quarters of the cache size is
     * reserved for small positive integer values.
     */
    private static final long CACHE_MAX = CACHE_MIN + CACHE.length - 1;

    /**
     * The PackedInteger for the value <tt>0</tt>. Also used as the smallest 1-, 2-, 4-,
     * 8-, 16-, and 32-byte <b>un</b>signed integer value.
     */
    public static final PackedInteger ZERO       = valueOf(0L);
    /**
     * The PackedInteger for the value <tt>1</tt>.
     */
    public static final PackedInteger ONE        = valueOf(1L);
    /**
     * The PackedInteger for the value <tt>-1</tt>.
     */
    public static final PackedInteger NEG_ONE    = valueOf(-1L);

    /**
     * Smallest 1-byte (8-bit) signed integer value.
     */
    public static final PackedInteger SINT1_MIN  = valueOf(-0x80L);
    /**
     * Smallest 2-byte (16-bit) signed integer value.
     */
    public static final PackedInteger SINT2_MIN  = valueOf(-0x8000L);
    /**
     * Smallest 4-byte (32-bit) signed integer value.
     */
    public static final PackedInteger SINT4_MIN  = valueOf(-0x80000000L);
    /**
     * Smallest 8-byte (64-bit) signed integer value.
     */
    public static final PackedInteger SINT8_MIN  = valueOf(-0x8000000000000000L);
    /**
     * Smallest 16-byte (128-bit) signed integer value.
     */
    public static final PackedInteger SINT16_MIN = new PackedInteger(new BigInteger("-8" + "0".repeat(31), 16));
    /**
     * Smallest N-byte signed integer value (arbitrary 1KB limit).
     */
    public static final PackedInteger SINTN_MIN = new PackedInteger(new BigInteger("-8" + "0".repeat(2047), 16));
    /**
     * Largest 1-byte (8-bit) signed integer value.
     */
    public static final PackedInteger SINT1_MAX  = valueOf(0x7F);
    /**
     * Largest 2-byte (16-bit) signed integer value.
     */
    public static final PackedInteger SINT2_MAX  = valueOf(0x7FFF);
    /**
     * Largest 4-byte (32-bit) signed integer value.
     */
    public static final PackedInteger SINT4_MAX  = valueOf(0x7FFF_FFFF);
    /**
     * Largest 8-byte (64-bit) signed integer value.
     */
    public static final PackedInteger SINT8_MAX  = valueOf(0x7FFF_FFFF_FFFF_FFFFL);
    /**
     * Largest 16-byte (128-bit) signed integer value.
     */
    public static final PackedInteger SINT16_MAX = new PackedInteger(new BigInteger("7" + "F".repeat(31), 16));
    /**
     * Largest N-byte signed integer value (arbitrary 1KB limit).
     */
    public static final PackedInteger SINTN_MAX = new PackedInteger(new BigInteger("7" + "F".repeat(2047), 16));
    /**
     * Largest 1-byte (8-bit) unsigned integer value.
     */
    public static final PackedInteger UINT1_MAX  = valueOf(0xFF);
    /**
     * Largest 2-byte (16-bit) unsigned integer value.
     */
    public static final PackedInteger UINT2_MAX  = valueOf(0xFFFF);
    /**
     * Largest 4-byte (32-bit) unsigned integer value.
     */
    public static final PackedInteger UINT4_MAX  = valueOf(0xFFFF_FFFFL);
    /**
     * Largest 8-byte (64-bit) unsigned integer value.
     */
    public static final PackedInteger UINT8_MAX  = new PackedInteger(new BigInteger("F".repeat(16), 16));
    /**
     * Largest 16-byte (128-bit) unsigned integer value.
     */
    public static final PackedInteger UINT16_MAX = new PackedInteger(new BigInteger("F".repeat(32), 16));
    /**
     * Largest N-byte unsigned integer value (arbitrary 1KB limit).
     */
    public static final PackedInteger UINTN_MAX = SINTN_MAX;

    /**
     * Decimal "Kilo".
     */
    public static final PackedInteger KB = valueOf(1000);
    /**
     * Binary "Kilo".
     */
    public static final PackedInteger KI = valueOf(1024);
    /**
     * Decimal "Mega".
     */
    public static final PackedInteger MB = valueOf(1000 * 1000);
    /**
     * Binary "Mega".
     */
    public static final PackedInteger MI = valueOf(1024 * 1024);
    /**
     * Decimal "Giga".
     */
    public static final PackedInteger GB = valueOf(1000 * 1000 * 1000);
    /**
     * Binary "Giga".
     */
    public static final PackedInteger GI = valueOf(1024 * 1024 * 1024);
    /**
     * Decimal "Tera".
     */
    public static final PackedInteger TB = valueOf(1000L * 1000 * 1000 * 1000);
    /**
     * Binary "Tera".
     */
    public static final PackedInteger TI = valueOf(1024L * 1024 * 1024 * 1024);
    /**
     * Decimal "Peta".
     */
    public static final PackedInteger PB = valueOf(1000L * 1000 * 1000 * 1000 * 1000);
    /**
     * Binary "Peta".
     */
    public static final PackedInteger PI = valueOf(1024L * 1024 * 1024 * 1024 * 1024);
    /**
     * Decimal "Exa".
     */
    public static final PackedInteger EB = valueOf(1000L * 1000 * 1000 * 1000 * 1000 * 1000);
    /**
     * Binary "Exa".
     */
    public static final PackedInteger EI = valueOf(1024L * 1024 * 1024 * 1024 * 1024 * 1024);
    /**
     * Decimal "Zetta".
     */
    public static final PackedInteger ZB = EB.mul(KB);
    /**
     * Binary "Zetta".
     */
    public static final PackedInteger ZI = EI.mul(KI);
    /**
     * Decimal "Yotta".
     */
    public static final PackedInteger YB = ZB.mul(KB);
    /**
     * Binary "Yotta".
     */
    public static final PackedInteger YI = ZI.mul(KI);

    /**
     * The decimal (1000x) factors.
     */
    public static final PackedInteger[] xB_FACTORS = {KB, MB, GB, TB, PB, EB, ZB, YB, };
    /**
     * The binary (1024x) factors.
     */
    public static final PackedInteger[] xI_FACTORS = {KI, MI, GI, TI, PI, EI, ZI, YI, };
}