<#-- This Source Code Form is subject to the terms of the Mozilla Public
   - License, v. 2.0. If a copy of the MPL was not distributed with this
   - file, You can obtain one at https://mozilla.org/MPL/2.0/. --><#compress>
<#setting locale="en"><#-- need to make sure we use this locale for proper number formatting -->
<#escape x as jsonUtils.encodeJSONString(x)>
{
    "caches" : [
        <#list cacheInfos as cacheInfo>
        {
            "grid" : "${cacheInfo.grid}",
            "name" : "${cacheInfo.name}",
            "configuredType" : "${cacheInfo.definedType}",
            "effectivetype" : "${cacheInfo.type}",
            "entryCount" : ${cacheInfo.metrics.size?c},
            "heapEntryCount" : ${cacheInfo.metrics.heapEntriesCount?c},
            <#if cacheInfo.evictionPolicy?? && cacheInfo.evictionPolicy.maxSize?? && cacheInfo.evictionPolicy.maxSize &gt; 0>"heapEntryCountLimit" : ${cacheInfo.evictionPolicy.maxSize?c},</#if>
            <#if cacheInfo.evictionPolicy?? && cacheInfo.evictionPolicy.currentMemorySize??>"usedHeapMemory" : ${cacheInfo.evictionPolicy.currentMemorySize?c},</#if>
            <#if cacheInfo.evictionPolicy?? && cacheInfo.evictionPolicy.maxMemorySize?? && cacheInfo.evictionPolicy.maxMemorySize &gt; 0>"heapMemoryLimit" : ${cacheInfo.evictionPolicy.maxMemorySize?c},</#if>
            "offHeapEntryCount" : ${cacheInfo.metrics.offHeapEntriesCount?c},
            <#-- memory metrics currently not provided by Ignite metrics (e.g. see IgniteCacheOffheapManagerImpl#offHeapAllocatedSize()) -->
            <#--
            "allocatedOffHeapMemory" : ${cacheInfo.metrics.offHeapAllocatedSize?c},
            -->
            "gets" : ${cacheInfo.metrics.cacheGets?c},
            "avgGetMircos" : ${cacheInfo.metrics.averageGetTime?string["0.#"]},
            "hits" : ${cacheInfo.metrics.cacheHits?c},
            "hitPercentage" : <#if cacheInfo.metrics.cacheGets != 0>${cacheInfo.metrics.cacheHitPercentage?string["0.#"]}<#else>100</#if>,
            "misses" : ${cacheInfo.metrics.cacheMisses?c},
            "missPercentage" : <#if cacheInfo.metrics.cacheGets != 0>${cacheInfo.metrics.cacheMissPercentage?string["0.#"]}<#else>0</#if>,
            "puts" : ${cacheInfo.metrics.cachePuts?c},
            "avgPutMicros" : ${cacheInfo.metrics.averagePutTime?string["0.#"]},
            "removals" : ${cacheInfo.metrics.cacheRemovals?c},
            "avgRemoveMicros" : ${cacheInfo.metrics.averageRemoveTime?string["0.#"]},
            "evictions" : ${cacheInfo.metrics.cacheEvictions?c}
        }<#if cacheInfo_has_next>,</#if>
        </#list>
    ]
}
</#escape></#compress>