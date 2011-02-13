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

    /**
     * Other IOSP's check the length of the 'file' object which is null for this
     * IOSP - override to avoid NPE
     * @return
     * @throws IOException
     */
    @Override
    public long length() throws IOException {
        return 0;
    }
}
