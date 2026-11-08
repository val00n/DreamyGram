package com.val00n.dmpg.network;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class HttpClient {
    private static final int DEFAULT_TIMEOUT = 25000;
    private static final int DEFAULT_READ_TIMEOUT = 35000;
    private static final int PING_TIMEOUT = 5000;
    private static String userAgent;

    public static void setUserAgent(String customUserAgent) {
        userAgent = customUserAgent;
    }

    public interface FileDownloadListener {
        void onProgress(long downloaded, long total);
        boolean isCancelled();
        void onConnectionOpened(HttpURLConnection connection);
    }

    public static HttpResponse send(HttpRequest request) throws IOException {
        return send(request, DEFAULT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    public static HttpResponse send(HttpRequest request, int connectTimeout, int readTimeout) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(request.buildUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(request.getMethod());
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
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

    public static void sendToFile(HttpRequest request, File file) throws IOException {
        sendToFile(request, file, DEFAULT_TIMEOUT, DEFAULT_READ_TIMEOUT, null);
    }

    public static void sendToFile(HttpRequest request,
                                  File file,
                                  int connectTimeout,
                                  int readTimeout,
                                  FileDownloadListener listener) throws IOException {
        if (file == null) {
            throw new IOException("file is null");
        }

        HttpURLConnection connection = null;
        InputStream is = null;
        FileOutputStream out = null;

        try {
            if (listener != null && listener.isCancelled()) {
                throw new IOException("Cancelled");
            }

            URL url = new URL(request.buildUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(request.getMethod());
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);

            connection.setRequestProperty("User-Agent", userAgent);
            connection.setRequestProperty("Accept", "*/*");

            for (Map.Entry<String, String> h : request.getHeaders().entrySet()) {
                connection.setRequestProperty(h.getKey(), h.getValue());
            }

            if (request.getBody() != null) {
                connection.setDoOutput(true);

                if (request.getBodyContentType() != null) {
                    connection.setRequestProperty("Content-Type", request.getBodyContentType());
                }

                connection.setFixedLengthStreamingMode(request.getBody().length);

                OutputStream os = connection.getOutputStream();
                try {
                    os.write(request.getBody());
                    os.flush();
                } finally {
                    try {
                        os.close();
                    } catch (IOException ignored) {
                    }
                }
            }

            if (listener != null) {
                listener.onConnectionOpened(connection);
            }

            if (listener != null && listener.isCancelled()) {
                throw new IOException("Cancelled");
            }

            int code = connection.getResponseCode();

            if (code < 200 || code >= 300) {
                String error = readLimitedString(connection.getErrorStream(), 1024);
                throw new IOException("HTTP " + code + ": " + error);
            }

            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Cannot create directory for download file");
            }

            long total = connection.getContentLength();
            long downloaded = 0;

            is = connection.getInputStream();
            out = new FileOutputStream(file);

            byte[] buffer = new byte[8192];
            int read;

            while ((read = is.read(buffer)) != -1) {
                if (listener != null && listener.isCancelled()) {
                    throw new IOException("Cancelled");
                }

                out.write(buffer, 0, read);
                downloaded += read;

                if (listener != null) {
                    listener.onProgress(downloaded, total);
                }
            }

            out.flush();
            out.getFD().sync();

            if (listener != null && listener.isCancelled()) {
                throw new IOException("Cancelled");
            }

        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }

            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readLimitedString(InputStream in, int maxBytes) throws IOException {
        if (in == null) {
            return "";
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int total = 0;
        int read;

        while (total < maxBytes && (read = in.read(buffer, 0, Math.min(buffer.length, maxBytes - total))) != -1) {
            out.write(buffer, 0, read);
            total += read;
        }

        return new String(out.toByteArray(), "UTF-8");
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

    public static int ping(String baseURL) throws IOException {
        URL url = new URL(baseURL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(PING_TIMEOUT);
        connection.setReadTimeout(PING_TIMEOUT);

        long start = System.currentTimeMillis();
        connection.connect();
        long elapsed = System.currentTimeMillis() - start;

        connection.disconnect();
        return (int) elapsed;
    }
}