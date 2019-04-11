/**
 * An IndexCursor is a simple [List.Cursor] implementation that delegates all operations back to a
 * using the index-based operations on the List itself.
 */
class IndexCursor<ElementType>
        implements List<ElementType>.Cursor
    {
    /**
     * Construct an IndexCursor for the specified List.
     *
     * @param index  the location of the cursor in the list, between `0` (inclusive) and [List.size]
     *               (exclusive)
     */
    construct(List<ElementType> list, Int index = 0)
        {
        if (index < 0 || index > list.size)
            {
            throw new OutOfBounds();
            }

        this.list  = list;
        this.index = index;
        }

    @Override
    public/private List list;

    @Override
    Int index
        {
        @Override
        Int get()
            {
            return super().minOf(list.size);
            }

        @Override
        void set(Int i)
            {
            if (i < 0 || i > list.size)
                {
                throw new OutOfBounds();
                }
            super(i);
            }
        }

    @Override
    Boolean exists.get()
        {
        return index < list.size;
        }

    @Override
    Boolean advance()
        {
        Int next = index + 1;
        Int size = list.size;
        index = next.minOf(size);
        return next < size;
        }

    @Override
    Boolean rewind()
        {
        Int prev = index;
        if (prev > 0)
            {
            index = prev - 1;
            return true;
            }
        return false;
        }

    @Override
    ElementType value
        {
        @Override
        ElementType get()
            {
            // may throw OutOfBounds
            return list[index];
            }

        @Override
        void set(ElementType value)
            {
            Int index = this.index;
            Int size  = list.size;
            if (index < size)
                {
                // may throw ReadOnly
                list[index] = value;
                }
            else if (list.mutability == Mutable)
                {
                list.add(value);
                index = size;
                }
            else
                {
                throw new ReadOnly();
                }
            }
        }

    @Override
    void insert(ElementType value)
        {
        if (list.mutability != Mutable)
            {
            throw new ReadOnly();
            }

        Int index = this.index;
        if (index < list.size)
            {
            list.insert(index, value);
            }
        else
            {
            list.add(value);
            }
        }

    @Override
    void delete()
        {
        if (list.mutability != Mutable)
            {
            throw new ReadOnly();
            }

        Int index = this.index;
        if (index < list.size)
            {
            list.delete(index);
            }
        }
    }
