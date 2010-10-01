/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ooici.netcdf.iosp;

import com.rabbitmq.client.AMQP;
import ion.core.messaging.MsgBrokerClient;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.ma2.StructureDataIterator;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.ParsedSectionSpec;
import ucar.nc2.Structure;
import ucar.nc2.Variable;
import ucar.nc2.iosp.IospHelper;
import ooici.netcdf.iosp.messaging.AttributeStore;
import ucar.nc2.util.CancelTask;
import ucar.unidata.io.RandomAccessFile;

/**
 *  IOSP implementation for direct communication with the OOI-CI Exchange
 * <p>
 *
 * Unidata implementation tutorial: {@link http://www.unidata.ucar.edu/software/netcdf-java/tutorial/IOSPdetails.html}
 *
 * @author cmueller
 */
public class OOICIiosp implements ucar.nc2.iosp.IOServiceProvider, ucar.nc2.iosp.IOServiceProviderWriter {

    private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OOICIiosp.class);
    private NetcdfFile ncfile;
    private String uuid;
    private static String ooiciServer;
    private static String ooiciExchange;
    private static String ooiciSysname;
    private NetcdfFile tempnc;
    private MsgBrokerClient brokercl = null;
    private AttributeStore store = null;

    public static boolean init(java.util.Properties props) {
        boolean ret = false;
        ooiciServer = props.getProperty("server", "localhost");
        ooiciExchange = props.getProperty("exchange", "magnet.topic");
        ooiciSysname = props.getProperty("sysname", "localhost");

        ret = true;
        return ret;
    }

    public boolean isValidFile(RandomAccessFile raf) throws IOException {

        /**
         * Claim the file as "OOI-CI"
         */
        return raf.getLocation().toLowerCase().startsWith("ooici:");
    }

    public void open(RandomAccessFile raf, NetcdfFile ncfile, CancelTask cancelTask) throws IOException {
        /**
         * Open the connection to OOI-CI, register data objects as necessary, attach the broker
         */
        brokercl = new MsgBrokerClient(ooiciServer, AMQP.PROTOCOL.PORT, ooiciExchange);
        brokercl.attach();

        /* make attstore - TODO: temporary - to be replaced by data registry */
        store = new AttributeStore(ooiciSysname, brokercl);

        /* Set local variables and prepare ncfile object with all metadata: dimensions, variables, attributes, etc */
        this.ncfile = ncfile;
        this.uuid = raf.getLocation().substring(6);//trim the "ooici:" from the front to obtain the UUID

        buildFromMessages();

        this.ncfile.setTitle(uuid);
        this.ncfile.setId(uuid);

        this.ncfile.finish();
    }

    private void buildFromMessages() throws IOException {
        try {
            // dimensions
            List<String> ret = store.query(uuid + ":dim:.+");
            String sdat;
            for (String s : ret) {
                sdat = store.get(s);
                ncfile.addDimension(null, ((SerialDimension) IospUtils.deserialize(sdat)).getNcDimension());
            }
            ret.clear();
            ret = store.query(uuid + ":gatt:.+");
            for (String s : ret) {
                sdat = store.get(s);
                ncfile.addAttribute(null, ((SerialAttribute) IospUtils.deserialize(sdat)).getNcAttribute());
            }
            ret.clear();
            ret = store.query(uuid + ":var:.+");
            for (String s : ret) {
                sdat = store.get(s);
                ncfile.addVariable(null, ((SerialVariable) IospUtils.deserialize(sdat)).getNcVariable(ncfile));
            }
            ret.clear();
        } catch (FileNotFoundException ex) {
            throw new IOException("Can't find serial file", ex);
        } catch (ClassNotFoundException ex) {
            throw new IOException("Can't find class", ex);
        }
    }

    public Array readData(Variable v2, Section section) throws IOException, InvalidRangeException {
        /* Data seperate from variable - go get it! */
        try {
            String key = uuid + ":data:" + v2.getName();
            String dstr = store.get(key);
            SerialData sd;
            Array arr = null;
            if (dstr != null) {
                sd = (SerialData) IospUtils.deserialize(dstr);
                arr = sd.getNcArray();
            } else {
                /* The data for this variable is indexed!! */
                /* Determine which indexes to acquire */
                ucar.ma2.Range outer = section.getRange(0);
                ucar.ma2.Range.Iterator iter = outer.getIterator();
                String key2;
                Array tarr;
                int idx = 0;
                int fs = 1;
                while(iter.hasNext()) {
                    key2 = key + ":idx=" + iter.next();
                    dstr = store.get(key2);
                    sd = (SerialData) IospUtils.deserialize(dstr);
                    tarr = sd.getNcArray();
                    /* If the array is null, make it */
                    if(arr == null) {
                        int[] shape = tarr.getShape();
                        for(int i : shape) {
                            fs *= i;
                        }
                        shape[0] = outer.length();
                        arr = Array.factory(v2.getDataType(), shape);
                    }
                    /* Fill the appropriate section of the array */
                    Array.arraycopy(tarr, 0, arr, (fs * idx) , (int)tarr.getSize());
                    idx++;
                }

            }
            if(arr != null) {
                return arr.sectionNoReduce(section.getRanges());
            }
            return null;
        } catch (ClassNotFoundException ex) {
            throw new IOException("Could not deserialize data", ex);
        }
    }

    public long readToByteChannel(Variable v2, Section section, WritableByteChannel channel) throws IOException, InvalidRangeException {
        Array data = readData(v2, section);
        return IospHelper.copyToByteChannel(data, channel);
    }

    public Array readSection(ParsedSectionSpec cer) throws IOException, InvalidRangeException {
        return IospHelper.readSection(cer);
    }

    public StructureDataIterator getStructureIterator(Structure s, int bufferSize) throws IOException {
        return null;
    }

    public void close() throws IOException {
        /**
         * Close the connection to OOI-CI, detach the broker
         */
        if (brokercl != null) {
            brokercl.detach();
            brokercl = null;
        }
        if (store != null) {
            store = null;
        }
        if (tempnc != null) {
            tempnc.close();
            tempnc = null;
        }
    }

    public boolean syncExtend() throws IOException {
        return false;
    }

    public boolean sync() throws IOException {
        return false;
    }

    public Object sendIospMessage(Object message) {
        return null;
    }

    public String toStringDebug(Object o) {
        return "";
    }

    public String getDetailInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("OOI-CI Exchange: ").append(ooiciServer);
        return sb.toString();
    }

    public String getFileTypeId() {
        return "OOI-CI";
    }

    public String getFileTypeVersion() {
        return "N/A";
    }

    public String getFileTypeDescription() {
        return "OOI-CI distributed format";
    }
    /* Methods for Writing with this IOSP */
    private boolean fill = false;

    public void create(String filename, NetcdfFile ncfile, int extra, long preallocateSize, boolean largeFile) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setFill(boolean fill) {
        this.fill = fill;
    }

    public void writeData(Variable v2, Section section, Array values) throws IOException, InvalidRangeException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean rewriteHeader(boolean largeFile) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void updateAttribute(Variable v2, Attribute att) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void flush() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
