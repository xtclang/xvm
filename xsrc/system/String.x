const String
        implements Sequence<Char>
    {
    private construct(Char[] chars)
        {
        this.chars = chars;
        }

    private Char[] chars;

    conditional Int indexOf(String value, Range<Int>? range = null)
        {
        TODO - native
        }

    conditional Int lastIndexOf(Char separator)
        {
        TODO - native
        }

    String substring(Int position)
        {
        TODO
        }

    String substring(Interval<Int> range)
        {
        TODO
        }

    Int count(Char char)
        {
        Int count = 0;
        for (Char ch : chars)
            {
            if (ch == char)
                {
                count++;
                }
            }
        return count;
        }

    Char get(Int index)
        {
        return chars[index];
        }

    String[] split(Char separator)
        {
        TODO -- native
        }

    String[] split(String separator)
        {
        TODO -- native
        }

    @Override
    Iterator<Char> iterator()
        {
        TODO -- native
        }

    @Override
    String! reify()
        {
        if (this instanceof StringSub)
            {

            }

        return this;
        }

    StringAscii to<StringAscii>()
        {
        TODO -- native
        }

    StringUtf8 to<StringUtf8>()
        {
        TODO -- native
        }
    StringUtf16 to<StringUtf16>()
        {
        TODO -- native
        }
    StringUtf32 to<StringUtf32>()
        {
        TODO -- native
        }

    @Override
    String to<String>()
        {
        return this;
        }

    // TODO Sequence<Char>

    @Override
    Int size.get()
        {
        return chars.size;
        }

    Char[] to<Char[]>()
        {
        return chars;
        }

    @Op("*") String! dup(Int n)
        {
        TODO
        }

    @Op("+") String append(Object o)
        {
        TODO
        }

    //
    const StringAscii(Byte[] bytes)
        {
        construct(Byte[] bytes)
            {
            for (Byte b : bytes)
                {
                assert:always b <= 0x7F;
                }

            this.bytes = bytes.reify();
            }

        construct(Sequence<Char> seq)
            {
            Byte[] bytes  = new Byte[seq.size];
            Int    offset = 0;
            for (Char ch : seq)
                {
                Int n = ch.codepoint;
                assert:always n >= 0 && n <= 0x7F;
                bytes[offset++] = n.to<Byte>();
                }

            this.bytes = bytes;
            }

        Char get(Int index)
            {
            return bytes[index].to<Char>();
            }
        }

    const StringUtf8(Byte[] bytes)
        {
        // TODO
        }

    const StringUtf16(UInt16[] int16s)
        {
        // TODO
        }

    const StringUtf32(UInt32[] int32s)
        {
        // TODO
        }

    const StringSub
        {
        private String source;
        private Int offset;
        private Int length;

        private construct(String source, Int offset, Int length)
            {
            assert:always offset >= 0;
            assert:always length >= 0;
            assert:always offset + length <= source.length;

            this.source = source;
            this.offset = offset;
            this.length = length;
            }
        }
    }
