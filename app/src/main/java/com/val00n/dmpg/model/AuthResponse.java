package com.val00n.dmpg.model;

import cc.nnproject.json.JSONObject;

public class AuthResponse {
    public String response;        // "res" in JSON
    public String userToken;       // "user" in JSON
    public String phoneCodeHash;   // "phone_code_hash" in JSON
    public String captchaID;       // "captcha_id" in JSON
    public String text;            // "text" in JSON

    public AuthResponse(JSONObject json) {
        if (json == null) return;

        response = json.getNullableString("res");
        userToken = json.getNullableString("user");
        phoneCodeHash = json.getNullableString("phone_code_hash");
        captchaID = json.getNullableString("captcha_id");
        text = json.getNullableString("text");
    }
}
