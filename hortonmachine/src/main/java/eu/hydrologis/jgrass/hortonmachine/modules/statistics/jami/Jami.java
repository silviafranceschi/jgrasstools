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
package eu.hydrologis.jgrass.hortonmachine.modules.statistics.jami;

import static eu.hydrologis.jgrass.jgrassgears.libs.modules.HMConstants.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import oms3.annotations.Author;
import oms3.annotations.Description;
import oms3.annotations.Execute;
import oms3.annotations.In;
import oms3.annotations.Keywords;
import oms3.annotations.License;
import oms3.annotations.Out;
import oms3.annotations.Role;
import oms3.annotations.Status;
import oms3.annotations.Unit;

import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;

import eu.hydrologis.jgrass.jgrassgears.io.eicalculator.EIAltimetry;
import eu.hydrologis.jgrass.jgrassgears.io.grass.JGrassConstants;
import eu.hydrologis.jgrass.jgrassgears.libs.modules.HMConstants;
import eu.hydrologis.jgrass.jgrassgears.libs.modules.HMModel;
import eu.hydrologis.jgrass.jgrassgears.libs.monitor.DummyProgressMonitor;
import eu.hydrologis.jgrass.jgrassgears.libs.monitor.IHMProgressMonitor;
import eu.hydrologis.jgrass.jgrassgears.utils.sorting.QuickSortAlgorithm;

@Description("Jami - Just another meteo interpolator")
@Author(name = "Silvia Franceschi, Andrea Antonello", contact = "www.hydrologis.com")
@Keywords("Meteo, Hydrology")
@Status(Status.DRAFT)
@License("http://www.gnu.org/licenses/gpl-3.0.html")
public class Jami extends HMModel {

    @Description("The features representing the meteo stations " + "and containing the position information as well " + "as the elevation. Every feature has a unique id "
            + "that is used to map the meteo data records to it.")
    @In
    public FeatureCollection<SimpleFeatureType, SimpleFeature> inStations;

    @Role(Role.PARAMETER)
    @Description("The field name of the attribute table that holds the station id.")
    @In
    public String fStationid;

    @Role(Role.PARAMETER)
    @Description("The field name of the attribute table that holds the station elevation.")
    @In
    public String fStationelev;

    @Role(Role.PARAMETER)
    @Description("The bins into which to divide the vertical range of stations in order to define levels.")
    @In
    public int pBins = 4;

    @Role(Role.PARAMETER)
    @Description("The minimum number of stations required at a certain level, to perform interpolation.")
    @In
    public int pNum = 3;

    @Description("The features representing the points in which the meteo data will be interpolated to. The baricenter of the basin is used to place the result of"
            + "the interpolation. These can be of any shape, the baricenter will be used anyways.")
    @In
    public FeatureCollection<SimpleFeatureType, SimpleFeature> inInterpolate;

    @Role(Role.PARAMETER)
    @Description("The field name of the attribute table that holds the basin id.")
    @In
    public String fBasinid;

    @Role(Role.PARAMETER)
    @Description("The meteo type that will be handled for interpolation.")
    @In
    public int pType = -1;

    @Role(Role.PARAMETER)
    @Description("The default value for relative humidity: 70%")
    @Unit("%")
    @In
    public double defaultRh = 70.0;

    @Role(Role.PARAMETER)
    @Description("The default value for wind speed: 1 m/s")
    @Unit("m/s")
    @In
    public double defaultW = 1.0;

    @Role(Role.PARAMETER)
    @Description("The hour of the day in which Tmin is supposed to happen.")
    @Unit("hours")
    @In
    public double pHtmin = 5.0;

    @Role(Role.PARAMETER)
    @Description("The hour of the day in which Tmax is supposed to happen.")
    @Unit("hours")
    @In
    public double pHtmax = 13.0;

    @Role(Role.PARAMETER)
    @Description("Dayly temperature range.")
    @Unit("celsius degrees")
    @In
    public double defaultDtday = 7.0;

    @Role(Role.PARAMETER)
    @Description("Monthly temperature range.")
    @Unit("celsius degrees")
    @In
    public double defaultDtmonth = 7.0;

    @Role(Role.PARAMETER)
    @Description("*** DESCRIBE ME ***")
    @Unit("hours")
    @In
    public double defaultTolltmin = 2.0;

    @Role(Role.PARAMETER)
    @Description("*** DESCRIBE ME ***")
    @Unit("hours")
    @In
    public double defaultTolltmax = 2.0;

    @Description("The progress monitor.")
    @In
    public IHMProgressMonitor pm = new DummyProgressMonitor();

    @Description("The current time variable.")
    @In
    public String tCurrent = null;

    @Description("The list of altimetric bands information, into which the basin is divided.")
    @In
    public List<EIAltimetry> inAltimetry = null;

    @Description("The map of meteo data to interpolate. Every value is associated to the id of the station.")
    @In
    public HashMap<Integer, double[]> inMeteo = null;

    @Description("The map of interpolated meteo data. Every value is associated to the id of the interpolation point.")
    @Out
    public HashMap<Integer, double[]> outInterpolated = null;

    /*
     * INTERNAL VARIABLES
     */
    // private MessageHandler msg = MessageHandler.getInstance();

    private List<SimpleFeature> stationFeatures;
    private List<Coordinate> stationCoordinates;

