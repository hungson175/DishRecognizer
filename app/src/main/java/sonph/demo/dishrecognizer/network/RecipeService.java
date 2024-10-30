package sonph.demo.dishrecognizer.network;

import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RecipeService {
    private static final String TAG = "RecipeService";
    static final String BASE_URL = "https://dish-backend-wlsl.onrender.com";
//
//    private static final String SERVER_URL = "http://192.168.0.138:8000/get_recipe";
    
    private final OkHttpClient client;

    public RecipeService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void sendImageForRecipe(byte[] imageBytes, RecipeCallback callback) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "image.jpg",
                        RequestBody.create(MediaType.parse("image/jpeg"), imageBytes))
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL+"/get_recipe")
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    callback.onSuccess(responseBody);
                } else {
                    callback.onFailure(new IOException("Unexpected response " + response));
                }
            }
        });
    }

    public interface RecipeCallback {
        void onSuccess(String response);
        void onFailure(Exception e);
    }
} 