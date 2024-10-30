package sonph.demo.dishrecognizer.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtils {
    private static final int TARGET_SIZE = 1024;
    private static final String TAG = "ImageUtils";

    public static byte[] getResizedImageBytes(Context context, Uri imageUri) throws IOException {
        InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
        Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
        inputStream.close();

        // Calculate scaling factors
        float scale = Math.min(
                (float) TARGET_SIZE / originalBitmap.getWidth(),
                (float) TARGET_SIZE / originalBitmap.getHeight());

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        Bitmap resizedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0, 0,
                originalBitmap.getWidth(),
                originalBitmap.getHeight(),
                matrix,
                true);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
        
        Log.d(TAG, String.format("Image resized from %dx%d to %dx%d",
                originalBitmap.getWidth(), originalBitmap.getHeight(),
                resizedBitmap.getWidth(), resizedBitmap.getHeight()));

        // Recycle bitmaps to free memory
        if (originalBitmap != resizedBitmap) {
            originalBitmap.recycle();
        }
        resizedBitmap.recycle();

        return outputStream.toByteArray();
    }
} 