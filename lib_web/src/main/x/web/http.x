import net.Host;

package http {

    /**
     * `HostInfo` represents a combination of an HTTP and HTTPS address. It is used to represent
     * both (i) bindings and (ii) routes. A "binding" refers to the low level socket connection that
     * a server listens on (receives an HTTP request on), while a "route" refers to the high level
     * web site name that a client specified when it sent a request.
     *
     * @param host       the host name or IP address of the HTTP server
     * @param httpPort   the HTTP (plain text) port of the HTTP server; the default is 80
     * @param httpsPort  the HTTPS (TLS) port of the HTTP server; the default is 443
     */
    const HostInfo(Host host, UInt16 httpPort=80, UInt16 httpsPort=443);

    /**
     * Validate the passed string as a file extension. This is based on the HTTP token definition,
     * as illustrated in [validToken], but with a reduced set of non-alpha-numeric characters, based
     * on the characters that ASCII DOS allowed back in the dark ages.
     *
     * @param a String that may be a file extension (but not including the dot)
     *
     * @return True iff the string is, in theory, a valid file extension
     */
    static Boolean validExtension(String s) {
        for (Char ch : s) {
            switch (ch) {
            case 'a'..'z':
            case 'A'..'Z':
            case '0'..'9':
                // this is the intersection of allowed special characters between the HTTP Token
                // specification and the ASCII DOS specification (which file systems often respect)
            case  '!', '#', '$', '%', '&', '-', '^', '_', '`', '~':
                break;

            default:
                return False;
            }
        }

        return s.size >= 1;
    }

    /**
     * Validate the passed string as an HTTP "token".
     *
     *     token = 1*tchar
     *     tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*"
     *                 / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
     *                 / DIGIT / ALPHA
     *                 ; any VCHAR, except delimiters
     *
     * @param a String that may be an HTTP token
     *
     * @return True iff the string is a valid HTTP token
     */
    static Boolean validToken(String s) {
        for (Char ch : s) {
            switch (ch) {
            case 'a'..'z':
            case 'A'..'Z':
            case '0'..'9':
            case  '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~':
                break;

            default:
                return False;
            }
        }

        return s.size >= 1;
    }

    /**
     * TODO
     *
     *     token          = 1*tchar
     *     tchar          = "!" / "#" / "$" / "%" / "&" / "'" / "*"
     *                      / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
     *                      / DIGIT / ALPHA
     *                      ; any VCHAR, except delimiters
     *     quoted-string  = DQUOTE *( qdtext / quoted-pair ) DQUOTE
     *     qdtext         = HTAB / SP /%x21 / %x23-5B / %x5D-7E / obs-text
     *     obs-text       = %x80-FF
     */
    conditional String validTokenOrQuotedString(String s) {
        if (s.startsWith('\"')) {
            return http.validToken(s), s;
        }

        Int length = s.size;
        if (length < 2 || !s.endsWith('\"')) {
            return False;
        }

        TODO check char [1..length-1)
    }

    /**
     * Parse an `IMF-fixdate` String.
     *
     * The format of `IMF-fixdate` is fixed length, and defined by
     * [RFC 7231](https://datatracker.ietf.org/doc/html/rfc7231#section-7.1.1.1).
     *
     * @param text    an `IMF-fixdate` string
     * @param strict  (optional) pass `True` to strictly validate the details of the `IMF-fixdate`;
     *                `False` may skip some strict validations in order to save time
     *
     * @return True iff the `IMF-fixdate` was successfully parsed
     * @return (conditional) the `Time` value corresponding to the `IMF-fixdate`
     */
    static conditional Time parseImfFixDate(String text, Boolean strict=False) {
        //           1         2
        // 01234567890123456789012345678
        // Sun, 06 Nov 1994 08:49:37 GMT
        if (text.size >= 28,
                Byte day0   := text[ 5].asciiDigit(),
                Byte day1   := text[ 6].asciiDigit(),
                Char month0 := text[ 8].asciiUppercase(),
                Char month1 := text[ 9].asciiLowercase(),
                Char month2 := text[10].asciiLowercase(),
                Byte year0  := text[12].asciiDigit(),
                Byte year1  := text[13].asciiDigit(),
                Byte year2  := text[14].asciiDigit(),
                Byte year3  := text[15].asciiDigit(),
                Byte hour0  := text[17].asciiDigit(),
                Byte hour1  := text[18].asciiDigit(),
                Byte min0   := text[20].asciiDigit(),
                Byte min1   := text[21].asciiDigit(),
                Byte sec0   := text[23].asciiDigit(),
                Byte sec1   := text[24].asciiDigit()
                ) {
            Int year  = year0 * 1000 + year1 * 100 + year2 * 10 + year3;
            Int month = switch (month0, month1, month2) {
                case ('J', 'a', 'n'): 1;
                case ('F', 'e', 'b'): 2;
                case ('M', 'a', 'r'): 3;
                case ('A', 'p', 'r'): 4;
                case ('M', 'a', 'y'): 5;
                case ('J', 'u', 'n'): 6;
                case ('J', 'u', 'l'): 7;
                case ('A', 'u', 'g'): 8;
                case ('S', 'e', 'p'): 9;
                case ('O', 'c', 't'): 10;
                case ('N', 'o', 'v'): 11;
                case ('D', 'e', 'c'): 12;
                default: -1;
            };
            Int day   = day0 * 10 + day1;
            if (!Date.isGregorian(year, month, day)) {
                return False;
            }
            Date date = new Date(year, month, day);

            Int hour = hour0 * 10 + hour1;
            Int min  = min0 * 10 + min1;
            Int sec  = sec0 * 10 + sec1;
            if (!TimeOfDay.validate(hour, min, sec)) {
                return False;
            }
            TimeOfDay timeOfDay = new TimeOfDay(hour, min, sec);

            if (strict) {
                String dow = date.dayOfWeek.name;
                if (!(text[ 0] == dow[0] &&
                      text[ 1] == dow[1] &&
                      text[ 2] == dow[2] &&
                      text[ 3] == ',' &&
                      text[ 4] == ' ' &&
                      text[ 7] == ' ' &&
                      text[11] == ' ' &&
                      text[16] == ' ' &&
                      text[19] == ':' &&
                      text[22] == ':' &&
                      text[25] == ' ' &&
                      text[26] == 'G' &&
                      text[27] == 'M' &&
                      text[28] == 'T' &&
                      text.substring(29).chars.all(Char.isWhitespace))) {
                    return False;
                }
            }

            return True, new Time(date, timeOfDay, UTC);
        }

        return False;
    }

    /**
     * Render a [Time] as an "IMF fix date".
     *
     * @param time  the time value
     *
     * @return an IMF fix date string
     */
    static String formatImfFixDate(Time time) {
        // GMT == UTC; it is the only supported timezone
        if (time.timezone != UTC) {
            time = time.with(timezone = UTC);
        }

        Date      date  = time.date;
        String    dow   = date.dayOfWeek.name;
        UInt32    day   = date.day.toUInt32();
        String    moy   = date.monthOfYear.name;
        UInt32    year  = date.year.toUInt32();
        TimeOfDay tod   = time.timeOfDay;
        UInt32    hour  = tod.hour.toUInt32();
        UInt32    min   = tod.minute.toUInt32();
        UInt32    sec   = tod.second.toUInt32();

        Char[] text = new Char[29];
        text[ 0] = dow[0];
        text[ 1] = dow[1];
        text[ 2] = dow[2];
        text[ 3] = ',';
        text[ 4] = ' ';
        text[ 5] = '0' + day / 10;
        text[ 6] = '0' + day % 10;
        text[ 7] = ' ';
        text[ 8] = moy[0];
        text[ 9] = moy[1];
        text[10] = moy[2];
        text[11] = ' ';
        text[12] = '0' + year / 1000 % 10;
        text[13] = '0' + year /  100 % 10;
        text[14] = '0' + year /   10 % 10;
        text[15] = '0' + year        % 10;
        text[16] = ' ';
        text[17] = '0' + hour / 10;
        text[18] = '0' + hour % 10;
        text[19] = ':';
        text[20] = '0' + min / 10;
        text[21] = '0' + min % 10;
        text[22] = ':';
        text[23] = '0' + sec / 10;
        text[24] = '0' + sec % 10;
        text[25] = ' ';
        text[26] = 'G';
        text[27] = 'M';
        text[28] = 'T';

        return new String(text);
    }

    /**
     * FormDataText represents the text contained in the HTTP request generated by the
     * [Javascript FormData](https://javascript.info/formdata) API.
     */
    const FormDataText(String name, String value);

    /**
     * FormDataFile represents the file information contained in the HTTP request generated by the
     * [Javascript FormData](https://javascript.info/formdata) API.
     */
    const FormDataFile(String name, Byte[] contents, String fileName, MediaType? mediaType);

    /**
     * Parse the [Body] of the HTTP request generated by the
     * [Javascript FormData](https://javascript.info/formdata) API.
     */
    (FormDataText|FormDataFile)[] parseFormData(Body body) {
        MediaType mediaType = body.mediaType;
        assert mediaType.type    == MediaType.FormData.type &&
               mediaType.subtype == MediaType.FormData.subtype
                    as $"Invalid media type {mediaType}";

        String contentHeader = mediaType.text;
        assert Int boundaryOffset := contentHeader.indexOf("boundary=");

        String boundary = contentHeader.substring(boundaryOffset + 9);

        // according to RFC 7578: "the parts are delimited with a boundary delimiter, constructed
        // using CRLF, "--", and the value of the "boundary" parameter"
        // however, the first appearance of the boundary doesn't require CRLF prefix

        Byte[] boundaryBytes = ("--" + boundary).utf8();
        Byte[] bytes         = body.bytes;
        Int    boundarySize  = boundaryBytes.size;

        Int[] indexes = new Array<Int>();
        ComputeOffsets:
        for (Int offset = 0; offset := bytes.indexOf(boundaryBytes, offset); ) {
            indexes += offset;
            offset  += boundarySize;

            if (ComputeOffsets.first) {
                boundaryBytes = ("\r\n--" + boundary).utf8();
                boundarySize  = boundaryBytes.size;
            }
        }

        static String HEADER_PREFIX              = "\r\n\r\n";
        static Byte[] HEADER_BOUNDARY            = HEADER_PREFIX.utf8();
        static Int    HEADER_BOUNDARY_SIZE       = HEADER_BOUNDARY.size;
        static String CONTENT_DISPOSITION_PREFIX = "\r\nContent-Disposition: form-data; name=";
        static String FILE_NAME_PREFIX           = "; filename=";
        static String CONTENT_TYPE_PREFIX        = "\r\nContent-Type: ";
        static function Boolean(Char) QUOTES     = ch -> ch == '"' || ch == '\'';

        (FormDataText|FormDataFile)[] parts = new Array();

        ExtractParts:
        for (Int i : 0 ..< indexes.size) {
            Int partStart = indexes[i];
            Int partEnd   = ExtractParts.last ? bytes.size : indexes[i+1];

            Byte[] part = bytes[partStart ..< partEnd];

            if (Int headerEnd := part.indexOf(HEADER_BOUNDARY)) {
                String header = part[0 ..< headerEnd].unpackUtf8();
                headerEnd += HEADER_BOUNDARY_SIZE;

                if (Int nameStart := header.indexOf(CONTENT_DISPOSITION_PREFIX)) {
                    nameStart += CONTENT_DISPOSITION_PREFIX.size;

                    // check for the optional file name
                    String? fileName = Null;
                    Int     nameEnd  = header.size;
                    if (Int fileNameStart := header.indexOf(FILE_NAME_PREFIX),
                        Int fileNameEnd   := header.indexOf("\r\n", nameStart)) {
                        nameEnd = fileNameStart;

                        fileNameStart += FILE_NAME_PREFIX.size;
                        fileName       = header[fileNameStart ..< fileNameEnd].trim(QUOTES);
                    } else {
                        nameEnd := header.indexOf("\r\n", nameStart);
                    }

                    String name = header[nameStart ..< nameEnd].trim(QUOTES);

                    Byte[] contents = part[headerEnd ..< part.size];
                    if (fileName == Null) {
                        parts += new FormDataText(name, contents.unpackUtf8());
                    } else {
                        MediaType? fileType = Null;
                        if (Int typeStart := header.indexOf(CONTENT_TYPE_PREFIX)) {
                            typeStart += CONTENT_TYPE_PREFIX.size;

                            Int typeEnd = header.size;
                            typeEnd    := header.indexOf("\r\n", typeStart);

                            fileType := MediaType.of(header[typeStart ..< typeEnd]);
                        }

                        parts += new FormDataFile(name, contents, fileName, fileType);
                    }
                }
            }
        }
        return parts;
    }

    /**
     * Parse the [Body] of the HTTP request and extract the [FormDataFile] parts.
     */
    FormDataFile[] extractFileData(Body body) {
        FormDataFile[] files = [];
        for (val part : parseFormData(body)) {
            if (part.is(FormDataFile)) {
                files += part;
            }
        }
        return files;
    }
}