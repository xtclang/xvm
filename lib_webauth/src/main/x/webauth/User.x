/**
 * A User is a representation of a login identity and password.
 *
 * TODO
 *   password history
 *   Email[]
 *   Phone[]
 *   history
 *   String?        note      = Null,
 *   HashInfo[]     passwordHistory,
 *   IPInfo[]       recentIPs = [],
 *   contact info etc.
 *
 * @param userName        the unique name (or email address, etc.) used as a user login identity
 * @param userId          the unique internal user identity
 * @param userHashes      the cryptographic digests of the user name
 * @param passwordHashes  the cryptographic digests of the user password
 * @param roleIds         the role identities that have been assigned to the user
 * @param enabled         True indicates that the User is active (can log in)
 */
const User
        (
        Int      userId,
        String   userName,
        HashInfo userHashes,
        HashInfo passwordHashes,
        Int[]    roleIds = [],
        Boolean  enabled = True,
        )
    {
    /**
     * A group of hashes, one for each of the supported hash algorithms. If a hash algorithm is not
     * configured to be used, then its hash will be a zero-length byte array.
     */
    static const HashInfo
            (
            Time created,
            Hash md5,
            Hash sha256,
            Hash sha512_256,
            )
        {
        conditional Hash hashFor(Signer hasher)
            {
            Hash hash;
            switch (hasher.algorithm.name)
                {
                case "MD5":
                    hash = md5;
                    break;

                case "SHA-256":
                    hash = sha256;
                    break;

                case "SHA-512-256":
                    hash = sha512_256;
                    break;

                default:
                    return False;
                }

            return hash.empty
                    ? False
                    : (True, hash);
            }
        }

    /**
     * Create a new User object based on this User object, but with specific properties modified
     * as indicated by the parameters.
     *
     * @param userId          (optional) the new unique internal user identity
     * @param userName        (optional) the new unique name (or email address, etc.) used as a user
     *                        login identity
     * @param userHashes      (optional) the cryptographic digests of the user name
     * @param passwordHashes  (optional) the cryptographic digests of the user password
     * @param roles           (optional) the role identities that have been assigned to the user
     * @param enabled         (optional) True indicates that the User is active (can log in); False
     *                                   disables the account
     */
    User with(
            Int?       userId         = Null,
            String?    userName       = Null,
            HashInfo?  userHashes     = Null,
            HashInfo?  passwordHashes = Null,
            Int[]?     roleIds        = Null,
            Int|Int[]? addRoleIds     = Null,
            Int|Int[]? removeRoleIds  = Null,
            Boolean?   enabled        = Null,
            )
        {
        if (roleIds != Null || addRoleIds != Null || removeRoleIds != Null)
            {
            HashSet<Int> roleIdSet = new HashSet(roleIds ?: this.roleIds);
            roleIdSet.add(addRoleIds?.is(Int)?);
            roleIdSet.addAll(addRoleIds?.is(Int[])?);
            roleIdSet.remove(removeRoleIds?.is(Int)?);
            roleIdSet.removeAll(removeRoleIds?.is(Int[])?);
            roleIds = roleIdSet.toArray(Constant);
            }

        return new User(
                userId         = userId         ?: this.userId,
                userName       = userName       ?: this.userName,
                userHashes     = userHashes     ?: this.userHashes,
                passwordHashes = passwordHashes ?: this.passwordHashes,
                roleIds        = roleIds        ?: this.roleIds,
                enabled        = enabled        ?: this.enabled,
                );
        }
    }

