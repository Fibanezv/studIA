package com.app.estudiaapp.api;

import com.app.estudiaapp.models.Question;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ApiService {
    @Multipart
    @POST("upload")
    Call<List<Question>> uploadFile(@Part MultipartBody.Part file);
}
