// $Id: NcDAS.java 51 2006-07-12 17:13:13Z caron $
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

package thredds.server.opendap;

import ucar.nc2.*;
import ucar.nc2.dods.*;
import ucar.unidata.util.StringUtil;
import ucar.ma2.DataType;

import java.util.Iterator;
import java.util.HashMap;
import java.util.List;

import opendap.dap.AttributeExistsException;

/**
 * Netcdf DAS object
 *
 * @version $Revision: 51 $
 * @author jcaron
 */

public class NcDAS extends opendap.dap.DAS {
  static private org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NcDAS.class);

  HashMap usedDims = new HashMap();

  /** Create a DAS for this netcdf file */
  NcDAS( NetcdfFile ncfile ) {

    // Variable attributes
    Iterator iter = ncfile.getVariables().iterator();
    while (iter.hasNext()) {
      Variable v = (Variable) iter.next();
      doVariable(v, null);
    }

    // Global attributes
    opendap.dap.AttributeTable gtable = new opendap.dap.AttributeTable("NC_GLOBAL");
    int count = addAttributes(gtable, null, ncfile.getGlobalAttributes().iterator());
    if (count > 0)
      try {
        addAttributeTable("NC_GLOBAL", gtable);
      } catch (AttributeExistsException e) {
        log.error("Cant add NC_GLOBAL", e);
      }

    // unlimited  dimension
    iter = ncfile.getDimensions().iterator();
    while (iter.hasNext()) {
      Dimension d = (Dimension) iter.next();
      if (d.isUnlimited()) {
        opendap.dap.AttributeTable table = new opendap.dap.AttributeTable("DODS_EXTRA");
        try {
          table.appendAttribute("Unlimited_Dimension", opendap.dap.Attribute.STRING, d.getName());
          addAttributeTable("DODS_EXTRA", table);
        } catch (Exception e) {
          log.error("Error adding Unlimited_Dimension ="+e);
        }
        break;
      }
    }

    // unused dimensions
    opendap.dap.AttributeTable dimTable = null;
    iter = ncfile.getDimensions().iterator();
    while (iter.hasNext()) {
      Dimension d = (Dimension) iter.next();
      if (null == usedDims.get(d.getName())) {
        if (dimTable == null) dimTable = new opendap.dap.AttributeTable("EXTRA_DIMENSION");
        try {
          dimTable.appendAttribute(d.getName(), opendap.dap.Attribute.INT32, Integer.toString(d.getLength()));
        } catch (Exception e) {
          log.error("Error adding Unlimited_Dimension ="+e);
        }
      }
    }
    if (dimTable != null)
      try {
        addAttributeTable("EXTRA_DIMENSION", dimTable);
      } catch (AttributeExistsException e) {
        log.error("Cant add EXTRA_DIMENSION", e);
      }

  }

  private void doVariable( Variable v, opendap.dap.AttributeTable parentTable) {

    List dims = v.getDimensions();
    for (int i = 0; i < dims.size(); i++) {
      Dimension dim = (Dimension) dims.get(i);
      if (dim.isShared())
        usedDims.put( dim.getName(), dim);
    }

    //if (v.getAttributes().size() == 0) return; // LOOK DAP 2 say must have empty

    String name = NcDDS.escapeName(v.getShortName());
    opendap.dap.AttributeTable table;

    if (parentTable == null) {
      table = new opendap.dap.AttributeTable(name);
      try {
        addAttributeTable(name, table);
      } catch (AttributeExistsException e) {
        log.error("Cant add "+name, e);
      }
    } else {
      table =  parentTable.appendContainer(name);
    }

    addAttributes(table, v, v.getAttributes().iterator());

    if (v instanceof Structure) {
      Structure s = (Structure) v;
      List nested = s.getVariables();
      for (int i = 0; i < nested.size(); i++) {
        Variable nv = (Variable) nested.get(i);
        doVariable( nv, table);
      }
    }


  }

  private int addAttributes(opendap.dap.AttributeTable table, Variable v, Iterator iter) {
    int count = 0;

    // add attribute table for this variable
    while (iter.hasNext()) {
      Attribute att = (Attribute) iter.next();
      int dods_type = DODSNetcdfFile.convertToDODSType( att.getDataType(), false);

      try {
        String attName = NcDDS.escapeName(att.getName());
        if (att.isString()) {
          String value = escapeAttributeStringValues(att.getStringValue());
          table.appendAttribute(attName, dods_type, "\""+value+"\"");

        } else {
          // cant send signed bytes
          if (att.getDataType() == DataType.BYTE) {
            boolean signed = false;
            for (int i=0; i< att.getLength(); i++) {
              if (att.getNumericValue(i).byteValue() < 0)
                signed = true;
            }
            if (signed) // promote to signed short
              dods_type = opendap.dap.Attribute.INT16;
          }

          for (int i=0; i< att.getLength(); i++)
            table.appendAttribute( attName, dods_type, att.getNumericValue(i).toString());
        }
        count++;

      } catch (Exception e) {
        log.error("Error appending attribute "+att.getName()+" = "+att.getStringValue()+"\n"+e);
      }
    } // loop over variable attributes

    // kludgy thing to map char arrays to DODS Strings
    if ((v != null) && (v.getDataType().getPrimitiveClassType() == char.class)) {
      int rank = v.getRank();
      int strlen = (rank == 0) ? 0 : v.getShape(rank-1);
      Dimension dim = (rank == 0) ? null : v.getDimension( rank-1);
      try {
        opendap.dap.AttributeTable dodsTable = table.appendContainer("DODS");
        dodsTable.appendAttribute("strlen", opendap.dap.Attribute.INT32, Integer.toString(strlen));
        if ((dim != null) && dim.isShared())
          dodsTable.appendAttribute("dimName", opendap.dap.Attribute.STRING, dim.getName());
        count++;
      } catch (Exception e) {
        log.error("Error appending attribute strlen\n"+e);
      }
    }

    return count;
  }

  static private String[] escapeAttributeStrings = {"\\", "\"" };
  static private String[] substAttributeStrings = {"\\\\", "\\\"" };
  private String escapeAttributeStringValues( String value) {
    return StringUtil.substitute(value, escapeAttributeStrings, substAttributeStrings);
  }

  private String unescapeAttributeStringValues( String value) {
    return StringUtil.substitute(value, substAttributeStrings, escapeAttributeStrings);
  }

}

