package org.osgeo.grass.r;

import org.jgrasstools.grass.utils.ModuleSupporter;

import oms3.annotations.Author;
import oms3.annotations.Documentation;
import oms3.annotations.Label;
import oms3.annotations.Description;
import oms3.annotations.Execute;
import oms3.annotations.In;
import oms3.annotations.UI;
import oms3.annotations.Keywords;
import oms3.annotations.License;
import oms3.annotations.Name;
import oms3.annotations.Out;
import oms3.annotations.Status;

@Description("Update raster map statistics")
@Author(name = "Grass Developers Community", contact = "http://grass.osgeo.org")
@Keywords("raster, statistics")
@Label("Grass/Raster Modules")
@Name("r__support__stats")
@Status(Status.CERTIFIED)
@License("General Public License Version >=2)")
public class r__support__stats {

	@UI("infile,grassfile")
	@Description("Name of input raster map")
	@In
	public String $$mapPARAMETER;

	@Description("Verbose module output")
	@In
	public boolean $$verboseFLAG = false;

	@Description("Quiet module output")
	@In
	public boolean $$quietFLAG = false;


	@Execute
	public void process() throws Exception {
		ModuleSupporter.processModule(this);
	}

}
