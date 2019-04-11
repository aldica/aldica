<#-- This Source Code Form is subject to the terms of the Mozilla Public
   - License, v. 2.0. If a copy of the MPL was not distributed with this
   - file, You can obtain one at https://mozilla.org/MPL/2.0/. -->

<#include "/org/orderofthebee/support-tools/admin/admin-template.ftl" />

<#function formatSize size>
    <#local result=size?c />
        <#if size &gt;= (1024 * 1024 * 1024 * 1024)>
            <#local result = (size/1024/1024/1024/1024)?string('0.##') + " " + msg("ignite.grids.unit.TiB") />
        <#elseif size &gt;= (1024 * 1024 * 1024)>
            <#local result = (size/1024/1024/1024)?string('0.##') + " " + msg("ignite.grids.unit.GiB") />
        <#elseif size &gt;= (1024 * 1024)>
            <#local result = (size/1024/1024)?string('0.##') + " " + msg("ignite.grids.unit.MiB") />
        <#elseif size &gt;= (1024)>
            <#local result = (size/1024)?string('0.##') + " " + msg("ignite.grids.unit.KiB") />
        </#if>
    <#return result />
</#function>

<@page title=msg("ignite.grids.title") readonly=true>

    <div class="column-full">
        <p class="intro">${msg("ignite.grids.intro")?html}</p>      
  
        <div class="control">
            <table class="results data grids" width="100%">
                <thead>
                    <tr>
                        <th title="${msg("ignite.grids.attr.name.title")?xml}">${msg("ignite.grids.attr.name.label")?html}</th>
                        <th title="${msg("ignite.grids.attr.topologyVersion.title")?xml}">${msg("ignite.grids.attr.topologyVersion.label")?html}</th>
                        <th title="${msg("ignite.grids.attr.noGridNodes.title")?xml}">${msg("ignite.grids.attr.noGridNodes.label")?html}</th>
                        <th title="${msg("ignite.grids.attr.noCPUs.title")?xml}">${msg("ignite.grids.attr.noCPUs.label")?html}</th>
                        <th title="${msg("ignite.grids.attr.heapMemory.title")?xml}">${msg("ignite.grids.attr.heapMemory.label")?html}</th>
                        <th title="${msg("ignite.grids.attr.nonHeapMemory.title")?xml}">${msg("ignite.grids.attr.nonHeapMemory.label")?html}</th>
                        <th title="${msg("ignite.grids.attr.noCaches.title")?xml}">${msg("ignite.grids.attr.noCaches.label")?html}</th>
                    </tr>
                </thead>
                <tbody>
                    <#list instanceInfos as instanceInfo>
                        <tr>
                            <td>${instanceInfo.name?html}</td>
                            <td>${instanceInfo.topologyVersion?c}</td>
                            <td>${instanceInfo.numberOfGridNodes?c}</td>
                            <td>${instanceInfo.numberOfCPUs?c}</td>
                            <td>${formatSize(instanceInfo.heapMemory)?html}</td>
                            <td>${formatSize(instanceInfo.nonHeapMemory)?html}</td>
                            <td>${instanceInfo.numberOfCaches?c}</td>
                        </tr>
                    </#list>
                </tbody>
            </table>
        </div>
    </div>
</@page>