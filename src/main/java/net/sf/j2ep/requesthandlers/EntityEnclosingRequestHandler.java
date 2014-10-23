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

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.protocol.HTTP;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Handler for POST and PUT methods.
 *
 * @author Anders Nyman, Daniel Deng
 */
public class EntityEnclosingRequestHandler extends RequestHandlerBase {

    /**
     * Will set the input stream and the Content-Type header to match this request.
     * Will also set the other headers send in the request.
     *
     * @throws IOException An exception is throws when there is a problem getting the input stream
     * @see net.sf.j2ep.model.RequestHandler#process(javax.servlet.http.HttpServletRequest, java.lang.String)
     */
    public HttpUriRequest process(HttpServletRequest request, String url) throws IOException {

        HttpEntityEnclosingRequestBase method;
        String rmethod = request.getMethod().toUpperCase();
        switch (rmethod) {
            case "POST":
                method = new HttpPost(url);
                break;
            case "PUT":
                method = new HttpPut(url);
                break;
            default:
                return null;
        }
        setHeaders(method, request);
        method.removeHeaders(HTTP.CONTENT_LEN);
        method.setEntity(new InputStreamEntity(request.getInputStream()));
        method.setHeader(HTTP.CONTENT_TYPE, request.getContentType());
        return method;
    }
}
