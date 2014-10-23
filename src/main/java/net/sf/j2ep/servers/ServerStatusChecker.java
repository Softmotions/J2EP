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

package net.sf.j2ep.servers;

import net.sf.j2ep.model.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

/**
 * A class that will check if servers are online and
 * notify a listener when servers goes down and comes
 * back online again.
 *
 * @author Anders Nyman, Daniel Deng
 */
public class ServerStatusChecker extends Thread {

    /**
     * The online servers.
     */
    private LinkedList<Server> online;

    /**
     * The offline servers.
     */
    private LinkedList<Server> offline;

    /**
     * Client used to make the connections.
     * todo
     */
    //private HttpClient httpClient;

    /**
     * The listener we notify when a servers status changes.
     */
    private ServerStatusListener listener;

    /**
     * The time we wait between checking the servers status.
     */
    private long pollingTime;

    /**
     * Logging element supplied by commons-logging.
     */
    private static Logger log = LoggerFactory.getLogger(ServerStatusChecker.class);

    /**
     * Basic constructor sets the listener to notify when
     * servers goes down/up. Also sets the polling time
     * which decides how long we wait between doing checks.
     *
     * @param listener    The listener
     * @param pollingTime The time we wait between checks, in milliseconds
     */
    public ServerStatusChecker(ServerStatusListener listener, long pollingTime) {
        this.listener = listener;
        this.pollingTime = Math.max(30 * 1000, pollingTime);
        setPriority(Thread.NORM_PRIORITY - 1);
        setDaemon(true);

        online = new LinkedList<Server>();
        offline = new LinkedList<Server>();


        //todo!!!
        /*httpClient = new HttpClient();
        httpClient.getParams().setBooleanParameter(HttpClientParams.USE_EXPECT_CONTINUE, false);
        httpClient.getParams().setCookiePolicy(CookiePolicy.IGNORE_COOKIES);*/
    }

    /**
     * Runs the tests
     *
     * @see java.lang.Runnable#run()
     */
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        while (true) {
            checkOnlineServers();
            checkOfflineServers();
            try {
                sleep(pollingTime);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Checks all the servers marked as being online
     * if they still are online.
     */
    private synchronized void checkOnlineServers() {
        //todo
      /*  Iterator itr;
        itr = online.listIterator();
        while (itr.hasNext()) {
            Server server = (Server) itr.next();
            String url = getServerURL(server);
            GetMethod get = new GetMethod(url);
            get.setFollowRedirects(false);

            try {
                httpClient.executeMethod(get);
                if (!okServerResponse(get.getStatusCode())) {
                    offline.add(server);
                    itr.remove();
                    if (log.isDebugEnabled()) log.debug("Server going OFFLINE! " + getServerURL(server));
                    listener.serverOffline(server);
                }
            } catch (Exception e) {
                offline.add(server);
                itr.remove();
                if (log.isDebugEnabled()) log.debug("Server going OFFLINE! " + getServerURL(server));
                listener.serverOffline(server);
            } finally {
                get.releaseConnection();
            }
        }*/
    }

    /**
     * Checks if the offline servers has come back online
     * again.
     */
    private synchronized void checkOfflineServers() {
        //todo
        /*Iterator itr = offline.listIterator();
        while (itr.hasNext()) {
            Server server = (Server) itr.next();
            String url = getServerURL(server);
            GetMethod get = new GetMethod(url);
            get.setFollowRedirects(false);

            try {
                httpClient.executeMethod(get);
                if (okServerResponse(get.getStatusCode())) {
                    online.add(server);
                    itr.remove();
                    if (log.isDebugEnabled()) log.debug("Server back online " + getServerURL(server));
                    listener.serverOnline(server);
                }
            } catch (Exception e) {
                listener.serverOffline(server);
            } finally {
                get.releaseConnection();
            }
        }*/
    }

    /**
     * Returns the URL to the server
     *
     * @param server The server we are connection to
     * @return The URL
     */
    private String getServerURL(Server server) {
        return "http://" + server.getDomainName() + server.getPath() + "/";
    }

    /**
     * Checks the status code received from the server and
     * validates if this server should be considered online
     * or offline.
     *
     * @param statusCode The status code received
     * @return true if the server if online, otherwise false
     */
    private boolean okServerResponse(int statusCode) {
        return !(statusCode / 100 == 5);
    }

    /**
     * Adds a server that we will check for it's status.
     * The server is added to the offline list and will first
     * come online when we have managed to make a connection
     * to it.
     *
     * @param server The server to add
     */
    public synchronized void addServer(Server server) {
        offline.add(server);
    }
}