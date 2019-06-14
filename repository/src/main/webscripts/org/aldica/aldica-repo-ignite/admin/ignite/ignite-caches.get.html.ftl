<#-- This Source Code Form is subject to the terms of the Mozilla Public
   - License, v. 2.0. If a copy of the MPL was not distributed with this
   - file, You can obtain one at https://mozilla.org/MPL/2.0/. -->

<#include "/org/orderofthebee/support-tools/admin/admin-template.ftl" />

<#function formatSize size>
    <#local result=size?c />
        <#if size &gt;= (1024 * 1024 * 1024 * 1024)>
            <#local result = (size/1024/1024/1024/1024)?string('0.#') + " " + msg("ignite.caches.unit.TiB") />
        <#elseif size &gt;= (1024 * 1024 * 1024)>
            <#local result = (size/1024/1024/1024)?string('0.#') + " " + msg("ignite.caches.unit.GiB") />
        <#elseif size &gt;= (1024 * 1024)>
            <#local result = (size/1024/1024)?string('0.#') + " " + msg("ignite.caches.unit.MiB") />
        <#elseif size &gt;= (1024)>
            <#local result = (size/1024)?string('0.#') + " " + msg("ignite.caches.unit.KiB") />
        </#if>
    <#return result />
</#function>

<@page title=msg("ignite.caches.title") readonly=true customCSSFiles=["ootbee-support-tools/css/jquery.dataTables.css", "aldica/ignite-admin-console.css"]
    customJSFiles=["ootbee-support-tools/js/jquery-2.2.3.js", "ootbee-support-tools/js/jquery.dataTables.js", "aldica/ignite-admin-caches.js"]>

    <div class="column-full">
        <p class="intro">${msg("ignite.caches.intro")?html}</p>      

        <div class="control">
            <table id="caches-table" class="results data grids" width="100%">
                <thead>
                    <tr>
                        <th title="${msg("ignite.caches.attr.grid.title")?xml}">${msg("ignite.caches.attr.grid.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.cache.title")?xml}">${msg("ignite.caches.attr.cache.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.type.alfresco.title")?xml}">${msg("ignite.caches.attr.type.alfresco.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.type.ignite.title")?xml}">${msg("ignite.caches.attr.type.ignite.label")?html}</th>

                        <#-- <th title="${msg("ignite.caches.attr.size.title")?xml}">${msg("ignite.caches.attr.size.label")?html}</th> -->
                        <th title="${msg("ignite.caches.attr.onHeapSize.title")?xml}">${msg("ignite.caches.attr.onHeapSize.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.offHeapSize.title")?xml}">${msg("ignite.caches.attr.offHeapSize.label")?html}</th>
                        <#-- memory metrics currently not provided by Ignite metrics (e.g. see IgniteCacheOffheapManagerImpl#offHeapAllocatedSize()) -->
                        <#-- TODO Need more efficient / better method to display onHeap vs offHeap details in available screen space -->
                        <#--
                        <th title="${msg("ignite.caches.attr.onHeapMemory.title")?xml}">${msg("ignite.caches.attr.onHeapMemory.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.onHeapSizeLimit.title")?xml}">${msg("ignite.caches.attr.onHeapSizeLimit.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.onHeapMemoryLimit.title")?xml}">${msg("ignite.caches.attr.onHeapMemoryLimit.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.offHeapMemory.title")?xml}">${msg("ignite.caches.attr.offHeapMemory.label")?html}</th>
                        -->
                        <th title="${msg("ignite.caches.attr.cacheGets.title")?xml}">${msg("ignite.caches.attr.cacheGets.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.averageGetTime.title")?xml}">${msg("ignite.caches.attr.averageGetTime.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.cacheHits.title")?xml}">${msg("ignite.caches.attr.cacheHits.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.cacheHitPercentage.title")?xml}">${msg("ignite.caches.attr.cacheHitPercentage.label")?html}</th>

                        <th title="${msg("ignite.caches.attr.cachePuts.title")?xml}">${msg("ignite.caches.attr.cachePuts.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.averagePutTime.title")?xml}">${msg("ignite.caches.attr.averagePutTime.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.cacheRemovals.title")?xml}">${msg("ignite.caches.attr.cacheRemovals.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.averageRemoveTime.title")?xml}">${msg("ignite.caches.attr.averageRemoveTime.label")?html}</th>
                        <th title="${msg("ignite.caches.attr.cacheEvictions.title")?xml}">${msg("ignite.caches.attr.cacheEvictions.label")?html}</th>
                    </tr>
                </thead>
                <tbody>
                    <#list cacheInfos as cacheInfo>
                        <tr>
                            <td>${cacheInfo.grid?html}</td>
                            <#-- remove common suffixes -->
                            <td title="${cacheInfo.name?xml}">${cacheInfo.name?replace("(Shared)?Cache$", "", "r")?html}</td>
                            <td>${cacheInfo.definedType?html}</td>
                            <td>${cacheInfo.type?html}</td>

                            <#-- <td class="numericalCellValue">
                                ${cacheInfo.metrics.cacheSize?c}
                            </td> -->
                            <td class="numericalCellValue">
                                <#if cacheInfo.metrics.heapEntriesCount &gt; 0>${cacheInfo.metrics.heapEntriesCount?c}</#if>
                            </td>
                            <td class="numericalCellValue">
                                <#if cacheInfo.metrics.offHeapEntriesCount &gt; 0>${cacheInfo.metrics.offHeapEntriesCount?c}</#if>
                            </td>
                            <#-- memory metrics currently not provided by Ignite metrics (e.g. see IgniteCacheOffheapManagerImpl#offHeapAllocatedSize()) -->
                            <#-- TODO Need more efficient / better method to display onHeap vs offHeap details in available screen space -->
                            <#--
                            <td class="numericalCellValue">
                                <#if cacheInfo.evictionPolicy?? && cacheInfo.evictionPolicy.currentMemorySize??>${formatSize(cacheInfo.evictionPolicy.currentMemorySize)?html}</#if>
                            </td>
                            <td class="numericalCellValue">
                                <#if cacheInfo.evictionPolicy?? && cacheInfo.evictionPolicy.maxSize?? && cacheInfo.evictionPolicy.maxSize &gt;= 0>${cacheInfo.evictionPolicy.maxSize?c}</#if>
                            </td>
                            <td class="numericalCellValue">
                                <#if cacheInfo.evictionPolicy?? && cacheInfo.evictionPolicy.maxMemorySize?? && cacheInfo.evictionPolicy.maxMemorySize &gt; 0>${formatSize(cacheInfo.evictionPolicy.maxMemorySize)?html}</#if>
                            </td>
                            <td class="numericalCellValue">
                                <#if cacheInfo.metrics.offHeapAllocatedSize &gt; 0>${formatSize(cacheInfo.metrics.offHeapAllocatedSize)}</#if>
                            </td>
                            -->

                            <td class="numericalCellValue">
                                ${cacheInfo.metrics.cacheGets?c}
                            </td>
                            <td class="numericalCellValue">
                                <#if cacheInfo.metrics.cacheGets != 0>${cacheInfo.metrics.averageGetTime?string["0"]}</#if>
                            </td>
                            <td class="numericalCellValue">
                                <#if cacheInfo.metrics.cacheGets != 0>${cacheInfo.metrics.cacheHits?c}</#if>
                            </td>
                            <td class="numericalCellValue">
                                <#if cacheInfo.metrics.cacheGets != 0>${cacheInfo.metrics.cacheHitPercentage?string["0.#"]}</#if>
                            </td>

                            <td class="numericalCellValue">
                                ${cacheInfo.metrics.cachePuts?c}
                            </td>
                            <td class="numericalCellValue">
                                <#if cacheInfo.metrics.cachePuts != 0>${cacheInfo.metrics.averagePutTime?string["0"]}</#if>
                            </td>
                            <td class="numericalCellValue">
                                ${cacheInfo.metrics.cacheRemovals?c}
                            </td>
                            <td class="numericalCellValue">
                                <#if cacheInfo.metrics.cacheRemovals != 0>${cacheInfo.metrics.averageRemoveTime?string["0"]}</#if>
                            </td>
                            <#--  this value doesn't seem to make sense (often) -->
                            <td class="numericalCellValue">
                                ${cacheInfo.metrics.cacheEvictions?c}
                            </td>
                        </tr>
                    </#list>
                </tbody>
            </table>
        </div>
    </div>
</@page>