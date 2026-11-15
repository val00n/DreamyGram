package com.val00n.dmpg.network;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class RestClient {
    private final int CONNECT_TIMEOUT = 20000;
    private final int READ_TIMEOUT = 30000;
    private final int FILE_READ_TIMEOUT = 120000;

    public String getString(String baseURL, String endpoint, boolean disableCertCheck) throws IOException {
        return getString(baseURL, endpoint, null, disableCertCheck);
    }

    public String getString(String baseURL, String endpoint, Map<String, String> queryParams, boolean disableCertCheck) throws IOException {
        HttpRequest request = new HttpRequest(baseURL, endpoint, "GET");
        request.addHeader("Accept", "application/json");
        request = addQueryParams(queryParams, request);

        HttpResponse response = HttpClient.send(request, CONNECT_TIMEOUT, READ_TIMEOUT, disableCertCheck);
        if (response.isSuccessfulCode()) {
            return response.readAsString();
        }
        throw new IOException("HTTP " + response.getStatusCode() + ": " + response.readAsString());
    }

    public byte[] getBytes(String baseURL, String endpoint, boolean disableCertCheck) throws IOException {
        return getBytes(baseURL, endpoint, null, disableCertCheck);
    }

    public byte[] getBytes(String baseURL, String endpoint, Map<String, String> queryParams, boolean disableCertCheck) throws IOException {
        HttpRequest request = new HttpRequest(baseURL, endpoint, "GET");
        request = addQueryParams(queryParams, request);

        HttpResponse response = HttpClient.send(request, CONNECT_TIMEOUT, READ_TIMEOUT, disableCertCheck);
        if (response.isSuccessfulCode()) {
            return response.readAsBytes();
        }
        throw new IOException("HTTP " + response.getStatusCode() + ": " + response.readAsString());
    }

    public void downloadFile(String baseURL, String endpoint, Map<String, String> queryParams, Map<String, String> headers, File targetFile, boolean disableCertCheck, HttpClient.FileDownloadListener listener) throws IOException {
        HttpRequest request = new HttpRequest(baseURL, endpoint, "POST");
        request = addQueryParams(queryParams, request);
        request = addHeaders(headers, request);

        HttpClient.sendToFile(request, targetFile, CONNECT_TIMEOUT, FILE_READ_TIMEOUT, disableCertCheck, listener);
    }

    public String postString(String baseURL, String endpoint, Map<String, String> queryParams, Map<String, String> headers, boolean disableCertCheck) throws IOException {
        return postString(baseURL, endpoint, 0, queryParams, headers, null, null, disableCertCheck);
    }

    public String postString(String baseURL, String endpoint, int timeout, Map<String, String> queryParams, Map<String, String> headers, boolean disableCertCheck) throws IOException {
        return postString(baseURL, endpoint, timeout, queryParams, headers, null, null, disableCertCheck);
    }

    public String postString(String baseURL, String endpoint, int timeout, Map<String, String> queryParams, Map<String, String> headers, String body, String contentType, boolean disableCertCheck) throws IOException {
        HttpRequest request = new HttpRequest(baseURL, endpoint, "POST");
        request = addQueryParams(queryParams, request);
        request = addHeaders(headers, request);
        if (body != null && body.length() > 0 && contentType != null && contentType.length() > 0)
            request.setBody(body, contentType);

        HttpResponse response;
        if (timeout > 0) {
            response = HttpClient.send(request, CONNECT_TIMEOUT, (timeout + 15) * 1000, disableCertCheck) ;
        } else {
            response = HttpClient.send(request, CONNECT_TIMEOUT, READ_TIMEOUT, disableCertCheck);
        }

        if (response.isSuccessfulCode()) {
            return response.readAsString();
        }
        throw new IOException("HTTP " + response.getStatusCode() + ": " + response.readAsString());
    }

    public byte[] postBytes(String baseURL, String endpoint, Map<String, String> queryParams, Map<String, String> headers, boolean disableCertCheck) throws IOException {
        return postBytes(baseURL, endpoint, queryParams, headers, null, null, disableCertCheck);
    }

    public byte[] postBytes(String baseURL, String endpoint, Map<String, String> queryParams, Map<String, String> headers, String body, String contentType, boolean disableCertCheck) throws IOException {
        HttpRequest request = new HttpRequest(baseURL, endpoint, "POST");
        request = addQueryParams(queryParams, request);
        request = addHeaders(headers, request);
        if (body != null && body.length() > 0 && contentType != null && contentType.length() > 0)
            request.setBody(body, contentType);

        HttpResponse response = HttpClient.send(request, CONNECT_TIMEOUT, READ_TIMEOUT, disableCertCheck);
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
