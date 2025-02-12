package com.datadobi.charset;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

public class UTF_16LE_FT extends Charset
{
    public static final UTF_16LE_FT INSTANCE = new UTF_16LE_FT();

    public UTF_16LE_FT()
    {
        super("UTF-16LE-FT", new String[0]);
    }

    @Override
    public boolean contains(Charset cs)
    {
        return cs instanceof UTF_16LE_FT;
    }

    @Override
    public CharsetDecoder newDecoder()
    {
        return new Decoder(this);
    }

    @Override
    public CharsetEncoder newEncoder()
    {
        return new Encoder(this);
    }

    private static class Decoder extends CharsetDecoder
    {
        public Decoder(Charset cs)
        {
            super(cs, 0.5f, 1.0f);
        }


        private char decodeChar(ByteBuffer src)
        {
            int b1 = src.get() & 0xff;
            int b2 = src.get() & 0xff;
            return (char) ((b2 << 8) | b1);
        }

        @Override
        protected CoderResult decodeLoop(ByteBuffer src, CharBuffer dst)
        {
            int srcPos = src.position();

            try {
                while (src.remaining() > 1) {
                    char c = decodeChar(src);

                    // Non surrogate character or unpaired low surrogate -> Decode as is
                    // An unpaired low surrogate is not valid UTF-16, but we intentionally
                    // retain what's in the byte stream
                    if (!dst.hasRemaining()) {
                        return CoderResult.OVERFLOW;
                    }
                    srcPos += 2;
                    dst.put(c);
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(srcPos);
            }
        }

        @Override
        protected void implReset()
        {
        }
    }

    private static class Encoder extends CharsetEncoder
    {
        protected Encoder(Charset cs)
        {
            super(cs, 2.0f, 2.0f, (new byte[] {(byte) 0xfd, (byte) 0xff}));
        }

        private void encodeChar(char c, ByteBuffer dst)
        {
            dst.put((byte) (c & 0xff));
            dst.put((byte) (c >> 8));
        }

        @Override
        protected CoderResult encodeLoop(CharBuffer src, ByteBuffer dst)
        {
            int srcPos = src.position();

            try {
                while (src.hasRemaining()) {
                    char c = src.get();
                    if (dst.remaining() < 2) {
                        return CoderResult.OVERFLOW;
                    }
                    srcPos++;
                    encodeChar(c, dst);
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(srcPos);
            }
        }

        @Override
        protected void implReset()
        {
        }

        @Override
        public boolean canEncode(char c)
        {
            return true;
        }
    }
}
