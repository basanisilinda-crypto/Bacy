package za.co.bacyflex.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class Cloud {
    public static final String URL = "https://fmhgrjckvdgdovqckoww.supabase.co/";
    // Publishable client key only. NEVER put a service-role key in an APK.
    private static final String KEY = "sb_publishable_3aHZgFWaKu_061_damWyhg_5PAEuJBn";
    private Cloud() {}

    public static String request(String method, String route, JSONObject data, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(URL + route).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setRequestProperty("apikey", KEY);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Content-Type", "application/json");
            if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
            if (data != null) {
                c.setDoOutput(true);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(data.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = c.getResponseCode();
            InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
            String result = "";
            if (stream != null) try (InputStream input=stream; ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int n;
                while ((n=input.read(buffer))!=-1) bytes.write(buffer,0,n);
                result = bytes.toString("UTF-8");
            }
            if (status >= 400) throw new IOException("Server " + status + ": " + result);
            return result;
        } finally { c.disconnect(); }
    }
    public static JSONObject signIn(String email, String password) throws Exception {
        JSONObject credentials=new JSONObject().put("email",email.trim()).put("password",password);
        return new JSONObject(request("POST","auth/v1/token?grant_type=password",credentials,null));
    }
    public static String signUp(String email,String password) throws Exception {
        JSONObject credentials=new JSONObject().put("email",email.trim()).put("password",password);
        return request("POST","auth/v1/signup",credentials,null);
    }
    public static JSONArray rows(String tableAndQuery,String token) throws Exception {
        return new JSONArray(request("GET","rest/v1/"+tableAndQuery,null,token));
    }
    public static String submit(String path,JSONObject body,String token) throws Exception {
        return request("POST","rest/v1/"+path,body,token);
    }
    public static JSONObject rpc(String name,JSONObject body,String token) throws Exception {
        return new JSONObject(request("POST","rest/v1/rpc/"+name,body,token));
    }
}

