/**
 * An implementation of the [Authenticator] interface that attempts to authorized the incoming
 * request using any of the specified `Authenticators`.
 */
const ChainAuthenticator(Realm realm, Authenticator[] chain)
        implements Authenticator {

    @Override
    construct(ChainAuthenticator that) {
        construct ChainAuthenticator(realm, new Authenticator[that.chain.size](i -> that.chain[i].duplicate()));
    }

    // ----- Authenticator API ---------------------------------------------------------------------

    @Override
    Attempt[] findAndRevokeSecrets(RequestIn request) {
        Attempt[] result = [];
        for (Authenticator auth : chain) {
            Attempt[] partial = auth.findAndRevokeSecrets(request);
            if (!partial.empty) {
                result += partial;
            }
        }
        return result;
    }

    @Override
    Attempt[] authenticate(RequestIn request) {
        Attempt[]  single = [];
        Attempt[]? merged = Null;
        for (Authenticator auth : chain) {
            Attempt[] current = auth.authenticate(request);
            if (!current.empty) {
                if (single.empty) {
                    single = current;
                } else {
                    if (merged == Null) {
                        merged = new Attempt[](single.size + current.size);
                        merged.addAll(single);
                    }
                    merged.addAll(current);
                }
            }
        }
        return merged?.freeze(True) : single;
    }
}
