package eu.hydrologis.jgrass.hortonmachine.models.hm;

import java.util.HashMap;

import org.geotools.coverage.grid.GridCoverage2D;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import eu.hydrologis.jgrass.hortonmachine.modules.hydrogeomorphology.shalstab.Shalstab;
import eu.hydrologis.jgrass.hortonmachine.utils.HMTestCase;
import eu.hydrologis.jgrass.hortonmachine.utils.HMTestMaps;
import eu.hydrologis.jgrass.jgrassgears.libs.monitor.PrintStreamProgressMonitor;
import eu.hydrologis.jgrass.jgrassgears.utils.coverage.CoverageUtilities;

/**
 * Test the Shalstab module.
 * 
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class TestShalstab extends HMTestCase {
    public void testShalstab() throws Exception {
        HashMap<String, Double> envelopeParams = HMTestMaps.envelopeParams;
        CoordinateReferenceSystem crs = HMTestMaps.crs;
        PrintStreamProgressMonitor pm = new PrintStreamProgressMonitor(System.out, System.out);

        double[][] slopeData = HMTestMaps.slopeData;
        GridCoverage2D slopeCoverage = CoverageUtilities.buildCoverage("slope", slopeData, envelopeParams, crs);
        double[][] abData = HMTestMaps.abData;
        GridCoverage2D abCoverage = CoverageUtilities.buildCoverage("ab", abData, envelopeParams, crs);
        
        Shalstab shalstab = new Shalstab();
        shalstab.inSlope = slopeCoverage;
        shalstab.inTca = abCoverage;
        shalstab.pTrasmissivity = 0.001;
        shalstab.pCohesion = 0.0;
        shalstab.pSdepth = 2.0;
        shalstab.pRho = 1.6;
        shalstab.pTgphi = 0.7;
        shalstab.pQ = 0.05;
        shalstab.pm = pm;

        shalstab.process();

        GridCoverage2D qcritCoverage = shalstab.outQcrit;
        GridCoverage2D classiCoverage = shalstab.outShalstab;

        checkMatrixEqual(qcritCoverage.getRenderedImage(), HMTestMaps.qcritmapData, 0);
        checkMatrixEqual(classiCoverage.getRenderedImage(), HMTestMaps.classimapData, 0);
    }

}
