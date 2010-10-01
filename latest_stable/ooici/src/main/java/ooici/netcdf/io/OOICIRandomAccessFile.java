/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ooici.netcdf.io;

import java.io.IOException;

/**
 *
 * @author cmueller
 */
public class OOICIRandomAccessFile extends ucar.unidata.io.RandomAccessFile {
    public OOICIRandomAccessFile(String location) {
        super(0);
        this.location = location;
    }

    @Override
    protected int read_(long pos, byte[] b, int offset, int len) throws IOException {
        return 0;
    }
}
