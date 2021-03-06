package edu.uiuc.ncsa.myproxy.oa4mp.server.admin.adminClient;

import edu.uiuc.ncsa.security.core.Identifier;
import edu.uiuc.ncsa.security.delegation.storage.BaseClient;
import edu.uiuc.ncsa.security.util.pkcs.KeyUtil;
import net.sf.json.JSONObject;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import static edu.uiuc.ncsa.security.core.util.BeanUtils.checkEquals;

public class AdminClient extends BaseClient {
    /**
     *  The maximum number of OIDC (i.e. standard) clients an admin client may create before
     *  being refused by the system. This is to prevent error (e.g. an admin client is used in a
     *  script which is misbehaving). This may be increased and is simply the default for newly
     *  created admin clients.
     */
    public static int DEFAULT_MAX_NUMBER_OF_OIDC_CLIENTS = 50;
    // Stored as a boolean in the config. Must be true to be treated as a root
    public static String ROOT_USER_KEY = "root";
    /**
     * Store in the config for this user. This will be used for secure communication.
     */
    public static String PUBLIC_KEY_KEY = "public_key";
    public boolean isRootUser(){
        if(hasConfig()){
           return getConfig().getBoolean(ROOT_USER_KEY);
        }
        return false;
    }
    public void setRootUser(boolean isRootUser){
        getConfig().put(ROOT_USER_KEY, isRootUser);
    }
    public PublicKey getPublicKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
        // The stored format of the public key is x509 public key format that is base64 encoded. This make sure that there
        // can be no issues with charsets or munged characters.
        if(!hasConfig()){
            return null;
        }
        if(!getConfig().containsKey(PUBLIC_KEY_KEY)){
            return null;
        }
        String raw = getConfig().getString(PUBLIC_KEY_KEY);
        if(raw.isEmpty()){
            return null;
        }
        byte[] bytes = Base64.getDecoder().decode(raw);
        String x509 = new String(bytes);
        return KeyUtil.fromX509PEM(x509);
    }

    public void setPublicKey(PublicKey publicKey){
        String key = KeyUtil.toX509PEM(publicKey);
        String encoded = Base64.getEncoder().encodeToString(key.getBytes());
        getConfig().put(PUBLIC_KEY_KEY, encoded);
    }
    public boolean hasConfig(){
        return getConfig() != null && !getConfig().isEmpty();
    }
    public AdminClient(Identifier identifier) {
        super(identifier);
    }

    String virtualOrganization;
    String issuer;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getVirtualOrganization() {
        return virtualOrganization;
    }

    public void setVirtualOrganization(String virtualOrganization) {
        this.virtualOrganization = virtualOrganization;
    }
    int maxClients = DEFAULT_MAX_NUMBER_OF_OIDC_CLIENTS;
     JSONObject config;

    public JSONObject getConfig() {
        return config;
    }

    public void setConfig(JSONObject config) {
        this.config = config;
    }

    /**
     * The maximum number of standard clients this admin client can create before the system
     * refuses to accept any more.
     * @return
     */
    public int getMaxClients() {
        return maxClients;
    }

    public void setMaxClients(int maxClients) {
        this.maxClients = maxClients;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AdminClient)) {
            return false;
        }
        AdminClient ac = (AdminClient) obj;
        if (!checkEquals(getIssuer(), ac.getIssuer())) return false;
        if (!checkEquals(getVirtualOrganization(), ac.getVirtualOrganization())) return false;
        if(getMaxClients() != ac.getMaxClients()) return false;
        return super.equals(obj);
    }

    @Override
    public BaseClient clone() {
        AdminClient ac = new AdminClient(getIdentifier());
        populateClone(ac);
        return ac;
    }

    @Override
    protected void populateClone(BaseClient client) {
        AdminClient c = (AdminClient) client;
        super.populateClone(c);
        c.setCreationTS(getCreationTS());
        c.setEmail(getEmail());
        c.setName(getName());
        c.setSecret(getSecret());
        c.setMaxClients(getMaxClients());
    }

    @Override
    public String toString() {
        return "AdminClient{" +
                "virtualOrganization='" + virtualOrganization + '\'' +
                ", issuer='" + issuer + '\'' +
                ", maxClients=" + maxClients +
                ", config=" + config +
                '}';
    }
}