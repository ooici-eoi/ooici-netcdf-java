/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ooici.netcdf.iosp.messaging;

import ion.core.BaseProcess;
import ion.core.messaging.IonMessage;
import ion.core.messaging.MessagingName;
import ion.core.messaging.MsgBrokerClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AttributeStore extends BaseProcess {

    private MessagingName ionServiceName;

    public AttributeStore(String sysname, MsgBrokerClient brokercl) {
        super(brokercl);
        ionServiceName = new MessagingName(sysname, "attributestore");
        this.spawn();
    }

    public void clearStore() {
        IonMessage msg = this.rpcSend(ionServiceName, "clear_store", new HashMap<String, String>());
        this.ackMessage(msg);
    }

    public String get(String key) {
        String ret = null;
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("key", key);
        IonMessage msg = this.rpcSend(ionServiceName, "get", map);
        HashMap<String, Object> content = (HashMap<String, Object>) msg.getContent();
        String status = (String) content.get("status");
        if (status != null & status.equalsIgnoreCase("ok")) {
            ret = (String) content.get("value");
        }
        this.ackMessage(msg);
        return ret;
    }

    public void put(String key, String value) {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("key", key);
        map.put("value", value);
        IonMessage msg = this.rpcSend(ionServiceName, "put", map);
        this.ackMessage(msg);
    }

    public List<String> query(String regex) {
        List<String> ret = null;
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("regex", regex);
        IonMessage msg = this.rpcSend(ionServiceName, "query", map);
        HashMap<String, Object> content = (HashMap<String, Object>) msg.getContent();
        String status = (String) content.get("status");
        if (status != null & status.equalsIgnoreCase("ok")) {
            ret = (ArrayList<String>) content.get("result");
        }
        this.ackMessage(msg);
        return ret;
    }
}
