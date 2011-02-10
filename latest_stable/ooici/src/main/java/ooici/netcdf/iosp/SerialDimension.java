/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ooici.netcdf.iosp;

import java.io.Serializable;

/**
 *
 * @author cmueller
 * @deprecated replaced by ion-object implementation
 */
@Deprecated
public class SerialDimension implements Serializable {

    private String name;
    private int length;
    private boolean isShared;
    private boolean isUnlim;
    private boolean isVarLen;

    public SerialDimension(ucar.nc2.Dimension dim) {
        name = dim.getName();
        length = dim.getLength();
        isShared = dim.isShared();
        isUnlim = dim.isUnlimited();
        isVarLen = dim.isVariableLength();
    }

    public ucar.nc2.Dimension getNcDimension() {
        return new ucar.nc2.Dimension(name, length, isShared, isUnlim, isVarLen);
    }

    @Override
    public String toString() {
        return name + "{" + length + ", " + isShared + ", " + isUnlim + ", " + isVarLen + "}";
    }
}
