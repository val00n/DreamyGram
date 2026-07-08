package com.val00n.dmpg.model;

import cc.nnproject.json.JSONException;
import cc.nnproject.json.JSONObject;

public class LoginResponse {
    private String response;        // "res" in JSON
    private String userToken;       // "user" in JSON
    private String phoneCodeHash;   // "phone_code_hash" in JSON
    private String captchaID;       // "captcha_id" in JSON
    private String text;            // "text" in JSON
    private long ping;              // not actually in JSON

    public long getPing() { return ping; }
    public void setPing(long ping) { this.ping = ping; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public String getUserToken() { return userToken; }
    public void setUserToken(String userToken) { this.userToken = userToken; }

    public String getPhoneCodeHash() { return phoneCodeHash; }
    public void setPhoneCodeHash(String phoneCodeHash) { this.phoneCodeHash = phoneCodeHash; }

    public String getCaptchaID() { return captchaID; }
    public void setCaptchaID(String captchaID) { this.captchaID = captchaID; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public static LoginResponse fromJSON(JSONObject obj) throws JSONException {
        if (obj == null) return null;

        LoginResponse resp = new LoginResponse();

        resp.setResponse(obj.getNullableString("res"));
        resp.setUserToken(obj.getNullableString("user"));
        resp.setPhoneCodeHash(obj.getNullableString("phone_code_hash"));
        resp.setCaptchaID(obj.getNullableString("captcha_id"));
        resp.setText(obj.getNullableString("text"));

        return resp;
    }
}