    private List<SimpleFeature> basinFeatures;
    private List<Coordinate> basinBaricenterCoordinates;

    /**
     * The matrix holding bands elevation for every basin.
     * 
     * <p>
     * Given a number of elevation bands in every basin a matrix is created with
     * the elevation of every band. To better understand, the matrix of v values
     * is created as below (without the basin header and band column.
     * </p>
     * <table>
     * <tr>
     * <td></td>
     * <td>basin1</td>
     * <td>basin2</td>
     * <td>basin3</td>
     * <td>basin...</td>
     * <tr>
     * <td>band1</td>
     * <td>v</td>
     * <td>v</td>
     * <td>v</td>
     * <td>v...</td>
     * <tr>
     * <td>band2</td>
     * <td>v</td>
     * <td>v</td>
     * <td>v</td>
     * <td>v...</td>
     * <tr>
     * <td>band3</td>
     * <td>v</td>
     * <td>v</td>
     * <td>v</td>
     * <td>v...</td>
     * <tr>
     * <td>band...</td>
     * <td>v</td>
     * <td>v</td>
     * <td>v</td>
     * <td>v...</td>
     * </tr>
     * </table>
     */
    private double[][] bandsBasins;

    private double[] statElev;

    private double[] statId;

    /**
     * Map containing the ids of the stations and the original positions in the
     * array read from database.
     * <p>
     * This is due to the fact that the arrays are sorted at some point and we
     * need to keep track of the positions , in order to get data from unsorted
     * arrays
     * </p>
     */
    private HashMap<Integer, Integer> stationid2StationindexMap;

    /**
     * Map of basin ids and their original position in the list originally read.
     */
    private HashMap<Integer, Integer> basinid2BasinindexMap;
    private HashMap<Integer, Integer> basinindex2basinidMap;

    /**
     * Map of the list of station ids found for every elevation band of the
     * stations elevation.
     */
    private HashMap<Integer, List<Integer>> bin2StationsListMap;

    /**
     * Map of station ids and their geometry.
     */
    private HashMap<Integer, Coordinate> stationId2CoordinateMap;

    private int basinIdFieldIndex = -1;

    // modificato il valore iniziale di hh_prev per le simulazioni con solo un
    // tempo...
    // rimettere il default
    // private double hh_prev = -1;
    private double hh_prev = 4;

    private int[] cont_min_max;

    private int[] flag_Tmin;

    private int[] flag_Tmax;

    private double[] minTempPerStation;

    private double[] maxTempPerStation;

    private double[][] DTday = null;

    private double[] DTmonth = null;

    private DateTimeFormatter formatter = HMConstants.utcDateFormatterYYYYMMDDHHMM;

    private DateTime currentTimestamp = null;

