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
package eu.hydrologis.jgrass.jgrassgears.modules.r.marchingsquares;

import static eu.hydrologis.jgrass.jgrassgears.libs.modules.HMConstants.doubleNovalue;
import static eu.hydrologis.jgrass.jgrassgears.libs.modules.HMConstants.isNovalue;
import static eu.hydrologis.jgrass.jgrassgears.utils.coverage.CoverageUtilities.COLS;
import static eu.hydrologis.jgrass.jgrassgears.utils.coverage.CoverageUtilities.ROWS;
import static eu.hydrologis.jgrass.jgrassgears.utils.coverage.CoverageUtilities.XRES;
import static eu.hydrologis.jgrass.jgrassgears.utils.coverage.CoverageUtilities.YRES;
import static eu.hydrologis.jgrass.jgrassgears.utils.coverage.CoverageUtilities.getRegionParamsFromGridCoverage;
import static eu.hydrologis.jgrass.jgrassgears.utils.coverage.CoverageUtilities.renderedImage2WritableRaster;

import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;

import javax.media.jai.iterator.RandomIterFactory;
import javax.media.jai.iterator.WritableRandomIter;

import oms3.annotations.Author;
import oms3.annotations.Description;
import oms3.annotations.Execute;
import oms3.annotations.In;
import oms3.annotations.Keywords;
import oms3.annotations.License;
import oms3.annotations.Out;
import oms3.annotations.Status;

import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureCollections;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

import eu.hydrologis.jgrass.jgrassgears.utils.geometry.GeometryUtilities;

@Description("Module for raster to vector conversion")
@Author(name = "Andrea Antonello", contact = "www.hydrologis.com")
@Keywords("Raster, Vector")
@Status(Status.TESTED)
@License("http://www.gnu.org/licenses/gpl-3.0.html")
@SuppressWarnings("nls")
public class MarchingSquaresVectorializer {

    @Description("The coverage that has to be converted.")
    @In
    public GridCoverage2D inGeodata;

    @Description("The value to use to trace the polygons.")
    @In
    public double pValue = doubleNovalue;

    @Description("The extracted features.")
    @Out
    public FeatureCollection<SimpleFeatureType, SimpleFeature> outGeodata = null;

    private WritableRandomIter iter = null;

    private double xRes;

    private double yRes;

    private GridGeometry2D gridGeometry;

    private int height;

    private int width;

    private BitSet bitSet = null;

    @Execute
    public void process() throws Exception {
        if (iter == null) {
            RenderedImage inputRI = inGeodata.getRenderedImage();
            WritableRaster inputWR = renderedImage2WritableRaster(inputRI, true);
            iter = RandomIterFactory.createWritable(inputWR, null);

            HashMap<String, Double> regionMap = getRegionParamsFromGridCoverage(inGeodata);
            height = regionMap.get(ROWS).intValue();
            width = regionMap.get(COLS).intValue();
            xRes = regionMap.get(XRES);
            yRes = regionMap.get(YRES);

            System.out.println(width + "/" + height + "/" + xRes + "/" + yRes);

            bitSet = new BitSet(width * height);

            gridGeometry = inGeodata.getGridGeometry();
        }

        List<Polygon> geometriesList = new ArrayList<Polygon>();

        // for( int j = 0; j < height; j++ ) {
        // for( int i = 0; i < width; i++ ) {
        // double v = iter.getSampleDouble(i, j, 0);
        // System.out.print(v + " ");
        // }
        // System.out.println();
        // }

        for( int col = 0; col < width; col++ ) {
            for( int row = 0; row < height; row++ ) {
                double value = iter.getSampleDouble(col, row, 0);
                if (!isNovalue(value) && value == pValue && !bitSet.get(row * width + col)) {
                    // trace the vector
                    Polygon polygon = identifyPerimeter(col, row);
                    if (polygon != null) {
                        geometriesList.add(polygon);
                    }
                }
            }
        }

        SimpleFeatureTypeBuilder b = new SimpleFeatureTypeBuilder();
        b.setName("raster2vector");
        b.setCRS(inGeodata.getCoordinateReferenceSystem());
        b.add("the_geom", Polygon.class);
        b.add("cat", Integer.class);
        SimpleFeatureType type = b.buildFeatureType();

        outGeodata = FeatureCollections.newCollection();
        int index = 0;
        for( Polygon polygon : geometriesList ) {
            SimpleFeatureBuilder builder = new SimpleFeatureBuilder(type);
            Object[] values = new Object[]{polygon, index};
            builder.addAll(values);
            SimpleFeature feature = builder.buildFeature(type.getTypeName() + "." + index);
            index++;
            outGeodata.add(feature);
        }

    }

