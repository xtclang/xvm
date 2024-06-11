import oodb.DBCounter;
import oodb.DBMap;
import oodb.DBSchema;
import oodb.DBValue;
import oodb.NoTx;

/**
 * This is a schema that can easily be added to an application schema in order to "drag in" the
 * entire [webauth] database design. The logic in the `webauth` module will search through the
 * database to find this schema, and then will use this schema to store all of the data necessary
 * to implement the web authentication support (which also is part of this module).
 */
interface AuthSchema
        extends DBSchema {
    /**
     * The configuration for the authorization mechanism.
     */
    @RO DBValue<Configuration> config;

    /**
     * The [Principal] objects that exist within the [DBRealm].
     */
    @RO DBMap<Int, Principal> principals;

    /**
     * Internal [Principal] id generator.
     */
    @RO @NoTx DBCounter principalGen;

    /**
     * A lookup table from [Credential] "locator" strings to the id of the [Principal] that contains
     * that `Credential`.
     */
    @RO DBMap<String, Int> principalLocators;

    /**
     * The [Group] objects that exist within the [DBRealm].
     */
    @RO DBMap<Int, Group> groups;

    /**
     * Internal [Group] id generator.
     */
    @RO @NoTx DBCounter groupGen;

    /**
     * The [Entitlement] objects that exist within the [DBRealm].
     */
    @RO DBMap<Int, Entitlement> entitlements;

    /**
     * Internal [Entitlement] id generator.
     */
    @RO @NoTx DBCounter entitlementGen;

    /**
     * A lookup table from [Credential] "locator" strings to the id of the [Entitlement] that
     * contains that `Credential`.
     */
    @RO DBMap<String, Int> entitlementLocators;
}