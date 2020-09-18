/**
 * A database user or group.
 */
const User(UInt            id,
           String          name,
           Boolean         active,
           Boolean         group,
           Set<DBUser>     groups      = Set:[],
           Set<Permission> permissions = Set:[],
           Set<Permission> revocations = Set:[])
        implements oodb.DBUser
    {
    User with(UInt?            id          = Null,
              String?          name        = Null,
              Boolean?         active      = Null,
              Boolean?         group       = Null,
              Set<DBUser>?     groups      = Null,
              Set<Permission>? permissions = Null,
              Set<Permission>? revocations = Null)
        {
        return new User(id          ?: this.id,
                        name        ?: this.name,
                        active      ?: this.active,
                        group       ?: this.group,
                        groups      ?: this.groups,
                        permissions ?: this.permissions,
                        revocations ?: this.revocations);
        }

    @Override
    UInt id;

    @Override
    String name;

    @Override
    Boolean active;

    @Override
    Boolean group;

    @Override
    Set<DBUser> groups;

    @Override
    Set<Permission> permissions;

    @Override
    Set<Permission> revocations;

    @Override
    Boolean isAllowed(Permission request)
        {
        }
    }
