import ecstasy.collections.CollectArray;

import oodb.DBMap;

/**
 * Records of changes related to a particular user.
 */
mixin UserHistory
        into DBMap<Int, UserChange> {
    /**
     * Find the change records related to the specified internal userId.
     *
     * @param userId  the internal user ID
     *
     * @return an array of zero or more change records
     */
    UserChange[] findByUser(Int userId) {
        return values.filter(chg -> chg.appliesTo(userId), CollectArray.of(UserChange))
                     .sorted((chg1, chg2) -> chg1.timestamp <=> chg2.timestamp, inPlace=True)
                     .freeze(inPlace=True);
    }
}