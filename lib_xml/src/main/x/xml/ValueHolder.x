/**
 * Represents a [Part] of an XML document that holds a named value. Specifically, an [Element] may
 * hold a value, and an [Attribute] must hold a value.
 */
interface ValueHolder
        extends Part {
    /**
     * The name associated with the value.
     */
    String name;

    /**
     * The textual form of the `ValueHolder`'s value, or `Null` if there is no value.
     */
    String? value;

    /**
     * Obtain the `ValueHolder`'s value converted to the specified `Value` type.
     *
     * @param format  a [Format] that can convert a `String` value to the specified `Value` type
     *
     * @return a decoded `Value`, or `Null`
     */
    <Value> Value? valueAs(Format<Value> format);

    /**
     * Obtain the `ValueHolder`'s value converted to the specified `Value` type, using the provided
     * default `Value` if this `ValueHolder` does not contain a value.
     *
     * @param format        a [Format] that can convert a `String` value to the specified `Value`
     *                      type
     * @param defaultValue  the default `Value` to use if the `ValueHolder` does not contain a value
     *
     * @return the decoded `Value` iff this `ValueHolder` contains a value; otherwise, the specified
     *         default `Value`
     */
    <Value> Value valueAs(Format<Value> format, Value defaultValue);

    /**
     * Convert the specified `Value` to a `String` using the provided [Format], and store that
     * `String` value in this `ValueHolder`.
     *
     * @param value   a `Value` that is compatible with the specified [Format]
     * @param format  a [Format] that can convert the specified `Value` to a `String`
     *
     * @return the `String` value that was stored in this `ValueHolder`
     */
    <Value> String? encode(Value? value, Format<Value> format);
}