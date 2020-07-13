/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.web.scripts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ignite.DataRegionMetrics;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.cluster.ClusterNode;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

/**
 * Instances of this web script preload data region metrics of all members of all grids that of which the Repository is a member.
 *
 * @author Axel Faust
 */
public class DataRegionsGet extends DeclarativeWebScript
{

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String, Object> executeImpl(final WebScriptRequest req, final Status status, final Cache cache)
    {
        final Map<String, Object> model = new HashMap<>();

        final String igniteInstanceName = req.getParameter("instance");

        final List<Ignite> grids = new ArrayList<>();
        if (igniteInstanceName != null)
        {
            final Ignite ignite = Ignition.ignite(igniteInstanceName);
            // never null but static FindBugs check cannot determine that
            if (ignite != null)
            {
                grids.add(ignite);
            }
        }
        else
        {
            grids.addAll(Ignition.allGrids());
        }

        final List<Object> gridRegionMetrics = new ArrayList<>();
        grids.forEach(grid -> {
            final Map<String, Object> gridModel = new HashMap<>();
            gridRegionMetrics.add(gridModel);

            gridModel.put("grid", grid.name());

            final List<Object> gridNodeRegionMetrics = new ArrayList<>();
            gridModel.put("gridNodeRegionMetrics", gridNodeRegionMetrics);

            final ClusterNode localNode = grid.cluster().localNode();
            final Collection<DataRegionMetrics> localDataRegionMetrics = grid.dataRegionMetrics();

            final Map<String, Object> localNodeRegionMetrics = new HashMap<>();
            localNodeRegionMetrics.put("node", localNode);
            localNodeRegionMetrics.put("dataRegionMetrics", localDataRegionMetrics);

            gridNodeRegionMetrics.add(localNodeRegionMetrics);

            final ClusterGroup remotes = grid.cluster().forRemotes();
            if (!remotes.nodes().isEmpty())
            {
                grid.compute(remotes).broadcast(() -> {
                    final Map<String, Object> remoteNodeRegionMetrics = new HashMap<>();
                    final Ignite localIgnite = Ignition.localIgnite();
                    remoteNodeRegionMetrics.put("node", localIgnite.cluster().localNode());
                    remoteNodeRegionMetrics.put("dataRegionMetrics", localIgnite.dataRegionMetrics());

                    return remoteNodeRegionMetrics;
                }).forEach(gridNodeRegionMetrics::add);
            }
        });
        model.put("gridRegionMetrics", gridRegionMetrics);

        return model;
    }

}
