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
 */
public class SerialAttribute implements Serializable {

    private String name;
    private String datatype;
    private String value;

    public SerialAttribute(ucar.nc2.Attribute att) throws IOException {
        name = att.getName();
        datatype = att.getDataType().name();
        switch (att.getDataType()) {
            case STRING:
                value = att.getStringValue();
                break;
            default:
                value = IospUtils.serialize(att.getValues().copyToNDJavaArray());
                break;
        }
    }

    public ucar.nc2.Attribute getNcAttribute() throws IOException, ClassNotFoundException {
        ucar.nc2.Attribute ret = null;
        switch(ucar.ma2.DataType.getType(datatype)) {
            case STRING:
                ret = new ucar.nc2.Attribute(name, value);
                break;
            default:
                ret = new ucar.nc2.Attribute(name, ucar.ma2.Array.factory(IospUtils.deserialize(value)));
                break;
        }
        return ret;
    }

    @Override
    public String toString() {
        return name + "{" + datatype + "} = " + value;
    }
}
