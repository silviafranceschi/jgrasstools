/*
 * JGrass - Free Open Source Java GIS http://www.jgrass.org 
 * (C) HydroloGIS - www.hydrologis.com 
 * 
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Library General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the Free Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jgrasstools.gears.modules.v.featuremerger;

import java.util.List;

import oms3.annotations.Author;
import oms3.annotations.Description;
import oms3.annotations.Execute;
import oms3.annotations.In;
import oms3.annotations.Keywords;
import oms3.annotations.Label;
import oms3.annotations.License;
import oms3.annotations.Out;
import oms3.annotations.Status;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollections;
import org.jgrasstools.gears.libs.exceptions.ModelsIllegalargumentException;
import org.jgrasstools.gears.libs.modules.JGTConstants;
import org.jgrasstools.gears.libs.modules.JGTModel;
import org.jgrasstools.gears.libs.monitor.IJGTProgressMonitor;
import org.jgrasstools.gears.libs.monitor.LogProgressMonitor;
import org.opengis.feature.simple.SimpleFeatureType;

@Description("A simple module that merges same featurecollections into one single.")
@Author(name = "Andrea Antonello", contact = "www.hydrologis.com")
@Keywords("IO, Feature, Vector, Merge")
@Label(JGTConstants.VECTORPROCESSING)
@Status(Status.CERTIFIED)
@License("http://www.gnu.org/licenses/gpl-3.0.html")
public class FeatureMerger extends JGTModel {
    @Description("The input features.")
    @In
    public List<SimpleFeatureCollection> inGeodata;

    @Description("The progress monitor.")
    @In
    public IJGTProgressMonitor pm = new LogProgressMonitor();

    @Description("The output features.")
    @Out
    public SimpleFeatureCollection outGeodata;

    @Execute
    public void process() throws Exception {
        checkNull(inGeodata);

        SimpleFeatureType firstType = null;

        pm.beginTask("Merging features...", IJGTProgressMonitor.UNKNOWN);
        try {
            outGeodata = FeatureCollections.newCollection();
            for( SimpleFeatureCollection featureCollection : inGeodata ) {
                if (firstType == null) {
                    firstType = featureCollection.getSchema();
                } else {
                    SimpleFeatureType schema = featureCollection.getSchema();
                    if (!schema.equals(firstType)) {
                        throw new ModelsIllegalargumentException("Merging is done only on same feature types.", this);
                    }
                }
                outGeodata.addAll(featureCollection);
            }
        } finally {
            pm.done();
        }
    }
}
