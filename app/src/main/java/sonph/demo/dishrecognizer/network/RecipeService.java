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
    private static final String SERVER_URL = "http://192.168.0.138:8000/get_recipe";
    
    private final OkHttpClient client;

    public RecipeService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void sendImageForRecipe(byte[] imageBytes, RecipeCallback callback) {
        RequestBody formBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "dish.jpg",
                        RequestBody.create(MediaType.parse("image/jpeg"), imageBytes))
                .addFormDataPart("message", "Describe this dish and provide the ingredients and recipe.")
                .build();

        Request request = new Request.Builder()
                .url(SERVER_URL)
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error", e);
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                Log.d(TAG, "Server response: " + responseBody);
                
                if (response.isSuccessful()) {
                    callback.onSuccess(responseBody);
                } else {
                    callback.onFailure(new IOException("Server error: " + response.code()));
                }
            }
        });
    }

    public interface RecipeCallback {
        void onSuccess(String response);
        void onFailure(Exception e);
    }
} 