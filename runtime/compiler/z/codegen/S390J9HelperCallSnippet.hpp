#ifndef TR_S390J9HELPERCALLSNIPPET_INCL
#define TR_S390J9HELPERCALLSNIPPET_INCL

#include "z/codegen/S390HelperCallSnippet.hpp"
#include "z/codegen/ConstantDataSnippet.hpp"
#include "z/codegen/S390Instruction.hpp" 

namespace TR { class CodeGenerator; }
namespace TR { class LabelSymbol; }
namespace TR { class Node; }

namespace TR {

class S390J9HelperCallSnippet : public TR::S390HelperCallSnippet
   {

   public:
      virtual uint8_t *emitSnippetBody();
   };
}

#endif