/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

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
