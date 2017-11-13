package org.xvm.util;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.math.BigInteger;

import static org.xvm.util.Handy.writePackedLong;


/**
 * A PackedInteger represents a 2s-complement integer of 1, 2, 4, 8, 16, or 32
 * bytes. Values up to 8 bytes can be accessed as a <tt>long</tt> value, while
 * values of any size can be accessed as a BigInteger.
 */
public class PackedInteger
        implements Comparable<PackedInteger>
    {
    // ----- constructors ------------------------------------------------------

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


    // ----- public methods ----------------------------------------------------

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
     * @return 1, 2, 4, 8, 16, or 32
     */
    public int getSignedByteSize()
        {
        verifyInitialized();

        int nBytes = m_fBig
                ? calculateSignedByteCount(m_bigint)
                : Math.max(1, (((64 - Long.numberOfLeadingZeros(Math.max(m_lValue, ~m_lValue))) & 0x3F) + 7) / 8);

        assert nBytes >= 1 && nBytes <= 32;

        // turn the raw number of bytes {1,2,3,4,5,6,7,8,9,...}
        //                         into {1,2,4,4,8,8,8,8,16,...}
        return Integer.highestOneBit(nBytes * 2 - 1);
        }

    /**
     * The size of the unsigned integer that would be necessary to hold the value.
     *
     * @return 1, 2, 4, 8, 16, or 32
     */
    public int getUnsignedByteSize()
        {
        verifyInitialized();
        if (m_fBig ? this.compareTo(ZERO) < 0 : m_lValue < 0)
            {
            throw new IllegalStateException("value is signed");
            }

        int nBytes = m_fBig
                ? calculateSignedByteCount(m_bigint)
                : Math.max(1, (((64 - Long.numberOfLeadingZeros(m_lValue)) + 7) / 8));

        assert nBytes >= 1 && nBytes <= 32;

        // turn the raw number of bytes {1,2,3,4,5,6,7,8,9,...}
        //                         into {1,2,4,4,8,8,8,8,16,...}
        return Integer.highestOneBit(nBytes * 2 - 1);
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
            throw new IllegalStateException("too big!");
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
        if (m_fBig)
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
        if (!(m_fBig = (calculateSignedByteCount(bigint) > 8)))
            {
            m_lValue = bigint.longValue();
            }

        m_bigint       = bigint;
        m_fInitialized = true;
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
        return new PackedInteger(this.getBigInteger().shiftLeft(that.getInt()));
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
        return that.isNegative()
                ? this
                : new PackedInteger(this.getBigInteger().shiftRight(that.getInt()));
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
        return that.isNegative()
                ? this
                : new PackedInteger(this.getBigInteger().shiftRight(that.getInt()));
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

        // the first bit of the first byte is used to indicate a single byte
        // format, in which the entire value is contained in the 7 MSBs
        int b = in.readByte();
        if ((b & 0x01) != 0)
            {
            setLong(b >> 1);
            return;
            }

        // the second bit is used to indicate a format that uses 1 or 2 bytes
        // in addition to the 5 MSBs of the first byte
        if ((b & 0x02) != 0)
            {
            // the third bit is used to indicate 0: 1 byte, or 1: 2 bytes
            setLong((((b & 0x04) == 0
                    ? (int) in.readByte()
                    : in.readShort()) << 5) | ((b >> 3) & 0x1F));
            return;
            }

        // the size of the integer value is defined by the third to fifth bit,
        // such that the number of bytes is (1 << nSize)
        final int cBytes = 1 << ((b & 0x1C) >> 2);
        if (cBytes == 8)
            {
            setLong(in.readLong());
            }
        else if (cBytes == 4)
            {
            setLong(in.readInt());
            }
        else if (cBytes == 2)
            {
            setLong(in.readShort());
            }
        else if (cBytes == 1)
            {
            setLong(in.readByte());
            }
        else
            {
            byte[] ab = new byte[cBytes];
            in.readFully(ab);
            setBigInteger(new BigInteger(ab));
            }
        }

    /**
     * Write a PackedInteger to a stream.
     *
     * @param out  a DataOutut stream to write to
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
            byte[] abBits = m_bigint.toByteArray();
            int    cbBits = abBits.length;
            assert cbBits > 8;

            // the size to write needs to be at least as big as the minimum size
            // of the byte array necessary to hold the significant bits
            int    cbWrite = getSignedByteSize();
            assert cbWrite == 16 || cbWrite == 32;
            assert cbWrite >= cbBits;

            out.write(cbWrite == 16 ? 0b10000 : 0b10100);
            if (cbWrite > cbBits)
                {
                out.write(EMPTY, 0, cbWrite - cbBits);
                }
            out.write(abBits);
            }
        else
            {
            writePackedLong(out, m_lValue);
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


    // ----- Object methods ----------------------------------------------------

    @Override
    public int hashCode()
        {
        return isBig() ? m_bigint.hashCode() : Long.hashCode(m_lValue);
        }

    @Override
    public boolean equals(Object obj)
        {
        if (!(obj instanceof PackedInteger))
            {
            return false;
            }

        PackedInteger that = (PackedInteger) obj;
        return this.isBig()
                ? that.isBig() && this.getBigInteger().equals(that.getBigInteger())
                : !that.isBig() && this.getLong() == that.getLong();
        }

    @Override
    public String toString()
        {
        return isBig() ? m_bigint.toString() : Long.toString(m_lValue);
        }


    // ----- Comparable methods ------------------------------------------------

    @Override
    public int compareTo(PackedInteger that)
        {
        if (this.isBig() || that.isBig())
            {
            return this.getBigInteger().compareTo(that.getBigInteger());
            }

        long lThis = this.m_lValue;
        long lThat = that.m_lValue;
        return lThis < lThat ? -1 : lThis == lThat ? 0 : 1;
        }


    // ----- internal ----------------------------------------------------------

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
        return (bigint.bitLength() + 7) / 8;
        }

    /**
     * Determine how many bytes is necessary to hold the specified BigInteger.
     *
     * @return the minimum number of bytes to hold the integer value in
     *         2s-complement form
     *
     * @throws IllegalArgumentException if the BigInteger is out of range
     */
    private static int calculateUnignedByteCount(BigInteger bigint)
        {
        // TODO this is from the signed version
        // return (bigint.bitLength() + 7) / 8;
        throw new UnsupportedOperationException();
        }


    // ----- data members ------------------------------------------------------

    /**
     * Set to true once the value has been set.
     */
    private boolean m_fInitialized;

    /**
     * Set to true if the value is too large to fit into a <tt>long</tt>.
     */
    private boolean m_fBig;

    /**
     * The <tt>long</tt> value (if the value fits into a <tt>long</tt>.
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


    // ----- constants ---------------------------------------------------------

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
    public static final PackedInteger SINT1_MIN  = valueOf(0x80L);
    /**
     * Smallest 2-byte (16-bit) signed integer value.
     */
    public static final PackedInteger SINT2_MIN  = valueOf(0x8000L);
    /**
     * Smallest 4-byte (32-bit) signed integer value.
     */
    public static final PackedInteger SINT4_MIN  = valueOf(0x80000000L);
    /**
     * Smallest 8-byte (64-bit) signed integer value.
     */
    public static final PackedInteger SINT8_MIN  = valueOf(0x8000000000000000L);
    /**
     * Smallest 16-byte (128-bit) signed integer value.
     */
    public static final PackedInteger SINT16_MIN = new PackedInteger(new BigInteger("-80000000000000000000000000000000", 16));
    /**
     * Smallest 32-byte (256-bit) signed integer value.
     */
    public static final PackedInteger SINT32_MIN = new PackedInteger(new BigInteger("-8000000000000000000000000000000000000000000000000000000000000000", 16));
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
    public static final PackedInteger SINT4_MAX  = valueOf(0x7FFFFFFF);
    /**
     * Largest 8-byte (64-bit) signed integer value.
     */
    public static final PackedInteger SINT8_MAX  = valueOf(0x7FFFFFFFFFFFFFFL);
    /**
     * Largest 16-byte (128-bit) signed integer value.
     */
    public static final PackedInteger SINT16_MAX = new PackedInteger(new BigInteger("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16));
    /**
     * Largest 32-byte (256-bit) signed integer value.
     */
    public static final PackedInteger SINT32_MAX = new PackedInteger(new BigInteger("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16));
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
    public static final PackedInteger UINT4_MAX  = valueOf(0xFFFFFFFF);
    /**
     * Largest 8-byte (64-bit) unsigned integer value.
     */
    public static final PackedInteger UINT8_MAX  = new PackedInteger(new BigInteger("FFFFFFFFFFFFFFFF", 16));
    /**
     * Largest 16-byte (128-bit) unsigned integer value.
     */
    public static final PackedInteger UINT16_MAX = new PackedInteger(new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16));
    /**
     * Largest 32-byte (256-bit) unsigned integer value.
     */
    public static final PackedInteger UINT32_MAX = new PackedInteger(new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16));

    /**
     * An array of zero bytes.
     */
    private static final byte[] EMPTY = new byte[32];
    }
