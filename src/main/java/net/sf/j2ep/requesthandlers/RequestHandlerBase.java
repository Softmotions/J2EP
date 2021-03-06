/*
 * Copyright 2000,2004 Anders Nyman.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sf.j2ep.requesthandlers;

import net.sf.j2ep.model.RequestHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * A basic implementation of the RequestHandler.
 * Includes a method to set the headers with.
 *
 * @author Anders Nyman
 */
public abstract class RequestHandlerBase implements RequestHandler {

    /**
     * A set of headers that are not to be set in the request,
     * these headers are for example Connection.
     */
    private static Set<String> bannedHeaders = new HashSet<>();

    /**
     * @see net.sf.j2ep.model.RequestHandler#process(javax.servlet.http.HttpServletRequest, java.lang.String)
     */
    public abstract HttpUriRequest process(HttpServletRequest request, String url) throws IOException;

    /**
     * Logging element supplied by commons-logging.
     */
    private static Logger log = LoggerFactory.getLogger(RequestHandlerBase.class);

    /**
     * Will write all request headers stored in the request to the method that
     * are not in the set of banned headers.
     * The Accept-Endocing header is also changed to allow compressed content
     * connection to the server even if the end client doesn't support that.
     * A Via headers is created as well in compliance with the RFC.
     *
     * @param method  The HttpMethod used for this connection
     * @param request The incoming request
     */
    protected void setHeaders(HttpUriRequest method, HttpServletRequest request) {
        Enumeration headers = request.getHeaderNames();
        String connectionToken = request.getHeader("connection");

        while (headers.hasMoreElements()) {
            String name = (String) headers.nextElement();
            boolean isToken = (connectionToken != null && name.equalsIgnoreCase(connectionToken));

            if (!isToken && !bannedHeaders.contains(name.toLowerCase())) {
                Enumeration value = request.getHeaders(name);
                while (value.hasMoreElements()) {
                    method.setHeader(name, (String) value.nextElement());
                }
            }
        }

        setProxySpecificHeaders(method, request);
    }

    /**
     * Will write the proxy specific headers such as Via and x-forwarded-for.
     *
     * @param method  Method to write the headers to
     * @param request The incoming request, will need to get virtual host.
     */
    private void setProxySpecificHeaders(HttpUriRequest method, HttpServletRequest request) {
        String serverHostName = "jEasyExtensibleProxy";
        try {
            serverHostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.error("Couldn't get the hostname needed for headers x-forwarded-server and Via", e);
        }

        String originalVia = request.getHeader("via");
        StringBuilder via = new StringBuilder();
        if (originalVia != null) {
            if (originalVia.contains(serverHostName)) {
                log.error("This proxy has already handled the request, will abort.");
                throw new RuntimeException("Request has a cyclic dependency on this proxy.");
            }
            via.append(originalVia).append(", ");
        }
        via.append(request.getProtocol()).append(" ").append(serverHostName);

        method.setHeader("via", via.toString());
        method.setHeader("x-forwarded-for", request.getRemoteAddr());
        method.setHeader("x-forwarded-host", request.getServerName());
        method.setHeader("x-forwarded-server", serverHostName);
        method.setHeader("accept-encoding", "");
    }

    /**
     * Adds a headers to the list of banned headers.
     *
     * @param header The header to add
     */
    public static void addBannedHeader(String header) {
        bannedHeaders.add(header);
    }

    /**
     * Adds all the headers in the input to the list
     * of banned headers. The input string should be
     * comma separated e.g. "Server,Connection,Via"
     * <p/>
     * This method is normally called by the factory that
     * is using this request handler.
     *
     * @param headers The headers that are banned
     */
    public static void addBannedHeaders(String headers) {
        StringTokenizer tokenizer = new StringTokenizer(headers, ",");
        while (tokenizer.hasMoreTokens()) {
            bannedHeaders.add(tokenizer.nextToken().trim().toLowerCase());
        }
    }

}
