/*
 * This file is part of JGrasstools (http://www.jgrasstools.org)
 * (C) HydroloGIS - www.hydrologis.com 
 * 
 * JGrasstools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jgrasstools.gears.modules.r.tmsgenerator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import oms3.annotations.Author;
import oms3.annotations.Description;
import oms3.annotations.Execute;
import oms3.annotations.In;
import oms3.annotations.Keywords;
import oms3.annotations.Label;
import oms3.annotations.License;
import oms3.annotations.Name;
import oms3.annotations.Status;
import oms3.annotations.UI;

import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.jgrasstools.gears.libs.exceptions.ModelsIOException;
import org.jgrasstools.gears.libs.modules.JGTConstants;
import org.jgrasstools.gears.libs.modules.JGTModel;
import org.jgrasstools.gears.libs.monitor.IJGTProgressMonitor;
import org.jgrasstools.gears.libs.monitor.LogProgressMonitor;
import org.jgrasstools.gears.utils.files.FileUtilities;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

@Description("Module for the downloading of map tiles.")
@Author(name = "Andrea Antonello", contact = "http://www.hydrologis.com")
@Keywords("Raster, Vector, TMS, Tiles")
@Label(JGTConstants.RASTERPROCESSING)
@Status(Status.DRAFT)
@UI(JGTConstants.HIDE_UI_HINT)
@Name("tmsdownloader")
@License("General Public License Version 3 (GPLv3)")
@SuppressWarnings("nls")
public class OnlineTilesDownloader extends JGTModel {

    @Description("An optional online tile service to include (XXX, YYY, ZZZ will be substituted by tile indexes and zoom level).")
    @In
    public String inServiceUrl = null;

    @Description("The type of tile source (0 = TMS, 1 = google).")
    @In
    public int pType = 0;

    @Description("A name of the tile source.")
    @In
    public String pName = "tmstiles";

    @Description("The min zoom for which to generate tiles.")
    @In
    public Integer pMinzoom = null;

    @Description("The max zoom for which to generate tiles.")
    @In
    public Integer pMaxzoom = null;

    @Description("The north bound of the region to consider.")
    @UI(JGTConstants.PROCESS_NORTH_UI_HINT)
    @In
    public Double pNorth = null;

    @Description("The south bound of the region to consider.")
    @UI(JGTConstants.PROCESS_SOUTH_UI_HINT)
    @In
    public Double pSouth = null;

    @Description("The west bound of the region to consider.")
    @UI(JGTConstants.PROCESS_WEST_UI_HINT)
    @In
    public Double pWest = null;

    @Description("The east bound of the region to consider.")
    @UI(JGTConstants.PROCESS_EAST_UI_HINT)
    @In
    public Double pEast = null;

    @Description("The coordinate reference system of the bound coordinates (ex. EPSG:4328).")
    @UI(JGTConstants.CRS_UI_HINT)
    @In
    public String pEpsg;

    @Description("Switch that set to true allows for some error due to different datums. If set to false, it won't reproject without Bursa Wolf parameters.")
    @In
    public boolean doLenient = true;

    @Description("The progress monitor.")
    @In
    public IJGTProgressMonitor pm = new LogProgressMonitor();

    @Description("The folder inside which to create the tiles.")
    @In
    public String inPath;

    private boolean doDryrun = false;

    private static final String EPSG_MERCATOR = "EPSG:3857";
    private static final String EPSG_LATLONG = "EPSG:4326";

    @Execute
    public void process() throws Exception {
        checkNull(inPath, inServiceUrl, pEpsg, pMinzoom, pMaxzoom, pWest, pEast, pSouth, pNorth);

        CoordinateReferenceSystem boundsCrs = CRS.decode(pEpsg);
        CoordinateReferenceSystem mercatorCrs = CRS.decode(EPSG_MERCATOR);
        CoordinateReferenceSystem latLongCrs = CRS.decode(EPSG_LATLONG);

        ReferencedEnvelope inBounds = new ReferencedEnvelope(pWest, pEast, pSouth, pNorth, boundsCrs);

        MathTransform in2MercatorTransform = CRS.findMathTransform(boundsCrs, mercatorCrs);
        Envelope mercatorEnvelope = JTS.transform(inBounds, in2MercatorTransform);
        ReferencedEnvelope mercatorBounds = new ReferencedEnvelope(mercatorEnvelope, mercatorCrs);

        MathTransform transform = CRS.findMathTransform(boundsCrs, latLongCrs);
        Envelope latLongBounds = JTS.transform(inBounds, transform);
        Coordinate latLongCentre = latLongBounds.centre();

        File inFolder = new File(inPath);
        File baseFolder = new File(inFolder, pName);

        double w = latLongBounds.getMinX();
        double s = latLongBounds.getMinY();
        double e = latLongBounds.getMaxX();
        double n = latLongBounds.getMaxY();

        GlobalMercator mercator = new GlobalMercator();

        for( int z = pMinzoom; z <= pMaxzoom; z++ ) {

            // get ul and lr tile number
            int[] llTileXY = mercator.GoogleTile(s, w, z);
            int[] urTileXY = mercator.GoogleTile(n, e, z);

            int startXTile = Math.min(llTileXY[0], urTileXY[0]);
            int endXTile = Math.max(llTileXY[0], urTileXY[0]);
            int startYTile = Math.min(llTileXY[1], urTileXY[1]);
            int endYTile = Math.max(llTileXY[1], urTileXY[1]);

            int tileNum = 0;

            ReferencedEnvelope levelBounds = new ReferencedEnvelope();

            pm.beginTask("Generating tiles at zoom level: " + z, (endXTile - startXTile + 1));
            for( int i = startXTile; i <= endXTile; i++ ) {

                for( int j = startYTile; j <= endYTile; j++ ) {
                    tileNum++;

                    double[] bounds = mercator.TileLatLonBounds(i, j, z);
                    double west = bounds[0];
                    double south = bounds[1];
                    double east = bounds[2];
                    double north = bounds[3];

                    ReferencedEnvelope tmpBounds = new ReferencedEnvelope(west, east, south, north, latLongCrs);
                    levelBounds.expandToInclude(tmpBounds);

                    if (!doDryrun) {
                        int[] onlineTileNumbers = {i, j};
                        int[] fileNameTileNumbers = {i, j};
                        // switch( pType ) {
                        // case 1:
                        // need to convert in TMS format
                        fileNameTileNumbers = mercator.TMSTileFromGoogleTile(i, j, z);
                        // break;
                        // case 0:
                        // default:
                        // break;
                        // }

                        File imageFolder = new File(baseFolder, z + "/" + fileNameTileNumbers[0]);
                        if (!imageFolder.exists()) {
                            if (!imageFolder.mkdirs()) {
                                throw new ModelsIOException("Unable to create folder:" + imageFolder, this);
                            }
                        }
                        File imageFile = new File(imageFolder, fileNameTileNumbers[1] + ".png");
                        if (imageFile.exists()) {
                            continue;
                        }

                        String tmp = inServiceUrl.replaceFirst("ZZZ", String.valueOf(z));
                        tmp = tmp.replaceFirst("XXX", String.valueOf(onlineTileNumbers[0]));
                        tmp = tmp.replaceFirst("YYY", String.valueOf(onlineTileNumbers[1]));
                        System.out.println(tmp);

                        URL url = new URL(tmp);
                        InputStream imgStream = null;
                        OutputStream out = null;
                        try {
                            imgStream = url.openStream();
                            out = new FileOutputStream(imageFile);
                            int read = 0;
                            byte[] bytes = new byte[1024];
                            while( (read = imgStream.read(bytes)) != -1 ) {
                                out.write(bytes, 0, read);
                            }
                        } catch (Exception ex) {
                            pm.errorMessage("Unable to get image: " + tmp);
                        } finally {
                            imgStream.close();
                            out.flush();
                            out.close();
                        }
                    }
                }
                pm.worked(1);
            }
            pm.done();

            pm.message("Zoom level: " + z + " has " + tileNum + " tiles.");
            pm.message("Boundary covered at Zoom level: " + z + ": " + levelBounds);
            pm.message("Total boundary wanted: " + mercatorBounds);
        }

        StringBuilder properties = new StringBuilder();
        properties.append("url=").append(pName).append("/ZZZ/XXX/YYY.png\n");
        properties.append("minzoom=").append(pMinzoom).append("\n");
        properties.append("maxzoom=").append(pMaxzoom).append("\n");
        properties.append("center=").append(latLongCentre.y).append(" ").append(latLongCentre.x).append("\n");
        properties.append("type=tms").append("\n");

        File propFile = new File(inFolder, pName + ".mapurl");
        FileUtilities.writeFile(properties.toString(), propFile);
    }
}
