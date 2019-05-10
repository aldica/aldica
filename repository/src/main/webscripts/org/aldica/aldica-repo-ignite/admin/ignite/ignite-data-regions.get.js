<import resource="classpath:alfresco/templates/webscripts/org/alfresco/repository/admin/admin-common.lib.js">
<import resource="classpath:alfresco/templates/webscripts/org/aldica/aldica-repo-ignite/admin/ignite/ignite.lib.js">

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

buildRegions();

model.tools = Admin.getConsoleTools("ignite-data-regions");
model.metadata = Admin.getServerMetaData();