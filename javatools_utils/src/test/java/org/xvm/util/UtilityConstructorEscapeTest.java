package org.xvm.util;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.math.BigInteger;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class UtilityConstructorEscapeTest {
    @Test
    public void packedIntegerConstructorsDoNotCallOverridableMutators() throws IOException {
        var big = new BigInteger("123456789012345678901234567890");

        assertEquals(123, new ConstructorSafePackedInteger(123).getLong());
        assertEquals(big, new ConstructorSafePackedInteger(big).getBigInteger());
        assertEquals(big, new ConstructorSafePackedInteger(inputFor(big)).getBigInteger());
    }

    @Test
    public void hasherReferenceConstructorDoesNotCallOverridableReset() {
        var ref = new ConstructorSafeHasherReference<>("value", Hasher.<String>natural());

        assertEquals("value", ref.get());
        assertEquals(Hasher.<String>natural().hash("value"), ref.hashCode());
    }

    @Test
    public void listSetCollectionConstructorDoesNotCallOverridableAdd() {
        var set = new ConstructorSafeListSet<>(List.of("a", "b", "a"));

        assertEquals(2, set.size());
        assertTrue(set.contains("a"));
        assertTrue(set.contains("b"));
    }

    private static DataInput inputFor(BigInteger value) throws IOException {
        var outRaw = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(outRaw)) {
            new PackedInteger(value).writeObject(out);
        }
        return new DataInputStream(new ByteArrayInputStream(outRaw.toByteArray()));
    }

    private static final class ConstructorSafePackedInteger extends PackedInteger {
        ConstructorSafePackedInteger(long value) {
            super(value);
        }

        ConstructorSafePackedInteger(BigInteger value) {
            super(value);
        }

        ConstructorSafePackedInteger(DataInput in) throws IOException {
            super(in);
        }

        @Override
        public void setLong(long value) {
            throw new IllegalStateException("constructor called overridable setLong");
        }

        @Override
        public void setBigInteger(BigInteger value) {
            throw new IllegalStateException("constructor called overridable setBigInteger");
        }

        @Override
        public void readObject(DataInput in) throws IOException {
            throw new IllegalStateException("constructor called overridable readObject");
        }
    }

    private static final class ConstructorSafeHasherReference<T> extends HasherReference<T> {
        ConstructorSafeHasherReference(T referent, Hasher<? super T> hasher) {
            super(referent, hasher);
        }

        @Override
        protected void reset(T referent, Hasher<? super T> hasher) {
            throw new IllegalStateException("constructor called overridable reset");
        }
    }

    private static final class ConstructorSafeListSet<E> extends ListSet<E> {
        ConstructorSafeListSet(Collection<? extends E> values) {
            super(values);
        }

        @Override
        public boolean add(E value) {
            throw new IllegalStateException("constructor called overridable add");
        }
    }
}
