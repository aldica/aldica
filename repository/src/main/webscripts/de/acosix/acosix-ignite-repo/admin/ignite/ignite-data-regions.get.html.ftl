<#-- Copyright 2016 - 2019 Acosix GmbH -->

<#include "/org/orderofthebee/support-tools/admin/admin-template.ftl" />

<#function formatSize size>
    <#local result=size?c />
        <#if size &gt;= (1024 * 1024 * 1024 * 1024)>
            <#local result = (size/1024/1024/1024/1024)?string('0.##') + " " + msg("ignite.regions.unit.TiB") />
        <#elseif size &gt;= (1024 * 1024 * 1024)>
            <#local result = (size/1024/1024/1024)?string('0.##') + " " + msg("ignite.regions.unit.GiB") />
        <#elseif size &gt;= (1024 * 1024)>
            <#local result = (size/1024/1024)?string('0.##') + " " + msg("ignite.regions.unit.MiB") />
        <#elseif size &gt;= (1024)>
            <#local result = (size/1024)?string('0.##') + " " + msg("ignite.regions.unit.KiB") />
        </#if>
    <#return result />
</#function>

<@page title=msg("ignite.regions.title") readonly=true>

    <div class="column-full">
        <p class="intro">${msg("ignite.regions.intro")?html}</p>      
  
        <div class="control">
            <table class="results data grids" width="100%">
                <thead>
                    <tr>
                        <th title="${msg("ignite.regions.attr.grid.title")?xml}">${msg("ignite.regions.attr.grid.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.name.title")?xml}">${msg("ignite.regions.attr.name.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.pageSize.title")?xml}">${msg("ignite.regions.attr.pageSize.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.allocatedPages.title")?xml}">${msg("ignite.regions.attr.allocatedPages.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.allocatedSize.title")?xml}">${msg("ignite.regions.attr.allocatedSize.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.allocationRate.title")?xml}">${msg("ignite.regions.attr.allocationRate.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.evictionRate.title")?xml}">${msg("ignite.regions.attr.evictionRate.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.pageFillFactor.title")?xml}">${msg("ignite.regions.attr.pageFillFactor.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.physicalMemoryPages.title")?xml}">${msg("ignite.regions.attr.physicalMemoryPages.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.physicalMemorySize.title")?xml}">${msg("ignite.regions.attr.physicalMemorySize.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.offHeapSize.title")?xml}">${msg("ignite.regions.attr.offHeapSize.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.offHeapUsedSize.title")?xml}">${msg("ignite.regions.attr.offHeapUsedSize.label")?html}</th>
                    </tr>
                </thead>
                <tbody>
                    <#list regionInfos as regionInfo>
                        <tr>
                            <td>${regionInfo.grid?html}</td>
                            <td>${regionInfo.name?html}</td>
                            <td title="${regionInfo.metrics.pageSize?c}">${formatSize(regionInfo.metrics.pageSize)?html}</td>
                            <td>${regionInfo.metrics.totalAllocatedPages?c}</td>
                            <td title="${regionInfo.metrics.totalAllocatedSize?c}">${formatSize(regionInfo.metrics.totalAllocatedSize)?html}</td>
                            <td>${regionInfo.metrics.allocationRate?string('0.##')}</td>
                            <td>${regionInfo.metrics.evictionRate?string('0.##')}</td>
                            <td>${regionInfo.metrics.pagesFillFactor?string('0.##')}</td>
                            <td>${regionInfo.metrics.physicalMemoryPages?c}</td>
                            <td title="${regionInfo.metrics.physicalMemorySize?c}">${formatSize(regionInfo.metrics.physicalMemorySize)?html}</td>
                            <td title="${regionInfo.metrics.offHeapSize?c}">${formatSize(regionInfo.metrics.offHeapSize)?html}</td>
                            <td title="${regionInfo.metrics.offheapUsedSize?c}">${formatSize(regionInfo.metrics.offheapUsedSize)?html}</td>
                        </tr>
                    </#list>
                </tbody>
            </table>
        </div>
    </div>
</@page>