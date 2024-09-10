/*******************************************************************************
 * Copyright IBM Corp. and others 2019
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
 *******************************************************************************/

package jit.test.recognizedMethod;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import java.util.Random;
import java.io.*;
import java.lang.Math.*;
import org.testng.asserts.SoftAssert;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestJavaLangMath {

    /**
    * Tests the constant corner cases defined by the {@link Math.sqrt} method.
    * <p>
    * The JIT compiler will transform calls to {@link Math.sqrt} within this test
    * into the following tree sequence:
    *
    * <code>
    * dsqrt
    *   dconst <x>
    * </code>
    *
    * Subsequent tree simplification passes will attempt to reduce this constant
    * operation to a <code>dsqrt</code> IL by performing the square root at compile
    * time. The transformation will be performed when the function get executed 
    * twice, therefore, the "invocationCount=2" is needed. However we must ensure the
    * result of the square root done by the compiler at compile time will be exactly 
    * the same as the result had it been done by the Java runtime at runtime. This 
    * test validates the results are the same.
    */
    @Test(groups = {"level.sanity"}, invocationCount=2)
    public void test_java_lang_Math_sqrt() {
        AssertJUnit.assertTrue(Double.isNaN(Math.sqrt(Double.NEGATIVE_INFINITY)));
        AssertJUnit.assertTrue(Double.isNaN(Math.sqrt(-42.25d)));
        AssertJUnit.assertEquals(-0.0d, Math.sqrt(-0.0d));
        AssertJUnit.assertEquals(+0.0d, Math.sqrt(+0.0d));
        AssertJUnit.assertEquals(7.5d, Math.sqrt(56.25d));
        AssertJUnit.assertEquals(Double.POSITIVE_INFINITY, Math.sqrt(Double.POSITIVE_INFINITY));
        AssertJUnit.assertTrue(Double.isNaN(Math.sqrt(Double.NaN)));
    }

    // constants used to various min/max tests
    private static final float pfZero = +0.0f;
    private static final float nfZero = -0.0f;
    private static final int nfZeroBits = 0x80000000;
    private static final int pfZeroBits = 0;
    private static final int  fqNaNBits = 0x7fcabdef;
    private static  final float  fqNaN = Float.intBitsToFloat(fqNaNBits);
    private static final int fsNaNBits = 0x7faedbaf;
    private static float fsNaN = Float.intBitsToFloat(fsNaNBits);
    private static final float fpInf = Float.POSITIVE_INFINITY;
    private static final float fnInf = Float.NEGATIVE_INFINITY;
	private static final int fquietBit = 0x00400000;
    private static final int fNaNExpStart = 0x7f800000;
    private static final int fNaNMantisaMax = 0x00400000;	

    private static final double pdZero = +0.0;
    private static final double ndZero = -0.0;
    private static final long  ndZeroBits = 0x8000000000000000L;
    private static final long  pdZeroBits = 0;
    private static final long  dqNaNBits = 0x7ff800000000000fL;
    private static final double  dqNaN = Double.longBitsToDouble(dqNaNBits);
    private static final  long dsNaNBits = 0x7ff400000000000fL;
    private static final double  dsNaN = Double.longBitsToDouble(dsNaNBits);
    private static final double dpInf = Double.POSITIVE_INFINITY;
    private static final double dnInf = Double.NEGATIVE_INFINITY;
    private static final long dNaNMantisaMax = 0x0008000000000000L;
    private static final long dNaNExpStart = 0x7ff0000000000000L;
    private static final long  dquietBit = 0x0008000000000000L;

    private static SoftAssert s = new SoftAssert();

    private static class NaNTestPair<T, BitType> {
        T first;
        T second;
        BitType bFirst;
        BitType bSecond;
        String message;
        BitType expected;
    }

    private static NaNTestPair<Double, Long> getDoubleNaNPair(){
        Random r = new Random();
        NaNTestPair<Double, Long> p = new NaNTestPair<Double, Long>();
        // 0->quiet, 1->signalling
            String message = "";
            double farg1, farg2, farg3;
			farg1 = 0.0f;
			farg2 = 0.0f;
			farg3 = 0.0f; 
            long iarg1, iarg2;
			iarg1 = r.nextLong() >> 13; // shift right to only mantisa has 1s, and quiet bit is not set
			
            int arg1Type = r.nextInt(3);

            if (arg1Type == 0) { // qNaN
                message = message + "(qNaN, ";
                iarg1 = iarg1 | dNaNExpStart | dquietBit;
                farg1 = Double.longBitsToDouble(iarg1);
            }else if (arg1Type == 1) { // sNaN
                message = message + "(sNaN, ";
                iarg1 = iarg1 | dNaNExpStart;
                farg1 = Double.longBitsToDouble(iarg1);
            }else{ // Normal number
                farg1 = r.nextDouble() * 1000000.0 + 1.0;
                message = message + "(" + String.valueOf(farg1) + ", ";
            }
            p.first = farg1;
            p.bFirst = iarg1;

            //arg 2 
            int arg2Type = r.nextInt(3);
            iarg2 = r.nextLong() >> 13;
            if (arg2Type == 0) {
                message = message + " qNaN)";
                iarg2 = iarg2 | dNaNExpStart | dquietBit;
                farg2 = Double.longBitsToDouble(iarg2);
            }else if (arg2Type == 1){
                message = message + " sNaN)"; 
                iarg2 = iarg2 | dNaNExpStart;
                farg2 = Double.longBitsToDouble(iarg2);
            }else {
                if (arg1Type != 2) {
                    farg2 = r.nextDouble() * 1000000.0 + 1;
                    message = message + String.valueOf(farg2) + ")";
                    iarg2 = Double.doubleToRawLongBits(farg2);
                
                }else{
                    return null;
                }
            }
            p.second = farg2;
            p.bSecond = iarg2;
            p.message = message;
            p.expected = (arg1Type != 2 ? iarg1 : iarg2) | dquietBit;
            return p;
    }

    private static NaNTestPair<Float, Integer> getFloatNaNPair(){ // 0: max, 1: min
        Random r = new Random();
        NaNTestPair<Float, Integer> p = new NaNTestPair<Float, Integer>();
        // 0->quiet, 1->signalling
        String message = "";
        float farg1, farg2, farg3;
        farg1 = farg2 = farg3 = 0.0f;
        int iarg1, iarg2;
        iarg1 = r.nextInt(fNaNMantisaMax - 1) + 1;
        int arg1Type = r.nextInt(3);
        if (arg1Type == 0) { // qNaN
                message = message + "(qNaN, ";
                iarg1 = iarg1 | fNaNExpStart | fquietBit;
                farg1 = Float.intBitsToFloat(iarg1);
        }else if (arg1Type == 1) { // sNaN
                message = message + "(sNaN, ";
                iarg1 = iarg1 | fNaNExpStart;
                farg1 = Float.intBitsToFloat(iarg1);
        }else{ // Normal number
                farg1 = r.nextFloat() * 1000000.0f + 1.0f;
                message = message + "(" + String.valueOf(farg1) + ", ";
        }

        //arg 2
        int arg2Type = r.nextInt(3);
        iarg2 = r.nextInt(fNaNMantisaMax - 1) + 1;
        if (arg2Type == 0) {
            message = message + " qNaN)";
            iarg2 = (r.nextInt(fNaNMantisaMax - 1) + 1) | fNaNExpStart | fquietBit;
            farg2 = Float.intBitsToFloat(iarg2);
        }else if (arg2Type == 1){
            message = message + " sNaN)";
            iarg2 = (r.nextInt(fNaNMantisaMax - 1) + 1) | fNaNExpStart;
            farg2 = Float.intBitsToFloat(iarg2);
        }else {
            if (arg1Type != 2) {
                farg2 = r.nextFloat() * 1000000.0f + 1f;
                message = message + String.valueOf(farg2) + ")";
                iarg2 = Float.floatToRawIntBits(farg2);
            }else{
                return null;
            }
        }
        p.first = farg1;
        p.second = farg2;
        p.bFirst = iarg1;
        p.bSecond = iarg2;
        p.message = message;
        p.expected = (arg1Type != 2 ? iarg1 : iarg2) | fquietBit;
        return p;
    }

// these 4 methods used as wrappers to ensure jit compilation
    private static  float fmax(float x, float y) {
        return Math.max(x, y);
    }

    private static  float fmin(float x, float y){
        return Math.min(x, y);
    }

    private static  double dmin(double x, double y){
        return Math.min(x, y);
    }

    private static  double dmax(double x, double y){
        return Math.max(x, y);
    }

    private static boolean isQuietNaN(float f){
        int intBits = Float.floatToRawIntBits(f);
        return (intBits & fquietBit) == fquietBit;
    }
    private static boolean isQuietNaN(double d){
        long longBits = Double.doubleToRawLongBits(d);
        return (longBits & dquietBit) == dquietBit;
    }

    private static void formatErrorFloat(SoftAssert s, String test) {
        try{
            s.assertAll();
            System.out.println("Passed " + test);
        }catch(AssertionError e){
            String[] lines = e.toString().split("\n");
            for (String line: lines) {
                Pattern p = Pattern.compile("(?<=\\[)[-]?\\d+");
                Matcher m = p.matcher(line);

                StringBuffer result = new StringBuffer();
                while (m.find()) {
                    String hex = String.format("0x%x", Long.parseLong(m.group()));
                    m.appendReplacement(result, hex);

                }
                m.appendTail(result);
                throw new AssertionError("Failed " + test + ":\n" + result.toString());    

            }

        }       
    }

    private static void formatErrorDouble(SoftAssert s, String test) {
        try{
            s.assertAll();
            System.out.println("Passed " + test);
        }catch(AssertionError e){
            String[] lines = e.toString().split("\n");
            for (String line: lines) {
                Pattern p = Pattern.compile("(?<=\\[)[-]?\\d+");
                Matcher m = p.matcher(line);

                StringBuffer result = new StringBuffer();
                while (m.find()) {
                    String hex = String.format("0x%x", Integer.parseInt(m.group()));
                    m.appendReplacement(result, hex);

                }
                m.appendTail(result);
                throw new AssertionError("Failed " + test + ":\n" + result.toString());    
            }
        }       
    }




    /**
    * Tests all execution paths defined by the {@link Math.max} methods, for float and double data types.
    */
    @Test(groups = {"level.sanity"}, invocationCount=1)
    public void test_java_lang_Math_max() {
        double count = 0;
        float countf = 0;
        Random r = new Random();
        for (int i = 0; i < 1000; i++){
            countf += fmax(r.nextFloat(), r.nextFloat());
            countf += fmin(r.nextFloat(), r.nextFloat());
            count += dmin(Math.random(), Math.random());
            count += dmax(Math.random(), Math.random());
        }
        //sanity check. If this fails we know the system converts NaNs to some canonical form automatically upon copy
        AssertJUnit.assertEquals("failed sanity check", fqNaNBits, Float.floatToRawIntBits(fqNaN));

        SoftAssert softAssert = new SoftAssert();
        for (int i = 0; i < 1000; i++){
            NaNTestPair<Float, Integer> fp = getFloatNaNPair();
            if (fp == null) continue;
            softAssert.assertEquals(Float.floatToRawIntBits(fmax(fp.first.floatValue(), fp.second.floatValue())), fp.expected.intValue(), fp.message);
        }
        formatErrorFloat(softAssert, "Float NaNs");
        softAssert = new SoftAssert();
        softAssert.assertEquals(Float.floatToRawIntBits((pfZero, nfZero)), pfZeroBits, "failed fmax(+0, -0)");
        softAssert.assertEquals(Float.floatToRawIntBits(fmax(nfZero, pfZero)), pfZeroBits, "failed fmax(-0, +0)");
        softAssert.assertEquals(Float.floatToRawIntBits(fmax(fqNaNBits, pfZero)), fqNaNBits, "failed fmax(NaN, +0)");
        softAssert.assertEquals(Float.floatToRawIntBits(fmax(pfZero, fqNaNBits)), fqNaNBits, "failed fmax(+0, NaN)");
        softAssert.assertEquals(Float.floatToRawIntBits(fmax(fqNaNBits, nfZero)), dqNaNBits, "failed fmax(NaN, -0)");
        softAssert.assertEquals(Float.floatToRawIntBits(fmax(nfZero, fqNaNBits)), dqNaNBits, "failed fmax(-0, NaN)");
        formatErrorFloat(softAssert, "+0/-0 floats");

               

        softAssert = new SoftAssert();
        for (int i = 0; i < 1000; i++){
            NaNTestPair<Double, Long> fp = getDoubleNaNPair();
            if (fp == null) continue;
            softAssert.assertEquals(Double.doubleToRawLongBits(dmax(fp.first.doubleValue(), fp.second.doubleValue())), fp.expected.longValue(), fp.message);
        }
        formatErrorDouble(softAssert, "Double NaNs");
        softAssert = new SoftAssert();
        softAssert.assertEquals(Double.doubleToRawLongBits(dmax(pdZero, ndZero)), pdZeroBits, "failed dmax(+0, -0)");
        softAssert.assertEquals(Double.doubleToRawLongBits(dmax(ndZero, pdZero)), pdZeroBits, "failed dmax(-0, +0)");
        softAssert.assertEquals(Double.doubleToRawLongBits(dmax(dqNaNBits, pdZero)), dqNaNBits, "failed dmax(NaN, +0)");
        softAssert.assertEquals(Double.doubleToRawLongBits(dmax(pdZero, dqNaNBits)), dqNaNBits, "failed dmax(+0, NaN)");
        softAssert.assertEquals(Double.doubleToRawLongBits(dmax(dqNaNBits, ndZero)), dqNaNBits, "failed dmax(NaN, -0)");
        softAssert.assertEquals(Double.doubleToRawLongBits(dmax(ndZero, dqNaNBits)), dqNaNBits, "failed dmax(-0, NaN)");
        formatErrorFloat(softAssert, "+0/-0 doubles");

        //Test Math.max with variation of random negative & positive doubles
        Random random = new Random();
        double d1 = -random.nextDouble() * 100; // ensures number is negative and within a reasonable range
        double d2 = -random.nextDouble() * 100;
        double d3 = random.nextDouble() * 100; // ensures number is positive and within a reasonable range
        double d4 = random.nextDouble() * 100;
        AssertJUnit.assertEquals(Math.max(d1, d2), (d1 > d2) ? d1 : d2, 0.0);
        AssertJUnit.assertEquals(Math.max(d2, d3), (d2 > d3) ? d2 : d3, 0.0);
        AssertJUnit.assertEquals(Math.max(d3, d4), (d3 > d4) ? d3 : d4, 0.0);
        AssertJUnit.assertEquals(Math.max(d1, d4), (d1 > d4) ? d1 : d4, 0.0);

        //Test Math.max with variation of random negative & positive floats
        float f1 = -random.nextFloat() * 100; // ensures number is negative and within a reasonable range
        float f2 = -random.nextFloat() * 100;
        float f3 = random.nextFloat() * 100; // ensures number is positive and within a reasonable range
        float f4 = random.nextFloat() * 100;
        AssertJUnit.assertEquals(Math.max(f1, f2), (f1 > f2) ? f1 : f2, 0.0f);
        AssertJUnit.assertEquals(Math.max(f2, f3), (f2 > f3) ? f2 : f3, 0.0f);
        AssertJUnit.assertEquals(Math.max(f3, f4), (f3 > f4) ? f3 : f4, 0.0f);
        AssertJUnit.assertEquals(Math.max(f1, f4), (f1 > f4) ? f1 : f4, 0.0f);
    }

    /**
    * Tests all execution paths defined by the {@link Math.min} method, for float and double data types.
    */
    @Test(groups = {"level.sanity"}, invocationCount=2)
    public void test_java_lang_Math_min() {
        double count = 0;
        float countf = 0;
        Random r = new Random();
        for (int i = 0; i < 1000; i++){
            countf += fmax(r.nextFloat(), r.nextFloat());
            countf += fmin(r.nextFloat(), r.nextFloat());
            count += dmin(Math.random(), Math.random());
            count += dmax(Math.random(), Math.random());
        }
        //sanity check. If this fails we know the system converts NaNs to some canonical form automatically upon copy
        AssertJUnit.assertEquals("failed sanity check", fqNaNBits, Float.floatToRawIntBits(fqNaN));

        SoftAssert softAssert = new SoftAssert();
        for (int i = 0; i < 1000; i++){
            NaNTestPair<Float, Integer> fp = getFloatNaNPair();
            if (fp == null) continue;
            softAssert.assertEquals(Float.floatToRawIntBits(fmin(fp.first.floatValue(), fp.second.floatValue())), fp.expected.intValue(), fp.message);
        }
        formatErrorFloat(softAssert, "Float NaNs");
        softAssert = new SoftAssert();
        softAssert.assertEquals(Float.floatToRawIntBits(fmin(pfZero, nfZero)), nfZeroBits, "failed fmin(+0, -0)");
        softAssert.assertEquals(Float.floatToRawIntBits(fmin(nfZero, pfZero)), nfZeroBits, "failed fmin(-0, +0)");
        softAssert.assertEquals(Float.floatToRawIntBits(fmin(fqNaNBits, pfZero)), fqNaNBits, "failed fmin(NaN, +0)");
        softAssert.assertEquals(Float.floatToRawIntBits(fmin(pfZero, fqNaNBits)), fqNaNBits, "failed fmin(+0, NaN)");
        softAssert.assertEquals(Float.floatToRawIntBits(fmin(fqNaNBits, nfZero)), dqNaNBits, "failed fmin(NaN, -0)");
        softAssert.assertEquals(Float.floatToRawIntBits(fmin(nfZero, fqNaNBits)), dqNaNBits, "failed fmin(-0, NaN)");
       formatErrorFloat(softAssert, "+0/-0 floats");

               

        softAssert = new SoftAssert();
        for (int i = 0; i < 1000; i++){
            NaNTestPair<Double, Long> fp = getDoubleNaNPair();
            if (fp == null) continue;
            softAssert.assertEquals(Double.doubleToRawLongBits(dmin(fp.first.doubleValue(), fp.second.doubleValue())), fp.expected.longValue(), fp.message);
        }
        formatErrorDouble(softAssert, "Double NaNs");
        softAssert = new SoftAssert();
        softAssert.assertEquals(Double.doubleToRawLongBits(dmin(pdZero, nfZero)), ndZeroBits, "failed dmin(+0, -0)");
        softAssert.assertEquals(Double.doubleToRawLongBits(dmin(nfZero, pdZero)), ndZeroBits, "failed dmin(-0, +0)");
        softAssert.assertEquals(Double.doubleToRawLongBits(dmin(fqNaNBits, pdZero)), fqNaNBits, "failed dmin(NaN, +0)");
        softAssert.assertEquals(Double.doubleToRawLongBits(dmin(pdZero, fqNaNBits)), fqNaNBits, "failed dmin(+0, NaN)");
        softAssert.assertEquals(Double.doubleToRawLongBits(dmin(fqNaNBits, nfZero)), dqNaNBits, "failed dmin(NaN, -0)");
        softAssert.assertEquals(Double.doubleToRawLongBits(dmin(nfZero, fqNaNBits)), dqNaNBits, "failed dmin(-0, NaN)");
        formatErrorFloat(softAssert, "+0/-0 doubles");

        //Test Math.min with variation of random negative & positive doubles
        Random random = new Random();
        double d1 = -random.nextDouble() * 100; // ensures number is negative and within a reasonable range
        double d2 = -random.nextDouble() * 100;
        double d3 = random.nextDouble() * 100; // ensures number is positive and within a reasonable range
        double d4 = random.nextDouble() * 100;
        AssertJUnit.assertEquals(Math.min(d1, d2), (d1 < d2) ? d1 : d2, 0.0);
        AssertJUnit.assertEquals(Math.min(d2, d3), (d2 < d3) ? d2 : d3, 0.0);
        AssertJUnit.assertEquals(Math.min(d3, d4), (d3 < d4) ? d3 : d4, 0.0);
        AssertJUnit.assertEquals(Math.min(d1, d4), (d1 < d4) ? d1 : d4, 0.0);

        //Test Math.min with variation of random negative & positive floats
        float f1 = -random.nextFloat() * 100; // ensures number is negative and within a reasonable range
        float f2 = -random.nextFloat() * 100;
        float f3 = random.nextFloat() * 100; // ensures number is positive and within a reasonable range
        float f4 = random.nextFloat() * 100;
        AssertJUnit.assertEquals(Math.min(f1, f2), (f1 < f2) ? f1 : f2, 0.0f);
        AssertJUnit.assertEquals(Math.min(f2, f3), (f2 < f3) ? f2 : f3, 0.0f);
        AssertJUnit.assertEquals(Math.min(f3, f4), (f3 < f4) ? f3 : f4, 0.0f);
        AssertJUnit.assertEquals(Math.min(f1, f4), (f1 < f4) ? f1 : f4, 0.0f);
    }
}
