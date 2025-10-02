/**
 * A mapping for TimeZone values.
 */
const TimeZoneMapping
        implements Mapping<TimeZone> {

    @Override
    String typeName.get() {
        return "TimeZone";
    }

    @Override
    Serializable read(ElementInput in) {
        String tz = in.readString("");
        return TimeZone.of(tz) ?: throw new IllegalJSON($"Bad timezone: \"{tz}\"");
    }

    @Override
    void write(ElementOutput out, Serializable value) {
        out.add(value.name ?: value.toString(True));
    }
}
