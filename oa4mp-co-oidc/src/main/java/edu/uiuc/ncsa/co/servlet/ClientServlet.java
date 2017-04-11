package edu.uiuc.ncsa.co.servlet;

import edu.uiuc.ncsa.co.ManagerFacade;
import edu.uiuc.ncsa.co.loader.COSE;
import edu.uiuc.ncsa.co.util.ResponseSerializer;
import edu.uiuc.ncsa.myproxy.oa4mp.server.servlet.EnvServlet;
import edu.uiuc.ncsa.security.core.exceptions.NotImplementedException;
import edu.uiuc.ncsa.security.delegation.services.Response;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * The client management servlet.
 * <p>Created by Jeff Gaynor<br>
 * on 10/6/16 at  11:41 AM
 */
public class ClientServlet extends EnvServlet {


    @Override
    public void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        throw new NotImplementedException("Get is not supported by this service");
    }

    public ManagerFacade getClientManager() {
        if (clientManager == null) {
            clientManager = new ManagerFacade((COSE) getEnvironment());
        }
        return clientManager;
    }

    ManagerFacade clientManager;

    @Override
    protected void doIt(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Throwable {
        BufferedReader br = httpServletRequest.getReader();
        System.err.println("query=" + httpServletRequest.getQueryString());
        StringBuffer stringBuffer = new StringBuffer();
        String line = br.readLine();
        System.err.println("line=" + line);
        while (line != null) {
            stringBuffer.append(line);
            line = br.readLine();
        }
        br.close();
        if (stringBuffer.length() == 0) {
            throw new IllegalArgumentException("Error: There is no content for this request");
        }
        JSON rawJSON = JSONSerializer.toJSON(stringBuffer.toString());

        System.err.println(rawJSON.toString());
        if (rawJSON.isArray()) {
            getMyLogger().info("Error: Got a JSON array rather than a request:" + rawJSON);
            throw new IllegalArgumentException("Error: incorrect argument. Not a valid JSON request");
        }
        try {
            Response response = getClientManager().process((JSONObject) rawJSON);
            getResponseSerializer().serialize(response, httpServletResponse);
        }catch(Throwable t){
            t.printStackTrace();
            throw t;
        }


    }

    protected COSE getCOSE(){
        return (COSE)getEnvironment();
    }
    public ResponseSerializer getResponseSerializer() {
        if(responseSerializer == null){
            responseSerializer = new ResponseSerializer(getCOSE());
        }
        return responseSerializer;
    }

    ResponseSerializer responseSerializer = null;



}