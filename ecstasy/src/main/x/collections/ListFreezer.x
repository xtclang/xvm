/**
 * Simple List implementations that need to implement Freezable can use this mix-in to do so:
 *
 *     incorporates conditional ListFreezer<Element extends immutable Object | Freezable>
 *
 * This implementation requires that the List have a copy constructor, i.e. a constructor that
 * requires only a "this" as its argument.
 */
mixin ListFreezer<Element extends ImmutableAble>
        into List<Element>
        implements Freezable
    {
    @Override
    immutable ListFreezer freeze(Boolean inPlace = False)
        {
        if (this.is(immutable ListFreezer))
            {
            return this;
            }

        if (inPlace && all(e -> e.is(immutable Object)))
            {
            return makeImmutable();
            }

        // inPlace  inPlace  indexed
        // request  List     List     description
        // -------  -------  -------  --------------------------------------------------------------
        //    N        N        N     these two will copy-construct the frozen contents of this list
        //    N        N        Y
        //
        //    N        Y        N     these two could just copy-construct this list, and freeze that
        //    N        Y        Y     list in place, but instead just do the same as above
        //
        //    Y        N        N     the request is in-place, but list is not, so these ends up
        //    Y        N        Y     being the same as "NNN" and "NNY"
        //
        //    Y        Y        N     easy and efficient: freeze element in place using a cursor
        //    Y        Y        Y     easy and efficient: freeze the elements in place using []

        if (inPlace && this.inPlace)
            {
            if (indexed)
                {
                for (Int i = 0, Int c = size; i < c; ++i)
                    {
                    Element e = this[i];
                    if (!e.is(immutable Object))
                        {
                        this[i] = e.freeze();
                        }
                    }
                }
            else
                {
                Cursor cur = cursor();
                while (cur.exists)
                    {
                    Element e = cur.value;
                    if (!e.is(immutable Object))
                        {
                        cur.value = e.freeze();
                        }
                    cur.advance();
                    }
                }
            return makeImmutable();
            }

        // find the copy constructor
        typedef Function<<>, <ListFreezer>> Constructor;
        assert Constructor constructor := &this.actualType.constructors.filter(
                f -> f.params.size >= 1
                    && f.params[0].ParamType.is(Type<Iterable<Element>>)
                    && (f.params.size == 1 || f.params[[1..f.params.size)].all(p -> p.defaultValue())))
                .iterator().next();

        // bind any default parameters
        if (constructor.params.size > 1)
            {
            constructor = constructor.bind(constructor.params[[1..constructor.params.size)]
                    .associateWith(p -> {assert val v := p.defaultValue(); return v;})).as(Constructor);
            }

        // create a List that enumerates the frozen contents of this list
        List frozenContents = new List<Element>()
            {
            @Override
            Int size.get()
                {
                return this.ListFreezer.size;
                }

            @Override
            @Op("[]") Element getElement(Int index)
                {
                Element e = this.ListFreezer[index];
                return e.is(immutable Element) ? e : e.freeze();
                }

            @Override
            Iterator<Element> iterator()
                {
                // implementations that are not indexed should provide a more efficient implementation
                Iterator<Element> unfrozen = this.ListFreezer.iterator();
                return new Iterator()
                    {
                    @Override
                    conditional Element next()
                        {
                        if (Element e := unfrozen.next())
                            {
                            return True, e.is(immutable Element) ? e : e.freeze();
                            }
                        return False;
                        }
                    };
                }
            };

        return constructor.invoke(Tuple:(frozenContents))[0].freeze(true);
        }
    }
