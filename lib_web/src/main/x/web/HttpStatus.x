/**
 * A representation of HTTP status codes.
 *
 * @see [RFC 2616 §10](https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html)
 * @see [RFC 7231 §8.2.3](https://datatracker.ietf.org/doc/html/rfc7231#section-8.2.3)
 */
const HttpStatus(Int code, String reason)
    {
    static HttpStatus Continue                         = new HttpStatus(100, "Continue");
    static HttpStatus SwitchingProtocols               = new HttpStatus(101, "Switching Protocols");
    static HttpStatus Processing                       = new HttpStatus(102, "Processing");                // note: WEBDAV
    static HttpStatus EarlyHints                       = new HttpStatus(103, "Early Hints");
    static HttpStatus OK                               = new HttpStatus(200, "Ok");
    static HttpStatus Created                          = new HttpStatus(201, "Created");
    static HttpStatus Accepted                         = new HttpStatus(202, "Accepted");
    static HttpStatus NonAuthoritativeInformation      = new HttpStatus(203, "Non-Authoritative Information");
    static HttpStatus NoContent                        = new HttpStatus(204, "No Content");
    static HttpStatus ResetContent                     = new HttpStatus(205, "Reset Content");
    static HttpStatus PartialContent                   = new HttpStatus(206, "Partial Content");
    static HttpStatus MultiStatus                      = new HttpStatus(207, "Multi Status");              // note: WEBDAV
    static HttpStatus AlreadyImported                  = new HttpStatus(208, "Already imported");          // note: WEBDAV
    static HttpStatus IMUsed                           = new HttpStatus(226, "IM Used");                   // note: HTTP Delta encoding
    static HttpStatus MultipleChoices                  = new HttpStatus(300, "Multiple Choices");
    static HttpStatus MovedPermanently                 = new HttpStatus(301, "Moved Permanently");
    static HttpStatus Found                            = new HttpStatus(302, "Found");
    static HttpStatus SeeOther                         = new HttpStatus(303, "See Other");
    static HttpStatus NotModified                      = new HttpStatus(304, "Not Modified");
    static HttpStatus UseProxy                         = new HttpStatus(305, "Use Proxy");
    static HttpStatus SwitchProxy                      = new HttpStatus(306, "Switch Proxy");
    static HttpStatus TemporaryRedirect                = new HttpStatus(307, "Temporary Redirect");
    static HttpStatus PermanentRedirect                = new HttpStatus(308, "Permanent Redirect");
    static HttpStatus BadRequest                       = new HttpStatus(400, "Bad Request");
    static HttpStatus Unauthorized                     = new HttpStatus(401, "Unauthorized");
    static HttpStatus PaymentRequired                  = new HttpStatus(402, "Payment Required");
    static HttpStatus Forbidden                        = new HttpStatus(403, "Forbidden");
    static HttpStatus NotFound                         = new HttpStatus(404, "Not Found");
    static HttpStatus MethodNotAllowed                 = new HttpStatus(405, "Method Not Allowed");
    static HttpStatus NotAcceptable                    = new HttpStatus(406, "Not Acceptable");
    static HttpStatus ProxyAuthenticationRequired      = new HttpStatus(407, "Proxy Authentication Required");
    static HttpStatus RequestTimeout                   = new HttpStatus(408, "Request Timeout");
    static HttpStatus Conflict                         = new HttpStatus(409, "Conflict");
    static HttpStatus Gone                             = new HttpStatus(410, "Gone");
    static HttpStatus LengthRequired                   = new HttpStatus(411, "Length Required");
    static HttpStatus PreconditionFailed               = new HttpStatus(412, "Precondition Failed");
    static HttpStatus PayloadTooLarge                  = new HttpStatus(413, "Payload Too Large");
    static HttpStatus UriTooLong                       = new HttpStatus(414, "URI Too Long");
    static HttpStatus UnsupportedMediaType             = new HttpStatus(415, "Unsupported Media Type");
    static HttpStatus RequestedRangeNotSatisfiable     = new HttpStatus(416, "Requested Range Not Satisfiable");
    static HttpStatus ExpectationFailed                = new HttpStatus(417, "Expectation Failed");
    static HttpStatus IamATeapot                       = new HttpStatus(418, "I am a teapot");
    static HttpStatus EnhanceYourCalm                  = new HttpStatus(420, "Enhance your calm");
    static HttpStatus MisdirectedRequest               = new HttpStatus(421, "Misdirected Request");
    static HttpStatus UnprocessableEntity              = new HttpStatus(422, "Unprocessable Entity");      // note: WEBDAV
    static HttpStatus Locked                           = new HttpStatus(423, "Locked");                    // note: WEBDAV
    static HttpStatus FailedDependency                 = new HttpStatus(424, "Failed Dependency");         // note: WEBDAV
    static HttpStatus UnorderedCollection              = new HttpStatus(425, "Unordered Collection");
    static HttpStatus UpgradeRequired                  = new HttpStatus(426, "Upgrade Required");
    static HttpStatus PreconditionRequired             = new HttpStatus(428, "Precondition Required");
    static HttpStatus TooManyRequests                  = new HttpStatus(429, "Too Many Requests");
    static HttpStatus RequestHeaderFieldsTooLarge      = new HttpStatus(431, "Request Header Fields Too Large");
    static HttpStatus NoResponse                       = new HttpStatus(444, "No Response");
    static HttpStatus BlockedByWindowsParentalControls = new HttpStatus(450, "Blocked by Windows Parental Controls");
    static HttpStatus UnavailableForLegalReasons       = new HttpStatus(451, "Unavailable For Legal Reasons");
    static HttpStatus RequestHeaderTooLarge            = new HttpStatus(494, "Request Header Too Large");
    static HttpStatus InternalServerError              = new HttpStatus(500, "Internal Server Error");
    static HttpStatus NotImplemented                   = new HttpStatus(501, "Not Implemented");
    static HttpStatus BadGateway                       = new HttpStatus(502, "Bad Gateway");
    static HttpStatus ServiceUnavailable               = new HttpStatus(503, "Service Unavailable");
    static HttpStatus GatewayTimeout                   = new HttpStatus(504, "Gateway Timeout");
    static HttpStatus HttpVersionNotSupported          = new HttpStatus(505, "HTTP Version Not Supported");
    static HttpStatus VariantAlsoNegotiates            = new HttpStatus(506, "Variant Also Negotiates");
    static HttpStatus InsufficientStorage              = new HttpStatus(507, "Insufficient Storage");      // note: WEBDAV
    static HttpStatus LoopDetected                     = new HttpStatus(508, "Loop Detected");             // note: WEBDAV
    static HttpStatus BandwidthLimitExceeded           = new HttpStatus(509, "Bandwidth Limit Exceeded");
    static HttpStatus NotExtended                      = new HttpStatus(510, "Not Extended");
    static HttpStatus NetworkAuthenticationRequired    = new HttpStatus(511, "Network Authentication Required");
    static HttpStatus ConnectionTimedOut               = new HttpStatus(522, "Connection Timed Out");

    /**
     * Obtain an HttpStatus by its status code.
     *
     * @param status  the status code
     *
     * @return the corresponding HttpStatus object
     */
    static HttpStatus of(Int status)
        {
        return switch (status)
            {
            case 100: Continue;
            case 101: SwitchingProtocols;
            case 102: Processing;
            case 103: EarlyHints;
            case 200: OK;
            case 201: Created;
            case 202: Accepted;
            case 203: NonAuthoritativeInformation;
            case 204: NoContent;
            case 205: ResetContent;
            case 206: PartialContent;
            case 207: MultiStatus;
            case 208: AlreadyImported;
            case 226: IMUsed;
            case 300: MultipleChoices;
            case 301: MovedPermanently;
            case 302: Found;
            case 303: SeeOther;
            case 304: NotModified;
            case 305: UseProxy;
            case 306: SwitchProxy;
            case 307: TemporaryRedirect;
            case 308: PermanentRedirect;
            case 400: BadRequest;
            case 401: Unauthorized;
            case 402: PaymentRequired;
            case 403: Forbidden;
            case 404: NotFound;
            case 405: MethodNotAllowed;
            case 406: NotAcceptable;
            case 407: ProxyAuthenticationRequired;
            case 408: RequestTimeout;
            case 409: Conflict;
            case 410: Gone;
            case 411: LengthRequired;
            case 412: PreconditionFailed;
            case 413: PayloadTooLarge;
            case 414: UriTooLong;
            case 415: UnsupportedMediaType;
            case 416: RequestedRangeNotSatisfiable;
            case 417: ExpectationFailed;
            case 418: IamATeapot;
            case 420: EnhanceYourCalm;
            case 421: MisdirectedRequest;
            case 422: UnprocessableEntity;
            case 423: Locked;
            case 424: FailedDependency;
            case 425: UnorderedCollection;
            case 426: UpgradeRequired;
            case 428: PreconditionRequired;
            case 429: TooManyRequests;
            case 431: RequestHeaderFieldsTooLarge;
            case 444: NoResponse;
            case 450: BlockedByWindowsParentalControls;
            case 451: UnavailableForLegalReasons;
            case 494: RequestHeaderTooLarge;
            case 500: InternalServerError;
            case 501: NotImplemented;
            case 502: BadGateway;
            case 503: ServiceUnavailable;
            case 504: GatewayTimeout;
            case 505: HttpVersionNotSupported;
            case 506: VariantAlsoNegotiates;
            case 507: InsufficientStorage;
            case 508: LoopDetected;
            case 509: BandwidthLimitExceeded;
            case 510: NotExtended;
            case 511: NetworkAuthenticationRequired;
            case 522: ConnectionTimedOut;
            default: assert as $"Unknown status: {status}";
            };
        }
    }