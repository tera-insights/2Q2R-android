package com.terainsights.a2q2r_android;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * Encapsulates several Retrofit interfaces for communicating with a 2Q2R server.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 6/16/16
 */
public class U2FServices {

    public interface ServerInfo {
        @GET("./")
        Call<ResponseBody> getInfo();
    }

    public interface Registration {
        @POST("register")
        Call<ResponseBody> register(@Body RequestBody body);
    }

    public interface Authentication {
        @POST("auth")
        Call<ResponseBody> authenticate(@Body RequestBody body);
    }

}
