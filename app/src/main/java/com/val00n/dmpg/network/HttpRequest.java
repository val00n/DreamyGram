package com.val00n.dmpg.network;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
    private final String baseUrl;
    private final String path;
    private final String method;
    private final Map<String, String> headers;
    private final Map<String, String> queryParams;
    private byte[] body;
    private String bodyContentType;

    public HttpRequest(String baseUrl, String path, String method) {
        this.baseUrl = baseUrl;
        this.path = path;
        this.method = method;
        this.headers = new HashMap<String, String>();
        this.queryParams = new HashMap<String, String>();
    }

    public HttpRequest addHeader(String key, String value) {
        headers.put(key, value);
        return this;
    }

    public HttpRequest addQuery(String key, String value) {
        queryParams.put(key, value);
        return this;
    }

    public HttpRequest setBody(String data, String contentType) {
        try {
            this.body = data.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {}
        this.bodyContentType = contentType;
        return this;
    }

    public HttpRequest setBody(byte[] data, String contentType) {
        this.body = data;
        this.bodyContentType = contentType;
        return this;
    }

    public String buildUrl() {
        StringBuilder sb = new StringBuilder(baseUrl);
        if (path != null && path.length() > 0) {
            if (!path.startsWith("/")) sb.append("/");
            sb.append(path);
        }
        if (!queryParams.isEmpty()) {
            sb.append("?");
            boolean first = true;
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                if (!first) sb.append("&");
                try {
                    sb.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                            .append("=")
                            .append(URLEncoder.encode(e.getValue(), "UTF-8"));
                } catch (UnsupportedEncodingException ex) {
                    sb.append(e.getKey()).append("=").append(e.getValue());
                }
                first = false;
            }
        }
        return sb.toString();
    }

    public String getMethod() { return method; }
    public Map<String, String> getHeaders() { return headers; }
    public byte[] getBody() { return body; }
    public String getBodyContentType() { return bodyContentType; }
}