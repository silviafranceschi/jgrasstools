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
package org.jgrasstools.gui.console;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utility to help filter out messages from the console.
 * 
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class ConsoleMessageFilter {

    private static Stream<String> containsStream;
    private static Stream<String> endsStream;
    static {
        List<String> containsStrings = new ArrayList<String>();
        containsStrings.add("Kakadu");
        containsStrings.add("Error while parsing JAI registry");
        containsStrings.add("A descriptor is already registered");
        containsStrings.add("Error in registry file");

        containsStream = containsStrings.parallelStream();

        List<String> endStrings = new ArrayList<String>();
        endStrings.add("factory.epsg.ThreadedEpsgFactory <init>");
        endStrings.add("to a 1800000ms timeout");
        endStrings.add("Native library load failed.");
        endStrings.add("gdalframework.GDALUtilities loadGDAL");
        endStrings.add("org.gdal.gdal.gdalJNI.HasThreadSupport()I");
        endStrings.add("org.gdal.gdal.gdalJNI.VersionInfo__SWIG_0(Ljava/lang/String;)Ljava/lang/String;");

        endsStream = endStrings.parallelStream();
    }

    public static boolean doRemove( final String line ) {
        try {
            boolean isPresent = endsStream.filter(string -> line.endsWith(string)).findFirst().isPresent();
            if (isPresent) {
                return true;
            }
            isPresent = containsStream.filter(string -> line.contains(string)).findFirst().isPresent();
            if (isPresent) {
                return true;
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

}
