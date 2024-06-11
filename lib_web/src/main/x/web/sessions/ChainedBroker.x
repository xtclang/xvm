/**
 * A `ChainedBroker` enables the use of more than one [Session Broker](Broker) when an application
 * needs to support different client types, such as when an application has both a web browser
 * client (relying on cookie support) and native device application clients (with unique client ID
 * support).
 */
 service ChainedBroker
        implements Broker {

    /**
     * Construct a `ChainedBroker` from a list of [Brokers](Broker).
     */
    construct(Broker[] brokers) {
        assert !brokers.empty;
        this.brokers = brokers.freeze();
    }

    /**
     * [Duplicable] constructor.
     */
    @Override
    construct(ChainedBroker that) {
        this.brokers = new Broker[that.brokers.size](i -> that.brokers[i].duplicate()).freeze(inPlace=True);
    }

    /**
     * A list of [Brokers](Broker).
     */
    public/private Broker[] brokers;

    @Override
    conditional Session findSession(RequestIn request) {
        for (Broker broker : brokers) {
            if (Session session := broker.findSession(request)) {
                // regardless of whether any other broker could answer the question positively, take
                // the first positive answer because the order of the brokers is significant
                return True, session;
            }
        }
        return False;
    }

    @Override
    conditional (Session|ResponseOut) requireSession(RequestIn request) {
        if (Session session := findSession(request)) {
            return True, session;
        }

        ResponseOut? firstResponse = Null;
        for (Broker broker : brokers) {
            if (val response := broker.requireSession(request)) {
                if (response.is(Session)) {
                    return True, response;
                }
                firstResponse ?:= response;
            }
        }
        return firstResponse.is(ResponseOut);
    }
}
