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
package org.openj9.criu;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

/**
 * Standalone reproducer for the post-restore NPE at ByteArray.getUnsignedShort.
 *
 * The crash call chain from the CRIU failure is:
 *   ZoneInfoFile.load(DataInputStream)
 *     -> DataInputStream.readUTF()
 *     -> DataInputStream.readUnsignedShort()
 *     -> ByteArray.getUnsignedShort()   <-- NPE: SHORT VarHandle is null
 *
 * This test exercises the same path (DataInputStream.readUTF -> ByteArray.getUnsignedShort)
 * in a tight loop so the JIT compiles the method at -Xjit:count=0, which is
 * the condition under which the bug manifests post-restore.
 *
 * Run with:
 *   java -Xjit:count=0 org.openj9.criu.TestByteArrayVarHandleRestore
 *
 * Expected output:
 *   PASSED
 */
public class TestByteArrayVarHandleRestore {

    // A valid DataInputStream.readUTF() payload: 2-byte big-endian length
    // followed by that many UTF-8 bytes.  "TZDB" -> length 4, then 'T','Z','D','B'.
    private static final byte[] UTF_TZDB = { 0x00, 0x04, 'T', 'Z', 'D', 'B' };

    public static void main(String[] args) throws Exception {
        // Warm up: drives ByteArray.getUnsignedShort into the JIT.
        // At -Xjit:count=0 every method is compiled on first call, but we loop
        // to ensure scorching / re-compilation at higher opt levels too.
        final int ITERATIONS = 50_000;
        for (int i = 0; i < ITERATIONS; i++) {
            String s = readUTF(UTF_TZDB);
            if (!"TZDB".equals(s)) {
                System.out.println("Unexpected value: " + s);
                System.out.println("FAILED");
                System.exit(1);
            }
        }

        // One final call to confirm the compiled version still works.
        String result = readUTF(UTF_TZDB);
        if ("TZDB".equals(result)) {
            System.out.println("PASSED");
        } else {
            System.out.println("Unexpected value: " + result);
            System.out.println("FAILED");
            System.exit(1);
        }
    }

    private static String readUTF(byte[] data) throws Exception {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
            return dis.readUTF();
        }
    }
}