    @Execute
    public void process() throws Exception {
        
        System.out.println("Jami processing " + tCurrent + " " + pType);

        currentTimestamp = formatter.parseDateTime(tCurrent);

        outInterpolated = new HashMap<Integer, double[]>();
        /*
         * get stations
         */
        pm.message("Read stations data.");
        stationCoordinates = new ArrayList<Coordinate>();
        stationFeatures = new ArrayList<SimpleFeature>();
        FeatureIterator<SimpleFeature> featureIterator = inStations.features();
        while( featureIterator.hasNext() ) {
            SimpleFeature feature = featureIterator.next();
            Coordinate stationCoord = ((Geometry) feature.getDefaultGeometry()).getCoordinate();
            stationCoordinates.add(stationCoord);
            stationFeatures.add(feature);
        }
        inStations.close(featureIterator);
        pm.done();

        /*
         * get basins and create of every basin a buffered geometry at 10,
         * 20 and 50 km, which will be used to find the nearest stations
         * around.
         */
        basinBaricenterCoordinates = new ArrayList<Coordinate>();
        basinFeatures = new ArrayList<SimpleFeature>();
        featureIterator = inInterpolate.features();

        pm.beginTask("Read basins data.", inInterpolate.size());
        while( featureIterator.hasNext() ) {
            pm.worked(1);
            SimpleFeature feature = featureIterator.next();
            Coordinate baricenterCoord = ((Geometry) feature.getDefaultGeometry()).getCentroid().getCoordinate();
            basinBaricenterCoordinates.add(baricenterCoord);
            basinFeatures.add(feature);
        }
        inInterpolate.close(featureIterator);
        pm.done();

        statElev = new double[stationCoordinates.size()];
        statId = new double[stationCoordinates.size()];
        stationId2CoordinateMap = new HashMap<Integer, Coordinate>();
        extractFromStationFeatures();

        stationid2StationindexMap = new HashMap<Integer, Integer>();
        for( int i = 0; i < statId.length; i++ ) {
            stationid2StationindexMap.put((int) statId[i], i);
        }
        /*
         * get the basin's id attribute index
         */
        SimpleFeature tmpfeature = basinFeatures.get(0);
        SimpleFeatureType featureType = tmpfeature.getFeatureType();
        basinIdFieldIndex = featureType.indexOf(fBasinid);
        if (basinIdFieldIndex == -1) {
            throw new IllegalArgumentException("The field of the basin id couldn't be found in the supplied basin data.");
        }
        basinid2BasinindexMap = new HashMap<Integer, Integer>();
        basinindex2basinidMap = new HashMap<Integer, Integer>();
        for( int i = 0; i < basinBaricenterCoordinates.size(); i++ ) {
            int basinid = ((Number) basinFeatures.get(i).getAttribute(basinIdFieldIndex)).intValue();
            basinid2BasinindexMap.put(basinid, i);
            basinindex2basinidMap.put(i, basinid);
        }

        pm.message("Creating the band's elevation for every basin matrix.");
        /*
         * create the altimetric bands matrix
         */
        int basinNum = basinBaricenterCoordinates.size();

        int fascieNum = 0;
        for( EIAltimetry altim : inAltimetry ) {
            if (altim.altimetricBandId > fascieNum) {
                fascieNum = altim.altimetricBandId;
            }
        }
        fascieNum++;

        bandsBasins = new double[fascieNum][basinNum];
        for( int i = 0; i < inAltimetry.size(); i++ ) {
            EIAltimetry eiAltimetry = inAltimetry.get(i);

            int idbasin = eiAltimetry.basinId;
            int idfascia = eiAltimetry.altimetricBandId;
            double elevationInBandBaricenter = eiAltimetry.elevationValue;
            Integer index = basinid2BasinindexMap.get(idbasin);
            if (index != null)
                bandsBasins[idfascia][index] = elevationInBandBaricenter;
            // TODO make it range aware
            // double range = altimetryScalarSet.get(i + 3);
            // bandsBasins[idfascia +
            // 1][basinid2BasinindexMap.get(idbasin)] = baricenter
            // + range / 2.0;
        }

        double[] stationBinsArrays = new double[pBins + 1];
        double maxStatElev = statElev[statElev.length - 1];
        double minStatElev = statElev[0];
        double interval = (maxStatElev - minStatElev) / (double) pBins;
        for( int i = 0; i < stationBinsArrays.length; i++ ) {
            stationBinsArrays[i] = minStatElev + i * interval;
        }

        /*
         * find all stations inside a elevation band for every basin
         */
        pm.beginTask("Finding all stations inside a elevation band for every basin.", stationBinsArrays.length - 1);
        bin2StationsListMap = new HashMap<Integer, List<Integer>>();
        for( int i = 0; i < stationBinsArrays.length - 1; i++ ) {
            List<Integer> stationsIds = new ArrayList<Integer>();
            for( int j = 0; j < statId.length; j++ ) {
                double id = statId[j];
                double elev = statElev[j];
                if (elev >= stationBinsArrays[i] && elev < stationBinsArrays[i + 1]) {
                    stationsIds.add((int) id);
                }
            }
            bin2StationsListMap.put(i, stationsIds);
            pm.worked(1);
        }
        pm.done();

        /*
         * get values for current timestep and order them with the stations ids
         */
        double[] statValues = new double[stationCoordinates.size()];
        for( int i = 0; i < statValues.length; i++ ) {
            statValues[i] = doubleNovalue;
        }
        Set<Integer> stationIdSet = inMeteo.keySet();
        for( Integer stationId : stationIdSet ) {
            int id = stationId;
            double[] value = inMeteo.get(id);
            Integer index = stationid2StationindexMap.get((int) id);
            if (index == null)
                continue;
            statValues[index] = value[0];
        }

        // number of active stations for every basin
        int[] activeStationsPerBasin = new int[basinBaricenterCoordinates.size()];
        int[][] stazBacMatrix = createStationBasinsMatrix(statValues, activeStationsPerBasin);
        int[][] stations = new int[stazBacMatrix.length][stazBacMatrix[0].length];
        int contStations = 0;
        // riempimento vettori/matrici
        for( int i = 0; i < stazBacMatrix[0].length; i++ ) { // indice bacino
            contStations = 0;
            for( int j = 0; j < stazBacMatrix.length; j++ ) { // indice stazione
                if (stazBacMatrix[j][i] == 1) {
                    stations[contStations][i] = j;
                    contStations += 1;
                }
            }
        }

        int bandsNum = bandsBasins.length;
        if (pType == DTDAY || pType == DTMONTH) {
            /*
             * calculate the DT month and day for each station
             */
            // System.out.println("Calculating the dayly and monthly Dt for each station...");
            rangeT(statValues);
        }

        pm.beginTask("Interpolating over bands and basins...", basinBaricenterCoordinates.size());

        // System.out.println("---");
        // for (int s = 0; s < minTempPerStation.length; s++) {
        // if (minTempPerStation[s] != 0) {
        // System.out.println("Temperature: "
        // + maxTempPerStation[s] + " "
        // + minTempPerStation[s]);
        //				
        // }
        // }

        for( int i = 0; i < basinBaricenterCoordinates.size(); i++ ) {
            pm.worked(1);
            // interpolated value for every band
            double[] interpolatedMeteoForBand = new double[bandsNum];

            int cont = 0;
            double h;
            int[] jj_av;

            // trova le stazioni che forniscono dati
            jj_av = new int[activeStationsPerBasin[i]]; // costruisco un nuovo
            // vettore jj_av con le
            // stazioni del bacino in studio
            for( int j = 0; j < activeStationsPerBasin[i]; j++ ) {
                if (pType != DTDAY || pType == DTMONTH) {
                    if (!isNovalue(statValues[stations[j][i]])) {
                        jj_av[cont] = stations[j][i]; // registro le stazioni
                        // attive
                        cont += 1;
                    }
                } else {
                    // se per la stazione j del bacino i minT e maxT sono
                    // diversi da
                    // NODATA
                    if (!isNovalue(minTempPerStation[stations[j][i]]) && isNovalue(maxTempPerStation[stations[j][i]])) {
                        // jj conterrà le stazioni che hanno dati di escursione
                        // termica
                        // giornaliera
                        jj_av[cont] = stations[j][i]; // registro le stazioni
                        // attive
                        cont += 1;
                    }
                }
            }

            // caso 0. se non c'e' nessuna stazione, cerco che il programma
            // sopravviva
            if (cont == 0) {
                if (pType == TEMPERATURE) { // caso dei dati di temperatura
                    pm.errorMessage("ERRORE: PER IL BACINO " + i + " NON SONO DISPONIBILI DATI DI TEMPERATURA, PER QUESTO BACINO STAND-BY");
                    for( int f = 0; f < bandsNum; f++ ) { // per tutte le fasce
                        // altimetriche metto il
                        // dato a -100
                        interpolatedMeteoForBand[f] = doubleNovalue;
                    }
                } else if (pType == PRESSURE) { // caso dei dati di pressione
                    pm.message("  -> Per il bacino " + i + " non sono disponibili dati di pressione, uso valori di default");
                    for( int f = 0; f < bandsNum; f++ ) { // per tutte le fasce
                        // altimetriche considero
                        // un'adiabatica
                        interpolatedMeteoForBand[f] = 1013.25 * Math.exp(-(bandsBasins[f][i]) * 0.00013);
                    }
                } else if (pType == HUMIDITY) { // caso dei dati di umidità
                    pm.message("  -> Per il bacino " + i + " non sono disponibili dati di umidita', uso valori di default");
                    for( int f = 0; f < bandsNum; f++ ) { // per tutte le fasce
                        // altimetriche metto NODATA
                        interpolatedMeteoForBand[f] = defaultRh;
                    }
                } else if (pType == WIND) { // caso dei dati di velocità del vento
                    pm.message("  -> Per il bacino " + i + " non sono disponibili dati di velocita' del vento, uso valori di default");
                    for( int f = 0; f < bandsNum; f++ ) { // per tutte le fasce
                        // altimetriche metto NODATA
                        interpolatedMeteoForBand[f] = defaultW;
                    }
                } else if (pType == DTDAY) { // caso dei dati di escursione termica
                    // giornaliera
                    pm.message("  -> Per il bacino " + i + " non sono disponibili dati di escursione termica giornaliera', uso valori di default");
                    for( int f = 0; f < bandsNum; f++ ) { // per tutte le fasce
                        // altimetriche del bacino
                        // assegno all'escursione termica giornaliera il dato
                        // DTd
                        // messo nel file dei parametri
                        interpolatedMeteoForBand[f] = defaultDtday;
                    }
                } else if (pType == DTMONTH) { // caso dei dati di escursione termica
                    // mensile
                    pm.message("  -> Per il bacino " + i + " non sono disponibili dati di escursione termica mensile', uso valori di default");
                    for( int f = 0; f < bandsNum; f++ ) {
                        /*
                         *  per tutte le fasce
                         * altimetriche del bacino
                         */
                        // assegno all'escursione termica media mensile il
                        // datoDTm
                        // messo nel file dei parametri
                        interpolatedMeteoForBand[f] = defaultDtmonth;
                    }
                }

            } else if (cont == 1) {
                // caso 1. c'e' solo una stazione presente . modello di
                // atmosfera
                // standard per T e P, valori costanti per RH e V
                for( int f = 0; f < bandsNum; f++ ) { // ciclo sulle fascie
                    // altimetriche
                    if (pType == TEMPERATURE) { // trasformo la temp in K e calcolo T
                        // con
                        // l'adiabatica semplice
                        interpolatedMeteoForBand[f] = (statValues[jj_av[0]] + tk) * Math.exp(-(bandsBasins[f][i] - statElev[jj_av[0]]) * GAMMA / (statValues[jj_av[0]] + tk)) - tk;
                    } else if (pType == PRESSURE) { // calcolo P con il gradiente
                        // adiabatico
                        interpolatedMeteoForBand[f] = statValues[jj_av[0]] * Math.exp(-(bandsBasins[f][i] - statElev[jj_av[0]]) * 0.00013);
                    } else if (pType == DTDAY) {
                        // se ho una sola stazione assegno il valore della
                        // stazione a tutto il
                        // bacino
                        // altimetriche del bacino assegno il valore di
                        // escursione massima
                        // giornaliera
                        interpolatedMeteoForBand[f] = maxTempPerStation[jj_av[0]] - minTempPerStation[jj_av[0]];
                        if ((maxTempPerStation[jj_av[0]] - minTempPerStation[jj_av[0]]) <= 0) {
                            interpolatedMeteoForBand[f] = defaultDtday;
                        }
                    } else if (pType == DTMONTH) {
                        // se ho una sola stazione assegno il valore della
                        // stazione a tutto il
                        // bacino
                        // altimetriche del bacino assegno il valore di
                        // escursione massima mensile
                        interpolatedMeteoForBand[f] = DTmonth[jj_av[0]];
                    } else { // RH e V sono costanti al variare delle fasce
                        // altimetriche
                        interpolatedMeteoForBand[f] = statValues[jj_av[0]];
                    }
                }
            } else {
                // caso 2. ci sono almeno 2 stazioni (a quote inferiori alla
                // stazioni piu' bassa considero atmosfera standard come a quote
                // superiori alla staz. piu' alta, in mezzo calcolo LAPSE RATE)
                // alloca L (vettore di dimensioni numero di stazioni attive-1)
                double[] lapseRate = new double[cont - 1];

                for( int j = 0; j < cont - 1; j++ ) { // le stazioni sono in
                    // ordine di
                    // quota
                    // L[j] e' il lapse rate tra la stazione j e j+1, puo'
                    // essere
                    // calcolato dai dati per j che va da 1 a n-1, dove n e' il
                    // numero di stazioni (cont)
                    lapseRate[j] = (statValues[jj_av[j]] - statValues[jj_av[j + 1]]) / (statElev[jj_av[j + 1]] - statElev[jj_av[j]]);
                }

                for( int f = 0; f < bandsNum; f++ ) { // ciclo sulle fascie
                    // altimetriche

                    // per le fasce altimetriche con quote piu' basse della
                    // quota
                    // della stazione piu' bassa prendo i dati della stazione
                    // più bassa
                    if (bandsBasins[f][i] <= statElev[jj_av[0]]) {
                        if (pType == TEMPERATURE) { // T
                            interpolatedMeteoForBand[f] = statValues[jj_av[0]] - GAMMA * (bandsBasins[f][i] - statElev[jj_av[0]]);
                        } else if (pType == PRESSURE) { // P
                            interpolatedMeteoForBand[f] = statValues[jj_av[0]] - (statValues[jj_av[0]] * 0.00013) * (bandsBasins[f][i] - statElev[jj_av[0]]);
                        } else if (pType == DTDAY) {
                            interpolatedMeteoForBand[f] = maxTempPerStation[jj_av[0]] - minTempPerStation[jj_av[0]];
                            if ((maxTempPerStation[jj_av[0]] - minTempPerStation[jj_av[0]]) <= 0) {
                                interpolatedMeteoForBand[f] = defaultDtday;
                            }
                        } else if (pType == DTMONTH) {
                            interpolatedMeteoForBand[f] = DTmonth[jj_av[0]];
                        } else { // RH e V
                            interpolatedMeteoForBand[f] = statValues[jj_av[0]];
                        }

                        // per le fasce altimetriche con quote piu' alte della
                        // quota
                        // della stazione piu' alta prendo i dati della stazione
                        // più alta
                    } else if (bandsBasins[f][i] >= statElev[jj_av[cont - 1]]) {
                        if (pType == TEMPERATURE) { // T
                            interpolatedMeteoForBand[f] = statValues[jj_av[cont - 1]] - GAMMA * (bandsBasins[f][i] - statElev[jj_av[cont - 1]]);
                        } else if (pType == PRESSURE) { // P
                            interpolatedMeteoForBand[f] = statValues[jj_av[cont - 1]] - (statValues[jj_av[cont - 1]] * 0.00013) * (bandsBasins[f][i] - statElev[jj_av[cont - 1]]);
                        } else if (pType == DTDAY) {
                            interpolatedMeteoForBand[f] = maxTempPerStation[jj_av[cont - 1]] - minTempPerStation[jj_av[cont - 1]];
                            if ((maxTempPerStation[jj_av[0]] - minTempPerStation[jj_av[0]]) <= 0) {
                                interpolatedMeteoForBand[f] = defaultDtday;
                            }
                        } else if (pType == DTMONTH) {
                            interpolatedMeteoForBand[f] = DTmonth[jj_av[cont - 1]];
                        } else { // RH e V
                            interpolatedMeteoForBand[f] = statValues[jj_av[cont - 1]];
                        }

                    } else {
                        int k = cont - 1;
                        if (pType == DTDAY) {
                            // per le fasce altimetriche intermedie devo
                            // interpolare tra la min e
                            // la max delle stazioni
                            do {
                                k -= 1;
                                h = statElev[jj_av[k]];
                            } while( bandsBasins[f][i] <= h );

                            // for (int j = 0; j < cont; j++) {
                            // if (f ==0 && i == 100) {
                            // System.out.println(j + " "+ statElev[jj_av[j]]);
                            // }
                            // }

                            // interpolatedMeteoForBand[f] =
                            // ((maxTempPerStation[jj_av[k]] -
                            // minTempPerStation[jj_av[k]])
                            // * (statElev[jj_av[k + 1]] - bandsBasins[f][i]) +
                            // (maxTempPerStation[jj_av[k + 1]] -
                            // minTempPerStation[jj_av[k + 1]])
                            // * (bandsBasins[f][i] - statElev[jj_av[k]]))
                            // / (statElev[jj_av[k + 1]] - statElev[jj_av[k]]);
                            interpolatedMeteoForBand[f] = ((maxTempPerStation[jj_av[k + 1]] - minTempPerStation[jj_av[k + 1]]) - (maxTempPerStation[jj_av[k]] - minTempPerStation[jj_av[k]]))
                                    * (bandsBasins[f][i] - statElev[jj_av[k]]) / (statElev[jj_av[k + 1]] - statElev[jj_av[k]]) + (maxTempPerStation[jj_av[k]] - minTempPerStation[jj_av[k]]);
                            // if (i == 100) {
                            // System.out.println("Banda " + f + " "
                            // + bandsBasins[f][i]);
                            // System.out.println("stazione1 " + k);
                            // System.out.println("elevazione: "
                            // + statElev[jj_av[k]]);
                            // System.out.println("stazione2 " + k + 1);
                            // System.out.println("max: "
                            // + maxTempPerStation[jj_av[k + 1]]);
                            // System.out.println("min: "
                            // + minTempPerStation[jj_av[k + 1]]);
                            // // System.out.println(statElev[jj_av[k + 1]]);
                            // }

                            if (interpolatedMeteoForBand[f] <= 0) {
                                interpolatedMeteoForBand[f] = defaultDtday;
                            }
                        } else if (pType == DTMONTH) {
                            // per le fasce altimetriche intermedie devo
                            // interpolare tra la min e
                            // la max delle stazioni
                            do {
                                k -= 1;
                                h = statElev[jj_av[k]];
                            } while( bandsBasins[f][i] <= h );
                            interpolatedMeteoForBand[f] = (DTmonth[jj_av[k]] * (statElev[jj_av[k + 1]] - bandsBasins[f][i]) + DTmonth[jj_av[k + 1]] * (bandsBasins[f][i] - statElev[jj_av[k]]))
                                    / (statElev[jj_av[k + 1]] - statElev[jj_av[k]]);
                        } else {
                            do {
                                k -= 1;
                                h = statElev[jj_av[k]];
                            } while( bandsBasins[f][i] <= h );
                            interpolatedMeteoForBand[f] = statValues[jj_av[k]] - lapseRate[k] * (bandsBasins[f][i] - statElev[jj_av[k]]);
                        }
                    }

                }

                // ADDED
                // controllo su RH>100 e v=0
                if (pType == HUMIDITY) { // RH
                    for( int f = 0; f < bandsNum; f++ ) {
                        if (interpolatedMeteoForBand[f] > 100)
                            interpolatedMeteoForBand[f] = 100;
                        if (interpolatedMeteoForBand[f] < 5)
                            interpolatedMeteoForBand[f] = 5;
                    }
                } else if (pType == WIND) { // V
                    for( int f = 0; f < bandsNum; f++ ) {
                        if (interpolatedMeteoForBand[f] < 0.01)
                            interpolatedMeteoForBand[f] = 0.01;
                    }
                }

                int basinid = ((Number) basinFeatures.get(i).getAttribute(basinIdFieldIndex)).intValue();
                outInterpolated.put(basinid, interpolatedMeteoForBand);
            }
        }
        pm.done();
    }

