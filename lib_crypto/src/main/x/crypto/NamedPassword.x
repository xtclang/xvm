/**
 * A `NamedPassword` is a holder of a name and a password.
 */
const NamedPassword(String name, String password)
        implements CryptoPassword {

    @Override
    conditional String isVisible() {
        return True, password;
    }

    @Override
    String toString() {
        return $"{name=}; password {isVisible() ? "" : "not "}visible";
    }
}