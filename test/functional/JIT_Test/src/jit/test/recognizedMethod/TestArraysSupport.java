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

public class TestArraysSupport {

    private static final int MAX_TEST_ARRAY_SIZE = 140;

    // =========================================================================
    // Tests for ArraysSupport.vectorizedMismatch, exercised via Arrays.mismatch
    //
    // Each test runs with invocationCount=2 so the first call is typically
    // interpreted and the second is JIT-compiled, allowing the compiler to
    // recognise and lower the intrinsic.  Data providers cover:
    //   - equal arrays (expect -1)
    //   - mismatch at every possible index from 0 to length-1
    //   - a variety of lengths to stress the scalar tail handling
    // =========================================================================

    // ---- byte ---------------------------------------------------------------

    @Test(groups = "level.sanity", dataProvider = "mismatchByteProvider", invocationCount = 2)
    public void testVectorizedMismatchByte(byte[] a, byte[] b, int expectedMismatch) {
        int result = Arrays.mismatch(a, b);
        Assert.assertEquals(result, expectedMismatch,
                String.format("byte mismatch: length=%d expected=%d got=%d", a.length, expectedMismatch, result));
    }

    @DataProvider(name = "mismatchByteProvider")
    public static Object[][] mismatchByteProvider() {
        return buildMismatchCases(MAX_TEST_ARRAY_SIZE, MismatchArrayFactory.BYTE);
    }

    // ---- char ---------------------------------------------------------------

    @Test(groups = "level.sanity", dataProvider = "mismatchCharProvider", invocationCount = 2)
    public void testVectorizedMismatchChar(char[] a, char[] b, int expectedMismatch) {
        int result = Arrays.mismatch(a, b);
        Assert.assertEquals(result, expectedMismatch,
                String.format("char mismatch: length=%d expected=%d got=%d", a.length, expectedMismatch, result));
    }

    @DataProvider(name = "mismatchCharProvider")
    public static Object[][] mismatchCharProvider() {
        return buildMismatchCases(MAX_TEST_ARRAY_SIZE / 2, MismatchArrayFactory.CHAR);
    }

    // ---- short --------------------------------------------------------------

    @Test(groups = "level.sanity", dataProvider = "mismatchShortProvider", invocationCount = 2)
    public void testVectorizedMismatchShort(short[] a, short[] b, int expectedMismatch) {
        int result = Arrays.mismatch(a, b);
        Assert.assertEquals(result, expectedMismatch,
                String.format("short mismatch: length=%d expected=%d got=%d", a.length, expectedMismatch, result));
    }

    @DataProvider(name = "mismatchShortProvider")
    public static Object[][] mismatchShortProvider() {
        return buildMismatchCases(MAX_TEST_ARRAY_SIZE / 2, MismatchArrayFactory.SHORT);
    }

    // ---- int ----------------------------------------------------------------

    @Test(groups = "level.sanity", dataProvider = "mismatchIntProvider", invocationCount = 2)
    public void testVectorizedMismatchInt(int[] a, int[] b, int expectedMismatch) {
        int result = Arrays.mismatch(a, b);
        Assert.assertEquals(result, expectedMismatch,
                String.format("int mismatch: length=%d expected=%d got=%d", a.length, expectedMismatch, result));
    }

    @DataProvider(name = "mismatchIntProvider")
    public static Object[][] mismatchIntProvider() {
        return buildMismatchCases(MAX_TEST_ARRAY_SIZE / 4, MismatchArrayFactory.INT);
    }

    // ---- long ---------------------------------------------------------------

    @Test(groups = "level.sanity", dataProvider = "mismatchLongProvider", invocationCount = 2)
    public void testVectorizedMismatchLong(long[] a, long[] b, int expectedMismatch) {
        int result = Arrays.mismatch(a, b);
        Assert.assertEquals(result, expectedMismatch,
                String.format("long mismatch: length=%d expected=%d got=%d", a.length, expectedMismatch, result));
    }

    @DataProvider(name = "mismatchLongProvider")
    public static Object[][] mismatchLongProvider() {
        return buildMismatchCases(MAX_TEST_ARRAY_SIZE / 8, MismatchArrayFactory.LONG);
    }

    // ---- float --------------------------------------------------------------

    @Test(groups = "level.sanity", dataProvider = "mismatchFloatProvider", invocationCount = 2)
    public void testVectorizedMismatchFloat(float[] a, float[] b, int expectedMismatch) {
        int result = Arrays.mismatch(a, b);
        Assert.assertEquals(result, expectedMismatch,
                String.format("float mismatch: length=%d expected=%d got=%d", a.length, expectedMismatch, result));
    }

    @DataProvider(name = "mismatchFloatProvider")
    public static Object[][] mismatchFloatProvider() {
        return buildMismatchCases(MAX_TEST_ARRAY_SIZE / 4, MismatchArrayFactory.FLOAT);
    }

    // ---- double -------------------------------------------------------------

    @Test(groups = "level.sanity", dataProvider = "mismatchDoubleProvider", invocationCount = 2)
    public void testVectorizedMismatchDouble(double[] a, double[] b, int expectedMismatch) {
        int result = Arrays.mismatch(a, b);
        Assert.assertEquals(result, expectedMismatch,
                String.format("double mismatch: length=%d expected=%d got=%d", a.length, expectedMismatch, result));
    }

    @DataProvider(name = "mismatchDoubleProvider")
    public static Object[][] mismatchDoubleProvider() {
        return buildMismatchCases(MAX_TEST_ARRAY_SIZE / 8, MismatchArrayFactory.DOUBLE);
    }

