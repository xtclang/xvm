/**
* TODO
*/
class ComplementSet<Element>(Set<Element> complementSet, immutable Set<Element> universalSet)
        implements Set<Element>
    {
    // TODO size contains iterator etc.

    @Override
    Set<Element> complement(immutable Set<Element> universalSet)
        {
        return universalSet == this.universalSet
                ? complementSet
                : super();
        }

    @Override
    Set<Element> reify()
        {
        return switch()
            {
            case size < complementSet.size.maxOf(0x100) : new ListSet(this);
            case complementSet.is(immutable Set)        : this;
            default: new ComplementSet(complementSet.freeze, universalSet);
            };
        }
    }
