/**
 * Types that handle binding of values to a parameter.
 */
package binder
    {
    /**
     * The result of a binding.
     */
    const BindingResult<ValueType>(ValueType?        value  = Null,
                                   Boolean           bound  = False,
                                   ConversionError[] errors = []);

    /**
     * A holder of an error that occurred converting a value from one type to another.
     *
     * @param cause  the exception that caused the error
     * @param value  the optional value being converted
     */
    const ConversionError(Exception cause, Object? value = Null);
    }