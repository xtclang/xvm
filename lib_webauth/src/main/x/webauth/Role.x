/**
 * A user role. Used with the `@Restrict` annotation.
 */
const Role
        (
        Int      roleId,
        String   roleName,
        String[] altNames,      // REVIEW  this would allow us to collapse several Roles into one
        String   description,
        ) {
    // TODO
}
