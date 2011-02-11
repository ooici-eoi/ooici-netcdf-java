/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ooici.netcdf.iosp;

import com.google.protobuf.InvalidProtocolBufferException;
import com.rabbitmq.client.AMQP;
import ion.core.messaging.MessagingName;
import ion.core.messaging.MsgBrokerClient;
import ion.core.utils.GPBWrapper;
import ion.core.utils.StructureManager;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.List;
import net.ooici.cdm.syntactic.Cdmarray;
import net.ooici.cdm.syntactic.Cdmattribute;
import net.ooici.cdm.syntactic.Cdmdatatype;
import net.ooici.cdm.syntactic.Cdmdimension;
import net.ooici.cdm.syntactic.Cdmgroup;
import net.ooici.cdm.syntactic.Cdmvariable;
import net.ooici.core.link.Link;
import net.ooici.data.cdm.Cdmdataset;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.ma2.StructureDataIterator;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.ParsedSectionSpec;
import ucar.nc2.Structure;
import ucar.nc2.Variable;
import ucar.nc2.iosp.IospHelper;
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
    private String datasetID;
    private static String ooiciServer = "localhost";
    private static String ooiciExchange = "eoitest";
    private static String ooiciService = "eoi_ingest";
    private static String ooiciTopic = "magnet.topic";
    private NetcdfFile tempnc;
    private MsgBrokerClient brokercl = null;
    private ion.core.messaging.MessagingName ooiToName;
    private ion.core.messaging.MessagingName ooiMyName;
    private String myQueue;
    private StructureManager structManager;
    private HashMap<Variable, Cdmvariable.Variable> _varMap;

    public static boolean init(java.util.HashMap<String, String> connInfo) {
        boolean ret = false;
        ooiciServer = connInfo.get("server");
        ooiciExchange = connInfo.get("exchange");
        ooiciService = connInfo.get("service");
        ooiciTopic = connInfo.get("topic");

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
        ooiToName = new MessagingName(ooiciExchange, ooiciService);
        ooiMyName = MessagingName.generateUniqueName();
        brokercl = new MsgBrokerClient(ooiciServer, AMQP.PROTOCOL.PORT, ooiciTopic);
        brokercl.attach();
        myQueue = brokercl.declareQueue(null);
        brokercl.bindQueue(myQueue, ooiMyName, null);
        brokercl.attachConsumer(myQueue);

        /* Set local variables and prepare ncfile object with all metadata: dimensions, variables, attributes, etc */
        this.ncfile = ncfile;
        this.datasetID = raf.getLocation().substring(6);//trim the "ooici:" from the front to obtain the UUID

        buildFromMessages();

        this.ncfile.setTitle(datasetID);
        this.ncfile.setId(datasetID);

        this.ncfile.finish();
    }

    private void buildFromMessages() throws IOException {
        ion.core.messaging.IonMessage msg = getResource(datasetID);
        String status = msg.getIonHeaders().get("status").toString();
        if (status.equalsIgnoreCase("ok")) {
            if (msg.getContent() instanceof byte[]) {
                structManager = StructureManager.Factory(msg);
                GPBWrapper<Cdmdataset.Dataset> dsWrap = structManager.getObjectWrapper(structManager.getHeadIds().get(0));
                Cdmdataset.Dataset dataset = dsWrap.getObjectValue();

                GPBWrapper<Cdmgroup.Group> rootWrap = structManager.getObjectWrapper(dataset.getRootGroup());
                Cdmgroup.Group rootGroup = rootWrap.getObjectValue();
                for(Link.CASRef cref : rootGroup.getDimensionsList()) {
                    ncfile.addDimension(null, getNcDimension(cref));
                }
                for (Link.CASRef cref : rootGroup.getAttributesList()) {
                    ncfile.addAttribute(null, getNcAttribute(cref));
                }
                /* Initialize the varMap */
                _varMap = new HashMap<Variable, Cdmvariable.Variable>();
                /* Process the variables */
                for (Link.CASRef cref : rootGroup.getVariablesList()) {
                    try {
                        Variable v = getNcVariable(cref);
                        ncfile.addVariable(null, v);
                    } catch (InvalidRangeException ex) {
                        throw new IOException("Error building variable, cannot continue", ex);
                    }
                }
            }
        } else if (status.equalsIgnoreCase("error")) {
            throw new IOException("Error receiving message: " + "something descriptive about what happened, from the msg");
        }
    }

    public Array readData(Variable v2, Section section) throws IOException, InvalidRangeException {
        Cdmvariable.Variable ooiVar = _varMap.get(v2);

        int[] shape = new int[ooiVar.getShapeCount()];
        int i = 0;
        for (Link.CASRef vRef : ooiVar.getShapeList()) {
            Cdmdimension.Dimension dim = (Cdmdimension.Dimension) structManager.getObjectWrapper(vRef).getObjectValue();
            shape[i++] = (int) dim.getLength();
        }
        ucar.ma2.Array arr = null;
        for (Link.CASRef vRef : ooiVar.getContentList()) {
            arr = getNcArray(vRef, ooiVar.getDataType(), shape);
            v2.setCachedData(arr);
        }
        if (arr != null) {
            return arr.sectionNoReduce(section.getRanges());
        }
        return arr;

//        throw new UnsupportedOperationException();


        /* Data seperate from variable - go get it! */
//        try {
//            String key = datasetID + ":data:" + v2.getName();
//            String dstr = store.get(key);
//            SerialData sd;
//            Array arr = null;
//            if (dstr != null) {
//                sd = (SerialData) IospUtils.deserialize(dstr);
//                arr = sd.getNcArray();
//            } else {
//                /* The data for this variable is indexed!! */
//                /* Determine which indexes to acquire */
//                ucar.ma2.Range outer = section.getRange(0);
//                ucar.ma2.Range.Iterator iter = outer.getIterator();
//                String key2;
//                Array tarr;
//                int idx = 0;
//                int fs = 1;
//                while (iter.hasNext()) {
//                    key2 = key + ":idx=" + iter.next();
//                    dstr = store.get(key2);
//                    sd = (SerialData) IospUtils.deserialize(dstr);
//                    tarr = sd.getNcArray();
//                    /* If the array is null, make it */
//                    if (arr == null) {
//                        int[] shape = tarr.getShape();
//                        for (int i : shape) {
//                            fs *= i;
//                        }
//                        shape[0] = outer.length();
//                        arr = Array.factory(v2.getDataType(), shape);
//                    }
//                    /* Fill the appropriate section of the array */
//                    Array.arraycopy(tarr, 0, arr, (fs * idx), (int) tarr.getSize());
//                    idx++;
//                }
//
//            }
//            if (arr != null) {
//                return arr.sectionNoReduce(section.getRanges());
//            }
//            return null;
//        } catch (ClassNotFoundException ex) {
//            throw new IOException("Could not deserialize data", ex);
//        }
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

    // <editor-fold defaultstate="collapsed" desc="OOI-CI Helper Methods">
    private ion.core.messaging.IonMessage getResource(String ooiResourceID) {
        brokercl.createSendMessage(ooiMyName, ooiToName, "retrieve", ooiResourceID);
        return brokercl.consumeMessage(myQueue);
    }

    private Dimension getNcDimension(Link.CASRef ref) {
        GPBWrapper<Cdmdimension.Dimension> dimWrap = structManager.getObjectWrapper(ref);
        Cdmdimension.Dimension ooiDim = dimWrap.getObjectValue();
        return new Dimension(ooiDim.getName(), (int) ooiDim.getLength());
    }

    private Attribute getNcAttribute(Link.CASRef ref) {
        GPBWrapper<Cdmattribute.Attribute> attWrap = structManager.getObjectWrapper(ref);
        Cdmattribute.Attribute ooiAtt = attWrap.getObjectValue();
        Attribute ncAtt = null;
        int cnt = 0;
        GPBWrapper arrWrap = structManager.getObjectWrapper(ooiAtt.getArray());
        switch(ooiAtt.getDataType()) {
            case STRING:
                Cdmarray.stringArray sarr = (Cdmarray.stringArray) arrWrap.getObjectValue();
                if (sarr.getValueCount() == 1) {
                    ncAtt = new Attribute(ooiAtt.getName(), sarr.getValue(0));
                } else {
                    /* Is this possible?? */
                }
                break;
            case BYTE:
                Cdmarray.int32Array i32arrB = (Cdmarray.int32Array) arrWrap.getObjectValue();
                ucar.ma2.ArrayByte barr = new ucar.ma2.ArrayByte(new int[]{i32arrB.getValueCount()});
                for (Integer val : i32arrB.getValueList()) {
                    barr.setByte(cnt++, val.byteValue());
                }
                if (barr.getSize() == 1) {
                    ncAtt = new Attribute(ooiAtt.getName(), barr.getByte(0));
                } else {
                    ncAtt = new Attribute(ooiAtt.getName(), barr);
                }
                break;
            case SHORT:
                Cdmarray.int32Array i32arrS = (Cdmarray.int32Array) arrWrap.getObjectValue();
                ucar.ma2.ArrayShort sharr = new ucar.ma2.ArrayShort(new int[]{i32arrS.getValueCount()});
                for (Integer val : i32arrS.getValueList()) {
                    sharr.setShort(cnt++, val.shortValue());
                }
                if (sharr.getSize() == 1) {
                    ncAtt = new Attribute(ooiAtt.getName(), sharr.getShort(0));
                } else {
                    ncAtt = new Attribute(ooiAtt.getName(), sharr);
                }
                break;
            case INT:
                Cdmarray.int32Array i32arr = (Cdmarray.int32Array) arrWrap.getObjectValue();
                ucar.ma2.ArrayInt iarr = new ucar.ma2.ArrayInt(new int[]{i32arr.getValueCount()});
                for (Integer val : i32arr.getValueList()) {
                    iarr.setInt(cnt++, val);
                }
                if (iarr.getSize() == 1) {
                    ncAtt = new Attribute(ooiAtt.getName(), iarr.getInt(0));
                } else {
                    ncAtt = new Attribute(ooiAtt.getName(), iarr);
                }
                break;
            case LONG:
                Cdmarray.int64Array i64arr = (Cdmarray.int64Array) arrWrap.getObjectValue();
                ucar.ma2.ArrayLong larr = new ucar.ma2.ArrayLong(new int[]{i64arr.getValueCount()});
                for (Long val : i64arr.getValueList()) {
                    larr.setLong(cnt++, val);
                }
                if (larr.getSize() == 1) {
                    ncAtt = new Attribute(ooiAtt.getName(), larr.getLong(0));
                } else {
                    ncAtt = new Attribute(ooiAtt.getName(), larr);
                }
                break;
            case FLOAT:
                Cdmarray.f32Array f32arr = (Cdmarray.f32Array) arrWrap.getObjectValue();
                ucar.ma2.ArrayFloat farr = new ucar.ma2.ArrayFloat(new int[]{f32arr.getValueCount()});
                for (Float val : f32arr.getValueList()) {
                    farr.setFloat(cnt++, val);
                }
                if (farr.getSize() == 1) {
                    ncAtt = new Attribute(ooiAtt.getName(), farr.getFloat(0));
                } else {
                    ncAtt = new Attribute(ooiAtt.getName(), farr);
                }
                break;
            case DOUBLE:
                Cdmarray.f64Array f64arr = (Cdmarray.f64Array) arrWrap.getObjectValue();
                ucar.ma2.ArrayDouble darr = new ucar.ma2.ArrayDouble(new int[]{f64arr.getValueCount()});
                for (Double val : f64arr.getValueList()) {
                    darr.setDouble(cnt++, val);
                }
                if (darr.getSize() == 1) {
                    ncAtt = new Attribute(ooiAtt.getName(), darr.getDouble(0));
                } else {
                    ncAtt = new Attribute(ooiAtt.getName(), darr);
                }
                break;
            /* TODO: Complete for other data types*/
        }

        return ncAtt;
    }
        
    private Variable getNcVariable(Link.CASRef ref) throws InvalidProtocolBufferException, InvalidRangeException, IOException {
        GPBWrapper<Cdmvariable.Variable> varWrap = structManager.getObjectWrapper(ref);
        Cdmvariable.Variable ooiVar = varWrap.getObjectValue();
        Variable ncVar = new Variable(ncfile, null, null, ooiVar.getName());
        ncVar.setDataType(IospUtils.getNcDataType(ooiVar.getDataType()));
        ncVar.setDimensions(getDimString(ooiVar.getShapeList()));
        ncVar.resetShape();
        for (Link.CASRef vAttRef : ooiVar.getAttributesList()) {
            ncVar.addAttribute(getNcAttribute(vAttRef));
        }
//
//        int[] shape = new int[ooiVar.getShapeCount()];
//        int i = 0;
//        for (Link.CASRef vRef : ooiVar.getShapeList()) {
//            Cdmdimension.Dimension dim = (Cdmdimension.Dimension) structManager.getObjectWrapper(vRef).getObjectValue();
//            shape[i++] = (int) dim.getLength();
//        }

        /* TODO: add variable data...NOTE: this would actually happen "on demand" - can't write the file without it... */
//        for (Link.CASRef vRef : ooiVar.getContentList()) {
//            _varMap.put(ncVar, vRef);
//            ucar.ma2.Array arr = getNcArray(vRef, ooiVar.getDataType(), shape);
//            ncVar.setCachedData(arr);
//        }

        _varMap.put(ncVar, ooiVar);
        return ncVar;
    }

    private ucar.ma2.Array getNcArray(Link.CASRef ref, Cdmdatatype.DataType dt, int[] shape) throws InvalidProtocolBufferException, InvalidRangeException {
        GPBWrapper<Cdmvariable.BoundedArray> barrWrap = structManager.getObjectWrapper(ref);
        Cdmvariable.BoundedArray bndArr = barrWrap.getObjectValue();
        GPBWrapper arrWrap = structManager.getObjectWrapper(bndArr.getNdarray());
        List<Cdmvariable.BoundedArray.Bounds> bndsList = bndArr.getBoundsList();

        boolean isScalar = shape.length == 0;

        ucar.ma2.Section sec = new ucar.ma2.Section();
        for (Cdmvariable.BoundedArray.Bounds bnds : bndsList) {
            sec.appendRange((int) bnds.getOrigin(), (int) (bnds.getOrigin() + bnds.getSize() - 1));
        }
        ucar.ma2.Section.Iterator secIter = sec.getIterator(sec.getShape());
        ucar.ma2.Array arr = null;
        int cnt = 0;
        switch (dt) {
//            case STRING:
//                Cdmarray.stringArray sarr = Cdmarray.stringArray.parseFrom(elementMap.get(ooiAtt.getArray().getKey()).getValue());
//                if (sarr.getValueCount() == 1) {
//                    ncAtt = new Attribute(ooiAtt.getName(), sarr.getValue(0));
//                } else {
//                    /* Is this possible?? */
//                }
//                break;
            case BYTE:
                Cdmarray.int32Array i32arrB = (Cdmarray.int32Array) arrWrap.getObjectValue();
                if (isScalar) {
                    arr = new ucar.ma2.ArrayByte.D0();
                    arr.setByte(arr.getIndex(), i32arrB.getValueList().get(0).byteValue());
                } else {
                    arr = ucar.ma2.Array.factory(IospUtils.getNcDataType(dt), shape);
                    while (secIter.hasNext()) {
                        arr.setLong(secIter.next(), Integer.valueOf(i32arrB.getValue(cnt++)).longValue());
                    }
//                    arr = new ucar.ma2.ArrayByte(new int[]{i32arrB.getValueCount()});
//                    for (Integer val : i32arrB.getValueList()) {
//                        arr.setByte(cnt++, val.byteValue());
//                    }
                }
                break;
            case SHORT:
                Cdmarray.int32Array i32arrS = (Cdmarray.int32Array) arrWrap.getObjectValue();
                if (isScalar) {
                    arr = new ucar.ma2.ArrayShort.D0();
                    arr.setShort(arr.getIndex(), i32arrS.getValueList().get(0).shortValue());
                } else {
                    arr = ucar.ma2.Array.factory(IospUtils.getNcDataType(dt), shape);
                    while (secIter.hasNext()) {
                        arr.setShort(secIter.next(), Integer.valueOf(i32arrS.getValue(cnt++)).shortValue());
                    }
//                    arr = new ucar.ma2.ArrayShort(new int[]{i32arrS.getValueCount()});
//                    for (Integer val : i32arrS.getValueList()) {
//                        arr.setShort(cnt++, val.shortValue());
//                    }
                }
                break;
            case INT:
                Cdmarray.int32Array i32arr = (Cdmarray.int32Array) arrWrap.getObjectValue();
                if (isScalar) {
                    arr = new ucar.ma2.ArrayInt.D0();
                    arr.setInt(arr.getIndex(), i32arr.getValue(0));
                } else {
                    arr = ucar.ma2.Array.factory(IospUtils.getNcDataType(dt), shape);
                    while (secIter.hasNext()) {
                        arr.setInt(secIter.next(), i32arr.getValue(cnt++));
                    }
//                    arr = new ucar.ma2.ArrayInt(new int[]{i32arr.getValueCount()});
//                    for (Integer val : i32arr.getValueList()) {
//                        arr.setInt(arr.getIndex().set(cnt), val);
//                    }
                }
                break;
            case LONG:
                Cdmarray.int64Array i64arr = (Cdmarray.int64Array) arrWrap.getObjectValue();
                if (isScalar) {
                    arr = new ucar.ma2.ArrayLong.D0();
                    arr.setLong(arr.getIndex(), i64arr.getValue(0));
                } else {
                    arr = ucar.ma2.Array.factory(IospUtils.getNcDataType(dt), shape);
                    while (secIter.hasNext()) {
                        arr.setLong(secIter.next(), i64arr.getValue(cnt++));
                    }
//                    arr = new ucar.ma2.ArrayLong(new int[]{i64arr.getValueCount()});
//                    for (Long val : i64arr.getValueList()) {
//                        arr.setLong(cnt++, val);
//                    }
                }
                break;
            case FLOAT:
                Cdmarray.f32Array f32arr = (Cdmarray.f32Array) arrWrap.getObjectValue();
                if (isScalar) {
                    arr = new ucar.ma2.ArrayFloat.D0();
                    arr.setFloat(arr.getIndex(), f32arr.getValue(0));
                } else {
                    arr = ucar.ma2.Array.factory(IospUtils.getNcDataType(dt), shape);
                    while (secIter.hasNext()) {
                        arr.setFloat(secIter.next(), f32arr.getValue(cnt++));
                    }
//                    arr = new ucar.ma2.ArrayFloat(new int[]{f32arr.getValueCount()});
//                    for (Float val : f32arr.getValueList()) {
//                        arr.setFloat(cnt++, val);
//                    }
                }
                break;
            case DOUBLE:
                Cdmarray.f64Array f64arr = (Cdmarray.f64Array) arrWrap.getObjectValue();
                if (isScalar) {
                    arr = new ucar.ma2.ArrayDouble.D0();
                    arr.setDouble(arr.getIndex(), f64arr.getValue(0));
                } else {
                    arr = ucar.ma2.Array.factory(IospUtils.getNcDataType(dt), shape);
                    while (secIter.hasNext()) {
                        arr.setDouble(secIter.next(), f64arr.getValue(cnt++));
                    }
//                    arr = new ucar.ma2.ArrayDouble(new int[]{f64arr.getValueCount()});
//                    for (Double val : f64arr.getValueList()) {
//                        arr.setDouble(cnt++, val);
//                    }
                }
                break;
            /* TODO: Complete for other data types*/
        }

        return arr;
    }

    private String getDimString(List<Link.CASRef> shapeList) throws InvalidProtocolBufferException {
        StringBuilder dimString = new StringBuilder();
        for (Link.CASRef shpRef : shapeList) {
            dimString.append(((Cdmdimension.Dimension)structManager.getObjectWrapper(shpRef).getObjectValue()).getName());
            dimString.append(" ");
        }
        return dimString.toString().trim();
    }
    // </editor-fold>
}