    /**
     * Creates the stations per basins matrix.
     * 
     * <p>
     * The matrix is a bitmap of the stations that will be used for every basin,
     * following the schema:
     * </p>
     * <table>
     * <tr>
     * <td></td>
     * <td>basin1</td>
     * <td>basin2</td>
     * <td>basin3</td>
     * <td>basin...</td>
     * <tr>
     * <td>station1</td>
     * <td>1</td>
     * <td>0</td>
     * <td>1</td>
     * <td>0...</td>
     * <tr>
     * <td>station2</td>
     * <td>1</td>
     * <td>0</td>
     * <td>1</td>
     * <td>0...</td>
     * <tr>
     * <td>station3</td>
     * <td>0</td>
     * <td>1</td>
     * <td>1</td>
     * <td>0...</td>
     * <tr>
     * <td>station...</td>
     * <td>0</td>
     * <td>0</td>
     * <td>0</td>
     * <td>0...</td>
     * </tr>
     * </table>
     * <p>
     * In the above case basin1 will use station1 and station2, while basin2
     * will use only station3, and so on.
     * </p>
     * 
     * @param statValues
     *            the station data values, properly ordered, containing
     *            {@link JGrassConstants#defaultNovalue novalues}.
     * @param activeStationsPerBasin
     *            an array to be filled with the number of active stations per
     *            basin.
     * @return the matrix of active stations for every basin.
     */
    private int[][] createStationBasinsMatrix( double[] statValues, int[] activeStationsPerBasin ) {
        int[][] stationsBasins = new int[stationCoordinates.size()][basinBaricenterCoordinates.size()];
        Set<Integer> bandsIdSet = bin2StationsListMap.keySet();
        Integer[] bandsIdArray = (Integer[]) bandsIdSet.toArray(new Integer[bandsIdSet.size()]);
        // for every basin
        for( int i = 0; i < basinBaricenterCoordinates.size(); i++ ) {
            Coordinate basinBaricenterCoordinate = basinBaricenterCoordinates.get(i);
            // for every stations band
            int activeStationsForThisBasin = 0;
            for( int j = 0; j < bandsIdArray.length; j++ ) {
                int bandId = bandsIdArray[j];
                List<Integer> stationIdsForBand = bin2StationsListMap.get(bandId);

                /*
                 * search for the nearest stations that have values.
                 */
                List<Integer> stationsToUse = extractStationsToUse(basinBaricenterCoordinate, stationIdsForBand, stationId2CoordinateMap, statValues, stationid2StationindexMap);
                if (stationsToUse.size() < pNum) {
                    pm.message("Found only " + stationsToUse.size() + " for basin " + basinindex2basinidMap.get(i) + " and bandid " + bandId + ".");
                }

                /*
                 * now we have the list of stations to use. With this list we
                 * need to enable (1) the proper matrix positions inside the
                 * stations-basins matrix.
                 */
                // i is the column (basin) index
                // the station id index can be taken from the idStationIndexMap
                for( Integer stationIdToEnable : stationsToUse ) {
                    int stIndex = stationid2StationindexMap.get(stationIdToEnable);
                    stationsBasins[stIndex][i] = 1;
                }
                activeStationsForThisBasin = activeStationsForThisBasin + stationsToUse.size();
            }

            activeStationsPerBasin[i] = activeStationsForThisBasin;
        }

        return stationsBasins;
    }
    /**
     * @param basinBaricenterCoordinate the basin baricenter coordinate used to 
     *                      Calculate distances with stations.
     * @param stationIdsToSearch the list of stations.
     * @param stationId2CoordinateMap
     * @param statValues the array of data to consider. Used to identify stations
     *                      that have no data for an instant.
     * @param idStationIndexMap
     * @return the list of needed nearest stations.
     */
    private List<Integer> extractStationsToUse( Coordinate basinBaricenterCoordinate, List<Integer> stationIdsToSearch, HashMap<Integer, Coordinate> stationId2CoordinateMap, double[] statValues,
            HashMap<Integer, Integer> idStationIndexMap ) {

        List<Integer> stationsToUse = new ArrayList<Integer>();
        List<Integer> stationsLeftOver = new ArrayList<Integer>();
        Map<Double, Integer> sortedByDistanceStationsMap = new TreeMap<Double, Integer>();
        for( Integer stId : stationIdsToSearch ) {
            /*
             * check the values of the stations. If there are novalues and there
             * are enough stations left, disable them
             */
            double currentValue = statValues[stationid2StationindexMap.get(stId)];
            if (isNovalue(currentValue)) {
                // if the station has a novalue, jump over it, even without
                // adding the station.
                // out.println("Jump over station: " + stId);
                stationsLeftOver.add(stId);
                continue;
            }

            /*
             * if it gets here, the station has a value. 
             * Put it to the list of stations to be checked 
             * for which is nearer. 
             */
            Coordinate stationCoord = stationId2CoordinateMap.get(stId);
            double distance = basinBaricenterCoordinate.distance(stationCoord);
            sortedByDistanceStationsMap.put(distance, stId);
        }
        Collection<Integer> statIds = sortedByDistanceStationsMap.values();
        Iterator<Integer> iterator = statIds.iterator();
        for( int i = 0; i < statIds.size(); i++ ) {
            if (iterator.hasNext() && i < pNum) {
                stationsToUse.add(iterator.next());
            } else if (iterator.hasNext() && i >= pNum) {
                stationsLeftOver.add(iterator.next());
            } else {
                System.out.println("SHOULD THIS EVER HAPPEN???");
                break;
            }
        }

        /*
         * if not enough stations were collected, add also stations that
         * don't have values. Those stations are taken in random way, 
         * since their value won't be considered.  
         */
        if (stationsToUse.size() < pNum) {
            for( int i = 0; i < pNum - stationsToUse.size(); i++ ) {
                stationsToUse.add(stationsLeftOver.get(i));
            }
        }

        return stationsToUse;
    }

