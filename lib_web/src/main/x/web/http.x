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
    const HostInfo(Host host, UInt16 httpPort = 80, UInt16 httpsPort = 443)
            implements Destringable {

        HostInfo with(Host?   host      = Null,
                      UInt16? httpPort  = Null,
                      UInt16? httpsPort = Null) {
            return new HostInfo(host      ?: this.host,
                                httpPort  ?: this.httpPort,
                                httpsPort ?: this.httpsPort);
        }

        construct(String text) {
            assert (Host host, UInt16 httpPort, UInt16 httpsPort) := parse(text, s -> throw new IllegalArgument(s));
            construct HostInfo(host, httpPort, httpsPort);
        }

        /**
         * Parse the provided HostInfo text into its constituent pieces. The format is a host name
         * or an IP address (either v4 or v6, with the v6 form inside of required square brackets),
         * followed by an optional port number pair, which (if present) is separated from the host
         * using a colon ':', with the two port numbers separated by a forward slash '/' character.
         *
         * Examples:
         * * `0.0.0.0`
         * * `localhost:80/443`
         *
         * @param text    the host name or address, with an optional pair of port numbers
         * @param report  (optional) the function to report a failure to, as a non-localized string
         *
         * @return `True` iff the parsing succeeded
         * @return (conditional) the host name or IP address
         * @return (conditional) the HTTP port, which defaults to 80
         * @return (conditional) the HTTPS port, which defaults to 443
         */
        static conditional (Host host, UInt16 httpPort, UInt16 httpsPort) parse(
                String text, function void (String)? report = Null) {
            text = text.trim();
            if (text.empty) {
                report?($"Invalid HostInfo {text.quoted()}: No host information");
                return False;
            }

            Host   host      = text;
            String ports     = "";
            UInt16 httpPort  = 80;
            UInt16 httpsPort = 443;

            // IPv6 format requires brackets
            if (text[0] == '[') {
                if (Int close := text.lastIndexOf(']')) {
                    String ipText = text[0>..<close];
                    if (Byte[] bytes := IPAddress.parseIPv6(ipText, report)) {
                        host = new IPAddress(bytes);
                        if (close == text.size-1) {
                            return True, host, httpPort, httpsPort;
                        } else if (text[close+1] == ':') {
                            ports = text.substring(close + 2);
                        } else {
                            report?($"Invalid HostInfo {text.quoted()}: Expected ':' after ']' at close of IPv6 address");
                            return False;
                        }
                    } else {
                        report?($"Invalid HostInfo {text.quoted()}: Invalid IPv6 address {ipText.quoted()}");
                        return False;
                    }
                } else {
                    report?($"Invalid HostInfo {text.quoted()}: The IPv6 address requires a closing ']'");
                    return False;
                }
            } else {
                // separate authority info from port info, if any
                String authority = text;
                if (Int first := text.indexOf(':'), Int last := text.lastIndexOf(':')) {
                    if (first == last) {
                        authority  = text[0..<last];
                        ports = text.substring(last+1);
                    } else {
                        // IPv6 without the brackets
                        report?($"Invalid HostInfo {text.quoted()}: IPv6 addresses must be enclosed in brackets");
                        return False;
                    }
                }

                // parse the host name or address
                if ((String? user, String? hostStr, IPAddress? ip, UInt16? port) := Uri.parseAuthority(authority, report)) {
                    if (user != Null) {
                        report?($"Invalid HostInfo {text.quoted()}: A user identity is not permitted");
                        return False;
                    } else if (port != Null) {
                        // this cannot happen (so it probably will)
                        report?($"Invalid HostInfo {text.quoted()}: A port parsing error occurred");
                        return False;
                    } else if (ip != Null) {
                        host = ip;
                    } else if (hostStr != Null) {
                        host = hostStr;
                    } else {
                        // this cannot happen (so it probably will)
                        report?($"Invalid HostInfo {text.quoted()}: The host string contain neither a host name nor an IP address");
                        return False;
                    }
                } else {
                    return False;
                }
            }

            if (!ports.empty) {
                if (Int slash := ports.indexOf('/')) {
                    if (httpPort  := UInt16.parse(ports[0 ..< slash]),
                        httpsPort := UInt16.parse(ports.substring(slash+1))) {
                    } else {
                        report?($"Invalid HostInfo {text.quoted()}: Invalid port number(s): {ports.quoted()}");
                        return False;
                    }
                } else {
                    report?($"Invalid HostInfo {text.quoted()}: Ports must be separated by a '/' character");
                    return False;
                }
            }
            return True, host, httpPort, httpsPort;
        }

        @Override
        String toString() {
            return $"{host}:{httpPort}/{httpsPort}";
        }
    }

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
    static conditional Time parseImfFixDate(String text, Boolean strict = False) {
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
     *
     * Note: contents size could be zero when the form data is piped
     */
    const FormDataFile(String name, Byte[] contents, String fileName, MediaType? mediaType);

    /**
     * Parse the [Body] of the HTTP request and extract the [FormDataFile] parts.
     */
    FormDataFile[] extractFileData(Body body) {
        MediaType mediaType = body.mediaType;
        if (mediaType.type    == MediaType.FormData.type &&
            mediaType.subtype == MediaType.FormData.subtype) {

            FormDataFile[] files = new FormDataFile[];
            for (val part : parseFormData(body)) {
                if (part.is(FormDataFile)) {
                    files += part;
                }
            }
            return files;
        }

        if (String disposition := body.header.firstOf(Header.ContentDisposition),
            Int    nameOffset  := disposition.indexOf("filename=") ) {

            String fileName = disposition.substring(nameOffset+9).trim();
            return [new FormDataFile(fileName, body.bytes, fileName, mediaType)];
        }
        return [];
    }

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
                    nameStart += DISPOSITION_PREFIX_SIZE;

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

                    String name     = header[nameStart ..< nameEnd].trim(QUOTES);
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
     * Stream the content of the multi-part body. This method is a stream-specialized copy of
     * the [parseFormData()] method above.

     * @return the number of [FormDataText] segments that have been encountered, each representing
     *         a name and value
     * @return the number of bytes piped for [FormDataFile] segments
     *
     * @see the [parseFormData()] method for additional explanations
     */
    (Int pipedValues, Int pipedBytes) pipeFormData(
            BinaryInput                          reader,
            String                               contentHeader,
            function void (String, String)       pipeValue,
            function void (FormDataFile, Byte[]) pipeFile,
            ) {
        assert Int boundaryOffset := contentHeader.indexOf("boundary=");

        String boundary = contentHeader.substring(boundaryOffset + 9);

        Byte[] boundaryBytes = ("--" + boundary).utf8();
        Int    boundarySize  = boundaryBytes.size;
        Int    pipedValues   = 0;
        Int    pipedBytes    = 0;

        String?       fragmentName = Null;
        FormDataFile? formFile     = Null;

        static Byte[] readChunk(BinaryInput reader) {
            return reader.readBytes(reader.available.notLessThan(1Kib));
        }

        ProcessAll: while (True) {
            Byte[] chunk     = readChunk(reader);
            Int    chunkSize = chunk.size;
            if (chunkSize == 0) {
                break;
            }

            Int prevOffset = 0;
            ProcessChunk: while (True) {
                if (Int offset := chunk.indexOf(boundaryBytes, prevOffset)) {
                    // we found the boundary
                    if (fragmentName == Null) {
                        // this is a very first boundary; get the content disposition
                        if (!chunk.indexOf(HEADER_BOUNDARY, offset + boundarySize)) {
                            chunk    += readChunk(reader);
                            chunkSize = chunk.size;
                        }
                        if ((fragmentName, formFile, prevOffset) :=
                               parseContentDisposition(chunk, offset + boundarySize, reader)) {
                            boundaryBytes = ("\r\n--" + boundary).utf8();
                            boundarySize  = boundaryBytes.size;
                            continue;
                        } else {
                            throw new IllegalState("Corrupt stream");
                        }
                    } else {
                        // not the first boundary; use the prevOffset to pipe
                        Byte[] contents = chunk[prevOffset..<offset];
                        if (formFile == Null) {
                            pipeValue(fragmentName, contents.unpackUtf8());
                            pipedValues++;
                        } else {
                            pipeFile(formFile, contents);
                        }

                        // get the next content disposition
                        if (!chunk.indexOf(HEADER_BOUNDARY, offset + boundarySize)) {
                            chunk    += readChunk(reader);
                            chunkSize = chunk.size;
                        }
                        if ((fragmentName, formFile, prevOffset) :=
                                    parseContentDisposition(chunk, offset + boundarySize, reader)) {
                            continue;
                        } else {
                            // no data
                            break ProcessAll;
                        }
                    }
                } else {
                    // look for a possible fragment at the tail
                    Int tailOffset = prevOffset + boundarySize < chunkSize
                            ? chunkSize - boundarySize + 1
                            : prevOffset;

                    while (Int offset := chunk.indexOf(boundaryBytes[0], tailOffset)) {
                        // the tail is less than the boundary; see if it matches
                        Int tailSize = chunkSize - offset;

                        if (chunk[offset..<chunkSize] == boundaryBytes[0..<tailSize]) {
                            // the most unfortunate scenario: potential boundary split
                            Byte[] nextChunk = readChunk(reader);
                            if (nextChunk.size == 0) {
                                // no more data; just pipe the remainder of the chunk
                                if (formFile != Null) {
                                    pipeFile(formFile, chunk[prevOffset..<chunkSize]);
                                    pipedBytes += chunkSize - prevOffset;
                                }
                                break ProcessAll;
                            } else {
                                // combine the chunks and repeat from the top
                                chunk    += nextChunk;
                                chunkSize = chunk.size;
                                continue ProcessChunk;
                            }
                        } else {
                            tailOffset = offset + 1;
                        }
                    }
                }

                // the tail doesn't have the boundary; pipe it
                if (formFile != Null) {
                    pipeFile(formFile, prevOffset == 0 ? chunk : chunk[prevOffset..<chunkSize]);
                    pipedBytes += chunkSize - prevOffset;
                }
                break; // go for the next chunk
            }
        }

        return pipedValues, pipedBytes;
    }

    /**
     * Parse the content of a single form-data part.
     */
    conditional (String fragmentName, FormDataFile? formFile, Int offset)
            parseContentDisposition(Byte[] chunk, Int offset, BinaryInput reader) {

        if (Int headerEnd := chunk.indexOf(HEADER_BOUNDARY, offset)) {
            String header = chunk[offset ..< headerEnd].unpackUtf8();
            headerEnd += HEADER_BOUNDARY_SIZE;

            if (Int nameStart := header.indexOf(CONTENT_DISPOSITION_PREFIX)) {
                nameStart += DISPOSITION_PREFIX_SIZE;

                String? fileName = Null;
                Int     nameEnd  = header.size;
                if (Int fileNameStart := header.indexOf(FILE_NAME_PREFIX),
                    Int fileNameEnd   := header.indexOf("\r\n", nameStart)) {

                    nameEnd        = fileNameStart;
                    fileNameStart += FILE_NAME_PREFIX.size;
                    fileName       = header[fileNameStart ..< fileNameEnd].trim(QUOTES);
                } else {
                    nameEnd := header.indexOf("\r\n", nameStart);
                }

                String        name     = header[nameStart ..< nameEnd].trim(QUOTES);
                FormDataFile? formFile = Null;
                if (fileName != Null) {
                    MediaType? fileType = Null;
                    if (Int typeStart := header.indexOf(CONTENT_TYPE_PREFIX)) {
                        typeStart += CONTENT_TYPE_PREFIX.size;

                        Int typeEnd = header.size;
                        typeEnd    := header.indexOf("\r\n", typeStart);

                        fileType := MediaType.of(header[typeStart ..< typeEnd]);
                    }
                    formFile = new FormDataFile(name, [], fileName, fileType);
                }
                return True, name, formFile, headerEnd;
            } else {
                throw new IllegalState("Content disposition is missing");
            }
        } else {
            return False;
        }
    }

    private static String HEADER_PREFIX              = "\r\n\r\n";
    private static Byte[] HEADER_BOUNDARY            = HEADER_PREFIX.utf8();
    private static Int    HEADER_BOUNDARY_SIZE       = HEADER_BOUNDARY.size;
    private static String CONTENT_DISPOSITION_PREFIX = "\r\nContent-Disposition: form-data; name=";
    private static Int    DISPOSITION_PREFIX_SIZE    = CONTENT_DISPOSITION_PREFIX.size;
    private static String FILE_NAME_PREFIX           = "; filename=";
    private static String CONTENT_TYPE_PREFIX        = "\r\nContent-Type: ";
    private static function Boolean(Char) QUOTES     = ch -> ch == '"' || ch == '\'';
}