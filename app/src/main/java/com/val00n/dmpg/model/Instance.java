package com.val00n.dmpg.model;

public class Instance {
    public String baseURL;
    public String apiEndpoint;
    public String avatarEndpoint;
    public String qrcodeEndpoint;
    public String apiVersion;
    public String instancePassword;
    public boolean disableCertCheck;

    public Instance(String baseURL) {
        this.baseURL = baseURL;
        apiEndpoint = "api.php";
        avatarEndpoint = "ava.php";
        qrcodeEndpoint = "qrcode.php";
        apiVersion = "10";
        disableCertCheck = false;
        instancePassword = "";
    }
}
