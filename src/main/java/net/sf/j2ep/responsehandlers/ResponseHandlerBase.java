/*
 * Copyright 2005 Anders Nyman.
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

package net.sf.j2ep.responsehandlers;

import net.sf.j2ep.model.ResponseHandler;
import net.sf.j2ep.requesthandlers.RequestHandlerBase;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;

/**
 * Basic implementation of a Response Handler. This class
 * can write the headers and process the output stream.
 *
 * @author Anders Nyman, Daniel Deng
 */
public abstract class ResponseHandlerBase implements ResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(ResponseHandlerBase.class);

    /**
     * Method we are using for this request.
     */
    protected CloseableHttpResponse hresp;

    /**
     * Basic constructor only setting the method.
     *
     * @param hresp The method we are using
     */
    public ResponseHandlerBase(CloseableHttpResponse hresp) {
        this.hresp = hresp;
    }

    /**
     * @see net.sf.j2ep.model.ResponseHandler#process(javax.servlet.http.HttpServletResponse)
     */
    public abstract void process(HttpServletResponse response) throws IOException;

    /**
     * Will release the connection for the method.
     *
     * @see net.sf.j2ep.model.ResponseHandler#close()
     */
    public void close() throws IOException {
        if (hresp != null) {
            hresp.close();
        }
    }

    /**
     * @see net.sf.j2ep.model.ResponseHandler#getStatusCode()
     */
    public int getStatusCode() {
        StatusLine sl = (hresp != null) ? hresp.getStatusLine() : null;
        return sl != null ? sl.getStatusCode() : 200;
    }

    /**
     * Writes the entire stream from the method to the response
     * stream.
     *
     * @param resp Response to send data to
     * @throws IOException An IOException is thrown when we are having problems with reading the streams
     */
    protected void sendStreamToClient(ServletResponse resp) throws IOException {
        HttpEntity entity = (hresp != null) ? hresp.getEntity() : null;
        try (
                InputStream streamFromServer = (entity != null) ? entity.getContent() : null;
                OutputStream responseStream = resp.getOutputStream();
        ) {
            if (streamFromServer != null) {
                IOUtils.copyLarge(streamFromServer, responseStream);
                responseStream.flush();
            }
        }
    }

    /**
     * Will write all response headers received in the method to the response.
     * One header, connection, is however omitted since we will only want the
     * client to keep his connection to the proxy not to the backing server.
     *
     * @param response The response that will have headers written to it
     */
    protected void setHeaders(HttpServletResponse response) {
        if (hresp == null) {
            return;
        }
        Iterator headers = hresp.headerIterator();
        while (headers.hasNext()) {
            Header header = (Header) headers.next();
            String name = header.getName();
            boolean contentLength = name.equalsIgnoreCase("content-length");
            boolean connection = name.equalsIgnoreCase("connection");
            if (!contentLength && !connection) {
                response.addHeader(name, header.getValue());
            }
        }
        setViaHeader(response);
    }

    /**
     * Will set the via header with this proxies data to the response.
     *
     * @param response The response we set the header for
     */
    private void setViaHeader(HttpServletResponse response) {
        String serverHostName = "jEasyReverseProxy";
        try {
            serverHostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            LoggerFactory.getLogger(RequestHandlerBase.class).error("Couldn't get the hostname needed for header Via", e);
        }
        Header originalVia = hresp.getFirstHeader("via");
        StringBuilder via = new StringBuilder("");
        if (originalVia != null) {
            via.append(originalVia.getValue()).append(", ");
        }
        via.append(hresp.getStatusLine().getProtocolVersion()).append(" ").append(serverHostName);
        response.setHeader("via", via.toString());
    }
}
