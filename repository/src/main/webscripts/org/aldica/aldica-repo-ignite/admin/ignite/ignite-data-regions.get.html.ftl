<#-- This Source Code Form is subject to the terms of the Mozilla Public
   - License, v. 2.0. If a copy of the MPL was not distributed with this
   - file, You can obtain one at https://mozilla.org/MPL/2.0/. -->

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
                        <th title="${msg("ignite.regions.attr.node.title")?xml}">${msg("ignite.regions.attr.node.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.name.title")?xml}">${msg("ignite.regions.attr.name.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.pageSize.title")?xml}">${msg("ignite.regions.attr.pageSize.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.allocatedPages.title")?xml}">${msg("ignite.regions.attr.allocatedPages.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.allocatedSize.title")?xml}">${msg("ignite.regions.attr.allocatedSize.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.allocationRate.title")?xml}">${msg("ignite.regions.attr.allocationRate.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.evictionRate.title")?xml}">${msg("ignite.regions.attr.evictionRate.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.pageFillFactor.title")?xml}">${msg("ignite.regions.attr.pageFillFactor.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.usedPages.title")?xml}">${msg("ignite.regions.attr.usedPages.label")?html}</th>
                        <th title="${msg("ignite.regions.attr.usedSize.title")?xml}">${msg("ignite.regions.attr.usedSize.label")?html}</th>
                    </tr>
                </thead>
                <tbody>
                    <#list gridRegionMetrics as gridRegionMetricModel>
                        <#list gridRegionMetricModel.gridNodeRegionMetrics as gridNodeRegionMetric>
                            <#list gridNodeRegionMetric.dataRegionMetrics as regionMetrics>
                                <tr>
                                    <td>${gridRegionMetricModel.grid?html}</td>
                                    <td>${(gridNodeRegionMetric.node.consistentId()!gridNodeRegionMetric.node.id())?html}</td>
                                    <td>${regionMetrics.name?html}</td>
                                    <#if regionMetrics.pageSize != 0>
                                        <td title="${regionMetrics.pageSize?c}">${formatSize(regionMetrics.pageSize)?html}</td>
                                    <#elseif regionMetrics.totalAllocatedPages != 0>
                                        <td title="${((regionMetrics.totalAllocatedSize / regionMetrics.totalAllocatedPages / 1024)?floor * 1024)?c}">${formatSize((regionMetrics.totalAllocatedSize / regionMetrics.totalAllocatedPages / 1024)?floor * 1024)?html}</td>
                                    <#else>
                                        <td></td>
                                    </#if>
                                    <td>${regionMetrics.totalAllocatedPages?c}</td>
                                    <td title="${regionMetrics.totalAllocatedSize?c}">${formatSize(regionMetrics.totalAllocatedSize)?html}</td>
                                    <td><#if regionMetrics.pagesFillFactor != 0>${regionMetrics.allocationRate?string('0.##')}</#if></td>
                                    <td><#if regionMetrics.pagesFillFactor != 0>${regionMetrics.evictionRate?string('0.##')}</#if></td>
                                    <td><#if regionMetrics.pagesFillFactor != 0>${regionMetrics.pagesFillFactor?string('0.##')}</#if></td>
                                    <td>${regionMetrics.totalUsedPages?c}</td>
                                    <#if regionMetrics.pageSize != 0>
                                        <td title="${(regionMetrics.totalUsedPages * regionMetrics.pageSize)?c}">${formatSize(regionMetrics.totalUsedPages * regionMetrics.pageSize)?html}</td>
                                    <#elseif regionMetrics.totalAllocatedPages != 0>
                                        <td title="${(regionMetrics.totalUsedPages / regionMetrics.totalAllocatedPages * regionMetrics.totalAllocatedSize)?c}">${formatSize(regionMetrics.totalUsedPages / regionMetrics.totalAllocatedPages * regionMetrics.totalAllocatedSize)?html}</td>
                                    <#else>
                                        <td></td>
                                    </#if>
                                </tr>
                            </#list>
                        </#list>
                    </#list>
                </tbody>
            </table>
        </div>
    </div>
</@page>