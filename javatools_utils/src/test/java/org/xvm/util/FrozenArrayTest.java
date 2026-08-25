package org.xvm.util;


import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract pins for {@link FrozenArray}, the stage-3 shared-metadata representation: adoption
 * vs copy semantics, copy independence, the identity contract of the {@code unsafeArray()}
 * escape hatch, and iteration.
 */
public class FrozenArrayTest {
    @Test
    public void adoptWrapsAndCopyOfCopies() {
        var aOrig = new String[] {"a", "b"};

        var adopted = FrozenArray.adopt(aOrig);
        assertSame(aOrig, adopted.unsafeArray(),
                "adopt is an ownership transfer backed by the array itself");

        var copied = FrozenArray.copyOf(aOrig);
        assertNotSame(aOrig, copied.unsafeArray(),
                "copyOf must leave the caller's array unaliased");
        assertTrue(copied.contentEquals(adopted));

        // a caller violating the adopt contract is visible through the view (documenting WHY
        // the contract exists); copyOf is immune
        aOrig[0] = "mutated";
        assertEquals("mutated", adopted.get(0));
        assertEquals("a", copied.get(0));
    }

    @Test
    public void copyIsIndependentOfTheView() {
        var frozen = FrozenArray.adopt(new String[] {"x", "y", "z"});

        var aCopy = frozen.copy();
        assertNotSame(frozen.unsafeArray(), aCopy);
        aCopy[1] = "changed";
        assertEquals("y", frozen.get(1), "mutating a copy must not affect the frozen view");
    }

    @Test
    public void sizeGetAndIterationAgree() {
        var frozen = FrozenArray.adopt(new Integer[] {1, 2, 3});

        assertEquals(3, frozen.size());
        assertFalse(frozen.isEmpty());
        assertEquals(2, frozen.get(1));

        var listSeen = new ArrayList<Integer>();
        for (var n : frozen) {
            listSeen.add(n);
        }
        assertEquals(List.of(1, 2, 3), listSeen);

        var iter = frozen.iterator();
        iter.next();
        iter.next();
        iter.next();
        assertThrows(NoSuchElementException.class, iter::next);

        assertTrue(FrozenArray.adopt(new Integer[0]).isEmpty());
    }

    @Test
    public void contentEqualsComparesElementsNotIdentity() {
        var frozenA = FrozenArray.adopt(new String[] {"p", "q"});
        var frozenB = FrozenArray.copyOf(new String[] {"p", "q"});
        var frozenC = FrozenArray.adopt(new String[] {"p"});

        assertTrue(frozenA.contentEquals(frozenB));
        assertTrue(frozenA.contentEquals(frozenA));
        assertFalse(frozenA.contentEquals(frozenC));
    }
}
