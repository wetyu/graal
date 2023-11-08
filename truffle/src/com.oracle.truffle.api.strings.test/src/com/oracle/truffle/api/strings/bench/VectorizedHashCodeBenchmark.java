/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.strings.bench;

import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = TStringBenchmarkBase.Defaults.FORKS, jvmArgsAppend = {"--add-opens=org.graalvm.truffle/com.oracle.truffle.api.strings=ALL-UNNAMED"})
public class VectorizedHashCodeBenchmark extends TStringBenchmarkBase {

    private static final int RANDOM_SEED = 42;

    private static Random random() {
        return new Random(RANDOM_SEED);
    }

    @State(Scope.Benchmark)
    public static class BenchState {

        @Param({"1", "10", "100", "1000", "10000"}) int length;

        byte[] b8;
        byte[] b16;
        byte[] b32;

        TruffleString s8;
        TruffleString s16;
        TruffleString s32;

        public BenchState() {
        }

        @Setup
        public void setUp() {
            b8 = random().ints(length, 0, 0xff).collect(ByteArrayOutputStream::new,
                            (bytes, i) -> bytes.write((byte) i),
                            (bytes, next) -> bytes.write(next.toByteArray(), 0, next.size())).toByteArray();

            b16 = TruffleString.fromIntArrayUTF32Uncached(random().ints(length, 0, 0xffff).toArray()).switchEncodingUncached(TruffleString.Encoding.UTF_16).copyToByteArrayUncached(
                            TruffleString.Encoding.UTF_16);

            b32 = TruffleString.fromIntArrayUTF32Uncached(random().ints(length, 0, 0x10_ffff).toArray()).copyToByteArrayUncached(TruffleString.Encoding.UTF_32);

            s8 = TruffleString.fromByteArrayUncached(b8, TruffleString.Encoding.ISO_8859_1, false);
            s16 = TruffleString.fromByteArrayUncached(b16, TruffleString.Encoding.UTF_16, false);
            s32 = TruffleString.fromByteArrayUncached(b32, TruffleString.Encoding.UTF_32, false);
        }
    }

    private static final MethodHandle INVALIDATE_HASH_CODE;
    static {
        try {
            var lookup = MethodHandles.lookup();
            var method = AbstractTruffleString.class.getDeclaredMethod("invalidateHashCode");
            method.setAccessible(true);
            INVALIDATE_HASH_CODE = lookup.unreflect(method);
        } catch (IllegalAccessException | NoSuchMethodException | SecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static void resetHashCode(TruffleString s) {
        try {
            INVALIDATE_HASH_CODE.invokeExact((AbstractTruffleString) s);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Benchmark
    public int hashCode8(BenchState state) {
        TruffleString.Encoding encoding = TruffleString.Encoding.ISO_8859_1;
        TruffleString string = state.s8;
        resetHashCode(string);
        return string.hashCodeUncached(encoding);
    }

    @Benchmark
    public int hashCode16(BenchState state) {
        TruffleString.Encoding encoding = TruffleString.Encoding.UTF_16;
        TruffleString string = state.s16;
        resetHashCode(string);
        return string.hashCodeUncached(encoding);
    }

    @Benchmark
    public int hashCode32(BenchState state) {
        TruffleString.Encoding encoding = TruffleString.Encoding.UTF_32;
        TruffleString string = state.s32;
        resetHashCode(string);
        return string.hashCodeUncached(encoding);
    }
}
