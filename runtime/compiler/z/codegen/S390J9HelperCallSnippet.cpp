#include "z/codegen/S390J9HelperCallSnippet.hpp"
#include "z/codegen/CallSnippet.hpp"
#include "z/codegen/S390J9CallSnippet.hpp"


uint8_t *
TR::S390J9HelperCallSnippet::emitSnippetBody() {
   uint8_t *cursor = cg()->getBinaryBufferCursor();
   getSnippetLabel()->setCodeLocation(cursor);

   TR::Node *callNode = getNode();
   TR::SymbolReference *helperSymRef = getHelperSymRef();
   bool jitInduceOSR = helperSymRef->isOSRInductionHelper();
   bool isJitDispatchJ9Method = callNode->isJitDispatchJ9MethodCall(cg()->comp());
   if (jitInduceOSR || isJitDispatchJ9Method) {
      // Flush in-register arguments back to the stack for interpreter
      cursor = TR::S390J9CallSnippet::S390flushArgumentsToStack(cursor, callNode, getSizeOfArguments(), cg());
   }

   /* The J9Method pointer to be passed to be interpreter is in R7, but the interpreter expects it to be in R1.
    * Since the first integer argument register is also R1, we only load it after we have flushed the args to the stack.
    *
    * 64 bit:
    *    LGR R1, R7
    * 32 bit:
    *    LR R1, R7
    */
   if (getNode()->isJitDispatchJ9MethodCall(comp()))
      {
      if (comp()->target().is64Bit())
         {
         *(int32_t *)cursor = 0xB9040017;
         cursor += sizeof(int32_t);
         }
      else
         {
         *(int16_t *)cursor = 0x1817;
         cursor += sizeof(int16_t);
         }
      }

   return emitSnippetBodyHelper(cursor, helperSymRef);
}