    public Polygon identifyPerimeter( int initialX, int initialY ) throws TransformException {
        if (initialX < 0 || initialX > width - 1 || initialY < 0 || initialY > height - 1)
            throw new IllegalArgumentException("Coordinate outside the bounds.");

        int initialValue = value(initialX, initialY);
        if (initialValue == 0) {
            throw new IllegalArgumentException(String.format(
                    "Supplied initial coordinates (%d, %d) do not lie on a perimeter.", initialX,
                    initialY));
        }
        if (initialValue == 15) {
            // not a border pixel
            return null;
        }

        DirectPosition worldPosition = gridGeometry.gridToWorld(new GridCoordinates2D(initialX,
                initialY));
        double[] start = worldPosition.getCoordinate();
        Coordinate startCoordinate = new Coordinate(start[0] + xRes / 2.0, start[1] - yRes / 2.0);
        List<Coordinate> coordinateList = new ArrayList<Coordinate>(200);
        coordinateList.add(startCoordinate);

        double currentX = startCoordinate.x;
        double currentY = startCoordinate.y;
        int x = initialX;
        int y = initialY;

        boolean previousWentNorth = false;
        boolean previousWentEast = false;

        do {
            Coordinate direction = null;
            int dx = 0;
            int dy = 0;
            int v = value(x, y);
            switch( v ) {
            case 1:
                dy = -1;
                currentY = currentY + yRes;
                direction = new Coordinate(currentX, currentY); // N;
                previousWentNorth = true;
                break;
            case 2:
                dx = +1;
                currentX = currentX + xRes;
                direction = new Coordinate(currentX, currentY); // E;
                previousWentEast = true;
                break;
            case 3:
                dx = +1;
                currentX = currentX + xRes;
                direction = new Coordinate(currentX, currentY); // E;
                previousWentEast = true;
                break;
            case 4:
                dx = -1;
                currentX = currentX - xRes;
                direction = new Coordinate(currentX, currentY); // W;
                previousWentEast = false;
                break;
            case 5:
                dy = -1;
                currentY = currentY + yRes;
                direction = new Coordinate(currentX, currentY); // N;
                previousWentNorth = true;
                break;
            case 6:
                if (!previousWentNorth) {
                    dx = -1;
                    currentX = currentX - xRes;
                    direction = new Coordinate(currentX, currentY); // W;
                    previousWentEast = false;
                } else {
                    dx = +1;
                    currentX = currentX + xRes;
                    direction = new Coordinate(currentX, currentY); // E;
                    previousWentEast = true;
                }
                // direction = previous == N ? W : E;
                break;
            case 7:
                dx = +1;
                currentX = currentX + xRes;
                direction = new Coordinate(currentX, currentY); // E;
                previousWentEast = true;
                break;
            case 8:
                dy = +1;
                currentY = currentY - yRes;
                direction = new Coordinate(currentX, currentY); // S;
                previousWentNorth = false;
                break;
            case 9:
                if (previousWentEast) {
                    dy = -1;
                    currentY = currentY + yRes;
                    direction = new Coordinate(currentX, currentY); // N;
                    previousWentNorth = true;
                } else {
                    dy = +1;
                    currentY = currentY - yRes;
                    direction = new Coordinate(currentX, currentY); // S;
                    previousWentNorth = false;
                }
                // direction = previous == E ? N : S;
                break;
            case 10:
                dy = +1;
                currentY = currentY - yRes;
                direction = new Coordinate(currentX, currentY); // S;
                previousWentNorth = false;
                break;
            case 11:
                dy = +1;
                currentY = currentY - yRes;
                direction = new Coordinate(currentX, currentY); // S;
                previousWentNorth = false;
                break;
            case 12:
                dx = -1;
                currentX = currentX - xRes;
                direction = new Coordinate(currentX, currentY); // W;
                previousWentEast = false;
                break;
            case 13:
                dy = -1;
                currentY = currentY + yRes;
                direction = new Coordinate(currentX, currentY); // N;
                previousWentNorth = true;
                break;
            case 14:
                dx = -1;
                currentX = currentX - xRes;
                direction = new Coordinate(currentX, currentY); // W;
                previousWentEast = false;
                break;
            default:
                throw new IllegalStateException("Illegat state: " + v);
            }

            coordinateList.add(direction);

            x = x + dx;
            y = y + dy;
        } while( x != initialX || y != initialY );

        GeometryFactory gf = GeometryUtilities.gf();

        Coordinate[] coordinateArray = (Coordinate[]) coordinateList
                .toArray(new Coordinate[coordinateList.size()]);
        LinearRing linearRing = gf.createLinearRing(coordinateArray);
        Polygon polygon = gf.createPolygon(linearRing, null);
        return polygon;
    }

    private int value( int x, int y ) {
        int sum = 0;
        if (isSet(x, y)) // UL
            sum |= 1;
        if (isSet(x + 1, y)) // UR
            sum |= 2;
        if (isSet(x, y + 1)) // LL
            sum |= 4;
        if (isSet(x + 1, y + 1)) // LR
            sum |= 8;

        if (sum == 0) {
            System.out.println(x + "/" + y);
        }
        return sum;
    }

    private boolean isSet( int x, int y ) {
        boolean isOutsideGrid = x <= 0 || x > width || y <= 0 || y > height;
        if (isOutsideGrid) {
            return false;
        }
        // boolean isValid = data[(y - 1) * width + (x - 1)] != 0;
        double value = iter.getSampleDouble(x, y, 0);
        if (!isNovalue(value)) {
            // mark the used position
            bitSet.set(y * width + x);
            // iter.setSample(x, y, 0, doubleNovalue);
        } else {
            return false;
        }

        if (value == pValue) {
            return true;
        }
        return false;

        // boolean isValid = data[(y - 1) * width + (x - 1)] != 0;
        // return isOutsideGrid ? false : isValid;
    }
}