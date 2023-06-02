/**
 * An `OpaquePassword` is used when a [CryptoPassword] needs to be injected into a container without
 * allowing the password material (the raw `String`) to be visible within that container.
 */
const OpaquePassword(CryptoPassword actualPassword)
        delegates CryptoPassword(actualPassword) {

    @Override
    conditional String isVisible() {
        return False;
    }
}
