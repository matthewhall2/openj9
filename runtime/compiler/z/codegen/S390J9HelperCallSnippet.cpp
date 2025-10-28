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
   
   // LGR R1 R7
   *(int32_t *)cursor = 0xB9040017;
   cursor += sizeof(int32_t);

   return emitSnippetBodyHelper(cursor, helperSymRef);
}