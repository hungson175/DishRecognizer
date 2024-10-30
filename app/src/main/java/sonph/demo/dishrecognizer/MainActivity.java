package sonph.demo.dishrecognizer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import sonph.demo.dishrecognizer.network.RecipeService;
import sonph.demo.dishrecognizer.utils.ImageUtils;

public class MainActivity extends AppCompatActivity {
    static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final String SERVER_URL = "http://192.168.0.138:8000/get_recipe";
    private Uri photoURI;
    private RecipeService recipeService;

    private void dispatchTakePictureIntent() {
        Log.d("MainActivity", "dispatchTakePictureIntent");
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        
        // Create a file to save the image
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            Log.e("MainActivity", "Error creating image file", ex);
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
            return;
        }

        // Continue if the file was created
        if (photoFile != null) {
            try {
                photoURI = FileProvider.getUriForFile(this,
                        "sonph.demo.dishrecognizer.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                
                // Add this flag to grant temporary permission to the camera app
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            } catch (Exception e) {
                Log.e("MainActivity", "Error starting camera", e);
                Toast.makeText(this, "Error starting camera: " + e.getMessage(), 
                              Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendImageToServer(Uri imageUri) {
        findViewById(R.id.loadingProgressBar).setVisibility(View.VISIBLE);
        findViewById(R.id.recipeTextView).setVisibility(View.GONE);

        new Thread(() -> {
            try {
                byte[] resizedImageBytes = ImageUtils.getResizedImageBytes(this, imageUri);
                
                recipeService.sendImageForRecipe(resizedImageBytes, new RecipeService.RecipeCallback() {
                    @Override
                    public void onSuccess(String response) {
                        runOnUiThread(() -> {
                            findViewById(R.id.loadingProgressBar).setVisibility(View.GONE);
                            parseRecipeResponse(response);
                        });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        runOnUiThread(() -> {
                            findViewById(R.id.loadingProgressBar).setVisibility(View.GONE);
                            findViewById(R.id.recipeTextView).setVisibility(View.VISIBLE);
                            String errorMessage = "Error: " + e.getMessage();
                            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            displayRecipe(errorMessage);
                        });
                    }
                });
            } catch (IOException e) {
                Log.e("MainActivity", "Error preparing image", e);
                runOnUiThread(() -> {
                    findViewById(R.id.loadingProgressBar).setVisibility(View.GONE);
                    String errorMessage = "Error preparing image: " + e.getMessage();
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    displayRecipe(errorMessage);
                });
            }
        }).start();
    }

    private void parseRecipeResponse(String jsonResponse) {
        runOnUiThread(() -> {
            try {
                JSONObject jsonObject = new JSONObject(jsonResponse);
                if (jsonObject.has("recipe")) {
                    String recipe = jsonObject.getString("recipe");
                    displayRecipe(recipe);
                } else if (jsonObject.has("error")) {
                    String error = jsonObject.getString("error");
                    Log.e("MainActivity", "parseRecipeResponse: Error: " + error);
                    Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    displayRecipe(error);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void displayRecipe(String recipe) {
        TextView recipeTextView = findViewById(R.id.recipeTextView);
        recipeTextView.setVisibility(View.VISIBLE);
        recipeTextView.setText(recipe);
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        /* prefix */
        /* suffix */
        /* directory */
        return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Handle the camera result
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // Display the image
            ImageView imageView = findViewById(R.id.imageView);
            imageView.setImageURI(photoURI);
            // Send the image to the backend server
            sendImageToServer(photoURI);
        }
    }

    // Handle the permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Camera permission granted");
                dispatchTakePictureIntent();
            } else {
                Log.d("MainActivity", "Camera permission denied");
                Toast.makeText(this, "Camera permission is required to use this feature.", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // Initialize RecipeService
        recipeService = new RecipeService();
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        Button captureButton = findViewById(R.id.captureButton);
        captureButton.setOnClickListener(v -> {
            Log.d("MainActivity", "Capture button clicked");
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Requesting camera permission");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            } else {
                dispatchTakePictureIntent();
            }
        });
        testServerConnection();
    }

    private void testServerConnection() {
        new Thread(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("192.168.0.138", 8000), 5000);
                socket.close();
                Log.d("MainActivity", "Server is reachable");
            } catch (IOException e) {
                Log.e("MainActivity", "Server is not reachable: " + e.getMessage());
            }
        }).start();
    }
}