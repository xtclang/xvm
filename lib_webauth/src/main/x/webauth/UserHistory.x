import oodb.DBMap;

/**
 * Records of changes related to a particular user.
 */
mixin UserHistory
        into DBMap<Int, UserChange>
    {
    /**
     * Find the change records related to the specified internal userId.
     *
     * @param userId  the internal user ID
     *
     * @return an array of zero or more change records
     */
    UserChange[] findByUser(Int userId)
        {
// TODO GG
//        return values.filter(uc -> uc.userId.is(Int)? == userId : uc.userId.contains(userId),
//                new User[]).as(User[]).sorted((uc1, uc2) -> uc1.timestamp <=> uc2.timestamp);
        return values.filter(uc -> uc.appliesTo(userId),
                new UserChange[]).as(UserChange[]).sorted((uc1, uc2) -> uc1.timestamp <=> uc2.timestamp);
        }
    }

