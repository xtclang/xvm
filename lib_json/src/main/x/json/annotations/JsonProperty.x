/**
 * An annotation to allow customization of the JSON name of a value.
 *
 * For example, if the JSON field name for a property needs to be "$data", this is invalid as an
 * Ecstasy identifier name. The property can be annotated like this:
 *
 *   @JsonProperty("$data")
 *   String data;
 *
 * The [ReflectionMapping] will use the value from the annotation to map the JSON field name to the
 * property name.
 *
 * @param jsonName - the customized name of the field
 */
annotation JsonProperty(String jsonName)
        into Class | Property {
}
