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

package net.sf.j2ep.factories;

import net.sf.j2ep.model.ResponseHandler;
import net.sf.j2ep.responsehandlers.DeleteResponseHandler;
import net.sf.j2ep.responsehandlers.GetResponseHandler;
import net.sf.j2ep.responsehandlers.HeadResponseHandler;
import net.sf.j2ep.responsehandlers.OptionsResponseHandler;
import net.sf.j2ep.responsehandlers.PostResponseHandler;
import net.sf.j2ep.responsehandlers.PutResponseHandler;
import net.sf.j2ep.responsehandlers.TraceResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;

/**
 * A factory creating ResponseHandlers.
 * This factory is used to get the handler for each request, it has
 * a list of methods it can handle and will throw a MethodNotAllowedException
 * when it can't handle a method.
 *
 * @author Anders Nyman
 */
public class ResponseHandlerFactory {

    /**
     * These methods are handled by this factory.
     */
    private static final String handledMethods = "OPTIONS,GET,HEAD,POST,PUT,DELETE,TRACE";

    /**
     * Checks the hresp being received and created a
     * suitable ResponseHandler for this hresp.
     *
     * @param hresp Method to handle
     * @return The handler for this response
     * @throws MethodNotAllowedException If no hresp could be choose this exception is thrown
     */
    public static ResponseHandler createResponseHandler(CloseableHttpResponse hresp, HttpUriRequest hreq, HttpClientContext ctx) throws MethodNotAllowedException {
        final String method = hreq.getMethod();
        ResponseHandler handler;
        switch (method) {
            case "OPTIONS":
                handler = new OptionsResponseHandler(hresp, hreq);
                break;
            case "GET":
                handler = new GetResponseHandler(hresp);
                break;
            case "HEAD":
                handler = new HeadResponseHandler(hresp);
                break;
            case "POST":
                handler = new PostResponseHandler(hresp);
                break;
            case "PUT":
                handler = new PutResponseHandler(hresp);
                break;
            case "DELETE":
                handler = new DeleteResponseHandler(hresp);
                break;
            case "TRACE":
                handler = new TraceResponseHandler(hresp, hreq);
                break;
            default:
                throw new MethodNotAllowedException("The hresp " + method + " was allowed by the AllowedMethodHandler, not by the factory.", handledMethods);
        }
        return handler;
    }
}
