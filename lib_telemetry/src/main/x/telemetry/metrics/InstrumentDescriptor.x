/**
 * Identity and metadata for a metric instrument.
 *
 * @param name         the name of the instrument. Must start with a letter and contain only
 *                     alphanumeric characters, underscores, hyphens, and forward slashes (max 255
 *                     characters).
 * @param unit         an optional string provided by the author of the Instrument. Unit is
 *                     case-sensitive (e.g. kb and kB are different units), have a maximum length of
 *                     63 characters.
 * @param description  an optional description of the Instrument.
 */
const InstrumentDescriptor(String name, String? unit = Null, String? description = Null) {
    static <CompileType extends InstrumentDescriptor> Boolean equals(CompileType id1, CompileType id2) {
        if (id1.name == id2.name) {
            String? unit1 = id1.unit;
            String? unit2 = id2.unit;
            if (unit1.is(String) && unit2.is(String)) {
                return unit1 == unit2;
            } else {
                return unit1 == Null && unit2 == Null;
            }
        }
        return False;
    }

    static <CompileType extends InstrumentDescriptor> Ordered compare(CompileType id1, CompileType id2) {
        Ordered o = id1.name <=> id2.name;
        if (o.is(Equal)) {
            String? unit1 = id1.unit;
            String? unit2 = id2.unit;
            o = switch(unit1.is(_), unit2.is(_)) {
                case (String, String): unit1 <=> unit2;
                case (String, _):      Greater;
                case (_, String):      Lesser;
                default:               Equal;
            };
        }
        return o;
    }

    static <CompileType extends InstrumentDescriptor> Int hashCode(CompileType id) {
        Int h = id.unit?.hashCode() : 0;
        return 31 * id.name.hashCode() + h;
    }
}
