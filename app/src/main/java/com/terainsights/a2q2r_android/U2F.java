package com.terainsights.a2q2r_android;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * Encapsulates multiple Retrofit interfaces for making U2F server calls.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/20/16
 */
public class U2F {

    /**
     * GET call for server info.
     */
    public interface ServerInfo {
        @GET("./")
        Call<ResponseBody> getInfo();
    }

    /**
     * POST call for sending U2F registration data.
     */
    public interface Registration {
        @POST("register")
        Call<ResponseBody> register(@Body RequestBody body);
    }

    /**
     * POST call for sending U2F authentication data.
     */
    public interface Authentication {
        @POST("auth")
        Call<ResponseBody> authenticate(@Body RequestBody body);
    }

}
