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
        S390J9HelperCallSnippet(TR::CodeGenerator *cg, TR::Node *node, TR::LabelSymbol *snippetlab,
        TR::SymbolReference *helper, TR::LabelSymbol *restartlab = NULL, int32_t s = 0)
        : TR::S390HelperCallSnippet(cg, node, snippetlab, helper, restartlab, s) {}
        virtual uint8_t *emitSnippetBody();
   };
}

#endif