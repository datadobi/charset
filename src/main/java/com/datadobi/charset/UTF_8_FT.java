/*
 * Copyright (c) 2000, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.datadobi.charset;

import javax.annotation.Nullable;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

public final class UTF_8_FT extends Charset
{
    public static final UTF_8_FT INSTANCE = new UTF_8_FT();

    public UTF_8_FT()
    {
        super("UTF-8-FT", new String[0]);
    }

    @Override
    public boolean contains(Charset cs)
    {
        return cs instanceof UTF_8_FT;
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

    static void updatePositions(Buffer src, int sp,
                                Buffer dst, int dp)
    {
        src.position(sp);
        dst.position(dp);
    }

    static void updatePositionsArray(Buffer src, int sp,
                                     Buffer dst, int dp)
    {
        src.position(sp);
        dst.position(dp);
    }

    private static class Decoder extends CharsetDecoder
    {

        private Decoder(Charset cs)
        {
            super(cs, 1.0f, 1.0f);
        }

        private static boolean isNotContinuation(int b)
        {
            return (b & 0xc0) != 0x80;
        }

        //  [E0]     [A0..BF] [80..BF]
        //  [E1..EF] [80..BF] [80..BF]
        private static boolean isMalformed3(int b1, int b2, int b3)
        {
            return (b1 == (byte) 0xe0 && (b2 & 0xe0) == 0x80) ||
                    (b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80;
        }

        // only used when there is only one byte left in src buffer
        private static boolean isMalformed3_2(int b1, int b2)
        {
            return (b1 == (byte) 0xe0 && (b2 & 0xe0) == 0x80) ||
                    (b2 & 0xc0) != 0x80;
        }

        //  [F0]     [90..BF] [80..BF] [80..BF]
        //  [F1..F3] [80..BF] [80..BF] [80..BF]
        //  [F4]     [80..8F] [80..BF] [80..BF]
        //  only check 80-be range here, the [0xf0,0x80...] and [0xf4,0x90-...]
        //  will be checked by Character.isSupplementaryCodePoint(uc)
        private static boolean isMalformed4(int b2, int b3, int b4)
        {
            return (b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80 ||
                    (b4 & 0xc0) != 0x80;
        }

        // only used when there is less than 4 bytes left in src buffer.
        // both b1 and b2 should be "& 0xff" before passed in.
        private static boolean isMalformed4_2(int b1, int b2)
        {
            return (b1 == 0xf0 && (b2 < 0x90 || b2 > 0xbf)) ||
                    (b1 == 0xf4 && (b2 & 0xf0) != 0x80) ||
                    (b2 & 0xc0) != 0x80;
        }

        // tests if b1 and b2 are malformed as the first 2 bytes of a
        // legal`4-byte utf-8 byte sequence.
        // only used when there is less than 4 bytes left in src buffer,
        // after isMalformed4_2 has been invoked.
        private static boolean isMalformed4_3(int b3)
        {
            return (b3 & 0xc0) != 0x80;
        }

        @Nullable
        private static CoderResult malformedN(ByteBuffer src, int nb)
        {
            switch (nb) {
                case 1:
                case 2:                    // always 1
                    return CoderResult.malformedForLength(1);
                case 3: {
                    int b1 = src.get();
                    int b2 = src.get();    // no need to lookup b3
                    return CoderResult.malformedForLength(
                            ((b1 == (byte) 0xe0 && (b2 & 0xe0) == 0x80) ||
                                    isNotContinuation(b2)) ? 1 : 2);
                }
                case 4: {
                    // we don't care the speed here
                    int b1 = src.get() & 0xff;
                    int b2 = src.get() & 0xff;
                    if (b1 > 0xf4 ||
                            (b1 == 0xf0 && (b2 < 0x90 || b2 > 0xbf)) ||
                            (b1 == 0xf4 && (b2 & 0xf0) != 0x80) ||
                            isNotContinuation(b2))
                        return CoderResult.malformedForLength(1);
                    if (isNotContinuation(src.get()))
                        return CoderResult.malformedForLength(2);
                    return CoderResult.malformedForLength(3);
                }
                default:
                    assert false;
                    return null;
            }
        }

        @Nullable
        private static CoderResult malformedArray(ByteBuffer src, int sp,
                                                  CharBuffer dst, int dp,
                                                  int nb)
        {
            src.position(sp);
            CoderResult cr = malformedN(src, nb);
            updatePositionsArray(src, sp, dst, dp);
            return cr;
        }

        @Nullable
        private static CoderResult malformed(ByteBuffer src,
                                             int mark, int nb)
        {
            src.position(mark);
            CoderResult cr = malformedN(src, nb);
            src.position(mark);
            return cr;
        }

        private static CoderResult malformedForLength(ByteBuffer src,
                                                      int sp,
                                                      CharBuffer dst,
                                                      int dp,
                                                      int malformedNB)
        {
            updatePositions(src, sp, dst, dp);
            return CoderResult.malformedForLength(malformedNB);
        }

        private static CoderResult malformedForLength(ByteBuffer src,
                                                      int mark,
                                                      int malformedNB)
        {
            src.position(mark);
            return CoderResult.malformedForLength(malformedNB);
        }


        private static CoderResult xflow(Buffer src, int sp, int sl,
                                         Buffer dst, int dp, int nb)
        {
            updatePositions(src, sp, dst, dp);
            return (nb == 0 || sl - sp < nb)
                    ? CoderResult.UNDERFLOW : CoderResult.OVERFLOW;
        }

        private static CoderResult xflow(Buffer src, int mark, int nb)
        {
            src.position(mark);
            return (nb == 0 || src.remaining() < nb)
                    ? CoderResult.UNDERFLOW : CoderResult.OVERFLOW;
        }

        @Nullable
        private CoderResult decodeArrayLoop(ByteBuffer src,
                                            CharBuffer dst)
        {
            // This method is optimized for ASCII input.
            byte[] sa = src.array();
            int sp = src.arrayOffset() + src.position();
            int sl = src.arrayOffset() + src.limit();

            char[] da = dst.array();
            int dp = dst.arrayOffset() + dst.position();
            int dl = dst.arrayOffset() + dst.limit();
            int dlASCII = dp + Math.min(sl - sp, dl - dp);

            // ASCII only loop
            while (dp < dlASCII && sa[sp] >= 0)
                da[dp++] = (char) sa[sp++];
            while (sp < sl) {
                int b1 = sa[sp];
                if (b1 >= 0) {
                    // 1 byte, 7 bits: 0xxxxxxx
                    if (dp >= dl)
                        return xflow(src, sp, sl, dst, dp, 1);
                    da[dp++] = (char) b1;
                    sp++;
                } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                    // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
                    //                   [C2..DF] [80..BF]
                    if (sl - sp < 2 || dp >= dl)
                        return xflow(src, sp, sl, dst, dp, 2);
                    int b2 = sa[sp + 1];
                    // Now we check the first byte of 2-byte sequence as
                    //     if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0)
                    // no longer need to check b1 against c1 & c0 for
                    // malformed as we did in previous version
                    //   (b1 & 0x1e) == 0x0 || (b2 & 0xc0) != 0x80;
                    // only need to check the second byte b2.
                    if (isNotContinuation(b2))
                        return malformedForLength(src, sp, dst, dp, 1);
                    da[dp++] = (char) (((b1 << 6) ^ b2)
                            ^
                            (((byte) 0xC0 << 6) ^
                                    ((byte) 0x80 << 0)));
                    sp += 2;
                } else if ((b1 >> 4) == -2) {
                    // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
                    int srcRemaining = sl - sp;
                    if (srcRemaining < 3 || dp >= dl) {
                        if (srcRemaining > 1 && isMalformed3_2(b1, sa[sp + 1]))
                            return malformedForLength(src, sp, dst, dp, 1);
                        return xflow(src, sp, sl, dst, dp, 3);
                    }
                    int b2 = sa[sp + 1];
                    int b3 = sa[sp + 2];
                    if (isMalformed3(b1, b2, b3))
                        return malformedArray(src, sp, dst, dp, 3);
                    char c = (char)
                            ((b1 << 12) ^
                                    (b2 << 6) ^
                                    (b3 ^
                                            (((byte) 0xE0 << 12) ^
                                                    ((byte) 0x80 << 6) ^
                                                    ((byte) 0x80 << 0))));
                    da[dp++] = c;
                    sp += 3;
                } else if ((b1 >> 3) == -2) {
                    // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                    int srcRemaining = sl - sp;
                    if (srcRemaining < 4 || dl - dp < 2) {
                        b1 &= 0xff;
                        if (b1 > 0xf4 ||
                                srcRemaining > 1 && isMalformed4_2(b1, sa[sp + 1] & 0xff))
                            return malformedForLength(src, sp, dst, dp, 1);
                        if (srcRemaining > 2 && isMalformed4_3(sa[sp + 2]))
                            return malformedForLength(src, sp, dst, dp, 2);
                        return xflow(src, sp, sl, dst, dp, 4);
                    }
                    int b2 = sa[sp + 1];
                    int b3 = sa[sp + 2];
                    int b4 = sa[sp + 3];
                    int uc = ((b1 << 18) ^
                            (b2 << 12) ^
                            (b3 << 6) ^
                            (b4 ^
                                    (((byte) 0xF0 << 18) ^
                                            ((byte) 0x80 << 12) ^
                                            ((byte) 0x80 << 6) ^
                                            ((byte) 0x80 << 0))));
                    if (isMalformed4(b2, b3, b4) ||
                            // shortest form check
                            !Character.isSupplementaryCodePoint(uc)) {
                        return malformedArray(src, sp, dst, dp, 4);
                    }
                    da[dp++] = Character.highSurrogate(uc);
                    da[dp++] = Character.lowSurrogate(uc);
                    sp += 4;
                } else
                    return malformedArray(src, sp, dst, dp, 1);
            }
            return xflow(src, sp, sl, dst, dp, 0);
        }

        @Nullable
        private CoderResult decodeBufferLoop(ByteBuffer src,
                                             CharBuffer dst)
        {
            int mark = src.position();
            int limit = src.limit();
            while (mark < limit) {
                int b1 = src.get();
                if (b1 >= 0) {
                    // 1 byte, 7 bits: 0xxxxxxx
                    if (dst.remaining() < 1)
                        return xflow(src, mark, 1); // overflow
                    dst.put((char) b1);
                    mark++;
                } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                    // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
                    if (limit - mark < 2 || dst.remaining() < 1)
                        return xflow(src, mark, 2);
                    int b2 = src.get();
                    if (isNotContinuation(b2))
                        return malformedForLength(src, mark, 1);
                    dst.put((char) (((b1 << 6) ^ b2)
                            ^
                            (((byte) 0xC0 << 6) ^
                                    ((byte) 0x80 << 0))));
                    mark += 2;
                } else if ((b1 >> 4) == -2) {
                    // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
                    int srcRemaining = limit - mark;
                    if (srcRemaining < 3 || dst.remaining() < 1) {
                        if (srcRemaining > 1 && isMalformed3_2(b1, src.get()))
                            return malformedForLength(src, mark, 1);
                        return xflow(src, mark, 3);
                    }
                    int b2 = src.get();
                    int b3 = src.get();
                    if (isMalformed3(b1, b2, b3))
                        return malformed(src, mark, 3);
                    char c = (char)
                            ((b1 << 12) ^
                                    (b2 << 6) ^
                                    (b3 ^
                                            (((byte) 0xE0 << 12) ^
                                                    ((byte) 0x80 << 6) ^
                                                    ((byte) 0x80 << 0))));
                    dst.put(c);
                    mark += 3;
                } else if ((b1 >> 3) == -2) {
                    // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                    int srcRemaining = limit - mark;
                    if (srcRemaining < 4 || dst.remaining() < 2) {
                        b1 &= 0xff;
                        if (b1 > 0xf4 ||
                                srcRemaining > 1 && isMalformed4_2(b1, src.get() & 0xff))
                            return malformedForLength(src, mark, 1);
                        if (srcRemaining > 2 && isMalformed4_3(src.get()))
                            return malformedForLength(src, mark, 2);
                        return xflow(src, mark, 4);
                    }
                    int b2 = src.get();
                    int b3 = src.get();
                    int b4 = src.get();
                    int uc = ((b1 << 18) ^
                            (b2 << 12) ^
                            (b3 << 6) ^
                            (b4 ^
                                    (((byte) 0xF0 << 18) ^
                                            ((byte) 0x80 << 12) ^
                                            ((byte) 0x80 << 6) ^
                                            ((byte) 0x80 << 0))));
                    if (isMalformed4(b2, b3, b4) ||
                            // shortest form check
                            !Character.isSupplementaryCodePoint(uc)) {
                        return malformed(src, mark, 4);
                    }
                    dst.put(Character.highSurrogate(uc));
                    dst.put(Character.lowSurrogate(uc));
                    mark += 4;
                } else {
                    return malformed(src, mark, 1);
                }
            }
            return xflow(src, mark, 0);
        }

        @Nullable
        @Override
        protected CoderResult decodeLoop(ByteBuffer src,
                                         CharBuffer dst)
        {
            if (src.hasArray() && dst.hasArray())
                return decodeArrayLoop(src, dst);
            else
                return decodeBufferLoop(src, dst);
        }
    }

    private static final class Encoder extends CharsetEncoder
    {
        private char underflowSurrogate;

        private Encoder(Charset cs)
        {
            super(cs, 1.1f, 3.0f);
            underflowSurrogate = 0;
        }

        @Override
        public boolean canEncode(char c)
        {
            return true;
        }

        @Override
        public boolean isLegalReplacement(byte[] repl)
        {
            return ((repl.length == 1 && repl[0] >= 0) ||
                    super.isLegalReplacement(repl));
        }

        private static CoderResult overflow(CharBuffer src, int sp,
                                            ByteBuffer dst, int dp)
        {
            updatePositions(src, sp, dst, dp);
            return CoderResult.OVERFLOW;
        }

        private static CoderResult overflowArray(CharBuffer src, int sp,
                                                 ByteBuffer dst, int dp)
        {
            updatePositionsArray(src, sp, dst, dp);
            return CoderResult.OVERFLOW;
        }

        private CoderResult encodeArrayLoop(CharBuffer src,
                                            ByteBuffer dst)
        {
            char[] sa = src.array();
            int sp = src.arrayOffset() + src.position();
            int sl = src.arrayOffset() + src.limit();

            byte[] da = dst.array();
            int dp = dst.arrayOffset() + dst.position();
            int dl = dst.arrayOffset() + dst.limit();
            int dlASCII = dp + Math.min(sl - sp, dl - dp);

            // Optimized loop for ASCII input
            if (underflowSurrogate == 0) {
                char c;
                while (dp < dlASCII && (c = sa[sp]) < '\u0080') {
                    da[dp++] = (byte) c;
                    sp++;
                }
            }

            while (sp < sl) {
                int nextSp;
                char c;
                if (underflowSurrogate == 0) {
                    c = sa[sp];
                    nextSp = sp + 1;
                } else {
                    c = underflowSurrogate;
                    nextSp = sp;
                }

                if (c < 0x80) {
                    // Have at most seven bits
                    if (dp >= dl) {
                        return overflowArray(src, sp, dst, dp);
                    }
                    da[dp++] = (byte) c;
                } else if (c < 0x800) {
                    // 2 bytes, 11 bits
                    if (dl - dp < 2) {
                        return overflowArray(src, sp, dst, dp);
                    }
                    da[dp++] = (byte) (0xc0 | (c >> 6));
                    da[dp++] = (byte) (0x80 | (c & 0x3f));
                } else if (Character.isHighSurrogate(c)) {
                    if (nextSp >= sl) {
                        underflowSurrogate = c;
                        updatePositionsArray(src, nextSp, dst, dp);
                        return CoderResult.UNDERFLOW;
                    }

                    char low = sa[nextSp];
                    if (Character.isLowSurrogate(low)) {
                        // Have a surrogate pair
                        int uc = Character.toCodePoint(c, low);
                        if (dl - dp < 4) {
                            return overflowArray(src, sp, dst, dp);
                        }

                        da[dp++] = (byte) (0xf0 | ((uc >> 18)));
                        da[dp++] = (byte) (0x80 | ((uc >> 12) & 0x3f));
                        da[dp++] = (byte) (0x80 | ((uc >> 6) & 0x3f));
                        da[dp++] = (byte) (0x80 | (uc & 0x3f));
                        nextSp++; // 2 chars
                    } else {
                        // Unpaired surrogate; write the high surrogate
                        // 3 bytes, 16 bits
                        if (dl - dp < 3) {
                            return overflowArray(src, sp, dst, dp);
                        }
                        da[dp++] = (byte) (0xe0 | ((c >> 12)));
                        da[dp++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                        da[dp++] = (byte) (0x80 | (c & 0x3f));
                    }
                } else {
                    // 3 bytes, 16 bits
                    if (dl - dp < 3) {
                        return overflowArray(src, sp, dst, dp);
                    }
                    da[dp++] = (byte) (0xe0 | ((c >> 12)));
                    da[dp++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                    da[dp++] = (byte) (0x80 | (c & 0x3f));
                }

                underflowSurrogate = 0;
                sp = nextSp;
            }
            updatePositionsArray(src, sp, dst, dp);
            return CoderResult.UNDERFLOW;
        }

        private CoderResult encodeBufferLoop(CharBuffer src,
                                             ByteBuffer dst)
        {
            int sp = src.position();
            int sl = src.limit();

            int dp = dst.position();
            int dl = dst.limit();
            int dlASCII = dp + Math.min(sl - sp, dl - dp);

            // Optimized loop for ASCII input
            if (underflowSurrogate == 0) {
                char c;
                while (dp < dlASCII && (c = src.get(sp)) < '\u0080') {
                    dst.put(dp++, (byte) c);
                    sp++;
                }
            }

            while (sp < sl) {
                int nextSp;
                char c;
                if (underflowSurrogate == 0) {
                    c = src.get(sp);
                    nextSp = sp + 1;
                } else {
                    c = underflowSurrogate;
                    nextSp = sp;
                }

                if (c < 0x80) {
                    // Have at most seven bits
                    if (dp >= dl) {
                        return overflow(src, sp, dst, dp);
                    }
                    dst.put(dp++, (byte) c);
                } else if (c < 0x800) {
                    // 2 bytes, 11 bits
                    if (dst.remaining() < 2) {
                        return overflow(src, sp, dst, dp);
                    }
                    dst.put(dp++, (byte) (0xc0 | (c >> 6)));
                    dst.put(dp++, (byte) (0x80 | (c & 0x3f)));
                } else if (Character.isHighSurrogate(c)) {
                    if (nextSp >= sl) {
                        underflowSurrogate = c;
                        updatePositions(src, nextSp, dst, dp);
                        return CoderResult.UNDERFLOW;
                    }

                    char low = src.get(nextSp);
                    if (Character.isLowSurrogate(low)) {
                        // Have a surrogate pair
                        int uc = Character.toCodePoint(c, low);
                        if (dst.remaining() < 4) {
                            return overflow(src, sp, dst, dp);
                        }
                        dst.put(dp++, (byte) (0xf0 | ((uc >> 18))));
                        dst.put(dp++, (byte) (0x80 | ((uc >> 12) & 0x3f)));
                        dst.put(dp++, (byte) (0x80 | ((uc >> 6) & 0x3f)));
                        dst.put(dp++, (byte) (0x80 | (uc & 0x3f)));
                        nextSp++; // 2 chars
                    } else {
                        // 3 bytes, 16 bits
                        if (dst.remaining() < 3) {
                            return overflow(src, sp, dst, dp);
                        }
                        dst.put(dp++, (byte) (0xe0 | ((c >> 12))));
                        dst.put(dp++, (byte) (0x80 | ((c >> 6) & 0x3f)));
                        dst.put(dp++, (byte) (0x80 | (c & 0x3f)));
                    }
                } else {
                    // 3 bytes, 16 bits
                    if (dst.remaining() < 3) {
                        return overflow(src, sp, dst, dp);
                    }
                    dst.put(dp++, (byte) (0xe0 | ((c >> 12))));
                    dst.put(dp++, (byte) (0x80 | ((c >> 6) & 0x3f)));
                    dst.put(dp++, (byte) (0x80 | (c & 0x3f)));
                }

                underflowSurrogate = 0;
                sp = nextSp;
            }
            updatePositions(src, sp, dst, dp);
            return CoderResult.UNDERFLOW;
        }

        @Override
        protected void implReset()
        {
            underflowSurrogate = 0;
        }

        @Override
        protected CoderResult implFlush(ByteBuffer dst)
        {
            if (underflowSurrogate == 0) {
                return super.implFlush(dst);
            } else {
                if (dst.remaining() < 3) {
                    return CoderResult.OVERFLOW;
                }

                // 3 bytes, 16 bits
                dst.put((byte) (0xe0 | ((underflowSurrogate >> 12))));
                dst.put((byte) (0x80 | ((underflowSurrogate >> 6) & 0x3f)));
                dst.put((byte) (0x80 | (underflowSurrogate & 0x3f)));
                return CoderResult.UNDERFLOW;
            }
        }

        @Override
        protected final CoderResult encodeLoop(CharBuffer src,
                                               ByteBuffer dst)
        {
            if (src.hasArray() && dst.hasArray())
                return encodeArrayLoop(src, dst);
            else
                return encodeBufferLoop(src, dst);
        }
    }
}
