/*
 * Copyright IBM Corp. and others 2024
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution and is available at https://www.eclipse.org/legal/epl-2.0/
 * or the Apache License, Version 2.0 which accompanies this distribution and
 * is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This Source Code may also be made available under the following
 * Secondary Licenses when the conditions for such availability set
 * forth in the Eclipse Public License, v. 2.0 are satisfied: GNU
 * General Public License, version 2 with the GNU Classpath
 * Exception [1] and GNU General Public License, version 2 with the
 * OpenJDK Assembly Exception [2].
 *
 * [1] https://www.gnu.org/software/classpath/license.html
 * [2] https://openjdk.org/legal/assembly-exception.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 OR GPL-2.0-only WITH OpenJDK-assembly-exception-1.0
 */
package jit.test.recognizedMethod;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;
import jdk.internal.misc.Unsafe;
import jdk.internal.util.ArraysSupport;

public class TestArraysSupport {

    // Backing arrays are allocated with MAX_SKIP extra elements at the front
    // so that starting at index aSkip or bSkip is always valid.
    private static final int MAX_TEST_ARRAY_SIZE = 32;
    private static final int MAX_SKIP            = 8;  // elements
    private static final int BACKING_SIZE        = MAX_SKIP + MAX_TEST_ARRAY_SIZE;

    // Element-index skips tried for aFromIndex and bFromIndex independently.
    // Chosen to cover: zero, sub-word, word, and cross-word-boundary offsets.
    private static final int[] SKIPS = { 0, 1, 3, 4, 7 };

    // =========================================================================
    // Tests for ArraysSupport.vectorizedMismatch
    //
    // One @Test per element type. Each test sweeps over:
    //   - aSkip, bSkip in SKIPS  (independent element-index start positions)
    //   - len in 1..MAX_TEST_ARRAY_SIZE
    //   - mismatch planted at every element position 0..len-1, plus equal case
    // and asserts that vectorizedMismatch agrees with a pure scalar reference.
    // Offsets are computed as BASE + fromIndex << log2Scale, matching exactly
    // what the JDK's own ArraysSupport.mismatch() overloads do.
    // invocationCount=2: first pass typically interpreted, second JIT-compiled.
    // =========================================================================

