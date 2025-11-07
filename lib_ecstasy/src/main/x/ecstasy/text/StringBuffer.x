/**
 * A StringBuffer is used to efficiently create a resulting String from any number of contributions
 * of any size.
 *
 * TODO insert and delete (including ranges) functionality
 */
class StringBuffer
        implements Appender<Char>
        implements Duplicable
        implements UniformIndexed<Int, Char>
        implements Iterable<Char>
        implements Sliceable<Int>
        implements Stringable {
    /**
     * Construct a StringBuffer.
     *
     * @param capacity  an optional value indicating the expected size of the resulting String
     */
    construct(Int capacity = 0) {
        nextCap = capacity.notLessThan(0);
    }

    @Override construct(StringBuffer that) {
        this.buf      = that.buf.duplicate();
        this.space    = this.buf.capacity - this.buf.size;
        this.bufs     = that.bufs.duplicate();
        this.prevSize = that.prevSize;
        this.nextCap  = that.nextCap;
    }

    private construct(
            Char[]   buf,
            Int?     space    = Null,
            Char[][] bufs     = [],
            Int?     prevSize = Null,
            Int      nextCap  = 0,
            ) {
        this.buf      = buf;
        this.space    = space ?: (buf.capacity - buf.size);
        this.bufs     = bufs;
        this.prevSize = prevSize ?: {
            // this should use the "Sum" Aggregator from the "aggregate" module, except that this
            // class is part of the root "ecstasy" module, which must not depend on other modules
            Int sum = 0;
            for (Char[] each : bufs) {
                 sum += each.size;
            }
            return sum;
        };
        this.nextCap  = nextCap;
    }

    /**
     * The current `Char[]` being appended into. This is only added to the [bufs] after it has been
     * filled.
     */
    private Char[] buf = [];

    /**
     * The amount of space that remains in the current buffer.
     */
    private Int space = 0;

    /**
     * All `Char[]` objects other than the current [buf] that make up the StringBuffer.
     */
    private Char[][] bufs = [];

    /**
     * Total of the size of [bufs].
     */
    private Int prevSize = 0;

    /**
     * Known capacity requirement for the next allocation.
     */
    private Int nextCap = 0;

    /**
     * Minimum allocation size in the absence of better data.
     */
    private static Int MinBuf = 64;

    /**
     * A cached iterator for use when the buffer is empty (to avoid allocating).
     */
    private static Iterator<Char> EmptyIterator = Char.PublicType.emptyIterator;

    // ----- StringBuffer API ----------------------------------------------------------------------

    /**
     * The current capacity of the StringBuffer, which can be modified (but not to less than the
     * current size).
     */
    Int capacity {
        @Override
        Int get() = size + space + nextCap;

        @Override
        void set(Int cap) {
            if (cap > get()) {
                ensureCapacity(cap - size);
            }
        }
    }

    /**
     * Append a value to the StringBuffer.
     *
     * @param o  the object to append
     *
     * @return this buffer
     */
    @Op("+")
    StringBuffer append(Object o) {
        if (o.is(Iterable<Char>)) {
            // this covers StringBuffer, Char[], String, etc.
            addAll(o);
        } else if (o.is(Stringable)) {
            // in a typical usage scenario, the capacity that would contain this object has already
            // been ensured, so if there's any capacity left, assume it's correct
            if (space + nextCap == 0) {
                ensureCapacity(o.estimateStringLength());
            }
            o.appendTo(this);
        } else {
            addAll(o.toString());
        }
        return this;
    }

    /**
     * Append the specified character repeatedly to the StringBuffer.
     *
     * @param ch  the character to repeatedly append
     * @param n   the number of times
     *
     * @return this buffer
     */
    StringBuffer addDup(Char ch, Int n) {
        ensureCapacity(n);
        while (--n >= 0) {
            add(ch);
        }
        return this;
    }

    /**
     * Look for the specified character value, starting at the specified index.
     *
     * @param value    the character value to search for
     * @param startAt  (optional) the first index to search from
     *
     * @return True iff this StringBuffer contains the character, at or after the `startAt` index
     * @return (conditional) the index at which the specified character was found
     */
    conditional Int indexOf(Char value, Int startAt = 0) {
        if (startAt >= prevSize) {
            return buf.indexOf(value, startAt-prevSize);
        }

        assert:bounds startAt >= 0;
        Int bufOffset = 0;
        for (Int i = 0, Int committed = bufs.size, Int count = committed + 1; i < count; ++i) {
            Char[] each      = i == committed ? buf : bufs[i];
            Int    bufLength = each.size;
            if (startAt < bufLength, Int location := each.indexOf(value, startAt)) {
                return True, bufOffset + location;
            }
            bufOffset += bufLength;
            startAt    = (startAt - bufLength).notLessThan(0);
        }

        return False;
    }

    /**
     * Look for the specified character value, starting at the specified index and searching
     * backwards.
     *
     * @param value    the character value to search for
     * @param startAt  (optional) the index to start searching backwards from
     *
     * @return True iff this StringBuffer contains the character, at or before the `startAt` index
     * @return (conditional) the index at which the specified value was found
     */
    conditional Int lastIndexOf(Char value, Int startAt = MaxValue) {
        // in theory, the starting point might be the last found location minus one, and the last
        // found location might be at offset zero, so allow an illegal starting position
        if (startAt < 0) {
            return False;
        }

        // check the last buffer first
        Int bufOffset = prevSize;
        if (startAt >= bufOffset, Int offset := buf.lastIndexOf(value, startAt-bufOffset)) {
            return True, bufOffset + offset;
        }

        for (Int i = bufs.size - 1; i >= 0; --i) {
            Char[] each  = bufs[i];
            bufOffset   -= each.size;
            if (startAt >= bufOffset, Int offset := each.lastIndexOf(value, startAt-bufOffset)) {
                return True, bufOffset + offset;
            }
        }

        return False;
    }

    /**
     * Modify the contents of this StringBuffer so that it has the specified size.
     *
     * @param newSize  if non-negative, the size to truncate the buffer to; otherwise the number of
     *                 characters to truncate from the end
     *
     * @return this buffer
     */
    StringBuffer truncate(Int newSize) {
        Int size = this.size;
        if (newSize < 0) {
            newSize += size;
        }

        if (newSize == 0) {
            return clear();
        }

        if (newSize == size) {
            return this;
        }

        assert:bounds 0 < newSize < size;
        if (newSize >= prevSize) {
            // the truncation applies only to the current buffer
            buf.deleteAll(newSize-prevSize..<buf.size);
            return this;
        }

        for (Int bufIx = 0, Int bufCount = bufs.size, Int total = 0; bufIx < bufCount; ++bufIx) {
            Char[] each = bufs[bufIx];
            if (total + each.size > newSize) {
                bufs[bufIx] = each[0..<newSize-total].reify(Constant); // REVIEW the reify
                if (bufIx+1 < bufCount) {
                    bufs.deleteAll(bufIx >..< bufCount);
                }
                break;
            }
            total += each.size;
        }
        buf      = [];
        space    = 0;
        nextCap  = 0;
        prevSize = newSize;
        return this;
    }

    /**
     * Clear the contents of this StringBuffer.
     */
    StringBuffer clear() {
        bufs     = [];
        prevSize = 0;
        nextCap  = 0;
        space    = buf.capacity;
        if (space > 0) {
            if (space > MinBuf) {
                buf   = [];
                space = 0;
            } else {
                assert buf.inPlace;
                buf.clear();
            }
        }
        return this;
    }

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() = size;

    @Override
    Appender<Char> appendTo(Appender<Char> appender) {
        if (appender.is(StringBuffer)) {
            return appender.appendStringBuffer(this);
        }

        appender.ensureCapacity(size);
        for (Char[] each : bufs) {
            appender.addAll(each);
        }
        if (!buf.empty) {
            appender.addAll(buf);
        }
        return appender;
    }

    // ----- Appender methods ----------------------------------------------------------------------

    @Override
    StringBuffer add(Char v) {
        if (space == 0) {
            commitBuf();
            allocNext();
        }
        buf.add(v);
        --space;
        return this;
    }

    @Override
    StringBuffer addAll(Iterable<Char> iterable) {
        Int sizeToAdd = iterable.size;
        if (sizeToAdd <= space) {
            buf.addAll(iterable);
            space = buf.capacity - buf.size;
        } else {
            addOverflow(iterable, sizeToAdd);
        }
        return this;
    }

    @Override
    StringBuffer ensureCapacity(Int count) {
        if (count > space) {
            nextCap = nextCap.notLessThan(count - space);
        }
        return this;
    }

    // ----- Iterable methods ----------------------------------------------------------------------

    @Override
    Int size.get() = prevSize + buf.size;

    @Override
    Iterator<Char> iterator() {
        if (bufs.empty) {
            return buf.empty ? EmptyIterator : buf.iterator();
        }

        Int committedCount = bufs.size;
        if (committedCount == 1 && buf.empty) {
            return bufs[0].iterator();
        }

        Char[][] snapshot = buf.empty
                ? (bufs.inPlace ? bufs.toArray(Constant) : bufs)
                : new Char[][committedCount+1](i -> i < committedCount ? bufs[i].freeze(inPlace=True) : buf);
        return new BufferIterator(snapshot, prevSize);

        static class BufferIterator
                implements Iterator<Char>, Duplicable {
            construct(Char[][] arrays, Int totalSize) {
                this.arrays    = arrays;
                this.nextArray = 1;
                this.chars     = arrays[0];
                this.charsSize = chars.size;
                this.totalSize = totalSize;
            }

            @Override construct(BufferIterator that) {
                this.arrays      = that.arrays;
                this.nextArray   = that.nextArray;
                this.charsOffset = that.charsOffset;
                this.chars       = that.chars;
                this.charsIndex  = that.charsIndex;
                this.charsSize   = that.charsSize;
                this.totalSize   = that.totalSize;
            }

            private Char[][] arrays;        // the Char[]'s that we're iterating Chars from
            private Int      nextArray;     // the index of the next Char[] to iterate from
            private Int      charsOffset;   // the StringBuffer offset of the first Char of `chars`
            private Char[]   chars;         // the current array that we're iterating Chars from
            private Int      charsIndex;
            private Int      charsSize;
            private Int      totalSize;

            @Override conditional Char next() {
                while (True) {
                    if (charsIndex < charsSize) {
                        return True, chars[charsIndex++];
                    }

                    if (nextArray >= arrays.size) {
                        return False;
                    }

                    charsOffset += charsSize;
                    chars        = arrays[nextArray++];
                    charsIndex   = 0;
                    charsSize    = chars.size;
                }
            }

            @Override conditional Int knownSize() = (True, totalSize - charsOffset - charsIndex);

            @Override (Iterator<Char>, Iterator<Char>) bifurcate() = (this, new BufferIterator(this));
        }
    }

    // ----- UniformIndexed methods ----------------------------------------------------------------------

    @Override
    @Op("[]")
    Char getElement(Int index) {
        if (index >= prevSize) {
            try {
                return buf[index-prevSize];
            } catch(OutOfBounds e) {
                throw new OutOfBounds($"{index=}, {size=}");
            }
        }

        assert:bounds index >= 0;
        for (Int i = 0, Int c = bufs.size, Int total = 0; i < c; ++i) {
            Char[] each = bufs[i];
            Int    next = total + each.size;
            if (index < next) {
                return each[index-total];
            }
            total = next;
        }
        assert;
    }

    @Override
    @Op("[]=")
    void setElement(Int index, Char value) {
        if (index >= prevSize && index <= size) {
            if (buf.mutability == Constant) {
                allocNext();
            }
            try {
                buf[index-prevSize] = value;
                return;
            } catch(OutOfBounds e) {
                throw new OutOfBounds($"{index=}, {size=}");
            }
        }

        assert:bounds index >= 0;
        for (Int i = 0, Int c = bufs.size, Int total = 0; i < c; ++i) {
            Char[] each = bufs[i];
            Int    next = total + each.size;
            if (index < next) {
                // note: going back and modifying an earlier buffer is quites expensive, because
                //       buffers can be frozen once they've been "committed", which happens as they
                //       fill up
                bufs[i] = bufs[i].reify(Mutable).replace(index-total, value);
                return;
            }
            total = next;
        }
        assert;
    }

    // ----- Sliceable methods ---------------------------------------------------------------------

    @Override
    @Op("[..]") StringBuffer slice(Range<Int> indexes) {
        Int thatSize = indexes.size;
        if (thatSize == 0) {
            return new StringBuffer();
        }

        Int     thisLo   = indexes.effectiveLowerBound;
        Int     thisHi   = indexes.effectiveUpperBound;
        Boolean reverse  = indexes.descending;
        Int     thisSize = this.size;
        assert:bounds 0 <= thisLo <= thisHi < thisSize;
        if (thisSize == thatSize && !reverse) {
            return duplicate();
        }

        if (thisLo >= prevSize) {
            // the entire slice comes out of the current append buffer
            Char[] thatBuf = reverse
                    ? buf[thisHi-prevSize .. thisLo-prevSize]
                    : buf[thisLo-prevSize .. thisHi-prevSize];
            return new StringBuffer(thatBuf);
        }

        // find which sub-buffers of this StringBuffer will be used (in whole or part) by the
        // resulting StringBuffer
        Int      loBuffer  = 0;
        Int      loOffset  = 0;
        Int      hiBuffer  = 0;
        Int      hiOffset  = 0;
        Int      total     = 0;
        Boolean  findLo    = True;
        Char[][] thisBufs  = bufs;
        Int      thisCount = thisBufs.size;
        for (Int i = 0, Int c = thisCount + (buf.empty ? 0 : 1); i < c; ++i) {
            Char[] cur = i < thisCount ? thisBufs[i] : buf;
            Int    len = cur.size;
            if (findLo && thisLo - total < len) {
                loBuffer = i;
                loOffset = thisLo - total;
                findLo = False;
            }

            if (!findLo && thisHi - total < len) {
                hiBuffer = i;
                hiOffset = thisHi - total;
                break;
            }

            total += len;
        }

        if (loBuffer == hiBuffer) {
            // the entire slice comes out of a single buffer (but not the append buffer)
            Char[] thisBuf = bufs[loBuffer];
            Char[] thatBuf = reverse ? thisBuf[hiOffset..loOffset] : thisBuf[loOffset..hiOffset];
            return new StringBuffer(thatBuf);
        }

        Int      thatCount = hiBuffer - loBuffer + 1;
        Char[][] thatBufs  = new Char[][](thatCount);
        if (reverse) {
            // add the first buffer (which could be the append buffer) from the "hi" location
            Char[] first = hiBuffer == thisCount ? buf : thisBufs[hiBuffer];
            thatBufs.add(first[hiOffset..0]);
            // add the in-between buffers
            for (Int i = hiBuffer - 1; i > loBuffer; --i) {
                thatBufs.add(thisBufs[i].reversed());
            }
            // add the last buffer from the "lo" location
            Char[] last = thisBufs[loBuffer];
            thatBufs.add(last[last.size >.. loOffset]);
        } else {
            // add the first buffer from the "lo" location
            Char[] first = thisBufs[loBuffer];
            thatBufs.add(first[loOffset..<first.size]);
            // add the in-between buffers
            for (Int i = loBuffer + 1; i < hiBuffer; ++i) {
                thatBufs.add(thisBufs[i]);
            }
            // add the last buffer (which could be the append buffer) from the "hi" location
            Char[] last = hiBuffer == thisCount ? buf : thisBufs[hiBuffer];
            thatBufs.add(last[0..hiOffset]);
        }
        return new StringBuffer([], bufs=thatBufs, prevSize=thatSize);
    }

    @Override
    StringBuffer reify() {
        buf = buf.reify();
        if (!bufs.empty) {
            bufs = bufs.toArray(mutability=Mutable);
            for (Int i = 0, Int c = bufs.size; i < c; ++i) {
                bufs[i] = bufs[i].reify();
            }
        }
        return this;
    }

    // ----- Object methods ------------------------------------------------------------------------

    @Auto
    @Override
    String toString() {
        Int size = this.size;
        if (size == 0) {
            return "";
        }

        Char[] concat;
        if (concat := hasSingleBuffer()) {
            Int cap = concat.capacity;
            if (cap > 32 && cap - size > size >> 2) { // max waste 20%
                // make a copy without the waste
                concat = new Array<Char>(Constant, concat);
            }
        } else {
            concat = new Char[](size);
            for (Char[] each : bufs) {
                concat.addAll(each);
            }
            concat.addAll(buf);
        }
        concat = concat.freeze(inPlace = True);

        buf      = [];
        space    = 0;
        bufs     = [concat];
        prevSize = size;
        nextCap  = 0;

        return new String(concat);
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * If the `StringBuffer` only contains a single `Char[]`, then return it.
     *
     * @return True iff the `StringBuffer` contains exactly one `Char[]`
     * @return (conditional) the single `Char[]`
     */
    private conditional Char[] hasSingleBuffer() {
        if (bufs.empty) {
            return True, buf;
        }

        if (buf.empty && bufs.size == 1) {
            return True, bufs[0];
        }

        return False;
    }

    /**
     * Move the "current" buffer to the list of "past" / "committed" buffers.
     */
    private void commitBuf() {
        Int used = buf.size;
        if (used == 0) {
            return;
        }

        Int waste = buf.capacity - used;
        if (waste > used && waste > MinBuf) {
            buf = new Array<Char>(Constant, buf);
        }

        bufs      = bufs.toArray(mutability=Mutable, inPlace=True).add(buf);
        prevSize += used;

        buf   = [];
        space = 0;
    }

    /**
     * Allocate a `Mutable` buffer to collect appending characters into.
     *
     * Note: There must not be anything in the current buffer when this is called; this method does
     * not check the current buffer.
     */
    private Char[] allocNext() {
        Int cap = nextCap == 0 ? MinBuf : nextCap;
        buf     = new Char[](cap);
        space   = cap;
        nextCap = 0;
        return buf;
    }

    /**
     * Add content that doesn't fit into the current buffer.
     */
    private void addOverflow(Iterable<Char> iterable, Int sizeToAdd) {
        if (sizeToAdd > MinBuf, Char[] chunk := hasStableCharArray(iterable)) {
            commitBuf();
            addChunk(chunk);
        } else if (iterable.is(Char[])) {
            appendOverflowChars(iterable);
        } else if (iterable.is(StringBuffer)) {
            appendStringBuffer(iterable);
        } else {
            ensureCapacity(sizeToAdd);
            for (Char ch : iterable) {
                add(ch);
            }
        }
    }

    /**
     * Append the specified chunk directly to the
     *
     * Note: There must not be anything in the current buffer when this is called; this method does
     * not check the current buffer.
     *
     * @param chunk  a `Char[]` chunk to add "as is"
     */
    private void addChunk(Char[] chunk) {
        if (bufs.mutability != Mutable) {
            bufs = bufs.toArray(mutability=Mutable, inPlace=True);
        }
        bufs.add(chunk);
        prevSize += chunk.size;
    }

    /**
     * When a `Char[]` doesn't fit in our current buffer, figure out how to efficiently append its
     * contents to this `StringBuffer`.
     *
     * @param from  a `Char[]` that doesn't fit in our current buffer
     */
    private void appendOverflowChars(Char[] from) {
        Int fromOffset = 0;
        if (space > 0) {
            for (Char[] to = buf, Int i = 0, Int c = space; i < c; ++i) {
                to.add(from[i]);
            }
            fromOffset = space;
        }
        commitBuf();

        Int remains = from.size - fromOffset;
        if (remains <= nextCap || remains <= MinBuf) {
            if (nextCap > 0 && remains > nextCap) {
                nextCap = 0;
            }
            nextCap = nextCap.notLessThan(remains);
            Char[] to  = allocNext();
            Int    end = from.size;
            while (fromOffset < end) {
                to.add(from[fromOffset++]);
            }
            space -= remains;
        } else {
            // just copy the rest of it as its own chunk
            Char[] chunk = from[fromOffset ..< from.size];
            if (chunk.inPlace) {
                chunk = chunk.toArray(mutability=Constant, inPlace=True);
            }
            addChunk(chunk);
        }
    }

    /**
     * Append that StringBuffer to this StringBuffer.
     *
     * @param that  the StringBuffer to copy the contents from, appending that to the end of `this`
     *              StringBuffer
     *
     * @return this
     */
    private StringBuffer appendStringBuffer(StringBuffer that) {
        if (that.size < MinBuf) {
            // just copy the small contents char-by-char
            Char[][] thatBufs = that.bufs;
            for (Int i = 0, Int c = thatBufs.size; i < c; ++i) {
                this.addAll(thatBufs[i]);
            }
            this.addAll(that.buf);
        } else {
            // copy over the contents of that StringBuffer as chunks
            this.commitBuf();
            that.commitBuf();
            for (Int i = 0, Int c = that.bufs.size; i < c; ++i) {
                Char[] thatBuf = that.bufs[i];
                if (thatBuf.inPlace) {
                    // we're sharing the buffers so freeze them to force CoW
                    thatBuf = thatBuf.freeze(inPlace=True);
                }
                this.addChunk(thatBuf);
            }
        }
        return this;
    }

    /**
     * Determine if we can use the passed `Iterable<Char>` reference to get a Char[] that we rely on
     * to not change size or content.
     *
     * @param iterable  any `Iterable<Char>`
     */
    private static conditional Char[] hasStableCharArray(Iterable<Char> iterable) {
        if (iterable.is(String)) {
            return True, iterable.chars;
        }

        if (iterable.is(Char[]) && !iterable.inPlace) {
            return True, iterable;
        }

        return False;
    }
}
