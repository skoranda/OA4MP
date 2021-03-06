package edu.uiuc.ncsa.myproxy.oa4mp.oauth2.servlet;

import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.storage.clients.OA2Client;
import edu.uiuc.ncsa.myproxy.oa4mp.server.servlet.AbstractRegistrationServlet;
import edu.uiuc.ncsa.security.core.exceptions.NFWException;
import edu.uiuc.ncsa.security.delegation.storage.Client;
import edu.uiuc.ncsa.security.oauth_2_0.OA2Errors;
import edu.uiuc.ncsa.security.oauth_2_0.OA2GeneralError;
import edu.uiuc.ncsa.security.servlet.ServletDebugUtil;
import org.apache.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * A budding set of utilities for working with clients.
 * <p>Created by Jeff Gaynor<br>
 * on 3/17/14 at  12:57 PM
 */
public class OA2ClientUtils {
    /**
     * Note that all of the exceptions thrown here are because the callback cannot be verified, hence it is unclear
     * where the error is to be sent.
     *
     * @param client
     * @param redirect
     */
    public static void check(Client client, String redirect) {

        if (client == null) {
            throw new NoClientIDException(OA2Errors.INVALID_REQUEST, "no client id", HttpStatus.SC_BAD_REQUEST);
        }
        if (!(client instanceof OA2Client)) {
            throw new NFWException("Internal error: Client is not an OA2Client");
        }

        OA2Client oa2Client = (OA2Client) client;


        boolean foundCB = false;
        if (oa2Client.getCallbackURIs() == null) {
            throw new NoRegisteredRedirectError(OA2Errors.INVALID_REQUEST, "client has not registered any callback URIs", HttpStatus.SC_BAD_REQUEST);
        }
        for (String uri : oa2Client.getCallbackURIs()) {
            if (uri.equals(redirect)) {
                foundCB = true;
                break;
            }
        }

        if (!foundCB) {
            ServletDebugUtil.trace(OA2ClientUtils.class,
                    "invalid redirect uri for client \"" +
                            oa2Client.getIdentifierString() + "\": \"" + redirect + "\"");
            throw new InvalidRedirectError(OA2Errors.INVALID_REQUEST, "The given redirect \"" + redirect + "\" is not valid for this client", HttpStatus.SC_BAD_REQUEST);
        }
    }

    public static class InvalidRedirectError extends OA2GeneralError {

        public InvalidRedirectError(String error, String description, int httpStatus) {
            super(error, description, httpStatus);
        }

    }

    public static class NoScopesError extends OA2GeneralError {

           public NoScopesError(String error, String description, int httpStatus) {
               super(error, description, httpStatus);
           }

       }

    public static class NoRegisteredRedirectError extends OA2GeneralError {

        public NoRegisteredRedirectError(String error, String description, int httpStatus) {
            super(error, description, httpStatus);
        }

    }

    public static class NoClientIDException extends OA2GeneralError {
        public NoClientIDException(String error, String description, int httpStatus) {
            super(error, description, httpStatus);
        }
    }


    /**
     * This takes a list of callbacks and checks policies for each of them. This does the actual work for checking
     *
     * @param rawCBs
     * @param dudUris -- this is a list of any URIs that are rejected. The caller may do with them what they will.
     * @return
     * @throws IOException
     */
    public static LinkedList<String> createCallbacks(List<String> rawCBs,
                                                     List<String> dudUris) throws IOException {
        LinkedList<String> uris = new LinkedList<>();

        for (String x : rawCBs) {
            // Fix for CIL-609 -- don't add empty strings or white space.
            if (x == null || x.isEmpty() || x.trim().isEmpty()) {
                continue;
            }
            // Fix for CIL-545. Allowing a wider range of redirect URIs to support devices such as smart phones.
            // How it works: Either the protocol is not http/https in which case it is allowed
            // but if it is http, only localhost is permitted. Any https works.
            try {
                URI temp = URI.create(x);
                String host = temp.getHost();
                String scheme = temp.getScheme();
                ServletDebugUtil.trace(OA2ClientUtils.class, "createCallbacks, processing callback \"" + x + "\"");

                if (scheme != null && scheme.toLowerCase().equals("https")) {
                    // any https works
                    uris.add(x);
                } else {
                    if (isPrivate(host, scheme)) {
                        uris.add(x);
                    } else {
                        if (temp.getAuthority() == null || temp.getAuthority().isEmpty()) {
                                  /*
                                  Finally, if it does not have an authority, then it is probably
                                  an intention for another (probably mobile) device (so in that case,
                                  the browser on the device has the table associating schemes with
                                  specific applications. When it sees a uri with this scheme, it
                                  invokes the associated application and hands it the URI. This allows
                                  the browser to do a redirect to an application. The requirement is that there is a scheme, but
                                  there is no authority:
                                   E.g. https://bob@foo.com/blah/woof
                                   has authority of "//bob@foo.com/"

                                   An example of what this block allows (or should) is a uri like

                                   com.example.app:/oauth2redirect/example-provider

                                   which has a scheme, no authority and a path.
                                   */
                            uris.add(x);
                        } else {
                            dudUris.add(x);
                        }
                    }
                }

            } catch (IllegalArgumentException urisx) {
                dudUris.add(x);
            }

              /*  Old stuff before CIL-545
                 if (!x.toLowerCase().startsWith("https:")) {
                      warn("Attempt to add bad callback uri for client " + client.getIdentifierString());
                      throw new ClientRegistrationRetryException("The callback \"" + x + "\" is not secure.", null, client);
                  }
                  URI.create(x); // passes here means it is a uri. All we want this to do is throw an exception if needed.

                  uris.add(x);*/
        }
        return uris;
    }

