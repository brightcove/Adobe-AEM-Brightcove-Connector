/*

    Adobe AEM Brightcove Connector

    Copyright (C) 2017 Coresecure Inc.

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.BASE64Encoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by alessandro.bonfatti on 7/14/17.
 */
public class S3UploadUtil {
    static int BUFFER_SIZE = 4096;
    private static final Logger LOGGER = LoggerFactory.getLogger(S3UploadUtil.class);

    public static boolean uploadToUrl(URL url, InputStream inputStream) {

        HttpURLConnection connection;
        int responseCode = 0;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("PUT");
            OutputStream out =
                    connection.getOutputStream();

            byte[] buf = new byte[1024];
            int count;
            int total = 0;

            while ((count =inputStream.read(buf)) != -1)
            {
                if (Thread.interrupted())
                {
                    throw new InterruptedException();
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
            }
        } catch (IOException e) {
            LOGGER.error("IOException",e);
        } catch (InterruptedException e) {
            LOGGER.error("InterruptedException",e);
        }
        return responseCode == 200;

    }
}
