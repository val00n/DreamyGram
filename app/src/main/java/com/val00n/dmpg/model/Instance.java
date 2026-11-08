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
}
