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

package net.sf.j2ep;

import net.sf.j2ep.factories.MethodNotAllowedException;
import net.sf.j2ep.factories.RequestHandlerFactory;
import net.sf.j2ep.factories.ResponseHandlerFactory;
import net.sf.j2ep.model.AllowedMethodHandler;
import net.sf.j2ep.model.RequestHandler;
import net.sf.j2ep.model.ResponseHandler;
import net.sf.j2ep.model.Rule;
import net.sf.j2ep.model.Server;
import net.sf.j2ep.rules.DirectoryRule;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClientBuilder;
import org.apache.http.protocol.HTTP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * A reverse proxy using a set of Rules to identify which resource to proxy.
 * <p/>
 * At first the rule chain is traversed trying to find a matching rule.
 * When the rule is found it is given the option to rewrite the URL.
 * The rewritten URL is then sent to a Server creating a Response Handler
 * that can be used to process the response with streams and headers.
 * <p/>
 * The rules and servers are created dynamically and are specified in the
 * XML data file. This allows the proxy to be easily extended by creating
 * new rules and new servers.
 *
 * @author Anders Nyman
 */

@WebFilter(asyncSupported = true)
public class ProxyFilter implements Filter {

    /**
     * The server chain, will be traversed to find a matching server.
     */
    private ServerChain serverChain;

    /**
     * Logging element supplied by commons-logging.
     */
    private static Logger log;

    /**
     * The httpclient used to make all connections with, supplied by commons-httpclient.
     */
    private CloseableHttpClient httpClient;

    /**
     * Implementation of a reverse-proxy. All request go through here. This is
     * the main class where are handling starts.
     *
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
     * javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    public void doFilter(ServletRequest req, ServletResponse resp,
                         FilterChain filterChain) throws IOException, ServletException {


        final HttpServletResponse httpResponse = (HttpServletResponse) resp;
        final HttpServletRequest httpRequest = (HttpServletRequest) req;

        Server server = (Server) httpRequest.getAttribute("proxyServer");
        if (server == null) {
            server = serverChain.evaluate(httpRequest);
        }
        if (server == null) {
            filterChain.doFilter(req, resp);
            return;
        }

        String redirect = server.getRedirect();
        if (redirect != null) {
            httpResponse.sendRedirect(redirect);
            return;
        }

        final Rule rule = server.getRule();
        if (rule == null) {
            log.warn("No proxy rule for: " + server);
            filterChain.doFilter(req, resp);
            return;
        }

        final Server fServer = server;
        final AsyncContext actx = req.isAsyncStarted() ? req.getAsyncContext() : req.startAsync();
        actx.start(() -> {
            try {
                runAsync(actx, rule, fServer);
            } catch (IllegalStateException ignored) {
            } catch (IOException | ServletException e) {
                log.error("", e);
            } finally {
                try {
                    actx.complete();
                } catch (IllegalStateException ignored) {
                }
            }
        });
    }

    private void runAsync(AsyncContext actx, Rule rule, Server server) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) actx.getRequest();
        HttpServletResponse httpResponse = (HttpServletResponse) actx.getResponse();
        String uri = rule.process(getURI(httpRequest));
        if ((rule instanceof DirectoryRule) && uri.isEmpty()) { //need redirect to slash terminated path
            String rurl = httpRequest.getRequestURL().toString();
            if (!rurl.endsWith("/")) {
                rurl += '/';
                if (log.isDebugEnabled()) log.debug("Redirect: " + rurl);
                httpResponse.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
                httpResponse.setHeader("Location", rurl);
                httpResponse.flushBuffer();
                return;
            }
        }
        String url = httpRequest.getScheme() + "://" + server.getDomainName() + server.getPath() + uri;
        if (log.isDebugEnabled()) log.debug("Connecting to " + url);
        ResponseHandler responseHandler = null;
        try {

            httpRequest = server.preExecute(httpRequest);
            responseHandler = executeRequest(server, httpRequest, url);
            httpResponse = server.postExecute(httpResponse);
            responseHandler.process(httpResponse);

        } catch (UnknownHostException e) {
            log.warn("Could not connection to the host specified. " + e);
            if (!httpResponse.isCommitted()) {
                httpResponse.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT);
            }
            server.setConnectionExceptionRecieved(e);
        } catch (IOException e) {
            log.warn("Problem probably with the input being send, either with a Header or the Stream. " + e);
            if (!httpResponse.isCommitted()) {
                httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (MethodNotAllowedException e) {
            log.warn("Incoming method could not be handled. " + e);
            if (!httpResponse.isCommitted()) {
                httpResponse.setHeader("Allow", e.getAllowedMethods());
                httpResponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } catch (Exception e) {
            log.warn("Problem while connecting to server. " + e);
            if (!httpResponse.isCommitted()) {
                httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            server.setConnectionExceptionRecieved(e);
        } finally {
            if (responseHandler != null) {
                responseHandler.close();
            }
        }
    }

    /**
     * Will build a URI but including the Query String. That means that it really
     * isn't a URI, but quite near.
     *
     * @param httpRequest Request to get the URI and query string from
     * @return The URI for this request including the query string
     */
    private String getURI(HttpServletRequest httpRequest) {
        String contextPath = httpRequest.getContextPath();
        String uri = httpRequest.getRequestURI().substring(contextPath.length());
        if (httpRequest.getQueryString() != null) {
            uri += "?" + httpRequest.getQueryString();
        }
        return uri;
    }