    // ---- helpers ------------------------------------------------------------

    /**
     * Builds test cases for a given element type and maximum array length.
     * For each length L from 1 to maxLength, we produce:
     *   - one equal-pair case   (expected mismatch index = -1)
     *   - L cases with a single mismatch at each possible index 0..L-1
     */
    private static Object[][] buildMismatchCases(int maxLength, MismatchArrayFactory factory) {
        // Count total cases: for each L: 1 (equal) + L (one mismatch per index)
        int total = 0;
        for (int len = 1; len <= maxLength; len++) {
            total += 1 + len;
        }
        Object[][] cases = new Object[total][];
        int idx = 0;
        for (int len = 1; len <= maxLength; len++) {
            // equal arrays -> mismatch = -1
            cases[idx++] = factory.equal(len);
            // mismatch at position i
            for (int mismatchAt = 0; mismatchAt < len; mismatchAt++) {
                cases[idx++] = factory.withMismatchAt(len, mismatchAt);
            }
        }
        return cases;
    }

    /** Strategy for building matched/mismatched array pairs of a given element type. */
    private interface MismatchArrayFactory {
        /** Returns { a, b, expectedMismatch } where a and b are identical. */
        Object[] equal(int length);
        /** Returns { a, b, expectedMismatch } where a[mismatchAt] != b[mismatchAt]. */
        Object[] withMismatchAt(int length, int mismatchAt);

        MismatchArrayFactory BYTE = new MismatchArrayFactory() {
            public Object[] equal(int length) {
                byte[] a = new byte[length];
                Arrays.fill(a, (byte) 42);
                return new Object[]{ a, a.clone(), -1 };
            }
            public Object[] withMismatchAt(int length, int mismatchAt) {
                byte[] a = new byte[length];
                byte[] b = new byte[length];
                Arrays.fill(a, (byte) 1);
                Arrays.fill(b, (byte) 1);
                b[mismatchAt] = 2;
                return new Object[]{ a, b, mismatchAt };
            }
        };

        MismatchArrayFactory CHAR = new MismatchArrayFactory() {
            public Object[] equal(int length) {
                char[] a = new char[length];
                Arrays.fill(a, 'x');
                return new Object[]{ a, a.clone(), -1 };
            }
            public Object[] withMismatchAt(int length, int mismatchAt) {
                char[] a = new char[length];
                char[] b = new char[length];
                Arrays.fill(a, 'a');
                Arrays.fill(b, 'a');
                b[mismatchAt] = 'z';
                return new Object[]{ a, b, mismatchAt };
            }
        };

        MismatchArrayFactory SHORT = new MismatchArrayFactory() {
            public Object[] equal(int length) {
                short[] a = new short[length];
                Arrays.fill(a, (short) 100);
                return new Object[]{ a, a.clone(), -1 };
            }
            public Object[] withMismatchAt(int length, int mismatchAt) {
                short[] a = new short[length];
                short[] b = new short[length];
                Arrays.fill(a, (short) 1);
                Arrays.fill(b, (short) 1);
                b[mismatchAt] = (short) 2;
                return new Object[]{ a, b, mismatchAt };
            }
        };

        MismatchArrayFactory INT = new MismatchArrayFactory() {
            public Object[] equal(int length) {
                int[] a = new int[length];
                Arrays.fill(a, 0xCAFEBABE);
                return new Object[]{ a, a.clone(), -1 };
            }
            public Object[] withMismatchAt(int length, int mismatchAt) {
                int[] a = new int[length];
                int[] b = new int[length];
                Arrays.fill(a, 1);
                Arrays.fill(b, 1);
                b[mismatchAt] = 2;
                return new Object[]{ a, b, mismatchAt };
            }
        };

        MismatchArrayFactory LONG = new MismatchArrayFactory() {
            public Object[] equal(int length) {
                long[] a = new long[length];
                Arrays.fill(a, 0xDEADBEEFCAFEBABEL);
                return new Object[]{ a, a.clone(), -1 };
            }
            public Object[] withMismatchAt(int length, int mismatchAt) {
                long[] a = new long[length];
                long[] b = new long[length];
                Arrays.fill(a, 1L);
                Arrays.fill(b, 1L);
                b[mismatchAt] = 2L;
                return new Object[]{ a, b, mismatchAt };
            }
        };

        MismatchArrayFactory FLOAT = new MismatchArrayFactory() {
            public Object[] equal(int length) {
                float[] a = new float[length];
                Arrays.fill(a, 1.0f);
                return new Object[]{ a, a.clone(), -1 };
            }
            public Object[] withMismatchAt(int length, int mismatchAt) {
                float[] a = new float[length];
                float[] b = new float[length];
                Arrays.fill(a, 1.0f);
                Arrays.fill(b, 1.0f);
                b[mismatchAt] = 2.0f;
                return new Object[]{ a, b, mismatchAt };
            }
        };

        MismatchArrayFactory DOUBLE = new MismatchArrayFactory() {
            public Object[] equal(int length) {
                double[] a = new double[length];
                Arrays.fill(a, 1.0);
                return new Object[]{ a, a.clone(), -1 };
            }
            public Object[] withMismatchAt(int length, int mismatchAt) {
                double[] a = new double[length];
                double[] b = new double[length];
                Arrays.fill(a, 1.0);
                Arrays.fill(b, 1.0);
                b[mismatchAt] = 2.0;
                return new Object[]{ a, b, mismatchAt };
            }
        };
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
