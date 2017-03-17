class SharedListView<ElementType implements Const>
    {
    private SharedListController<ElementType> service;

    construct(SharedListController<ElementType> service)
        {
        this.service = service;
        }

    @ro Int size.get() {return service.size;}
    @ro Boolean empty.get()
        {
        return size == 0;
        }

    @ro Boolean contains(function Boolean match(ElementType))
        {
        if (&match.isConst)
            {
            // const function can be evaluated "in-place" service side
            return service.contains(match);
            }

        return iterator().forEach(match);
        }
    @ro Boolean contains(ElementType element)
        {
        return contains(el -> el == element);
        }

    Boolean add(ElType el)    {return service.add(el);}
    Boolean remove(ElType el) {return service.remove(el);}
    Void clear()              {return service.clear();}

    Iterator<ElementType> iterator();
    Stream<ElementType> stream();
    }

async SharedListController<ElementType implements Const>
    {
    private List<ElementType> model;

    construct()
        {
        model = new ArrayList();
        }

    @ro size.get()
        {
        return model.size;
        }

    @ro Boolean contains(function Boolean match(ElementType))
        {
        return model.iterator().forEach(match);
        }
    }