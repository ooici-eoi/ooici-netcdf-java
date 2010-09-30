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

// $Id: Grib1IndexExtender.java,v 1.14 2006/08/18 20:21:59 rkambic Exp $


package ucar.grib.grib1;


import ucar.grib.*;
import ucar.grib.grib2.Grib2WriteIndex;

import ucar.unidata.io.RandomAccessFile;

import java.io.BufferedOutputStream;  // Input/Output functions
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;      // Input/Output functions
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;           // Input/Output functions

import java.lang.*;                   // Standard java functions

import java.net.URL;

import java.util.*;                   // Extra utilities from sun
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * extends the index of a Grib file that is growing in size.
 * see <a href="../../../IndexFormat.txt"> IndexFormat.txt</a>
 * @deprecated
 * @author Robb Kambic  06/13/05
 * @version 1.0
 */
public final class Grib1IndexExtender extends Grib1Indexer {

  /**
   * _more_
   */
  private final boolean showTime = false;

  /**
   * length of Grib file from the original index file.
   */
  private long indexLength = 0;

  /**
   * PDS from the original index file.
   */
  private final ArrayList origPds = new ArrayList();

  /**
   * GDS from the original index file.
   */
  private final HashMap origGds = new HashMap();

  /**
   * Pattern to get length out of attributes index section.
   */
  private static final Pattern idxlength =
          Pattern.compile("length = (\\d+)$");

  /**
   * Pattern to get GDSkey out of GDS index section.
   */
  private static final Pattern gdskey = Pattern.compile("GDSkey = (\\d+)$");

  /**
   * Pattern to get end of data section.
   */
  private static final Pattern endOfData =
          Pattern.compile("\\s(\\d*)\\s(\\d*)\\s(true|false)");

  /**
   * where to start in the file looking for new data records.
   */
  private long startSeek = 0;

  /**
   * section delimiter for the index file.
   */
  private final String delimiter =
          "--------------------------------------------------------------------";

  /**
   * Read/Update a Grib file index.
   *
   * @param inputRaf  GRIB file
   * @param indexFile
   * @param index
   * @return Index
   * @throws IOException
   */

