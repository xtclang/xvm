/**
 * An entry in the UserHistory table.
 */
const UserChange
        (
        Int|Int[] userId,
        Form      form,
        Time      timestamp,
        String?   desc     = Null,
        Int       byUserId = 0,
        Details?  details  = Null,
        ) {
    /**
     * The form of the user change record.
     */
    enum Form {
        Create,
        Destroy,
        Enable,
        Disable,
        ChangePassword,
        ClearPassword,
        SetPassword,
    }

    /**
     * The form of the user change record.
     */
    typedef User.HashInfo as Details;

    /**
     * @param userId  the User key
     *
     * @return True iff this specific change record is related to the specified user id
     */
    Boolean appliesTo(Int userId) {
        Int|Int[] userIds = this.userId;
        return userIds.is(Int[])?.contains(userId) : userIds == userId;
    }
}