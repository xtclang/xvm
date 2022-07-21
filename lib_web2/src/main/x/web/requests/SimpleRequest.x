class SimpleRequest
        implements Request
    {
    @RO HttpMessage!? parentMessage;

    @RO Header headers;

    enum ContentArity {None, Single, Multi}

    @RO ContentArity contentArity;

    Body ensureBody();

    List<HttpMessage> ensureMultiPart();
    }