package com.val00n.dmpg.network;

import java.io.IOException;
import java.util.Map;

public class RestClient {
    private final int CONNECT_TIMEOUT = 20000;
    private final int READ_TIMEOUT = 30000;

    public String getString(String baseURL, String endpoint) throws IOException {
        return getString(baseURL, endpoint, null);
    }

    public String getString(String baseURL, String endpoint, Map<String, String> queryParams) throws IOException {
        HttpRequest request = new HttpRequest(baseURL, endpoint, "GET");
        request.addHeader("Accept", "application/json");
        request = addQueryParams(queryParams, request);

        HttpResponse response = HttpClient.send(request, CONNECT_TIMEOUT, READ_TIMEOUT);
        if (response.isSuccessfulCode()) {
            return response.readAsString();
        }
        throw new IOException("HTTP " + response.getStatusCode() + ": " + response.readAsString());
    }

    public byte[] getBytes(String baseURL, String endpoint) throws IOException {
        return getBytes(baseURL, endpoint, null);
    }

    public byte[] getBytes(String baseURL, String endpoint, Map<String, String> queryParams) throws IOException {
        HttpRequest request = new HttpRequest(baseURL, endpoint, "GET");
        request = addQueryParams(queryParams, request);

        HttpResponse response = HttpClient.send(request, CONNECT_TIMEOUT, READ_TIMEOUT);
        if (response.isSuccessfulCode()) {
            return response.readAsBytes();
        }
        throw new IOException("HTTP " + response.getStatusCode() + ": " + response.readAsString());
    }

    public String postString(String baseURL, String endpoint, Map<String, String> queryParams, Map<String, String> headers) throws IOException {
        return postString(baseURL, endpoint, 0, queryParams, headers, null, null);
    }

    public String postString(String baseURL, String endpoint, int timeout, Map<String, String> queryParams, Map<String, String> headers) throws IOException {
        return postString(baseURL, endpoint, timeout, queryParams, headers, null, null);
    }

    public String postString(String baseURL, String endpoint, int timeout, Map<String, String> queryParams, Map<String, String> headers, String body, String contentType) throws IOException {
        HttpRequest request = new HttpRequest(baseURL, endpoint, "POST");
        request = addQueryParams(queryParams, request);
        request = addHeaders(headers, request);
        if (body != null && body.length() > 0 && contentType != null && contentType.length() > 0)
            request.setBody(body, contentType);

        HttpResponse response;
        if (timeout > 0) {
            response = HttpClient.send(request, CONNECT_TIMEOUT, (timeout + 10) * 1000) ;
        } else {
            response = HttpClient.send(request, CONNECT_TIMEOUT, READ_TIMEOUT);
        }

        if (response.isSuccessfulCode()) {
            return response.readAsString();
        }
        throw new IOException("HTTP " + response.getStatusCode() + ": " + response.readAsString());
    }

    public byte[] postBytes(String baseURL, String endpoint, Map<String, String> queryParams, Map<String, String> headers) throws IOException {
        return postBytes(baseURL, endpoint, queryParams, headers, null, null);
    }

    public byte[] postBytes(String baseURL, String endpoint, Map<String, String> queryParams, Map<String, String> headers, String body, String contentType) throws IOException {
        HttpRequest request = new HttpRequest(baseURL, endpoint, "POST");
        request = addQueryParams(queryParams, request);
        request = addHeaders(headers, request);
        if (body != null && body.length() > 0 && contentType != null && contentType.length() > 0)
            request.setBody(body, contentType);

        HttpResponse response = HttpClient.send(request, CONNECT_TIMEOUT, READ_TIMEOUT);
        if (response.isSuccessfulCode()) {
            return response.readAsBytes();
        }
        throw new IOException("HTTP " + response.getStatusCode() + ": " + response.readAsString());
    }

    private HttpRequest addQueryParams(Map<String, String> queryParams, HttpRequest request) {
        if (queryParams != null && !queryParams.isEmpty()) {
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                request.addQuery(e.getKey(), e.getValue());
            }
        }
        return request;
    }

    private HttpRequest addHeaders(Map<String, String> headers, HttpRequest request) {
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                request.addHeader(e.getKey(), e.getValue());
            }
        }
        return request;
    }
}
