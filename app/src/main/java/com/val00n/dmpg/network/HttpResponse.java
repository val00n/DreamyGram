package com.val00n.dmpg.network;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpResponse {
    private final int statusCode;
    private final String statusMessage;
    private final Map<String, List<String>> headers;
    private final byte[] bodyBytes;

    public HttpResponse(int statusCode, String statusMessage, Map<String, List<String>> headers, byte[] bodyBytes) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers = headers != null ? headers : new HashMap<String, List<String>>();
        this.bodyBytes = bodyBytes;
    }

    public boolean isSuccessfulCode() {
        return statusCode >= 200 && statusCode < 300;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public Map<String, List<String>> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public byte[] readAsBytes() {
        return bodyBytes != null ? bodyBytes : new byte[0];
    }

    public String readAsString() {
        if (bodyBytes == null || bodyBytes.length == 0) return "";
        try {
            return new String(bodyBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return new String(bodyBytes);
        }
    }
}
