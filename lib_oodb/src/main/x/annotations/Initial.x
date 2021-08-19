/**
 * An annotation used for specifying an initial value for a DBValue.
 */
mixin Initial<Value>(Value initial)
         into Property<DBSchema, DBValue<Value>>
    {
    }