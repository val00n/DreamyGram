package com.val00n.dmpg.model;

import com.val00n.dmpg.services.MPGramService;
import java.util.Map;

public class FileParams {
    private String p;
    private String s;
    private String tw;
    private String th;

    private FileParams(String p) {
        this.p = p;
    }

    public static FileParams original() {
        return new FileParams("rorig");
    }

    public static FileParams preview() {
        FileParams params = new FileParams("rprev");
        params.s = MPGramService.FILE_PREVIEW_SIZE;
        return params;
    }

    public static FileParams preview(String customSize) {
        FileParams params = new FileParams("rprev");
        params.s = customSize;
        return params;
    }

    public static FileParams fileThumb() {
        return new FileParams("thumbrprev");
    }

    public static FileParams audioThumb() {
        return new FileParams("thumbraudio");
    }

    public static FileParams file() {
        return new FileParams(null);
    }

    public static FileParams view(int targetWidth, int targetHeight) {
        FileParams params = new FileParams("rview");
        params.tw = Integer.toString(targetWidth);
        params.th = Integer.toString(targetHeight);
        return params;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String p;
        private String s;
        private String tw;
        private String th;

        public Builder conversion(String p) {
            this.p = p;
            return this;
        }

        public Builder size(String s) {
            this.s = s;
            return this;
        }

        public Builder targetSize(int tw, int th) {
            this.tw = Integer.toString(tw);
            this.th = Integer.toString(th);
            return this;
        }

        public FileParams build() {
            FileParams params = new FileParams(p);
            params.s = s;
            params.tw = tw;
            params.th = th;
            return params;
        }
    }

    public boolean isThumbnail() {
        return p != null && p.startsWith("thumb");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof FileParams)) {
            return false;
        }

        FileParams other = (FileParams) o;

        return stringEquals(p, other.p)
                && stringEquals(s, other.s)
                && stringEquals(tw, other.tw)
                && stringEquals(th, other.th);
    }

    @Override
    public int hashCode() {
        int result = 17;

        result = 31 * result + stringHash(p);
        result = 31 * result + stringHash(s);
        result = 31 * result + stringHash(tw);
        result = 31 * result + stringHash(th);

        return result;
    }

    private static boolean stringEquals(String a, String b) {
        if (a == null) {
            return b == null;
        }

        return a.equals(b);
    }

    private static int stringHash(String s) {
        if (s == null) {
            return 0;
        }

        return s.hashCode();
    }

    public void applyTo(Map<String, String> queryParams) {
        if (p != null) queryParams.put("p", p);
        if (s != null) queryParams.put("s", s);
        if (tw != null) queryParams.put("tw", tw);
        if (th != null) queryParams.put("th", th);
    }
}
