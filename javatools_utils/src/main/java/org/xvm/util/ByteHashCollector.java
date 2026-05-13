package org.xvm.util;

import java.util.Random;

/**
 * A hash collector that computes a hash code based on the bytes added to the collector.
 */
public interface ByteHashCollector {

    /**
     * A random seed to use for generating hash codes.
     */
    long HashCodeSeed = new Random(System.currentTimeMillis()).nextLong(0, Long.MAX_VALUE);

    /**
     * Add a byte to the hash code computation.
     *
     * @param value  the byte to add
     *
     * @return this collector, for chaining
     */
    ByteHashCollector addByte(byte value);

    /**
     * Add the least significant byte of an {@code int} to the hash code compuation.
     *
     * @param value  the {@code int} to add
     *
     * @return this collector, for chaining
     */
    ByteHashCollector addInt8(int value);

    /**
     * Add the least significant two bytes of an {@code int} to the hash code compuation.
     *
     * @param value  the {@code int} to add
     *
     * @return this collector, for chaining
     */
    ByteHashCollector addInt16(int value);

    /**
     * Add an {@code int} to the hash code compuation.
     *
     * @param value  the {@code int} to add
     *
     * @return this collector, for chaining
     */
    ByteHashCollector addInt32(int value);

    /**
     * Add a {@code long} to the hash code compuation.
     *
     * @param value  the {@code long} to add
     *
     * @return this collector, for chaining
     */
    ByteHashCollector addLong(long value);

    /**
     * Reset this collector.
     *
     * @return this collector, for chaining
     */
    ByteHashCollector reset();

    /**
     * @return the computed hash code
     */
    long compute();

    // ----- Simple implementation -----------------------------------------------------------------

    /**
     * A simple hash collector that computes a hash code using a simple algorithm.
     */
    class Simple
            implements ByteHashCollector {

        /**
         * The initial value for the hash.
         */
        private final long seed;

        /**
         * The computed hash.
         */
        private long hashCode;

        /**
         * Create a new {@link Simple} hash collector seeded from the default {@link #HashCodeSeed}.
         */
        public Simple() {
            this(HashCodeSeed);
        }

        /**
         * Create a new {@link Simple} hash collector seeded from the specified value.
         *
         * @param seed  the seed value to use as a base for computing hashes
         */
        public Simple(long seed) {
            this.seed     = seed;
            this.hashCode = seed;
        }

        @Override
        public Simple addByte(byte value) {
            hashCode = hashCode * 31 + (value & 0xFF);
            return this;
        }

        @Override
        public Simple addInt8(int value) {
            hashCode = hashCode * 31 + (value & 0xFF);
            return this;
        }

        @Override
        public Simple addInt16(int value) {
            hashCode = hashCode * 31 + ((value >> 8) & 0xFF);
            hashCode = hashCode * 31 + (value & 0xFF);
            return this;
        }

        @Override
        public Simple addInt32(int value) {
            hashCode = hashCode * 31 + ((value >> 24) & 0xFF);
            hashCode = hashCode * 31 + ((value >> 16) & 0xFF);
            hashCode = hashCode * 31 + ((value >> 8) & 0xFF);
            hashCode = hashCode * 31 + (value & 0xFF);
            return this;
        }

        @Override
        public Simple addLong(long value) {
            hashCode = hashCode * 31 + ((value >> 56) & 0xFF);
            hashCode = hashCode * 31 + ((value >> 48) & 0xFF);
            hashCode = hashCode * 31 + ((value >> 40) & 0xFF);
            hashCode = hashCode * 31 + ((value >> 32) & 0xFF);
            hashCode = hashCode * 31 + ((value >> 24) & 0xFF);
            hashCode = hashCode * 31 + ((value >> 16) & 0xFF);
            hashCode = hashCode * 31 + ((value >> 8) & 0xFF);
            hashCode = hashCode * 31 + (value & 0xFF);
            return this;
        }

        @Override
        public Simple reset() {
            hashCode = seed;
            return this;
        }

        @Override
        public long compute() {
            return hashCode;
        }
    }
}
