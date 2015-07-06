package com.example.minu.tericone;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.ispeech.SpeechSynthesis;
import org.ispeech.SpeechSynthesisEvent;
import org.ispeech.error.BusyException;
import org.ispeech.error.InvalidApiKeyException;
import org.ispeech.error.NoNetworkException;

public class MainActivity extends Activity {
    public static final String PACKAGE_NAME = "com.example.minu.tericone";
    public static final String DATA_PATH = Environment
            .getExternalStorageDirectory().toString() + "/Tericone/";

    public static final String lang = "eng";

    private static final String TAG = "tericone";

    protected Button ocrButton;
    protected Button speakButton;
    protected Button stopButton;
    protected TextView minusTextView;
    protected String _path;
    protected boolean _taken;

    protected static final String PHOTO_TAKEN = "photo_taken";

    SpeechSynthesis synthesis;
    Context _context;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        //Creating directory
        String[] paths = new String[] { DATA_PATH, DATA_PATH + "tessdata/" };

        for (String path : paths) {
            File dir = new File(path);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.v(TAG, "ERROR: Creation of directory " + path + " on sdcard failed");
                    return;
                } else {
                    Log.v(TAG, "Created directory " + path + " on sdcard");
                }
            }

        }

        //Copying traineddata file to the phone
        if (!(new File(DATA_PATH + "tessdata/" + lang + ".traineddata")).exists()) {
            try {

                AssetManager assetManager = getAssets();
                InputStream in = assetManager.open("tessdata/" + lang + ".traineddata");
                OutputStream out = new FileOutputStream(DATA_PATH
                        + "tessdata/" + lang + ".traineddata");

                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();

                Log.v(TAG, "Copied " + lang + " traineddata");
            } catch (IOException e) {
                Log.e(TAG, "Was unable to copy " + lang + " traineddata " + e.toString());
            }
        }

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        minusTextView = (TextView) findViewById(R.id.minusTextView);
        ocrButton = (Button) findViewById(R.id.ocrButton);
        ocrButton.setOnClickListener(new OcrButtonClickListener());

        speakButton = (Button) findViewById(R.id.speakButton);
        speakButton.setOnClickListener(new OnSpeakListener());

        stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new OnStopListener());

        _path = DATA_PATH + "/ocr.jpg";

        prepareTTSEngine();

        synthesis.setStreamType(AudioManager.STREAM_MUSIC);

        try {
            synthesis.speak("Opening Tericone");

        } catch (BusyException e) {
            Log.e(TAG, "SDK is busy");
            e.printStackTrace();
            Toast.makeText(_context, "ERROR: SDK is busy", Toast.LENGTH_LONG).show();
        } catch (NoNetworkException e) {
            Log.e(TAG, "Network is not available\n" + e.getStackTrace());
            Toast.makeText(_context, "ERROR: Network is not available", Toast.LENGTH_LONG).show();
        }

    }

    //Step 1: Preparation of TTS engine
    private void prepareTTSEngine() {
        try {
            synthesis = SpeechSynthesis.getInstance(this);
            synthesis.setSpeechSynthesisEvent(new SpeechSynthesisEvent() {

                public void onPlaySuccessful() {
                    Log.i(TAG, "onPlaySuccessful");
                }

                public void onPlayStopped() {
                    Log.i(TAG, "onPlayStopped");
                }

                public void onPlayFailed(Exception e) {
                    Log.e(TAG, "onPlayFailed");


                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage("Error[TTSActivity]: " + e.toString())
                            .setCancelable(false)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                }
                            });
                    AlertDialog alert = builder.create();
                    alert.show();
                }

                public void onPlayStart() {
                    Log.i(TAG, "onPlayStart");
                }

                @Override
                public void onPlayCanceled() {
                    Log.i(TAG, "onPlayCanceled");
                }


            });


        } catch (InvalidApiKeyException e) {
            Log.e(TAG, "Invalid API key\n" + e.getStackTrace());
            Toast.makeText(_context, "ERROR: Invalid API key", Toast.LENGTH_LONG).show();
        }

    }

    //Step 2: OCR button click
    public class OcrButtonClickListener implements View.OnClickListener {
        public void onClick(View view) {
            Log.v(TAG, "Starting Camera app");
            try {
                synthesis.speak("Opening camera");

            } catch (BusyException e) {
                Log.e(TAG, "SDK is busy");
                e.printStackTrace();
                Toast.makeText(_context, "ERROR: SDK is busy", Toast.LENGTH_LONG).show();
            } catch (NoNetworkException e) {
                Log.e(TAG, "Network is not available\n" + e.getStackTrace());
                Toast.makeText(_context, "ERROR: Network is not available", Toast.LENGTH_LONG).show();
            }
            startCameraActivity();
        }
    }

    //Step 3: Starting camera
    protected void startCameraActivity() {
        File file = new File(_path);
        Uri outputFileUri = Uri.fromFile(file);

        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);

        startActivityForResult(intent, 0);
    }

    //Step 4: Override methods for camera activity in different cases
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.i(TAG, "resultCode: " + resultCode);

        if (resultCode == -1) {
            onPhotoTaken();
        } else {
            Log.v(TAG, "User cancelled");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(MainActivity.PHOTO_TAKEN, _taken);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.i(TAG, "onRestoreInstanceState()");
        if (savedInstanceState.getBoolean(MainActivity.PHOTO_TAKEN)) {
            onPhotoTaken();
        }
    }

    //Step 5: Doing stuff with the taken picture, getting the text and setting the EditText field
    protected void onPhotoTaken() {
        _taken = true;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;

        Bitmap bitmap = BitmapFactory.decodeFile(_path, options);

        try {
            ExifInterface exif = new ExifInterface(_path);
            int exifOrientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);

            Log.v(TAG, "Orient: " + exifOrientation);

            int rotate = 0;

            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
            }

            Log.v(TAG, "Rotation: " + rotate);

            if (rotate != 0) {

                // Getting width & height of the given image.
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();

                // Setting pre rotate
                Matrix mtx = new Matrix();
                mtx.preRotate(rotate);

                // Rotating Bitmap
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
            }

            // Convert to ARGB_8888, required by tess
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        } catch (IOException e) {
            Log.e(TAG, "Couldn't correct orientation: " + e.toString());
        }

        Log.v(TAG, "Before baseApi");

        //Creating TessBaseAPI object
        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.setDebug(true);
        baseApi.init(DATA_PATH, lang);
        baseApi.setImage(bitmap);

        //This variable will contain the text after processing
        String recognizedText = baseApi.getUTF8Text();

        baseApi.end();

        Log.v(TAG, "OCRED TEXT: " + recognizedText);

        if ( lang.equalsIgnoreCase("eng") ) {
            recognizedText = recognizedText.replaceAll("[^a-zA-Z0-9]+", " ");
        }

        recognizedText = recognizedText.trim();

        //Setting the EditText field with the OCRed text
        if ( recognizedText.length() != 0 ) {
            minusTextView.setText(recognizedText);
        }

    }

    //Step 6: Speak button click
    private class OnSpeakListener implements View.OnClickListener {

        public void onClick(View v) {

            try {
                String ttsText = minusTextView.getText().toString();
                synthesis.speak(ttsText);

            } catch (BusyException e) {
                Log.e(TAG, "SDK is busy");
                e.printStackTrace();
                Toast.makeText(_context, "ERROR: SDK is busy", Toast.LENGTH_LONG).show();
            } catch (NoNetworkException e) {
                Log.e(TAG, "Network is not available\n" + e.getStackTrace());
                Toast.makeText(_context, "ERROR: Network is not available", Toast.LENGTH_LONG).show();
            }
        }
    }

    //Step 7: Stop button click
    public class OnStopListener implements View.OnClickListener {

        public void onClick(View v) {
            if (synthesis != null) {
                synthesis.stop();
            }
        }
    }

    //To stop speaking when activity is paused
    @Override
    protected void onPause() {
        synthesis.stop();
        super.onPause();
    }

}