package edu.uiuc.ncsa.myproxy.oa4mp.server.admin.adminClient;

import edu.uiuc.ncsa.security.delegation.storage.BaseClientKeys;

import java.util.List;

/**
 * <p>Created by Jeff Gaynor<br>
 * on 10/20/16 at  12:53 PM
 */
public class AdminClientKeys extends BaseClientKeys {
    public AdminClientKeys() {
        super();
        identifier("admin_id");
        secret("secret");
    }
    String maxClients = "max_clients";
    String issuer = "issuer";
    String config = "config";

    public String config(String... x) {
           if (0 < x.length) config= x[0];
           return config;
       }

    public String maxClients(String... x) {
           if (0 < x.length) maxClients= x[0];
           return maxClients;
       }

    public String issuer(String... x) {
           if (0 < x.length) issuer = x[0];
           return issuer;
       }

    String vo="vo";

    public String vo(String... x) {
           if (0 < x.length) vo = x[0];
           return vo;
       }

    @Override
    public List<String> allKeys() {
        List<String> allKeys =  super.allKeys();
        allKeys.add(config());
        allKeys.add(issuer());
        allKeys.add(maxClients());
        allKeys.add(vo());
        return allKeys;
    }
}
