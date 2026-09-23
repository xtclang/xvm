            // Test-only provider cases inserted into the production runner source.
            switch (type.isNullable() ?: type, name) {
            case (net.Network, "network"):
                @Inject net.Network network;
                return network;
            case (web.Client.Connector, "connector"):
                @Inject web.Client.Connector connector;
                return connector;
            case (xenia.HttpServer, "server"):
                @Inject xenia.HttpServer server;
                return server;
            }