    /**
     * Fills the elevation and id arrays for the stations, ordering in ascending
     * elevation order.
     * 
     * @throws Exception
     *             in the case the sorting gives problems.
     */
    private void extractFromStationFeatures() throws Exception {
        int stationIdIndex = -1;
        int stationElevIndex = -1;
        pm.beginTask("Filling the elevation and id arrays for the stations, ordering them in ascending elevation order.", stationCoordinates.size());
        for( int i = 0; i < stationCoordinates.size(); i++ ) {
            pm.worked(1);
            SimpleFeature stationF = stationFeatures.get(i);
            Coordinate stationCoord = stationCoordinates.get(i);
            if (stationIdIndex == -1) {
                SimpleFeatureType featureType = stationF.getFeatureType();
                stationIdIndex = featureType.indexOf(fStationid);
                stationElevIndex = featureType.indexOf(fStationelev);
                if (stationIdIndex == -1) {
                    throw new IllegalArgumentException("Could not find the field: " + fStationid);
                }
                if (stationElevIndex == -1) {
                    throw new IllegalArgumentException("Could not find the field: " + fStationelev);
                }
            }
            int id = ((Number) stationF.getAttribute(stationIdIndex)).intValue();
            double elev = ((Number) stationF.getAttribute(stationElevIndex)).doubleValue();
            statElev[i] = elev;
            statId[i] = id;
            stationId2CoordinateMap.put(id, stationCoord);
        }
        pm.done();
        // sort
        QuickSortAlgorithm qsA = new QuickSortAlgorithm(pm);
        qsA.sort(statElev, statId);
    }

