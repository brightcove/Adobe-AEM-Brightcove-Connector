/*

    Adobe AEM Brightcove Connector

    Copyright (C) 2018 Coresecure Inc.

    Authors:    Alessandro Bonfatti
                Yan Kisen
                Pablo Kropilnicki

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

    - Additional permission under GNU GPL version 3 section 7
    If you modify this Program, or any covered work, by linking or combining
    it with httpclient 4.1.3, httpcore 4.1.4, httpmine 4.1.3, jsoup 1.7.2,
    squeakysand-commons and squeakysand-osgi (or a modified version of those
    libraries), containing parts covered by the terms of APACHE LICENSE 2.0
    or MIT License, the licensors of this Program grant you additional
    permission to convey the resulting work.

 */
package com.coresecure.brightcove.wrapper.utils;

import com.coresecure.brightcove.wrapper.sling.ConfigurationGrabber;
import com.coresecure.brightcove.wrapper.sling.ConfigurationService;
import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by alessandro.bonfatti on 7/14/17.
 */
public class S3UploadUtil {

    private transient ServiceUtil serviceUtil = null;
    private transient ConfigurationGrabber cg;
    private transient com.coresecure.brightcove.wrapper.BrightcoveAPI brAPI;
    private List<String> allowedGroups = new ArrayList<String>();
    private transient ConfigurationService cs;


    public S3UploadUtil() {/* default implementation ignored */}

    static int BUFFER_SIZE = 4096;
    private static final Logger LOGGER = LoggerFactory.getLogger(S3UploadUtil.class);

    public static boolean uploadToUrl(URL url, InputStream inputStream) {

        int responseCode = 0;
        try {
            HttpURLConnection connection;


            //UNCOMMENT FOR REGULAR CONNECTION
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("PUT");
            OutputStream out = connection.getOutputStream();

            byte[] buf = new byte[1024];
            int count;
            int total = 0;

            while ((count =inputStream.read(buf)) != -1)
            {
                if (Thread.interrupted())
                {
                    throw new IOException();
                }
                out.write(buf, 0, count);
                total += count;

                LOGGER.trace(String.format("bytes: %d", total));
            }
            out.close();
            inputStream.close();

            LOGGER.debug("Finishing...");
            responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                LOGGER.info("Successfully uploaded.");
            } else {
            	LOGGER.error("***************************S3UploadUtil: Error uploading asset: " + responseCode);
            }
        } catch (IOException e) {
            LOGGER.error("IOException",e);
        } catch (Exception e) {
        	LOGGER.error("***************************S3UploadUtil: Error uploading asset");
        }
        return responseCode == 200;

    }


    public static boolean uploadToUrl(URL url, InputStream inputStream, String proxy_address) {

        LOGGER.trace("*** INPUT PROXY ADDRESS ***" + proxy_address);


        String _host = "";
        int _port =  0;
        if(!proxy_address.isEmpty() && proxy_address.contains(":"))
        {
            _host = proxy_address.split(":")[0];
            _port =  Integer.parseInt(proxy_address.split(":")[1]);
        }

        LOGGER.trace("*** PROCESSED ADDRESS: " + _host + ":" + _port);

        int responseCode = 0;
        try {

            HttpURLConnection connection;
            //MANUAL PROXY ADDITION
            //Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("192.168.11.89", 3128));


            LOGGER.trace("***>>>" + proxy_address);

            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(_host, _port));
            connection = proxy!=null ? (HttpsURLConnection) url.openConnection(proxy) : (HttpURLConnection) url.openConnection();

            //UNCOMMENT FOR REGULAR CONNECTION
            //            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("PUT");
            OutputStream out = connection.getOutputStream();

            byte[] buf = new byte[1024];
            int count;
            int total = 0;

            while ((count =inputStream.read(buf)) != -1)
            {
                if (Thread.interrupted())
                {
                    throw new IOException();
                }
                out.write(buf, 0, count);
                total += count;

                LOGGER.trace(String.format("bytes: %d", total));
            }
            out.close();
            inputStream.close();

            LOGGER.debug("Finishing...");
            responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                LOGGER.info("Successfully uploaded.");
            } else {
            	LOGGER.error("***************************S3UploadUtil: Error uploading asset: " + responseCode);
            }
        } catch (IOException e) {
            LOGGER.error("IOException",e);
        } catch (Exception e) {
        	LOGGER.error("***************************S3UploadUtil: Error uploading asset");
        }
        return responseCode == 200;

    }



    public static boolean uploadToUrl(URL url, InputStream inputStream, Proxy proxy) {

        LOGGER.trace("*** INPUT PROXY ADDRESS ***" + proxy.toString());


        int responseCode = 0;
        try {

            HttpURLConnection connection;
            //MANUAL PROXY ADDITION
            //Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("192.168.11.89", 3128));
            connection = proxy!=null ? (HttpsURLConnection) url.openConnection(proxy) : (HttpURLConnection) url.openConnection();

            //UNCOMMENT FOR REGULAR CONNECTION
            //            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("PUT");
            OutputStream out = connection.getOutputStream();

            byte[] buf = new byte[1024];
            int count;
            int total = 0;

            while ((count =inputStream.read(buf)) != -1)
            {
                if (Thread.interrupted())
                {
                    throw new IOException();
                }
                out.write(buf, 0, count);
                total += count;

                LOGGER.trace(String.format("bytes: %d", total));
            }
            out.close();
            inputStream.close();

            LOGGER.debug("Finishing...");
            responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                LOGGER.info("Successfully uploaded.");
            } else {
            	LOGGER.error("***************************S3UploadUtil: Error uploading asset: " + responseCode);
            }
        } catch (IOException e) {
            LOGGER.error("IOException",e);
        } catch (Exception e) {
        	LOGGER.error("***************************S3UploadUtil: Error uploading asset");
        }
        return responseCode == 200;

    }




}
