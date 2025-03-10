package org.openj9.test.jsr335.defendersupersends.asm;

public class TestRunner {
        public static void main(String[] args) throws Throwable
        {
        System.out.println("Start");
        System.out.println("Test");
       // try {
        TestDefenderMethodLookupAsm test = new TestDefenderMethodLookupAsm();
        System.out.println(test);
        test.testConflictingDefinitionsInSuperInterface();
        test.testConflictingDefinitionsInSuperInterface();

//      test.test335DefenderSupersendsAsmAsMethodHandles();
//      test.test335DefenderSupersendsAsmAsMethodHandles();
        // } catch (Throwable t) {
        //         System.out.println("error");
        // }
}
}