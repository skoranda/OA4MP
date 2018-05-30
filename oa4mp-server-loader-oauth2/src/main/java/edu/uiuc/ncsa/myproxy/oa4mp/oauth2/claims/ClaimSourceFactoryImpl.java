package edu.uiuc.ncsa.myproxy.oa4mp.oauth2.claims;

import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.OA2SE;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.storage.clients.OA2Client;
import edu.uiuc.ncsa.security.core.util.DebugUtil;
import edu.uiuc.ncsa.security.oauth_2_0.server.claims.ClaimSource;
import edu.uiuc.ncsa.security.oauth_2_0.server.claims.ClaimSourceFactory;
import edu.uiuc.ncsa.security.oauth_2_0.server.claims.ClaimSourceFactoryRequest;
import edu.uiuc.ncsa.security.oauth_2_0.server.config.LDAPConfiguration;

import java.util.LinkedList;

/**
 * <p>Created by Jeff Gaynor<br>
 * on 12/16/16 at  3:08 PM
 */
public class ClaimSourceFactoryImpl extends ClaimSourceFactory{

    public ClaimSourceFactoryImpl() {
    }

    @Override
    public ClaimSource create(ClaimSourceFactoryRequest request) {
        if (request instanceof LDAPClaimSourceFactoryRequest) {
            LDAPClaimSourceFactoryRequest req = (LDAPClaimSourceFactoryRequest) request;
            LDAPClaimsSource h = new LDAPClaimsSource(req.getLdapConfiguration(), req.getLogger());
            h.setScopes(req.getScopes());
            return h;
        }
        BasicClaimsSourceImpl h = new BasicClaimsSourceImpl();
        h.setScopes(request.getScopes());
        return h;
    }

    /**
     * This creates a uniform list of scope handlers for both the access token servlet and the user info servlet.
     * It will use a common handler if there is one and use the configured factory to create appropriate ones
     * (and populate them with the right runtime environment otherwise.
     * @param oa2SE
     * @param client
     * @return
     */
    public static LinkedList<ClaimSource> createClaimSources(OA2SE oa2SE, OA2Client client) {
        DebugUtil.dbg(ClaimSourceFactoryImpl.class, "Starting to create LDAPScopeHandlers per client");
             LinkedList<ClaimSource> scopeHandlers = new LinkedList<>();

             if (client.getLdaps()==null || client.getLdaps().isEmpty()) {
                 DebugUtil.dbg(ClaimSourceFactoryImpl.class, "using default scope handler=");
                 if(oa2SE.getClaimSource() instanceof BasicClaimsSourceImpl){
                     BasicClaimsSourceImpl bb = (BasicClaimsSourceImpl)oa2SE.getClaimSource();
                     if(bb.getOa2SE() == null){
                         DebugUtil.dbg(ClaimSourceFactoryImpl.class,"setting scope handler environment #1");
                         bb.setOa2SE(oa2SE);
                     }
                 }
                 scopeHandlers.add(oa2SE.getClaimSource());
             } else {
                 for (LDAPConfiguration cfg : client.getLdaps()) {
                     DebugUtil.dbg(ClaimSourceFactoryImpl.class,"Got LDAP configuration for server " + cfg.getServer());
                     LDAPClaimSourceFactoryRequest req = new LDAPClaimSourceFactoryRequest(oa2SE.getMyLogger(),
                             cfg, client.getScopes());
                     ClaimSource claimSource = ClaimSourceFactory.newInstance(req);
                     if(claimSource instanceof BasicClaimsSourceImpl){
                         DebugUtil.dbg(ClaimSourceFactoryImpl.class, "Scope handler\"" + claimSource.getClass().getSimpleName() + "\" is configured.");

                         ((BasicClaimsSourceImpl)claimSource).setOa2SE(oa2SE);
                         DebugUtil.dbg(ClaimSourceFactoryImpl.class, "setting scope handler environment #2");
                     }
                     scopeHandlers.add(claimSource);
                 }
             }
             return scopeHandlers;
         }

}
