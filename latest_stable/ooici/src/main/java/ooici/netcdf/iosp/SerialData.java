/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ooici.netcdf.iosp;

import java.io.IOException;
import java.io.Serializable;

/**
 *
 * @author cmueller
 * @deprecated replaced by ion-object implementation
 */
@Deprecated
public class SerialData implements Serializable {

    private String sData;
    private String datatype;
    private boolean isScalar;

    public SerialData(ucar.ma2.DataType dt, ucar.ma2.Array arr) throws IOException {
        datatype = dt.name();
        isScalar = false;
        if (arr.getShape().length > 1) {
            sData = IospUtils.serialize(arr.copyToNDJavaArray());
        } else if (arr.getShape().length == 1) {
            sData = IospUtils.serialize(arr.copyTo1DJavaArray());
        } else {
            isScalar = true;
            sData = IospUtils.serialize(arr.getObject(0));
        }
    }

    public ucar.ma2.Array getNcArray() throws IOException, ClassNotFoundException {
        ucar.ma2.Array ret = null;
        Object dataobj = IospUtils.deserialize(sData);
        if (isScalar) {
            switch (ucar.ma2.DataType.getType(datatype)) {
                case BOOLEAN:
                    ret = new ucar.ma2.ArrayBoolean.D0();
                    ret.setBoolean(0, (Boolean) dataobj);
                    break;
                case BYTE:
                    ret = new ucar.ma2.ArrayByte.D0();
                    ret.setByte(0, (Byte) dataobj);
                    break;
                case CHAR:
                case STRING:
                    ret = new ucar.ma2.ArrayChar.D0();
                    ret.setChar(0, (Character) dataobj);
                    break;
                case DOUBLE:
                    ret = new ucar.ma2.ArrayDouble.D0();
                    ret.setDouble(0, (Double) dataobj);
                    break;
                case FLOAT:
                    ret = new ucar.ma2.ArrayFloat.D0();
                    ret.setFloat(0, (Float) dataobj);
                    break;
                case INT:
                    ret = new ucar.ma2.ArrayInt.D0();
                    ret.setInt(0, (Integer) dataobj);
                    break;
                case LONG:
                    ret = new ucar.ma2.ArrayLong.D0();
                    ret.setLong(0, (Long) dataobj);
                    break;
                case SHORT:
                    ret = new ucar.ma2.ArrayShort.D0();
                    ret.setShort(0, (Short) dataobj);
                    break;
            }
        } else {
            ret = ucar.ma2.Array.factory(dataobj);
        }
        return ret;
    }
}
