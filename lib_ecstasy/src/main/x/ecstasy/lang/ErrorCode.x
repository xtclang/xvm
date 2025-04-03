/**
 * An `ErrorCode` is intended to be implemented by error code enumerations.
 */
interface ErrorCode
        extends Enum {
    /**
     * The error code string that identifies the error message.
     */
    @RO String code;

    /**
     * The default unformatted error message in the default language.
     */
    @RO String message;
}