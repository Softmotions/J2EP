/*
 * Copyright 2000,2004 The Apache Software Foundation.
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

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;

import javax.servlet.http.HttpServletRequest;

/**
 * A handler for GET, HEAD, DELETE. Since these methods basically
 * only will need the headers set they can be handled by the same
 * handler.
 *
 * @author Anders Nyman
 */
public class BasicRequestHandler extends RequestHandlerBase {

    /**
     * Will only set the headers.
     *
     * @see net.sf.j2ep.model.RequestHandler#process(javax.servlet.http.HttpServletRequest, java.lang.String)
     */
    public HttpUriRequest process(HttpServletRequest request, String url) {
        String rmethod = request.getMethod().toUpperCase();
        HttpUriRequest method;
        switch (rmethod) {
            case "GET":
                method = new HttpGet(url);
                break;
            case "HEAD":
                method = new HttpHead(url);
                break;
            case "DELETE":
                method = new HttpDelete(url);
                break;
            default:
                return null;
        }
        setHeaders(method, request);
        return method;
    }


}
