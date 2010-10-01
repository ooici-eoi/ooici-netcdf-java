/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ooici.netcdf.iosp;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import ucar.ma2.DataType;

/**
 *
 * @author cmueller
 */
public class SerialVariable implements Serializable {

    private String name;
    private String datatype;
//    private String value;
    private List<SerialDimension> dimensions;
    private List<SerialAttribute> attributes;
    private String dimString;
    private boolean isScalar;

    public SerialVariable(ucar.nc2.Variable var) throws IOException {
        name = var.getName();
        datatype = var.getDataType().name();
        dimString = var.getDimensionsString();
        isScalar = var.isScalar();

        attributes = new ArrayList<SerialAttribute>();
        for (ucar.nc2.Attribute a : var.getAttributes()) {
            attributes.add(new SerialAttribute(a));
        }

        dimensions = new ArrayList<SerialDimension>();
        for (ucar.nc2.Dimension d : var.getDimensions()) {
            dimensions.add(new SerialDimension(d));
        }
//        if (var.getShape().length > 1) {
//            value = IospUtils.serialize(var.read().copyToNDJavaArray());
//        } else if (var.getShape().length == 1) {
//            value = IospUtils.serialize(var.read().copyTo1DJavaArray());
//        } else {
//            value = IospUtils.serialize(var.read().getObject(0));
//        }
    }

    public ucar.nc2.Variable getNcVariable(ucar.nc2.NetcdfFile ncfile) throws IOException, ClassNotFoundException {
        ucar.nc2.Variable var = new ucar.nc2.Variable(ncfile, null, null, name);
        ucar.ma2.DataType dt = DataType.getType(datatype);
        var.setDataType(dt);
        var.setDimensions(dimString);
        for (SerialAttribute sa : attributes) {
            var.addAttribute(sa.getNcAttribute());
        }
//        ucar.ma2.Array arr = null;
//        Object dataobj = IospUtils.deserialize(value);
//        if (isScalar) {
//            var.setIsScalar();
//            switch (dt) {
//                case BOOLEAN:
//                    arr = new ucar.ma2.ArrayBoolean.D0();
//                    arr.setBoolean(0, (Boolean) dataobj);
//                    break;
//                case BYTE:
//                    arr = new ucar.ma2.ArrayByte.D0();
//                    arr.setByte(0, (Byte) dataobj);
//                    break;
//                case CHAR:
//                case STRING:
//                    arr = new ucar.ma2.ArrayChar.D0();
//                    arr.setChar(0, (Character) dataobj);
//                    break;
//                case DOUBLE:
//                    arr = new ucar.ma2.ArrayDouble.D0();
//                    arr.setDouble(0, (Double) dataobj);
//                    break;
//                case FLOAT:
//                    arr = new ucar.ma2.ArrayFloat.D0();
//                    arr.setFloat(0, (Float) dataobj);
//                    break;
//                case INT:
//                    arr = new ucar.ma2.ArrayInt.D0();
//                    arr.setInt(0, (Integer) dataobj);
//                    break;
//                case LONG:
//                    arr = new ucar.ma2.ArrayLong.D0();
//                    arr.setLong(0, (Long) dataobj);
//                    break;
//                case SHORT:
//                    arr = new ucar.ma2.ArrayShort.D0();
//                    arr.setShort(0, (Short) dataobj);
//                    break;
//            }
//        } else {
//            arr = ucar.ma2.Array.factory(dataobj);
//        }
//        if(arr != null) {
//            var.setCachedData(arr);
//        }
        return var;
    }

    @Override
    public String toString() {
        return name + " {" + datatype + ", " + ((isScalar) ? "[isScalar]" : dimString) + "}";
    }
}
