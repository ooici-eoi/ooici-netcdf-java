/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package ucar.nc2;

import ucar.nc2.dt.grid.GeoGrid;
import ucar.nc2.dt.grid.GridDataset;
import ucar.ma2.Array;

import java.io.IOException;

/**
 * Class Description.
 *
 * @author caron
 */
public class ReadGrid {

  public static void read( String filename) throws IOException {

    GridDataset gds = GridDataset.open (filename);
    GeoGrid grid = gds.findGridByName("Temperature");

    long startTime = System.currentTimeMillis();

    Array data = grid.readDataSlice(0, -1, -1, -1);

    long endTime = System.currentTimeMillis();
    long diff = endTime - startTime;
    System.out.println("read "+data.getSize()+"  took "+diff+ " msecs");

    startTime = endTime;
    float[] jdata = (float []) data.get1DJavaArray(float.class);
    endTime = System.currentTimeMillis();
    diff = endTime - startTime;
    System.out.println("convert took "+diff+ " msecs "+jdata[0]);
  }


  public static void main( String arg[]) throws IOException {
    String defaultFilename = "C:/data/grib/nam/conus12/NAM_CONUS_12km_20060604_1800.grib2";
    String filename = (arg.length > 0) ? arg[0] : defaultFilename;
    read(filename);
  }

}
