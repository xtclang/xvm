import libnet.Uri;

import libweb.Body;
import libweb.Client;
import libweb.Header;
import libweb.Header.Entry;
import libweb.HttpStatus;
import libweb.MediaType;
import libweb.RequestOut;
import libweb.ResponseIn;

import libweb.codecs.Codec;
import libweb.codecs.Registry;

/**
 * The native Client implementation.
 */
service RTClient
        implements Client
    {
    @Override
    @Lazy Registry registry.calc()
        {
        return new Registry();
        }

    @Override
    @Lazy Header defaultHeaders.calc()
        {
        (String[] defaultHeaderNames, String[] defaultHeaderValues) = getDefaultHeaders();

        Header header = new ResponseHeader(defaultHeaderNames, defaultHeaderValues);
        return &header.maskAs(Header);
        }

    @Override
    ResponseIn send(RequestOut request)
        {
        List<Entry> entries = request.header.entries;

        Int      headerCount  = entries.size;
        String[] headerNames  = new Array(headerCount);
        String[] headerValues = new Array(headerCount);
        for (Int i : 0 ..< headerCount)
            {
            Entry entry = entries[i];

            headerNames  += entry[0];
            headerValues += entry[1];
            }

        String  method    = request.method.name;
        Uri     uri       = request.uri;
        Byte[]  bytes     = request.body?.bytes : [];

        // TODO: allow these to be configurable
        Boolean autoRedirect = True;
        Int     retryCount   = 7;

        while (True)
            {
            (Int      statusCode,
             String[] responseHeaderNames,
             String[] responseHeaderValues,
             Byte[]   responseBytes) = sendRequest(method, uri.toString(), headerNames, headerValues, bytes);

            if (autoRedirect && 300 <= statusCode < 400 && retryCount-- > 0,
                    Int index := responseHeaderNames.indexOf("Location"))
                {
                Uri redirect = new Uri(responseHeaderValues[index]);
                uri = uri.apply(redirect);
                continue;
                }

            HttpStatus status         = HttpStatus.of(statusCode);
            Header     responseHeader = new ResponseHeader(responseHeaderNames, responseHeaderValues);
            ResponseIn response       = new Response(status, &responseHeader.maskAs(Header), responseBytes);
            return &response.maskAs(ResponseIn);
            }
        }

    @Override
    String toString()
        {
        return "Client";
        }


    // ----- native helpers ------------------------------------------------------------------------

    (String[] defaultHeaderNames, String[] defaultHeaderValues) getDefaultHeaders()
        {
        TODO("native");
        }

    (Int statusCode, String[] responseHeaderNames, String[] responseHeaderValues, Byte[] responseBytes)
            sendRequest(String method, String uri,
                        String[] headerNames, String[] headerValues, Byte[] bytes)
        {
        TODO("native");
        }


    // ----- helper classes ------------------------------------------------------------------------

    const ResponseHeader
            implements Header
        {
        construct(String[] headerNames, String[] headerValues)
            {
            Int headerCount = headerNames.size;

            assert headerValues.size == headerCount;

            this.entries = new Entry[headerCount](i -> (headerNames[i], headerValues[i])).freeze(True);
            }

        @Override
        @RO Boolean isRequest.get()
            {
            return False;
            }

        @Override
        List<Entry> entries;

        @Override
        immutable ResponseHeader freeze(Boolean inPlace = False)
            {
            return this;
            }

        @Override
        Int estimateStringLength()
            {
            Int length = 0;
            for (Int i : 0 ..< entries.size)
                {
                Entry entry = entries[i];
                length += entry[0].size + entry[1].size + 2;
                }
            return length;
            }

        @Override
        Appender<Char> appendTo(Appender<Char> buf)
            {
            for (Int i : 0 ..< entries.size)
                {
                Entry entry = entries[i];

                entry[0].appendTo(buf).add('=');
                entry[1].appendTo(buf).add('\n');
                }
            return buf;
            }
        }

    class Response(HttpStatus status, Header header, Byte[] bytes)
            implements ResponseIn
            implements Body
        {
        // ----- ResponseIn interface --------------------------------------------------------------

        @Override
        <Result> conditional Result to(Type<Result> type)
            {
            if (status == OK)
                {
                Registry registry = this.RTClient.registry;

                if (Codec<Result> codec := registry.findCodec(mediaType, Result))
                    {
                    try
                        {
                        Result result = codec.decode(bytes);
                        return True, result;
                        }
                    catch (Exception ignore) {}
                    }
                }
            return False;
            }

        @Override
        @RO Body body.get()
            {
            return this;
            }

        @Override
        Body ensureBody(MediaType mediaType, Boolean streaming=False)
            {
            throw new ReadOnly();
            }

        // ----- Body interface --------------------------------------------------------------------

        @Override
        MediaType mediaType.get()
            {
            if (String    mediaTypeName := header.firstOf("Content-Type"),
                MediaType mediaType     := MediaType.of(mediaTypeName))
                {
                return mediaType;
                }
            throw new IllegalState("MediaType is not supplied");
            }

        @Override
        Body from(Object content)
            {
            throw new UnsupportedOperation();
            }

        @Override
        String toString()
            {
            return status.toString();
            }
        }
    }