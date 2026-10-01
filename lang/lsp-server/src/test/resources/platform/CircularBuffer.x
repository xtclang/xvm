// From platform b8be627, common/src/main/x/common/utils/CircularBuffer.x.
// Unchanged class, wrapped in a standalone module for reproducible compiler-adapter tests.
module PlatformBuffer {
    /**
     * The CircularBuffer holds up to a specified capacity of elements, and once it reaches that number,
     * each additional element causes the oldest element to be removed. The elements are arranged in
     * the order from newest to oldest.
     */
    class CircularBuffer<Element>
            implements UniformIndexed<Int, Element>
            implements Appender<Element>
            implements Iterable<Element> {
        // ----- constructors --------------------------------------------------------------------------

        /**
         * Construct a CircularBuffer of the specified capacity.
         *
         * @param capacity  the maximum number of elements in the buffer
         */
        construct(Int capacity) {
            this.contents = new Element?[capacity];
        }

        // ----- properties ----------------------------------------------------------------------------

        @Override
        public/private Int size;

        /**
         * The allocated storage capacity of the CircularBuffer.
         */
        Int capacity.get() = contents.size;

        /**
         * An array that holds the elements of the CircularBuffer
         */
        protected/private Element?[] contents;

        /**
         * The index of the next element to add at the `head`. The index starts at zero, and is never
         * reset, so the index into the underlying `contents` array is the modulo of the `head` and the
         * size of the `contents` array. The size of the CircularBuffer is `head` until it's `full`.
         */
        protected/private Int head;

        // ----- public interface ----------------------------------------------------------------------

        /**
         * Clear the buffer.
         */
        CircularBuffer clear() {
            contents.fill(Null);
            head = 0;
            size = 0;
            return this;
        }

        // ----- UniformIndexed interface --------------------------------------------------------------

        @Override
        @Op("[]")
        Element getElement(Int index) {
            return contents[indexFor(index)].as(Element);
        }

        @Override
        @Op("[]=")
        void setElement(Int index, Element value) {
            contents[indexFor(index)] = value;
        }

        // ----- Iterable interface --------------------------------------------------------------------

        @Override
        Iterator<Element> iterator() {
            return new Iterator() {
                Int index = 0;

                @Override
                conditional Element next() {
                    return index < this.CircularBuffer.size
                            ? (True, this.CircularBuffer[index++])
                            : False;
                }
            };
        }

        // ----- Appender interface --------------------------------------------------------------------

        @Override
        @Op("+")
        CircularBuffer add(Element value) {
            Int index = head;
            contents[index++] = value;
            head = index == capacity ? 0 : index;
            if (size < capacity) {
                size++;
            }
            return this;
        }

        // ----- Object interface ----------------------------------------------------------------------

        @Override
        String toString(
                String                    sep    = ", ",
                String?                   pre    = "[",
                String?                   post   = "]",
                Int?                      limit  = Null,
                String                    trunc  = "...",
                function String(Element)? render = Null) {

            return toArray().toString(sep, pre, post, limit, trunc, render);
        }

        // ----- internal ------------------------------------------------------------------------------

        /**
         * Given an absolute index, where zero is the "newest" element, determine the corresponding
         * index in the contents array.
         *
         * @param index an index
         *
         * @return a corresponding index into the contents array
         */
        protected Int indexFor(Int index) {
            assert:bounds 0 <= index < size;
            index = head - 1 - index;
            return index < 0 ? capacity + index : index;
        }
    }
}