    /**
     * Will create the method and execute it. After this the method
     * is sent to a ResponseHandler that is returned.
     *
     * @param req Request we are receiving from the client
     * @param url The location we are proxying to
     * @return A ResponseHandler that can be used to write the response
     * @throws MethodNotAllowedException If the method specified by the request isn't handled
     * @throws IOException               When there is a problem with the streams
     */
    private ResponseHandler executeRequest(Server server, HttpServletRequest req, String url) throws MethodNotAllowedException, IOException {
        RequestHandler requestHandler = RequestHandlerFactory.createRequestMethod(req.getMethod());
        HttpUriRequest hreq = requestHandler.process(req, url);
        if (!AllowedMethodHandler.methodAllowed(hreq)) {
            throw new MethodNotAllowedException("The method " + req.getMethod() + " is not in the AllowedHeaderHandler's list of allowed methods.", AllowedMethodHandler.getAllowHeader());
        }
        hreq.setHeader(HTTP.TARGET_HOST, server.getDomainName());
        if (log.isDebugEnabled()) log.debug("" + hreq + " H=" + Arrays.asList(hreq.getAllHeaders()));
        ResponseHandler rh = null;
        CloseableHttpResponse hresp = null;
        try {
            HttpClientContext ctx = HttpClientContext.create();
            hresp = httpClient.execute(hreq, ctx);
            StatusLine sline = hresp.getStatusLine();
            //log.info("sline=" + sline);
            //log.info("h=" + Arrays.asList(hreq.getAllHeaders()));

            if (sline.getStatusCode() == 405) {
                Header allow = hreq.getFirstHeader("allow");
                String value = allow.getValue();
                throw new MethodNotAllowedException("Status code 405 from server", AllowedMethodHandler.processAllowHeader(value));
            }
            rh = ResponseHandlerFactory.createResponseHandler(hresp, hreq, ctx);
        } finally {
            if (rh == null && hresp != null) {
                hresp.close();
            }
        }
        return rh;
    }

