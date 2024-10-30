package sonph.demo.dishrecognizer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import sonph.demo.dishrecognizer.network.RecipeService;
import sonph.demo.dishrecognizer.utils.ImageUtils;

public class MainActivity extends AppCompatActivity {
    static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private Uri photoURI;
    private RecipeService recipeService;
    private MaterialCardView imagePreviewCard;
    private MaterialCardView recipeCard;
    private FrameLayout loadingContainer;
    private Markwon markwon;

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
        showLoading();
        imagePreviewCard.setAlpha(0.5f); // Dim the image while processing
        
        new Thread(() -> {
            try {
                byte[] resizedImageBytes = ImageUtils.getResizedImageBytes(this, imageUri);
                
                recipeService.sendImageForRecipe(resizedImageBytes, new RecipeService.RecipeCallback() {
                    @Override
                    public void onSuccess(String response) {
                        runOnUiThread(() -> {
                            hideLoading();
                            parseRecipeResponse(response);
                        });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        runOnUiThread(() -> {
                            hideLoading();
                            recipeCard.setVisibility(View.VISIBLE);
                            String errorMessage = "Error: " + e.getMessage();
                            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            displayRecipe(errorMessage);
                        });
                    }
                });
            } catch (IOException e) {
                Log.e("MainActivity", "Error preparing image", e);
                runOnUiThread(() -> {
                    hideLoading();
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
        hideLoading();
        recipeCard.setVisibility(View.VISIBLE);
        
        // Get reference to TextView
        TextView recipeContent = findViewById(R.id.recipeContent);
        
        // Set markdown text
        markwon.setMarkdown(recipeContent, recipe);
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
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // Make the preview card visible
            imagePreviewCard.setVisibility(View.VISIBLE);
            
            // Display the image
            ImageView imageView = findViewById(R.id.imagePreview);
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
        
        // Initialize views
        imagePreviewCard = findViewById(R.id.imagePreviewCard);
        recipeCard = findViewById(R.id.recipeCard);
        loadingContainer = findViewById(R.id.loadingContainer);

        // Initialize Markwon with plugins
        markwon = Markwon.builder(this)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(ImagesPlugin.create())
                .build();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        MaterialButton buttonCamera = findViewById(R.id.buttonCamera);
        buttonCamera.setOnClickListener(v -> {
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

    private void showError(String message) {
        loadingContainer.setVisibility(View.GONE);
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
            .setAction("Retry", v -> {
                // Implement retry logic here
            })
            .show();
    }

    private void showLoading() {
        loadingContainer.setVisibility(View.VISIBLE);
        recipeCard.setVisibility(View.GONE);
    }

    private void hideLoading() {
        loadingContainer.setVisibility(View.GONE);
        imagePreviewCard.setAlpha(1.0f); // Restore image opacity
    }

    private void displayImage(Uri imageUri) {
        try {
            ImageView imageView = findViewById(R.id.imagePreview);
            imageView.setImageURI(null); // Clear the previous image
            imageView.setImageURI(imageUri);
            imagePreviewCard.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e("MainActivity", "Error displaying image", e);
            Toast.makeText(this, "Error displaying image", Toast.LENGTH_SHORT).show();
        }
    }
}