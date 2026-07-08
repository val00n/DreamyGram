package com.val00n.dmpg.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class HttpClient {
    private static final int DEFAULT_TIMEOUT = 20000;
    private static String userAgent;

    public static void setUserAgent(String customUserAgent) {
        userAgent = customUserAgent;
    }

    public static HttpResponse send(HttpRequest request) throws IOException {
        return send(request, DEFAULT_TIMEOUT);
    }

    public static HttpResponse send(HttpRequest request, int timeoutMs) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(request.buildUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(request.getMethod());
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(true);

            // headers
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setRequestProperty("Accept", "*/*");
            for (Map.Entry<String, String> h : request.getHeaders().entrySet()) {
                connection.setRequestProperty(h.getKey(), h.getValue());
            }

            // body
            if (request.getBody() != null) {
                connection.setDoOutput(true);
                if (request.getBodyContentType() != null) {
                    connection.setRequestProperty("Content-Type", request.getBodyContentType());
                }
                connection.setFixedLengthStreamingMode(request.getBody().length);
                OutputStream os = connection.getOutputStream();
                os.write(request.getBody());
                os.flush();
                os.close();
            }

            // read
            int code = connection.getResponseCode();
            String msg = connection.getResponseMessage();
            Map<String, List<String>> respHeaders = connection.getHeaderFields();

            InputStream is = (code >= 200 && code < 300) ? connection.getInputStream() : connection.getErrorStream();
            byte[] body = readStream(is);
            if (is != null) is.close();

            return new HttpResponse(code, msg, respHeaders, body);

        } catch (IOException e) {
            throw e;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] readStream(InputStream in) throws IOException {
        if (in == null) return new byte[0];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
}