    @Test(groups = "level.sanity", invocationCount = 2)
    public void testVectorizedMismatchByte() {
        byte[] a = new byte[BACKING_SIZE];
        byte[] b = new byte[BACKING_SIZE];
        Arrays.fill(a, (byte) 1);
        Arrays.fill(b, (byte) 1);
        for (int aSkip : SKIPS) {
            for (int bSkip : SKIPS) {
                long aOff = Unsafe.ARRAY_BYTE_BASE_OFFSET + ((long) aSkip << ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE);
                long bOff = Unsafe.ARRAY_BYTE_BASE_OFFSET + ((long) bSkip << ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE);
                for (int len = 1; len <= MAX_TEST_ARRAY_SIZE; len++) {
                    checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE, "byte");
                    for (int pos = 0; pos < len; pos++) {
                        b[bSkip + pos] = 2;
                        checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE, "byte");
                        b[bSkip + pos] = 1;
                    }
                }
            }
        }
    }

    @Test(groups = "level.sanity", invocationCount = 2)
    public void testVectorizedMismatchChar() {
        char[] a = new char[BACKING_SIZE];
        char[] b = new char[BACKING_SIZE];
        Arrays.fill(a, 'a');
        Arrays.fill(b, 'a');
        for (int aSkip : SKIPS) {
            for (int bSkip : SKIPS) {
                long aOff = Unsafe.ARRAY_CHAR_BASE_OFFSET + ((long) aSkip << ArraysSupport.LOG2_ARRAY_CHAR_INDEX_SCALE);
                long bOff = Unsafe.ARRAY_CHAR_BASE_OFFSET + ((long) bSkip << ArraysSupport.LOG2_ARRAY_CHAR_INDEX_SCALE);
                for (int len = 1; len <= MAX_TEST_ARRAY_SIZE; len++) {
                    checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_CHAR_INDEX_SCALE, "char");
                    for (int pos = 0; pos < len; pos++) {
                        b[bSkip + pos] = 'z';
                        checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_CHAR_INDEX_SCALE, "char");
                        b[bSkip + pos] = 'a';
                    }
                }
            }
        }
    }

    @Test(groups = "level.sanity", invocationCount = 2)
    public void testVectorizedMismatchShort() {
        short[] a = new short[BACKING_SIZE];
        short[] b = new short[BACKING_SIZE];
        Arrays.fill(a, (short) 1);
        Arrays.fill(b, (short) 1);
        for (int aSkip : SKIPS) {
            for (int bSkip : SKIPS) {
                long aOff = Unsafe.ARRAY_SHORT_BASE_OFFSET + ((long) aSkip << ArraysSupport.LOG2_ARRAY_SHORT_INDEX_SCALE);
                long bOff = Unsafe.ARRAY_SHORT_BASE_OFFSET + ((long) bSkip << ArraysSupport.LOG2_ARRAY_SHORT_INDEX_SCALE);
                for (int len = 1; len <= MAX_TEST_ARRAY_SIZE; len++) {
                    checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_SHORT_INDEX_SCALE, "short");
                    for (int pos = 0; pos < len; pos++) {
                        b[bSkip + pos] = (short) 2;
                        checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_SHORT_INDEX_SCALE, "short");
                        b[bSkip + pos] = (short) 1;
                    }
                }
            }
        }
    }

    @Test(groups = "level.sanity", invocationCount = 2)
    public void testVectorizedMismatchInt() {
        int[] a = new int[BACKING_SIZE];
        int[] b = new int[BACKING_SIZE];
        Arrays.fill(a, 1);
        Arrays.fill(b, 1);
        for (int aSkip : SKIPS) {
            for (int bSkip : SKIPS) {
                long aOff = Unsafe.ARRAY_INT_BASE_OFFSET + ((long) aSkip << ArraysSupport.LOG2_ARRAY_INT_INDEX_SCALE);
                long bOff = Unsafe.ARRAY_INT_BASE_OFFSET + ((long) bSkip << ArraysSupport.LOG2_ARRAY_INT_INDEX_SCALE);
                for (int len = 1; len <= MAX_TEST_ARRAY_SIZE; len++) {
                    checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_INT_INDEX_SCALE, "int");
                    for (int pos = 0; pos < len; pos++) {
                        b[bSkip + pos] = 2;
                        checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_INT_INDEX_SCALE, "int");
                        b[bSkip + pos] = 1;
                    }
                }
            }
        }
    }

    @Test(groups = "level.sanity", invocationCount = 2)
    public void testVectorizedMismatchLong() {
        long[] a = new long[BACKING_SIZE];
        long[] b = new long[BACKING_SIZE];
        Arrays.fill(a, 1L);
        Arrays.fill(b, 1L);
        for (int aSkip : SKIPS) {
            for (int bSkip : SKIPS) {
                long aOff = Unsafe.ARRAY_LONG_BASE_OFFSET + ((long) aSkip << ArraysSupport.LOG2_ARRAY_LONG_INDEX_SCALE);
                long bOff = Unsafe.ARRAY_LONG_BASE_OFFSET + ((long) bSkip << ArraysSupport.LOG2_ARRAY_LONG_INDEX_SCALE);
                for (int len = 1; len <= MAX_TEST_ARRAY_SIZE; len++) {
                    checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_LONG_INDEX_SCALE, "long");
                    for (int pos = 0; pos < len; pos++) {
                        b[bSkip + pos] = 2L;
                        checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_LONG_INDEX_SCALE, "long");
                        b[bSkip + pos] = 1L;
                    }
                }
            }
        }
    }

    @Test(groups = "level.sanity", invocationCount = 2)
    public void testVectorizedMismatchFloat() {
        float[] a = new float[BACKING_SIZE];
        float[] b = new float[BACKING_SIZE];
        Arrays.fill(a, 1.0f);
        Arrays.fill(b, 1.0f);
        for (int aSkip : SKIPS) {
            for (int bSkip : SKIPS) {
                long aOff = Unsafe.ARRAY_FLOAT_BASE_OFFSET + ((long) aSkip << ArraysSupport.LOG2_ARRAY_FLOAT_INDEX_SCALE);
                long bOff = Unsafe.ARRAY_FLOAT_BASE_OFFSET + ((long) bSkip << ArraysSupport.LOG2_ARRAY_FLOAT_INDEX_SCALE);
                for (int len = 1; len <= MAX_TEST_ARRAY_SIZE; len++) {
                    checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_FLOAT_INDEX_SCALE, "float");
                    for (int pos = 0; pos < len; pos++) {
                        b[bSkip + pos] = 2.0f;
                        checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_FLOAT_INDEX_SCALE, "float");
                        b[bSkip + pos] = 1.0f;
                    }
                }
            }
        }
    }

    @Test(groups = "level.sanity", invocationCount = 2)
    public void testVectorizedMismatchDouble() {
        double[] a = new double[BACKING_SIZE];
        double[] b = new double[BACKING_SIZE];
        Arrays.fill(a, 1.0);
        Arrays.fill(b, 1.0);
        for (int aSkip : SKIPS) {
            for (int bSkip : SKIPS) {
                long aOff = Unsafe.ARRAY_DOUBLE_BASE_OFFSET + ((long) aSkip << ArraysSupport.LOG2_ARRAY_DOUBLE_INDEX_SCALE);
                long bOff = Unsafe.ARRAY_DOUBLE_BASE_OFFSET + ((long) bSkip << ArraysSupport.LOG2_ARRAY_DOUBLE_INDEX_SCALE);
                for (int len = 1; len <= MAX_TEST_ARRAY_SIZE; len++) {
                    checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_DOUBLE_INDEX_SCALE, "double");
                    for (int pos = 0; pos < len; pos++) {
                        b[bSkip + pos] = 2.0;
                        checkVectorizedMismatch(a, aOff, b, bOff, len, ArraysSupport.LOG2_ARRAY_DOUBLE_INDEX_SCALE, "double");
                        b[bSkip + pos] = 1.0;
                    }
                }
            }
        }
    }

    // ---- helpers ------------------------------------------------------------

    /**
     * Calls ArraysSupport.vectorizedMismatch and compares against a pure scalar
     * reference. Fails if they disagree.
     */
    private static void checkVectorizedMismatch(Object a, long aOffset, Object b, long bOffset,
                                                int length, int log2Scale, String type) {
        int intrinsic = ArraysSupport.vectorizedMismatch(a, aOffset, b, bOffset, length, log2Scale);
        int reference = referenceMismatch(a, aOffset, b, bOffset, length, log2Scale);
        Assert.assertEquals(intrinsic, reference,
                String.format("%s aOff=%d bOff=%d len=%d: vectorizedMismatch=%d reference=%d",
                        type, aOffset, bOffset, length, intrinsic, reference));
    }

    /**
     * Pure-Java scalar mismatch — reads element-by-element using Unsafe so it
     * is never itself intrinsified. aOffset and bOffset are independent byte
     * offsets from their respective object headers, computed as
     * BASE + fromIndex << log2Scale.
     */
    private static final Unsafe U = Unsafe.getUnsafe();

    private static int referenceMismatch(Object a, long aOffset, Object b, long bOffset,
                                         int length, int log2Scale) {
        int eSize = 1 << log2Scale;
        for (int i = 0; i < length; i++) {
            long aOff = aOffset + ((long) i << log2Scale);
            long bOff = bOffset + ((long) i << log2Scale);
            boolean equal;
            switch (eSize) {
                case 1:  equal = U.getByte(a, aOff)  == U.getByte(b, bOff);  break;
                case 2:  equal = U.getShort(a, aOff) == U.getShort(b, bOff); break;
                case 4:  equal = U.getInt(a, aOff)   == U.getInt(b, bOff);   break;
                default: equal = U.getLong(a, aOff)  == U.getLong(b, bOff);  break;
            }
            if (!equal) return i;
        }
        return -1;
    }


    @Test(groups = "level.sanity", dataProvider = "byteArrayProvider", invocationCount = 2)
    public void testVectorHashCodeByte(final byte[] arr) {
        int expectedResult = hashCode(1, arr, 0, arr.length);
        int intrinsicResult = Arrays.hashCode(arr);

        Assert.assertEquals(intrinsicResult, expectedResult, String.format("Unexpected byte hashcode result for array of length %d", arr.length));
    }

    @Test(groups = "level.sanity", dataProvider = "charArrayProvider", invocationCount = 2)
    public void testVectorHashCodeChar(final char[] arr) {
        int expectedResult = hashCode(1, arr, 0, arr.length);
        int intrinsicResult = Arrays.hashCode(arr);

        Assert.assertEquals(intrinsicResult, expectedResult, String.format("Unexpected char hashcode result for array of length %d", arr.length));
    }

    @Test(groups = "level.sanity", dataProvider = "shortArrayProvider", invocationCount = 2)
    public void testVectorHashCodeShort(final short[] arr) {
        int expectedResult = hashCode(1, arr, 0, arr.length);
        int intrinsicResult = Arrays.hashCode(arr);

        Assert.assertEquals(intrinsicResult, expectedResult, String.format("Unexpected short hashcode result for array of length %d", arr.length));
    }

    @Test(groups = "level.sanity", dataProvider = "intArrayProvider", invocationCount = 2)
    public void testVectorHashCodeInteger(final int[] arr) {
        int expectedResult = hashCode(1, arr, 0, arr.length);
        int intrinsicResult = Arrays.hashCode(arr);

        Assert.assertEquals(intrinsicResult, expectedResult, String.format("Unexpected integer hashcode result for array of length %d", arr.length));
    }

    /* Generate MAX_TEST_ARRAY_SIZE number of random arrays for each element type */

    @DataProvider(name = "byteArrayProvider")
    public static Object[][] byteArrayProvider() {
        final Random random = new Random(0);

        // Generate MAX_TEST_ARRAY_SIZE arrays
        return IntStream.range(0, MAX_TEST_ARRAY_SIZE)
                .mapToObj(i -> new Object[]{generateByteArray(random, i)})
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "charArrayProvider")
    public static Object[][] charArrayProvider() {
        final Random random = new Random(0);

        // Generate MAX_TEST_ARRAY_SIZE arrays
        return IntStream.range(0, MAX_TEST_ARRAY_SIZE)
                .mapToObj(i -> new Object[]{generateCharArray(random, i)})
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "shortArrayProvider")
    public static Object[][] shortArrayProvider() {
        final Random random = new Random(0);

        // Generate MAX_TEST_ARRAY_SIZE arrays
        return IntStream.range(0, MAX_TEST_ARRAY_SIZE)
                .mapToObj(i -> new Object[]{generateShortArray(random, i)})
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "intArrayProvider")
    public static Object[][] intArrayProvider() {
        final Random random = new Random(0);

        // Generate MAX_TEST_ARRAY_SIZE arrays
        return IntStream.range(0, MAX_TEST_ARRAY_SIZE)
                .mapToObj(i -> new Object[]{generateIntArray(random, i)})
                .toArray(Object[][]::new);
    }

    private static byte[] generateByteArray(final Random random, final int length) {
        final byte[] result = new byte[length];
        random.nextBytes(result);
        return result;
    }

    private static char[] generateCharArray(final Random random, final int length) {
        final char[] result = new char[length];
        IntStream.range(0, length).forEach(i -> result[i] = (char) (random.nextInt(Character.MAX_VALUE)));
        return result;
    }

    private static short[] generateShortArray(final Random random, final int length) {
        final short[] result = new short[length];
        IntStream.range(0, length).forEach(i -> result[i] = (short) (random.nextInt(Short.MAX_VALUE - Short.MIN_VALUE + 1)));
        return result;
    }

    private static int[] generateIntArray(final Random random, final int length) {
        return random.ints(length).toArray();
    }

    /* Not intrinsic hashCode implementations (reference of truth) */

    private static int hashCode(int result, byte[] a, int fromIndex, int length) {
        int end = fromIndex + length;
        for (int i = fromIndex; i < end; i++) {
            result = 31 * result + a[i];
        }
        return result;
    }

    private static int hashCode(int result, char[] a, int fromIndex, int length) {
        int end = fromIndex + length;
        for (int i = fromIndex; i < end; i++) {
            result = 31 * result + a[i];
        }
        return result;
    }

    private static int hashCode(int result, short[] a, int fromIndex, int length) {
        int end = fromIndex + length;
        for (int i = fromIndex; i < end; i++) {
            result = 31 * result + a[i];
        }
        return result;
    }

    private static int hashCode(int result, int[] a, int fromIndex, int length) {
        int end = fromIndex + length;
        for (int i = fromIndex; i < end; i++) {
            result = 31 * result + a[i];
        }
        return result;
    }
}
