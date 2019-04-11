/*
 * Copyright 2016 - 2019 Acosix GmbH
 */

/* global Admin: false, $: false*/

/**
 * Ignite Caches Component
 */
var AdminICA = AdminICA || {};

/* Page load handler */
Admin.addEventListener(window, 'load', function()
{
    AdminICA.setupTables();
});

(function()
{
    AdminICA.setupTables = function()
    {
        var dataTableConfig;

        dataTableConfig = {
            paging : false,
            searching : false,
            autoWidth : false
        };

        $('#caches-table').DataTable(dataTableConfig);
    };

})();
