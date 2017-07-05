const String
        implements Sequence<Char>
    {
    private construct String() {}

    conditional Int indexOf(ElementType value, Range<Int>? range = null)
        {
        return super(value, range);
        }

    Char get(Int index);

    Iterator<Char> iterator()
        {
        return new TODO
        }

    String reify()
        {
        if (this instanceof StringSub)
            {

            }

        return this;
        }

    StringAscii to<StringAscii>()
        {

        }

    StringUtf8 to<StringUtf8>()
        {
        }
    StringUtf16 to<StringUtf16>()
        {
        }
    StringUtf32 to<StringUtf32>()
        {
        }

    // TODO Sequence<Char>

    Int length.get()
        {
        return chars.length;
        }

    Char[] to<Char[]>()
        {
        return chars;
        }

    //
    const StringAscii(Byte[] bytes)
        {
        Char get(Int index)
            {
            return bytes[index].to<Char>();
            }

        construct StringAscii(Byte[] bytes)
            {
            for (Byte b : bytes)
                {
                assert:always b <= 0x7F;
                }

            this.bytes = bytes.reify();
            }

        construct StringAscii(Sequence<Char> seq)
            {
            Byte[] bytes  = new Byte[seq.length];
            Int    offset = 0;
            for (Char ch : seq)
                {
                Int n = ch.codepoint;
                assert:always n >= 0 && n <= 0x7F;
                bytes[offset++] = n.to<Byte>();
                }

            this.bytes = bytes;
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

        private construct StringSub(String source, Int offset, Int length)
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