    /**
     * This is for use with the web interface. The string in this case is the contents of a textbox that has
     * one callback per line. Each callback is processed.
     *
     * @param client
     * @param rawCBs
     * @return
     * @throws IOException
     */
    public static LinkedList<String> createCallbacksForWebUI(OA2Client client,
                                                             String rawCBs) throws IOException {
        BufferedReader br = new BufferedReader(new StringReader(rawCBs));
        String x = br.readLine();

        LinkedList<String> uris = new LinkedList<>();
        LinkedList<String> dudUris = new LinkedList<>();

        while (x != null) {
            uris.add(x);
            x = br.readLine();
        }
        br.close();
        LinkedList<String> foundURIs = createCallbacks(uris, dudUris);
        if (0 < dudUris.size()) {
            String xx = "</br>";
            boolean isOne = dudUris.size() == 1;
            for (String y : dudUris) {
                xx = xx + y + "</br>";
            }
            String helpfulMessage = "The callback" + (isOne ? " " : "s ") + xx + (isOne ? "is" : "are") + " not valid.";
            throw new AbstractRegistrationServlet.ClientRegistrationRetryException(helpfulMessage, null, client);

        }

        return foundURIs;

    }

    protected static int[] toQuad(String address) {
        StringTokenizer stringTokenizer = new StringTokenizer(address, ".");
        if (!stringTokenizer.hasMoreTokens()) {
            return null;
        }
        int[] quad = new int[4];

        for (int i = 0; i < 4; i++) {
            if (!stringTokenizer.hasMoreTokens()) {
                return null;
            }
            String raw = stringTokenizer.nextToken();
            try {
                quad[i] = Integer.parseInt(raw);
                if (!(0 <= quad[i] && quad[i] <= 255)) {
                    return null;
                }
            } catch (NumberFormatException nfx) {
                return null;
            }
        }
        if (stringTokenizer.hasMoreTokens()) {
            return null;
        }
        return quad;

    }

    protected static boolean isOnPrivateNetwork(String address) {
        String regex = "\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b";
        if (!address.matches(regex)) {
            return false;
        }
        int[] quad = toQuad(address);
        if (quad == null) {
            return false;
        }
        if (quad[0] == 10) {
            return true;
        }
        if (quad[0] == 192 && quad[1] == 168) return true;
        if (quad[0] == 172 && (16 <= quad[1] && quad[1] <= 31)) return true;
        if (quad[0] == 127 && quad[1] == 0 && quad[2] == 0 && quad[3] == 1) return true;

        // This just checked that it is a dotted quad address. We could have used InetAddress which
        // only checks a valid dotted quad for format, **but** might also do an actual address lookup
        // if there is a question, so that really doesn't help.

        // now we have to check that address in the range 172.16.x.x to 172.31.x.x are included.
        // Do the easy ones first.
        return false;
    }

    protected static boolean isPrivate(String host, String scheme) {
        if (host != null && isOnPrivateNetwork(host)) {
            // scheme does not matter in this case since it is a private network.
            // note that this also catches the loopback address of 127.0.0.1
            return true;
        }
        if (scheme != null && scheme.toLowerCase().equals("http")) {
            // only localhost works for http
            if (host.toLowerCase().equals("localhost") ||
                    host.equals("[::1]")) {
                return true;
            }
        }


        return false;
    }

}
