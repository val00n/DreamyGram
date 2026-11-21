package com.val00n.dmpg.network;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MultipartBody {
    private static final byte[] CRLF = new byte[] { '\r', '\n' };

    private final String boundary;
    private final byte[] endBoundary;
    private final List<Part> parts = new ArrayList<Part>();

    public MultipartBody() {
        this.boundary = "----Boundary" + UUID.randomUUID().toString().replace("-", "");
        try {
            this.endBoundary = ("--" + boundary + "--\r\n").getBytes("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getContentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    public MultipartBody addField(String name, String value) throws IOException {
        if (name == null || value == null) return this;

        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"").append(sanitize(name)).append("\"\r\n");
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n\r\n");

        byte[] header = sb.toString().getBytes("UTF-8");
        byte[] data = value.getBytes("UTF-8");
        parts.add(new BytesPart(header, data));
        return this;
    }

    public MultipartBody addFile(String fieldName, File file, String fileName, String mimeType) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IOException("File does not exist");
        }

        String safeFileName = (fileName != null && fileName.length() > 0) ? fileName : file.getName();
        String contentType = (mimeType != null && mimeType.length() > 0)
                ? mimeType
                : "application/octet-stream";

        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"").append(sanitize(fieldName))
                .append("\"; filename=\"").append(sanitize(safeFileName)).append("\"\r\n");
        sb.append("Content-Type: ").append(contentType).append("\r\n\r\n");

        byte[] header = sb.toString().getBytes("UTF-8");
        parts.add(new FilePart(header, file));
        return this;
    }

    public long getContentLength() {
        long total = endBoundary.length;
        for (int i = 0; i < parts.size(); i++) {
            total += parts.get(i).getLength();
        }
        return total;
    }

    public void writeTo(OutputStream out) throws IOException {
        for (int i = 0; i < parts.size(); i++) {
            parts.get(i).writeTo(out);
        }
        out.write(endBoundary);
    }

    private static String sanitize(String s) {
        return s.replace("\"", "_").replace("\r", "").replace("\n", "");
    }

    private interface Part {
        long getLength();
        void writeTo(OutputStream out) throws IOException;
    }

    private static class BytesPart implements Part {
        private final byte[] header;
        private final byte[] data;

        BytesPart(byte[] header, byte[] data) {
            this.header = header;
            this.data = data;
        }

        public long getLength() {
            return (long) header.length + (long) data.length + CRLF.length;
        }

        public void writeTo(OutputStream out) throws IOException {
            out.write(header);
            out.write(data);
            out.write(CRLF);
        }
    }

    private static class FilePart implements Part {
        private final byte[] header;
        private final File file;
        private final long fileLength;

        FilePart(byte[] header, File file) {
            this.header = header;
            this.file = file;
            this.fileLength = file.length();
        }

        public long getLength() {
            return (long) header.length + fileLength + CRLF.length;
        }

        public void writeTo(OutputStream out) throws IOException {
            out.write(header);
            InputStream in = null;
            try {
                in = new FileInputStream(file);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } finally {
                if (in != null) {
                    try { in.close(); } catch (IOException ignored) {}
                }
            }
            out.write(CRLF);
        }
    }
}