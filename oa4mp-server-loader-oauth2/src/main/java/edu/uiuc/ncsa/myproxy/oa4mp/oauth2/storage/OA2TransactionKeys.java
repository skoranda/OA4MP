package edu.uiuc.ncsa.myproxy.oa4mp.oauth2.storage;

import edu.uiuc.ncsa.myproxy.oa4mp.server.admin.transactions.DSTransactionKeys;

import java.util.List;

/**
 * <p>Created by Jeff Gaynor<br>
 * on 2/28/14 at  5:22 PM
 */
public class OA2TransactionKeys extends DSTransactionKeys {
    public OA2TransactionKeys() {
        callbackUri("callback_uri");
        verifier("verifier_token");
        clientKey("client_id");
    }

    protected String refreshToken = "refresh_token";
    protected String refreshTokenValid = "refresh_token_valid";
    protected String expiresIn = "expires_in";
    protected String scopes = "scopes";
    protected String authTime = "auth_time";
    protected String states = "states";

    public String refreshToken(String... x) {
        if (0 < x.length) refreshToken = x[0];
        return refreshToken;
    }

    public String states(String... x) {
        if (0 < x.length) states= x[0];
        return states;
    }

    public String refreshTokenValid(String... x) {
        if (0 < x.length) refreshTokenValid = x[0];
        return refreshTokenValid;
    }

    public String expiresIn(String... x) {
        if (0 < x.length) expiresIn = x[0];
        return expiresIn;
    }


    public String scopes(String... x) {
        if (0 < x.length) scopes = x[0];
        return scopes;
    }
    public String authTime(String... x) {
        if (0 < x.length) authTime = x[0];
        return authTime;
    }

    @Override
    public List<String> allKeys() {
        List<String> allKeys =  super.allKeys();
        allKeys.add(refreshToken());
        allKeys.add(refreshTokenValid());
        allKeys.add(expiresIn());
        allKeys.add(scopes());
        allKeys.add(authTime());
        allKeys.add(states());
        return allKeys;

    }
}
