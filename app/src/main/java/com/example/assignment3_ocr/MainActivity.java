package com.example.assignment3_ocr;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int IMAGE_PICK_CODE = 1000;
    private ImageView imageView;
    private TextView textViewOcrResult;
    private Uri imageUri;

    private Bitmap mBitmap;

    private String api_key = "Your_API_KEY";
    private String app_link = "https://ocrimageapi.cognitiveservices.azure.com/computervision/imageanalysis:analyze?api-version=2024-02-01&features=read&model-version=latest&language=en";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        textViewOcrResult = findViewById(R.id.textViewOcrResult);
        Button buttonSelectImage = findViewById(R.id.buttonSelectImage);
        Button buttonAnalyze = findViewById(R.id.buttonAnalyze);

        buttonSelectImage.setOnClickListener(view -> pickImageFromGallery());

        buttonAnalyze.setOnClickListener(view -> {
            try {
                analyzeImage();
            } catch (IOException e) {
                e.printStackTrace();
                textViewOcrResult.setText("Failed to analyze image.");
            }
        });
    }

    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityIfNeeded(intent, IMAGE_PICK_CODE);
    }

    // Handle the result of image selection
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == IMAGE_PICK_CODE) {
            imageUri = data.getData();
            imageView.setImageURI(imageUri);
            try {
                mBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (mBitmap != null) {
                // Show the image on screen.
                imageView.setImageBitmap(mBitmap);
            }
        }
    }

    private void analyzeImage() throws IOException {
        if (mBitmap == null) {
            Log.e("AzureCV", "Error: Bitmap is null");
            return;
        }

        if (mBitmap.getWidth() == 0 || mBitmap.getHeight() == 0) {
            Log.e("AzureCV", "Error: Bitmap dimensions are zero");
            return;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        boolean compressionSuccess = mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

        if (!compressionSuccess) {
            Log.e("AzureCV", "Error: Bitmap compression failed");
            return;
        }
       // mBitmap.compress(Bitmap.CompressFormat.JPEG,100,outputStream);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        if (imageUri != null) {
            // Convert image URI to byte array, then make a request to the OCR API
            byte[] imageBytes = outputStream.toByteArray();
            Log.d("AzureCV", "Image size: " + imageBytes.length);
            // Check if the image size is within the allowed limits
            if (imageBytes.length == 0 || imageBytes.length > 20971520) {
                Log.e("AzureCV", "Error: Invalid image size");
                return;
            }

            // Run network request in the background
            new Thread(() -> {

               OkHttpClient client = new OkHttpClient();
                MediaType MEDIA_TYPE_OCTET_STREAM = MediaType.parse("application/octet-stream");
                RequestBody requestBody = RequestBody.create(imageBytes,MEDIA_TYPE_OCTET_STREAM);
                Request request = new Request.Builder()
                        .url(app_link)
                        .post(requestBody)
                        .addHeader("Ocp-Apim-Subscription-Key", api_key)
                        .addHeader("Content-Type", "application/octet-stream")
                        .build();


                try(Response response = client.newCall(request).execute()){
                    final String responseData = Objects.requireNonNull(response.body()).string();
                    JSONObject jsonResponse = new JSONObject(responseData);

                    // Extract text from the JSON based on its structure
                    String extractedText = extractTextFromJson(jsonResponse);

                    // Update UI with the extracted text
                    runOnUiThread(() -> textViewOcrResult.setText(extractedText));

                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> textViewOcrResult.setText("Error during image analysis."));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
    }

    private String extractTextFromJson(JSONObject jsonResponse) {
        StringBuilder extractedText = new StringBuilder();

        try {
            // Get the readResult JSONObject
            JSONObject readResult = jsonResponse.getJSONObject("readResult");

            // Get the blocks JSONArray
            JSONArray blocks = readResult.getJSONArray("blocks");

            // Iterate through each block
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.getJSONObject(i);
                JSONArray lines = block.getJSONArray("lines");

                // Iterate through each line
                for (int j = 0; j < lines.length(); j++) {
                    JSONObject line = lines.getJSONObject(j);
                    String lineText = line.getString("text");

                    // Append the text of the line to the extracted text
                    extractedText.append(lineText).append(" ");
                }
            }
        } catch (JSONException e) {
            Log.e("AzureCV", "Error extracting text from JSON: " + e.getMessage());
        }

        return extractedText.toString();
    }



}