    /**
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     * <p/>
     * Called upon initialization, Will create the ConfigParser and get the
     * RuleChain back. Will also configure the httpclient.
     */
    public void init(FilterConfig cfg) throws ServletException {
        log = LoggerFactory.getLogger(ProxyFilter.class);
        AllowedMethodHandler.setAllowedMethods("OPTIONS,GET,HEAD,POST,PUT,DELETE,TRACE");

        HttpClientBuilder builder;

        if (BooleanUtils.toBoolean(cfg.getInitParameter("cache"))) {
            CachingHttpClientBuilder cb = CachingHttpClientBuilder.create();
            builder = cb;
            String val = cfg.getInitParameter("cacheDir");
            if (!StringUtils.isBlank(val)) {
                File cacheDir = new File(val);
                if (!cacheDir.exists()) {
                    cacheDir.mkdir();
                }
                if (!cacheDir.exists() || !cacheDir.canWrite()) {
                    throw new ServletException("Failed to setup cache directory: " + cacheDir);
                }
                cb.setCacheDir(cacheDir);
            }

            CacheConfig.Builder cc = CacheConfig.custom();
            val = cfg.getInitParameter("maxCacheEntries");
            if (!StringUtils.isBlank(val)) {
                cc.setMaxCacheEntries(Integer.parseInt(val));
            }
            val = cfg.getInitParameter("maxCacheEntitySize");
            if (!StringUtils.isBlank(val)) {
                cc.setMaxObjectSize(Integer.parseInt(val));
            }
            if (BooleanUtils.toBoolean(cfg.getInitParameter("useHeuristicCaching"))) {
                cc.setHeuristicCachingEnabled(true);
            }
            CacheConfig cConf = cc.build();
            cb.setCacheConfig(cConf);
            val = cfg.getInitParameter("httpCacheStorage");
            if (!StringUtils.isBlank(val)) {
                HttpCacheStorage cs = null;
                ClassLoader cl = ObjectUtils.firstNonNull(Thread.currentThread().getContextClassLoader(),
                                                          getClass().getClassLoader());
                try {
                    Class<?> csClass = cl.loadClass(val);
                    Constructor<?> constructor = null;
                    try {
                        constructor = csClass.getConstructor(Map.class, CacheConfig.class);
                        Map props = new HashMap();
                        Enumeration<String> pnames = cfg.getInitParameterNames();
                        while (pnames.hasMoreElements()) {
                            String pname = pnames.nextElement();
                            props.put(pname, cfg.getInitParameter(pname));
                        }
                        cs = (HttpCacheStorage) constructor.newInstance(props, cConf);
                    } catch (NoSuchMethodException ignored) {
                        cs = (HttpCacheStorage) csClass.newInstance();
                    }
                } catch (Exception e) {
                    throw new ServletException(e);
                }
                log.info("Using cache HttpCacheStorage: " + cs);
                cb.setHttpCacheStorage(cs);
            }
        } else {
            builder = HttpClientBuilder.create();
        }

        httpClient = builder
                .setDefaultRequestConfig(RequestConfig.custom()
                                                 .setConnectionRequestTimeout(NumberUtils.toInt(cfg.getInitParameter("connectionRequestTimeout"), 1000))
                                                 .setConnectTimeout(NumberUtils.toInt(cfg.getInitParameter("connectTimeout"), 1000))
                                                 .setSocketTimeout(NumberUtils.toInt(cfg.getInitParameter("socketTimeout"), 10000))
                                                 .setAuthenticationEnabled(false)
                                                 .setCircularRedirectsAllowed(false)
                                                 .setRedirectsEnabled(false)
                                                 .setRelativeRedirectsAllowed(false)
                                                 .setStaleConnectionCheckEnabled(false)
                                                 .setExpectContinueEnabled(false)
                                                 .build())
                .setMaxConnPerRoute(NumberUtils.toInt(cfg.getInitParameter("maxConnPerRoute"), 10))
                .setMaxConnTotal(NumberUtils.toInt(cfg.getInitParameter("maxConnTotal"), 100))
                .setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE)
                .disableCookieManagement()
                .disableAuthCaching()
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableContentCompression()
                .build();


        //httpClient.//removeRequestInterceptorByClass(org.apache.http.protocol.RequestContent.class);


        String data = cfg.getInitParameter("dataUrl");
        if (data == null) {
            serverChain = null;
        } else {
            try {
                File dataFile = new File(cfg.getServletContext().getRealPath(data));
                ConfigParser parser = new ConfigParser(dataFile);
                serverChain = parser.getServerChain();
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    /**
     * @see javax.servlet.Filter#destroy()
     * <p/>
     * Called when this filter is destroyed.
     * Releases the fields.
     */
    public void destroy() {
        try {
            httpClient.close();
        } catch (IOException e) {
            log.error("", e);
        }
        log = null;
        httpClient = null;
        serverChain = null;
    }
}