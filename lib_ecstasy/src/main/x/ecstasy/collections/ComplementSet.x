import iterators.FilteredIterator;

/**
* TODO
*/
class ComplementSet<Element>(Set<Element> complementSet, immutable Set<Element> universalSet)
        implements Set<Element>
    {
    @Override
    Int size.get()
        {
        return universalSet.size - complementSet.size;
        }

    @Override
    Iterator<Element> iterator()
        {
        return new FilteredIterator(universalSet.iterator(), e -> !complementSet.contains(e));
        }

    // TODO contains etc.

    @Override
    Set<Element> complement(immutable Set<Element> universalSet)
        {
        return universalSet == this.universalSet
                ? complementSet
                : super(universalSet);
        }

    @Override
    Set<Element> reify()
        {
        return switch ()
            {
            case size < complementSet.size.notLessThan(0x100): new ListSet(this);
            case complementSet.is(immutable Set)             : this;
            default: new ListSet(this); // TODO CP ??? new ComplementSet(complementSet.freeze(), universalSet);
            };
        }
    }
