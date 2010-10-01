/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ooici.netcdf.iosp;

import biz.source_code.base64Coder.Base64Coder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 *
 * @author cmueller
 */
public class IospUtils {

    public static String serialize(Object obj) throws IOException {
        String ret = null;
        ObjectOutputStream out = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            out = new ObjectOutputStream(baos);
            out.writeObject(obj);

            ret = new String(Base64Coder.encode(baos.toByteArray()));
        } finally {
            if (out != null) {
                out.close();
            }
        }
        return ret;
    }

    public static Object deserialize(String s) throws IOException, ClassNotFoundException {
        Object ret = null;
        ObjectInputStream in = null;
        try {
            byte[] buf = Base64Coder.decode(s);
            ByteArrayInputStream bais = new ByteArrayInputStream(buf);
            in = new ObjectInputStream(bais);
            ret = in.readObject();
        } finally {
            if (in != null) {
                in.close();
            }
        }
        return ret;
    }

}
