import collections.IdentitySet;

import net.IPAddress;

import web.CookieConsent;
import web.Header;
import web.TrustLevel;

import HttpServer.RequestInfo;


/**
 * A representation of a session cookie. There are three session cookies used:
 *
 * 1. `xplaintext` - a non-persistent cookie that is subject to hijacking (not protected by TLS)
 *    * this cookie is created when any request arrives without a `xplaintext` cookie
 *    * specifies `Path=/;SameSite=Strict;HttpOnly`; does not specify `Expires` or `Domain`
 *    * contains base64 encoding of encrypted data: session id, cookie id, change counter, creation
 *      date, known consents, known cookies (1/2/3), ip address, random salt
 *
 * 2. `__Host-xtemporary` - a non-persistent cookie protected by TLS
 *    * this cookie is created when a request comes in over a TLS connection and there is no
 *      `__Host-xtemporary` cookie in the request
 *    * specifies `Path=/;SameSite=Strict;secure;HttpOnly`; does not specify `Expires` or `Domain`
 *    * contains base64 encoding of encrypted data: session id, cookie id, change counter, creation
 *      date, known consents, known cookies (1/2/3), ip address, random salt
 *
 * 3. `__Host-xconsented` - a persistent cookie protected by TLS
 *    * this cookie is only created when a request comes in over a TLS connection, and the session
 *      [cookieConsent] has been set, and there is no `__Host-xconsented` cookie in the request
 *    * specifies `Path=/;SameSite=Strict;secure;HttpOnly;Expires=...` ; does not specify `Domain`
 *    * contains human readable consent string and date/time of consent
 *    * contains base64 encoding of encrypted data: session id, cookie id, change counter, creation
 *      date, expiry, known consents, known cookies (1/2/3), ip address, random salt
 *    * if consent is withheld, then the session id and IP address are **not** recorded in the
 *      persistent portion.
 *
 * Notes:
 *
 * * All three cookies may show up on a secure connection, but it's always possible that any
 *   combination of the cookies can show up with any request. Here is how the server handles an
 *   incoming request with some combination of cookies 1, 2, and 3, with (1) or without (0) TLS:
 *
 *     1 2 3 TLS  description/action
 *     - - - ---  ------------------
 *            0   create new session; create cookie 1; redirect to verification
 *            1   create new session; create cookies 1 and 2; redirect to verification
 *     x      0   validate cookie 1
 *     x      1   validate cookie 1 & verify [prevTLS_] `== False`; create cookie 2;
 *                redirect to verification; set [prevTLS_] `= True`
 *       x    0   error (no TLS, so cookie 2 is illegally present; also missing cookie 1)
 *       x    1   error (missing cookie 1)
 *     x x    0   error (no TLS, so cookie 2 is illegally present)
 *     x x    1   validate cookie 1 & 2; if [cookieConsent] has been set, create cookie 3 and
 *                redirect to verification
 *         x  0   error (no TLS, so cookie 3 is illegally present)
 *         x  1   validate cookie 3; assume temporary cookies absent due to user agent restart;
 *                create cookie 1 & 2 and redirect to verification
 *     x   x  0   error (no TLS, so cookie 3 is illegally present)
 *     x   x  1   validate cookie 1 & 3 (1 must be newer than 3), and verify [prevTLS_] `== False`;
 *                use session specified by cookie 3 if possible, otherwise 1; create cookie 2;
 *                redirect to verification; set [prevTLS_] `= True`
 *       x x  0   error (no TLS, so cookie 2 and 3 are illegally present)
 *       x x  1   error (missing cookie 1)
 *     x x x  0   error (no TLS, so cookies 2 and 3 are illegally present)
 *     x x x  1   validate cookie 1 & 2 & 3 (must be identical)
 *
 * * When a cookie is created, the counter inside the cookie is incremented, and any existing
 *   cookies are also re-written so their contents are in agreement (although each necessarily
 *   differing at a binary level, in order to make spoofing of one cookie from another cookie's
 *   contents impossible)
 *
 * * Cookies need to be replaced whenever an IP address etc. changes, also after a period of time.
 *
 * * Persistent cookies are only used when permission has been given to use persistent cookies.
 *
 * * When a session is first created on a plaintext connection, it uses a temporary cookie and no
 *   significant information is expected to be related to that session (because of the lack of TLS).
 *   Subsequently, when transitioning to a TLS connection, a previously existent persistent
 *   connection may suddenly show up with the first request over TLS, and for which session data
 *   (perhaps even persistent session data) may exist. In that case, the session indicated by the
 *   persistent (and TLS) session ID is used, possibly with a few details lifted from the non-TLS
 *   session before it is discarded, and all three cookies are then re-written.
 */
const SessionCookie
        implements Destringable
    {
    enum (String name, Boolean persistent)
 * 1. `xplaintext` - a non-persistent cookie that is subject to hijacking (not protected by TLS)
 *    * this cookie is created when any request arrives without a `xplaintext` cookie
 *    * specifies `Path=/;SameSite=Strict;HttpOnly`; does not specify `Expires` or `Domain`
 *    * contains base64 encoding of encrypted data: session id, cookie id, change counter, creation
 *      date, known consents, known cookies (1/2/3), ip address, random salt
 *
 * 2. `__Host-xtemporary` - a non-persistent cookie protected by TLS
 *    * this cookie is created when a request comes in over a TLS connection and there is no
 *      `__Host-xtemporary` cookie in the request
 *    * specifies `Path=/;SameSite=Strict;secure;HttpOnly`; does not specify `Expires` or `Domain`
 *    * contains base64 encoding of encrypted data: session id, cookie id, change counter, creation
 *      date, known consents, known cookies (1/2/3), ip address, random salt
 *
 * 3. `__Host-xconsented` - a persistent cookie protected by TLS


    @Override
    construct(String text)
        {
        }


    // ----- encryption support --------------------------------------------------------------------

    /**
     * Encrypt the passed readable string into an unreadable, tamper-proof, BASE-64 string
     *
     * @param text  the readable string
     *
     * @return the encrypted string in BASE-64 format
     */
    String encrypt(String text)
        {
        // TODO
        return text;
        }

    /**
     * Decrypt the passed string back into a readable String
     *
     * @param text  the encrypted string in BASE-64 format
     *
     * @return the readable string
     */
    String decrypt(String text)
        {
        // TODO
        return text;
        }

    @Override
    public String toString()
        {
        }
    }