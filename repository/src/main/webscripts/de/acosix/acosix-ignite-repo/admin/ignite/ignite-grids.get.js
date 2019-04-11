<import resource="classpath:alfresco/templates/webscripts/org/alfresco/repository/admin/admin-common.lib.js">
<import resource="classpath:alfresco/templates/webscripts/de/acosix/acosix-ignite-repo/admin/ignite/ignite.lib.js">

/*
 * Copyright 2016 - 2019 Acosix GmbH
 */

buildInstances();

model.tools = Admin.getConsoleTools("ignite-grids");
model.metadata = Admin.getServerMetaData();