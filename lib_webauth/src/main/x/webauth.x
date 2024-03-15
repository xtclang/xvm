/*
 * The `webauth` module is used to easily add data driven authentication to an application.
 * It includes:
 * * A [user-data schema](AuthSchema) that can easily be added to an applications database schema;
 * * A [realm implementation](DBRealm) that operates using the data in that schema;
 * * A [Configuration] that is designed to be easily represented in a reflection-driven UI for
 *   configuring injectable services;
 * * TODO
 *   need URL of web site / service
 *   ip whitelist / blacklist
 *   2-factor
 *   oauth
 *   device based authenticators
 *
 * TODO initialize database automatically if necessary
 */
module webauth.xtclang.org {
    package crypto import crypto.xtclang.org;
    package net    import net.xtclang.org;         // TODO "for this module, I want to override injection of ..."
    package oodb   import oodb.xtclang.org;
    package web    import web.xtclang.org;

    import crypto.Signer;
    import crypto.Signature;
    import net.IPAddress;
    import web.security.Realm;
    import Realm.Hash;
    import Realm.HashInfo;

    /**
     * Information about the use of a particular IP address.
     */
    const IPInfo
            (
            IPAddress ip,
            Int       passCount,
            Time?     lastPass,
            Int       failCount,
            Time?     lastFail,
            );

//    /**
//     * For a telephonic device, what type is the device?
//     */
//    enum PhoneType
//        {
//        /**
//         * The phone has not been categorized.
//         */
//        Unspecified,
//
//        /**
//         * The phone is a mobile phone, and it is assumed to have both voice and text capability.
//         */
//        Mobile,
//
//        /**
//         * The phone is a home phone, and may be a land-line.
//         */
//        Home,
//
//        /**
//         * The phone is a home phone, and may be a land-line.
//         */
//        Office,
//
//        /**
//         * The phone is for a fax machine, which is a device apparently used by cave men to
//         * communicate when smoke signals were not working.
//         */
//        Fax,
//        }
//
//    /**
//     * Telephone country codes. In theory, these could be in a database, but they are relatively
//     * static.
//     */
//    enum CountryCode(Int code)
//        {
//        America(1),
//        // TODO
//        }
//
// possibly need "this" country code (from user's point of view? from server's point of view?
//
//    /**
//     * For a particular feature, is a device capable of that feature?
//     */
//    enum Capability
//        {
//        /**
//         * The capability is absent.
//         */
//        Absent,
//
//        /**
//         * The capability may or may not exist.
//         */
//        Unknown,
//
//        /**
//         * The capability may or may not exist, but it is likely to be present.
//         */
//        Assumed,
//
//        /**
//         * The capability is known to exist.
//         */
//        Present,
//        }
//
//    /**
//     * For a particular feature, is a device capable of that feature?
//     */
//    enum Preference
//        {
//        /**
//         * The choice has not been specified.
//         */
//        Unknown,
//
//        /**
//         * The use of the capability is explicitly forbidden.
//         */
//        Forbidden,
//
//        /**
//         * The capability exists but should not be used unless necessary.
//         */
//        Avoided,
//
//        /**
//         * The capability exists and may be used when appropriate.
//         */
//        Approved,
//
//        /**
//         * The capability exists and should be used as the preferred capability, when appropriate.
//         */
//        Preferred,
//        }
//
//    /**
//     * The Phone represents a combination of a number, and the capabilities and preferences related
//     * to that number.
//     */
//    const Phone(PhoneType type, CountryCode? country, String digits)
//        {
//        Capability textCapable  = Unknown;
//        Capability voiceCapable = Unknown;
//        Capability faxCapable   = Unknown;
//
//        /**
//         *
//         */
//        CountryCode? country;
//
//        /**
//         * May contain:
//         * * Digits
//         * * Commas may indicate a pause in a dialing sequence.
//         * * hash (`#`) and asterisk (`*`)
//         * * Whitespace and dots are ignored.
//         */
//        String digits;
//
//        /**
//         * @return a String representing the sequence of digits to dial, potentially containing a
//         *         leading plus sign, digits, commas to represent pauses in the dialing sequence,
//         *         hash (`#`) and asterisk (`*`) for unknown purposes, and whitespace and periods
//         *         that are ignored
//         */
//        String dialSequence.get()
//            {
//            if (CountryCode country ?= this.country)
//                {
//                return $"+{country.code} {digits}");
//                }
//
//            return digits;
//            }
//        }
//
//    enum EmailType
//        {
//        Personal,
//        Work,
//        }
//
//    const Email
//        (
//        EmailType type,
//        String    address,
//        Boolean   primary = False,
//        );
//
//    const UserDetail
//        {
//        }
//
//    /**
//     * This the result of a login attempt. A single login may have a sequence of results, if
//     * 2-factor authentication is used.
//     */
//    enum LoginResult
//        {
//        /**
//         * The user agent is connecting from an IP that has been "black-holed".
//         */
//        BlackholedIP,
//
//        /**
//         * The user agent identifies itself as a client that has been "black-holed".
//         */
//        BlackholedAgent,
//
//        /**
//         * The user name (the login ID) is unknown.
//         *
//         * In most applications, it is a security hole to report this information back to the user,
//         * because doing so would support probing for valid login IDs.
//         */
//        UnknownUser,
//
//        /**
//         * The login ID has been suspended. (The reason is on the [User] record itself.)
//         */
//        SuspendedUser,
//
//        /**
//         * The password that was provided does not match the current password on record.
//         */
//        WrongPassword,
//
//        /**
//         * The password matched; a second factor of authentication has been initiated.
//         */
//        Awaiting2ndFactor,
//
//        /**
//         * The second factor of authentication was not responded to in time.
//         */
//        TimedOut2ndFactor,
//
//        /**
//         * The user explicitly rejected the second factor of authentication. (This could signify a
//         * serious problem, e.g. the password has been compromised.)
//         */
//        Rejected2ndFactor,
//
//        /**
//         * Authentication succeeded.
//         */
//        Success,
//        }
//
//    /**
//     * Information about an attempt to log in.
//     */
//    const LoginAttempt
//        (
//        String      name,
//        Time        time,
//        IPAddress   ip,
//        String      agent,
//        LoginResult result,
//        );
//
//    /**
//     * Suspend the user account.
//     *
//     * @param reason  a human readable explanation for the suspension
//     *
//     * @return the suspended instance of this user account data
//     */
//    User suspend(String reason)
//        {
//        return this.with(enabled=False, note=reason);
//        }
//
//    /**
//     * Undo the suspension of    the user account.
//     *
//     * @return the restored (no longer suspended) instance of this user account data
//     */
//    User unsuspend()
//        {
//        return this.with(enabled=True, clearNote=True);
//        }
}