/* Change History:
   $Log: NcDAS.java,v $
   Revision 1.11  2006/04/20 22:25:21  caron
   opendap server: handle name escaping consistently
   rename, reorganize servlets
   update Paths doc

   Revision 1.10  2006/01/20 20:42:02  caron
   convert logging
   use nj22 libs

   Revision 1.9  2005/11/11 02:17:27  caron
   NcML Aggregation

   Revision 1.8  2005/08/26 22:48:31  caron
   fix catalog.xsd 
   bug in NcDAS: addds extra dimensions
   add "ecutiry" debug page

   Revision 1.7  2005/07/27 23:25:37  caron
   ncdods refactor, add Structure (2)

   Revision 1.6  2005/07/24 01:28:13  caron
   clean up logging
   add variable description
   move WCSServlet to thredds.wcs.servlet

   Revision 1.5  2005/04/12 21:58:40  caron
   add EXTRA_DIMENSION attribute group

   Revision 1.4  2005/01/21 00:58:11  caron
   *** empty log message ***

   Revision 1.3  2005/01/07 02:08:44  caron
   use nj22, commons logging, clean up javadoc

   Revision 1.2  2004/09/24 03:26:25  caron
   merge nj22

   Revision 1.1.1.1  2004/03/19 19:48:31  caron
   move AS code here

   Revision 1.3  2002/12/20 20:42:03  caron
   catalog, bug fixes

   Revision 1.2  2002/09/13 21:16:44  caron
   version 0.6

   Revision 1.1.1.1  2001/09/26 15:34:30  caron
   checkin beta1


 */
