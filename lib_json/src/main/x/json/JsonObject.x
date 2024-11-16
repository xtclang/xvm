/**
 * JSON Object.
 */
@AutoFreezable
class JsonObject
        implements Freezable
        delegates Map<String, Doc>(jsonObject) {

    construct(Map<String, Doc> map = []) {
        jsonObject = new ListMap();
        if (!map.empty) {
            jsonObject.putAll(map);
        }
    }

    private Map<String, Doc> jsonObject;

    @Override
    immutable Freezable freeze(Boolean inPlace = False) = makeImmutable();

    @Override
    @Op("[]") Value? getOrNull(Key key) = super(key);

    @Override
    @Op("[]=") void putInPlace(Key key, Value value) = super(key, value);

    // this doesn't work atm, but it should; will make put ops more usable
//    @Op("[]=") void putInPlace(Key key, Int value) = putInPlace(key, value.toIntLiteral());
//
//    @Op("[]=") void putInPlace(Key key, FPNumber value) = putInPlace(key, value.toFPLiteral());
}