  public final Index extendIndex(RandomAccessFile inputRaf, File indexFile, Index index) throws IOException {

    /**
     * date format "yyyy-MM-dd'T'HH:mm:ss'Z'".
     */
    final java.text.SimpleDateFormat dateFormat;
    dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));  //same as UTC

    Date now = Calendar.getInstance().getTime();
    //System.out.println(now.toString() + " ... Start of Grib1IndexExtender");

    long start = System.currentTimeMillis();
    int count = 0;
    // set buffer size for performance
    int rafBufferSize = inputRaf.getBufferSize();
    inputRaf.setBufferSize( Grib2WriteIndex.indexRafBufferSize );

    PrintStream ps = null;
    File tmp = null;
    boolean rename = false;

    // Opening of grib data must be inside a try-catch block
    try {
      // first get PDS and GDS stuff from original index for insertion later
      getPdsAndGdsFromOrigIndex(indexFile);

      // return if Grib file length from index = raf.length()
      if (indexLength == inputRaf.length()) {
        //System.out.println( "Grib file length from index = raf.length()");
        return index;
      }

      // start at end of last record looking for new info
      inputRaf.seek(startSeek);
      // Create Grib1Input instance
      Grib1Input g1i = new Grib1Input(inputRaf);
      // params getProducts (implies  unique GDSs too), oneRecord
      g1i.scan(true, false);

      // open a temporary index file to extend index, then rename
      if (indexFile.getParent() == null) {
        tmp = new File("." + indexFile.getName());
      } else {
        tmp = new File(indexFile.getParent() + "/." + indexFile.getName());
      }
      //System.out.println( "tmp ="+ tmp.getPath() );
      ps = new PrintStream(
              new BufferedOutputStream(
                      new FileOutputStream(tmp, false)));

      // Section 1 Global attributes
      // while needed here to process complete stream
      ps.println("index_version = " + GribReadTextIndex.currentTextIndexVersion);
      ps.println("grid_edition = " + 1);
      ps.println("location = " + inputRaf.getLocation().replaceAll( " ", "%20"));
      ps.println("length = " + inputRaf.length());
      ps.println("created = " + dateFormat.format(now));
      //ps.println("version = 1.0");
      if (index != null) {
        index.addGlobalAttribute("length", Long.toString(inputRaf.length()));
        index.addGlobalAttribute("location", inputRaf.getLocation().replaceAll( " ", "%20"));
        index.addGlobalAttribute("created", dateFormat.format(now));
        //index.addGlobalAttribute("version", "1.0");
      }

      // section 2 grib records
      HashMap gdsHM = g1i.getGDSs();  // needed for tiled, thin grids check

      ArrayList products = g1i.getProducts();
      if ( products.size() == 0 ) {  // no new data
        ps.close();
        ps = null;
        tmp.delete();
        return null;
      }
      for (int i = 0; i < products.size(); i++) {
        Grib1Product product = (Grib1Product) products.get(i);
        Grib1ProductDefinitionSection pds = product.getPDS();
        if (i == 0) {  // finish writing header and original Pds info
          ps.println("center = " + pds.getCenter());
          ps.println("sub_center = " + pds.getSubCenter());
          ps.println("table_version = " + pds.getTableVersion());
          checkForTiledThinEnsemble((index != null), index, pds,
                  gdsHM, origGds, ps);
          ps.println(delimiter);
          for (int j = 0; j < origPds.size(); j++) {
            ps.println((String) origPds.get(j));
          }
        }
        ps.println(pds.getProductDefinition() + " "
                + product.getDiscipline() + " "
                + product.getCategory() + " "
                + pds.getParameter().getNumber() + " "
                + pds.getTypeGenProcess() + " "
                + pds.getLevelType() + " " + pds.getLevelValue1()
                + " " + "255 " +  // Grib 1 doesn't have 2 level types
                pds.getLevelValue2() + " " + dateFormat.format(pds.getBaseTime()) + " "
                + pds.getForecastTime() + " "
                + product.getGDSkey() + " "
                + product.getOffset1() + " "
                + product.getOffset2() + " "
                + pds.getDecimalScale() + " "
                + pds.bmsExists() + " "
                + pds.getCenter() + " "
                + pds.getSubCenter() + " "
                + pds.getTableVersion());


        if (index != null) {
          index.addGribRecord(makeGribRecord(index, product));
        }
        count++;
      }

      // section 3: GDSs in this File, skipping already entered ones
      //  first print out original GDS from orig Index
      for (Iterator it = origGds.keySet().iterator(); it.hasNext();) {
        ps.println(delimiter);
        String key = (String) it.next();
        ps.print((String) origGds.get(key));
      }

      for (Iterator it = gdsHM.keySet().iterator(); it.hasNext();) {
        String key = (String) it.next();
        if (origGds.containsKey(key)) {  // already have this GDS
          continue;
        }
        Grib1GridDefinitionSection gds =
                (Grib1GridDefinitionSection) gdsHM.get(key);
        // only lat/lon grids can have > 1 GDS
        if ((gds.getGridType() == 0) || (gds.getGridType() == 4)) {
          ps.println(delimiter);
          ps.println("GDSkey = " + key);
          printGDS(gds, ps);
          if (index != null) {
            index.addHorizCoordSys(makeGdsRecord(gds));
          }
        }
      }
      rename = true;

    } catch (IOException ioe) {
      throw ioe;

    } catch (Exception e) {
      throw new RuntimeException(e);

    } finally {
      //reset
      inputRaf.setBufferSize( rafBufferSize );
      if (ps != null) {
        ps.close();
        if (rename && indexFile.delete() )
          if ( !tmp.renameTo(indexFile))
            throw new IOException("Failed to rename indexFile to "+indexFile.getPath());
      }
    }

    // Goodbye message
    //now = Calendar.getInstance().getTime();
    //ps.println(now.toString() + " ... End of Grib1IndexExtender!");
    if (showTime) {
      System.out.println(" " + count + " products took "
              + (System.currentTimeMillis() - start)
              + " msec");
    }

    return index;
  }  // end extendIndex

  /**
   * _more_
   *
   * @param indexFile _more_
   * @throws IOException _more_
   */
  private void getPdsAndGdsFromOrigIndex(File indexFile)
          throws IOException {
    open(indexFile.getPath());
  }

  /**
   * Constructor for scanning a Grib Index file.
   *
   * @param location URL or local filename of Grib Index file
   * @throws IOException
   */
  private void open(String location) throws IOException {
    InputStream ios = null;
    if (location.startsWith("http:")) {
      URL url = new URL(location);
      ios = url.openStream();
    } else {
      ios = new FileInputStream(location);
    }
    open(ios);
  }

  /**
   * _more_
   *
   * @param ios _more_
   * @throws IOException _more_
   */
  private void open(InputStream ios) throws IOException {

    BufferedReader dataIS =
            new BufferedReader(new InputStreamReader(ios));
    String lastLine = "";

    // section 1 - global attributes
    while (true) {
      String line = dataIS.readLine();
      if (line == null) {
        break;
      }
      Matcher m = idxlength.matcher(line);
      if (m.find()) {
        indexLength = Long.parseLong(m.group(1));
        //System.out.println( "indexLength = " + indexLength );
      }
      if (line.startsWith("--")) {
        break;
      }
    }
    // section 2 - get PDS
    while (true) {
      String line = dataIS.readLine();
      if (line == null) {
        break;
      }
      if (line.startsWith("--")) {
        break;
      }
      origPds.add(line);
      lastLine = line;
    }
    // obtain last record read getting end of data section offset
    Matcher m = endOfData.matcher(lastLine);
    //System.out.println( "lastLine =" + lastLine );
    if (m.find()) {
      startSeek = Long.parseLong(m.group(1));
    }
    //System.out.println( "startSeek =" + startSeek );

    // section 3 - get GDS
    StringBuffer gdsBuffer = new StringBuffer();
    String gdsKey = "";
    while (true) {
      String line = dataIS.readLine();
      if (line == null) {
        break;
      }
      // get GDS key
      m = gdskey.matcher(line);
      if (m.find()) {
        gdsKey = m.group(1);
        //System.out.println( "gdsKey = " + gdsKey );
      }
      if (line.startsWith("--")) {
        origGds.put(gdsKey, gdsBuffer.toString());
        //System.out.println( "gdsBuffer = \n" + gdsBuffer.toString());
        gdsBuffer.delete(0, gdsBuffer.length());
        gdsKey = "";
      } else {
        gdsBuffer.append(line).append("\n");
      }
    }
    // no delimiter for last GDS so catch
    origGds.put(gdsKey, gdsBuffer.toString());
    //System.out.println( "gdsBuffer = \n" + gdsBuffer.toString());
    dataIS.close();
  }

  /**
   * Dumps usage of the class, if called without arguments.
   *
   * @param className Grib1IndexExtender
   */
  private static void usage(String className) {
    System.out.println();
    System.out.println("Usage of " + className + ":");
    System.out.println("Parameters:");
    System.out.println("<GribFileToRead> reads/scans for index");
    System.out.println();
    System.out.println("java -Xmx256m " + className
            + " <GribFileToRead>");
    System.exit(0);
  }

  /**
   * Creates a new index if the Grib file is growing in size.
   *
   * @param args name of the grib file
   * @throws IOException
   */
  public static void main(String args[]) throws IOException {

    // Function References
    Grib1IndexExtender indexExt = new Grib1IndexExtender();

    // Test usage
    if (args.length < 1) {
      // Get class name as String
      Class cl = indexExt.getClass();
      usage(cl.getName());
      System.exit(0);
    }

    RandomAccessFile raf = new RandomAccessFile(args[0], "r");
    raf.order(RandomAccessFile.BIG_ENDIAN);
    File indexFile = new File(args[0].concat(".gbx"));
    Index index = null;

    if (indexFile.canWrite()) {
      //System.out.println("do IndexExtender");
      Index idxExt = indexExt.extendIndex(raf, indexFile, index);
    } else {
      System.err.println("index open failed, no write permission");
      System.exit(0);
    }
  }
}  // end Grib1IndexExtender


