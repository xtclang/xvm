/**
 * Application telemetry support.
 *
 * The functionality in this module is based on the Open Telemetry specification.
 * see: https://opentelemetry.io/docs/specs/
 */
module telemetry.xtclang.org {

    package collections import collections.xtclang.org;
    package convert     import convert.xtclang.org;
    package json        import json.xtclang.org;
    package web         import web.xtclang.org;

    import ecstasy.Service.Shareable;
    import ecstasy.SharedContext;

    /**
     * The injection name prefix for all telemetry configuration values.
     */
    static String ConfigPrefix = "xvm.telemetry";

    /**
     * AnyValue is used to represent various values of specific types in telemetry.
     *
     * An AnyValue is either:
     *
     * - a primitive type: string, boolean, double precision floating point (IEEE 754-1985), or
     *   signed 64 bit integer,
     * - a homogeneous array of primitive type values. A homogeneous array MUST NOT contain values
     *   of different types.
     * - a byte array.
     * - an array of AnyValue,
     * - a Mmp<string, AnyValue>,
     * - a Null value
     *
     * Arbitrary deep nesting of values for arrays and maps is allowed (essentially allows to
     * represent an equivalent of a JSON object).
     * Using array and map values may carry a higher performance overhead compared to primitive
     * values.
     */
    typedef PrimitiveValue | AnyValue[] | Map<String, AnyValue> as AnyValue;

    /**
     * The various primitive values allowed in an AnyValue.
     */
    typedef Nullable | Boolean | String | Int | IntLiteral | Float | FPLiteral | Byte[]
            as PrimitiveValue;

    /**
     * A map of attribute key-value pairs carried by resources, scopes, and data points.
     */
    typedef Map<String, AnyValue> as Attributes;

    /**
     * A `Resource` is an immutable representation of the entity producing telemetry as attributes.
     * Note that there are certain attributes that have prescribed meanings.
     *
     * The primary purpose of resources as a first-class concept in the API is decoupling of
     * discovery of resource information from telemetry exporters. This allows for independent
     * development and easy customization for users that need to integrate with closed source
     * environments.
     *
     * When used with distributed tracing, a resource can be associated with the tracer provider
     * when the tracer provider is created. That association cannot be changed later. When
     * associated with a tracer provider, all Spans produced by any Tracer from the provider MUST be
     * associated with this Resource.
     *
     * Analogous to distributed tracing, when used with metrics, a resource can be associated with a
     * MeterProvider. When associated with a MeterProvider, all metrics produced by any Meter from
     * the provider will be associated with this Resource.
     *
     * @param attributes              the attributes that describe the resource
     * @param entityRefs              the set of entities that participate in this Resource, Note:
     *                                keys in the references MUST exist in the attributes property
     * @param droppedAttributesCount  the number of dropped attributes
     */
    const Resource(Attributes attributes = [],
                   EntityRef[] entityRefs = [],
                   UInt32 droppedAttributesCount = 0) {
        /**
         * An empty Resource.
         */
        static Resource Empty = new Resource();

        static String ServiceNameKey = "service.name";

        static String ServiceVersionKey = "service.version";

        static String TelemetrySdkNameKey = "telemetry.sdk.name";

        static String TelemetrySdkLanguageKey = "telemetry.sdk.language";

        static String TelemetrySdkVersionKey = "telemetry.sdk.version";
    }

    /**
     * A ContextKey is a key used to access state in a Context.
     *
     * Keys are unique such that other libraries which may use the same context cannot
     * accidentally use the same key.
     *
     * @param <Value> the type of the value associated with the key
     */
    interface ContextKey<Value extends Shareable> {
        @RO String name;
    }
}
