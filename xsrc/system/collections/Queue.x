interface Queue<ElementType>
    {
    conditional ElementType peek()
    
    conditional ElementType take();

    typedef function void () Cancellable;

    Cancellable route(Appender<ElementType>);
    }