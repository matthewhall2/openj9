/*******************************************************************************
 * Copyright IBM Corp. and others 2018
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

#ifndef JITSERVER_IPROFILER_HPP
#define JITSERVER_IPROFILER_HPP

#include "control/JITServerHelpers.hpp"
#include "runtime/IProfiler.hpp"

namespace JITServer
{
class ClientStream;
}
namespace TR
{
class CompilationInfoPerThreadRemote;
}
class ClientSessionData;
struct TR_ContiguousIPMethodData
   {
   TR_OpaqueMethodBlock *_method;
   uint32_t _pcIndex;
   uint32_t _weight;
   };

// The following structure is used by JITServer to keep a summary of the fanin info.
// We don't need to track the identity of the callee because this info will be stored
// into TR_ResolvedJ9JITServerMethod which already includes the j9method for the callee.
struct TR_FaninSummaryInfo
   {
   uint64_t _totalSamples; // Number of samples across all callers
   uint64_t _samplesOther; // Number of samples received by callers that could not be tracked individually
   uint32_t _numCallers;   // Number of distinct callers that received samples. Capped at MAX_IPMETHOD_CALLERS
   };

struct TR_ContiguousIPMethodHashTableEntry
   {
   static std::string serialize(const TR_IPMethodHashTableEntry *entry);

   TR_OpaqueMethodBlock *_method; // callee
   size_t _callerCount;
   uint64_t _totalSamples; // This includes the samples in the _otherBucket
   TR_ContiguousIPMethodData _callers[TR_IPMethodHashTableEntry::MAX_IPMETHOD_CALLERS]; // array of callers and their weights. null _method means EOL
   TR_DummyBucket _otherBucket;
   };

/**
 * @class JITServerIProfiler
 * @brief Class for manipulating IProfiler data at the server side
 *
 * This class is an extension of the TR_IProfiler class so that the server
 * can intercept IProfiler requests from the JIT compiler and redirect them
 * to the client. For this purpose a few methods from TR_IProfiler class
 * have been overridden.
 * To reduce the communication between server and client the JITServer
 * implements a caching mechanism while the client uses message aggregation,
 * where, instead of sending only the requested information for the bytecode
 * of interest, it will send the IProfile information for the entire method.
 * There are two levels of caches: methods that have been already compiled
 * cannot accumulate more IProfile information, so they can be cached globally.
 * For methods that are still interpreted, the IProfile information is still
 * growing, so the server will cache this information only for the duration of
 * the current compilation. The idea is that new information which is accumulated
 * during the short time span of the current compilation is not going to affect
 * optimizer decisions too much. For the same reason, IProfile information
 * for the method currently being compiled is cached globally.
 * The per-compilation cache is stored in a `CompilationInfoPerThreadRemote`
 * object while the global IProfile cache is stored in `struct J9MethodInfo`
 * which is part of `ClientSessionData` (thus, the global cache is more like
 * a collection of caches, one for each method).
 * As a RAS feature, caching can be disabled if the environment variable
 * TR_DisableIPCaching is set.
 * Another RAS feature is the validation of the cached data. For a build with
 * assumes enabled (or for a debug build) every time the server uses a cached
 * value it will send a message to the client and compare the cached value to
 * the value retrieved from the client. An warning message will be printed to
 * stderr in case of a missmatch.
 */

