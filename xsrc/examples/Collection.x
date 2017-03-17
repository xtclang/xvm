interface Collection<ElType>
        // usually used with SimpleCollection
    {
    @ro Int size;
    @ro Boolean empty;
    @ro Boolean contains(ElType el);

    Boolean add(ElType el); `
    Boolean remove(ElType el);
    void clear();

    Boolean addAll(Collection<ElType> col);
    Boolean removeAll(Collection<ElType> col);

    Stream<ElType> stream();
    }

class SimpleCollectionSkeleton<ElType>
    {
    @ro Int size;

    Boolean add(ElType el);
    Boolean remove(ElType el);
    }

trait SimpleCollection<ElType>
        implements Collection<ElType>
        requires SimpleCollectionSkeleton<ElType>
    {
    @ro Boolean empty.get()
        {
        return size == 0;
        }

    @ro ElType[] toArray()
        {
        ElType[] array = new ElType[size];
        int      i     = 0;
        for (ElType el : this)
            {
            array[i++] = el;
            }
        return array;
        }

    Boolean addAll(Collection<ElType> col)
        {
        Boolean fChanged = false;
        for (ElType el : col)
            {
            fChanged |= add(el);
            }
        return fChanged;
        }
        }

    Boolean removeAll(Collection<ElType> col)
        {
        // ditto addAll
        }
    }