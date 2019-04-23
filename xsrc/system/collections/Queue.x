interface Queue<ElementType>
    {
    conditional ElementType peek();
    
    ElementType take();

    typedef function void (ElementType) Consumer;
    typedef function void () Cancellable;

    Cancellable route(Consumer consumer);
    }