class SharedListView<Element implements Const>
    {
    private SharedListController<Element> service;

    construct(SharedListController<Element> service)
        {
        this.service = service;
        }

    @ro Int size.get() {return service.size;}
    @ro Boolean empty.get()
        {
        return size == 0;
        }

    @ro Boolean contains(function Boolean match(Element))
        {
        if (&match.isConst)
            {
            // const function can be evaluated "in-place" service side
            return service.contains(match);
            }

        return iterator().forEach(match);
        }
    @ro Boolean contains(Element element)
        {
        return contains(el -> el == element);
        }

    Boolean add(ElType el)    {return service.add(el);}
    Boolean remove(ElType el) {return service.remove(el);}
    Void clear()              {return service.clear();}

    Iterator<Element> iterator();
    Stream<Element> stream();
    }

async SharedListController<Element implements Const>
    {
    private List<Element> model;

    construct()
        {
        model = new ArrayList();
        }

    @ro size.get()
        {
        return model.size;
        }

    @ro Boolean contains(function Boolean match(Element))
        {
        return model.iterator().forEach(match);
        }
    }