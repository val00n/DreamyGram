package com.val00n.dmpg.model;

public class Instance {
    public String baseURL;
    public String apiEndpoint;
    public String avatarEndpoint;
    public String fileEndpoint;
    public String voiceEndpoint;
    public String qrcodeEndpoint;
    public String apiVersion;
    public String instancePassword;
    public boolean disableCertCheck;

    public Instance(String baseURL) {
        this.baseURL = baseURL;
        apiEndpoint = "api.php";
        avatarEndpoint = "ava.php";
        fileEndpoint = "file.php";
        voiceEndpoint = "voice.php";
        qrcodeEndpoint = "qrcode.php";
        apiVersion = "12";
        disableCertCheck = false;
        instancePassword = "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Instance)) return false;

        Instance other = (Instance) o;
        return baseURL != null && baseURL.equals(other.baseURL);
    }

    @Override
    public int hashCode() {
        return baseURL != null ? baseURL.hashCode() : 0;
    }
}