    private void rangeT( double[] statValues ) {
        // calcola la temperatura massima maxT[j] e minima minT[j] per ogni
        // stazione
        // calcola anche il DTmonth[j], l'escursione termica giornaliera mediata
        // nei 30 giorni precedenti
        // DTday e' variabile ausiliaria

        int stationNum = statValues.length;

        if (cont_min_max == null) {
            cont_min_max = new int[]{0, 0};
            flag_Tmin = new int[stationNum];
            flag_Tmax = new int[stationNum];
            minTempPerStation = new double[stationNum];
            maxTempPerStation = new double[stationNum];
            DTday = new double[stationNum][32];
            DTmonth = new double[stationNum];
        }

        int currentHour = currentTimestamp.getHourOfDay();
        int currentMinute = currentTimestamp.getMinuteOfHour();
        double hh = currentHour + currentMinute / 60.0;

        if (hh_prev != -1) {
            // cicli if da usare nel caso non sia disponibile la temperatura
            // all'istante h_T_min
            // ciclo if per la Tmin a cavallo dell'istante h_T_min
            if (hh >= pHtmin && hh_prev < pHtmin) {
                cont_min_max[0] += 1;
                // per ogni stazione di misura di T setto la flag_Tmin a 1
                for( int j = 0; j < stationNum; j++ ) {
                    flag_Tmin[j] = 1;
                }
            }
            // ciclo if per la Tmax a cavallo dell'istante h_T_max
            if (hh >= pHtmax && hh_prev < pHtmax) {
                cont_min_max[1] += 1;
                // per ogni stazione di misura di T setto la flag_Tmax a 1
                for( int j = 0; j < stationNum; j++ ) {
                    flag_Tmax[j] = 1;
                }
            }
        }

        // per ogni stazione di misura di T
        for( int j = 0; j < stationNum; j++ ) {

            if (flag_Tmin[j] == 1) { // sono a ridosso della Tmin
                if (!isNovalue(statValues[j])) {
                    minTempPerStation[j] = statValues[j];
                    flag_Tmin[j] = 2;

                    // ADDED
                    if (minTempPerStation[j] >= maxTempPerStation[j])
                        maxTempPerStation[j] = minTempPerStation[j] + defaultDtday;

                    // se l'ora di osservazione è fuori dall'intervallo di
                    // tolleranza
                    // setto minT a NODATA
                } else if (hh > pHtmin + defaultTolltmin || hh < pHtmin) {
                    minTempPerStation[j] = doubleNovalue;
                    flag_Tmin[j] = 2;
                }
            }

            // come per Tmin lavoro con Tmax
            if (flag_Tmax[j] == 1) {
                if (!isNovalue(statValues[j])) {
                    maxTempPerStation[j] = statValues[j];
                    flag_Tmax[j] = 2;

                    // ADDED
                    if (minTempPerStation[j] >= maxTempPerStation[j])
                        minTempPerStation[j] = maxTempPerStation[j] - defaultDtday;

                } else if (hh > pHtmax + defaultTolltmax || hh < pHtmax) {
                    maxTempPerStation[j] = doubleNovalue;
                    flag_Tmax[j] = 2;
                }
            }

            // calcolo effettivo del DT giornaliero e aggiornamento della
            // variabile flag_Tmin e max
            if (cont_min_max[0] == cont_min_max[1] && cont_min_max[0] >= 0) {
                if (flag_Tmin[j] == 2 && flag_Tmax[j] == 2) {

                    if (!isNovalue(minTempPerStation[j]) && !isNovalue(maxTempPerStation[j])) {
                        DTday[j][cont_min_max[0]] = maxTempPerStation[j] - minTempPerStation[j];
                    } else {
                        DTday[j][cont_min_max[0]] = defaultDtday;
                    }

                    flag_Tmin[j] = 3;
                    flag_Tmax[j] = 3;
                }
            }

        }

        boolean[] hasMinMaxT = new boolean[stationNum];
        boolean atLeastOneStation = false;
        // per ogni stazione di misura di T
        for( int j = 0; j < stationNum; j++ ) {
            if (flag_Tmin[j] == 3) {
                hasMinMaxT[j] = true;
                atLeastOneStation = true;
            }
        }

        if (atLeastOneStation) {
            if (cont_min_max[0] > 30) {
                for( int j = 0; j < stationNum; j++ ) { // j sono le stazioni
                    for( int k = 0; k < 30; k++ ) { // k sono i giorni
                        DTday[j][k] = DTday[j][k + 1];
                    }
                }
                cont_min_max[0] = 30;
                cont_min_max[1] = 30;
            }

            // per ogni stazione di misura di T
            for( int j = 0; j < stationNum; j++ ) {

                if (!hasMinMaxT[j]) {
                    continue;
                }

                // inizializzo a zero la variabile DTmonth
                DTmonth[j] = 0.0;
                int cont = 0;

                for( int k = 0; k <= cont_min_max[0]; k++ ) {
                    if (!isNovalue(DTday[j][k])) {
                        // aggiorno DTmonth con i valori di Dtday e anche il
                        // contatore
                        DTmonth[j] += DTday[j][k];
                        cont += 1;
                    }
                }

                if (cont == 0) {
                    // assegno a NODATA il valore di DTmonth per la stazione
                    // corrente
                    DTmonth[j] = defaultDtmonth;
                } else {
                    // calcolo del Dt medio mensile per la stazione corrente
                    DTmonth[j] /= (double) cont;
                }

                flag_Tmin[j] = 0;
                flag_Tmax[j] = 0;

            }

        } else {
            for( int j = 0; j < stationNum; j++ ) {
                if (DTmonth[j] == 0.0) {
                    DTmonth[j] = defaultDtmonth;
                }
            }
        }

        hh_prev = hh;
    }

}
