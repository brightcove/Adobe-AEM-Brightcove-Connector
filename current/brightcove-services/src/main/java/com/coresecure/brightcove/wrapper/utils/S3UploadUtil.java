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
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return responseCode == 200;

    }
}