class JITServerIProfiler : public TR_IProfiler
   {
public:
   static JITServerIProfiler * allocate(J9JITConfig *jitConfig);
   JITServerIProfiler(J9JITConfig *);

   // Data accessors, overridden for JITServer
   //
   virtual TR_IPMethodHashTableEntry *searchForMethodSample(TR_OpaqueMethodBlock *omb, int32_t bucket) override;

   // This method is used to search only the hash table
   virtual TR_IPBytecodeHashTableEntry *profilingSample (uintptr_t pc, uintptr_t data, bool addIt, bool isRIData = false, uint32_t freq  = 1) override;
   // This method is used to search the hash table first, then the shared cache
   virtual TR_IPBytecodeHashTableEntry *profilingSample (TR_OpaqueMethodBlock *method, uint32_t byteCodeIndex,
                                                         TR::Compilation *comp, uintptr_t data = 0xDEADF00D, bool addIt = false) override;

   virtual void persistIprofileInfo(TR::ResolvedMethodSymbol *methodSymbol, TR_ResolvedMethod *method, TR::Compilation *comp) override;

   static TR_IPBytecodeHashTableEntry *ipBytecodeHashTableEntryFactory(TR_IPBCDataStorageHeader *storage, uintptr_t pc, TR_Memory* mem, TR_AllocationKind allocKind);
   // This is used for fanin data
   TR_FaninSummaryInfo *cacheFaninDataForMethod(TR_OpaqueMethodBlock *method, const std::string &clientFaninData, TR_FrontEnd *fe, TR_Memory *trMemory);
   TR_FaninSummaryInfo *deserializeFaninMethodEntry(const TR_ContiguousIPMethodHashTableEntry *serialEntry, TR_Memory *trMemory);
   // This is used for bytecode profile data
   static void deserializeIProfilerData(J9Method *method, const std::string &ipdata, Vector<TR_IPBytecodeHashTableEntry *> &ipEntries, TR_Memory *trMemory, bool cgEntriesOnly = false);
   void printStats();

protected:
   virtual bool invalidateEntryIfInconsistent(TR_IPBytecodeHashTableEntry *entry) override;

private:
   void validateCachedIPEntry(TR_IPBytecodeHashTableEntry *entry, TR_IPBCDataStorageHeader *clientData, uintptr_t methodStart, bool isMethodBeingCompiled, TR_OpaqueMethodBlock *method, bool fromPerCompilationCache, bool isCompiledWhenProfiling);
   bool cacheProfilingDataForMethod(TR_OpaqueMethodBlock *method, const std::string &ipdata, bool usePersistentCache, ClientSessionData *clientSessionData,
                                    TR::CompilationInfoPerThreadRemote *compInfoPT, bool isCompiled, TR::Compilation *comp);
   bool _useCaching;
   // Statistics
   uint32_t _statsIProfilerInfoFromCache;  // IP cache answered the query
   uint32_t _statsIProfilerInfoMsgToClient; // queries sent to client
   uint32_t _statsIProfilerInfoReqNotCacheable; // info returned from client should not be cached
   uint32_t _statsIProfilerInfoIsEmpty; // client has no IP info for indicated PC
   uint32_t _statsIProfilerInfoCachingFailures;
   };

/**
 * @class JITClientIProfiler
 * @brief Class for manipulating IProfiler data at the client side
 *
 * This class is an extension of the TR_IProfiler and has the ability of
 * serializing the entire IProfile data for a given method and send it to
 * server.
 */

class JITClientIProfiler : public TR_IProfiler
   {
public:

   static JITClientIProfiler * allocate(J9JITConfig *jitConfig);
   JITClientIProfiler(J9JITConfig *);
   // NOTE: since the JITClient can act as a regular JVM compiling methods
   // locally, we must not change the behavior of functions used in TR_IProfiler
   // Thus, any virtual function here must call the corresponding method in
   // the base class. It may be better not to override any methods though

   bool serializeAndSendIProfileInfoForMethod(TR_OpaqueMethodBlock*method, TR::Compilation *comp, JITServer::ClientStream *client,
                                              bool usePersistentCache, bool isCompiled, bool sharedProfile);
   std::string serializeFaninMethodEntry(TR_OpaqueMethodBlock *omb);
   void gatherUncachedClassesUsedInCGEntry(TR_IPBCDataCallGraph *cgEntry, TR::Compilation *comp,
                                           std::vector<J9Class *> &uncachedClasses,
                                           std::vector<JITServerHelpers::ClassInfoTuple> &classInfos);
private:
   uint32_t walkILTreeForIProfilingEntries(uintptr_t *pcEntries, uint32_t &numEntries, TR_J9ByteCodeIterator *bcIterator,
                                           TR_OpaqueMethodBlock *method, TR_BitVector *BCvisit, bool &abort, TR::Compilation *comp);
   uintptr_t serializeIProfilerMethodEntries(const uintptr_t *pcEntries, uint32_t numEntries,
                                             uintptr_t memChunk, uintptr_t methodStartAddress,
                                             TR::Compilation *comp, bool sharedProfile, uint64_t &totalSamples,
                                             std::vector<J9Class *> &uncachedClasses,
                                             std::vector<JITServerHelpers::ClassInfoTuple> &classInfos);
   };

#endif
