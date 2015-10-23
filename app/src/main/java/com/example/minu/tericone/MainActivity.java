package com.example.minu.tericone;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Math;
import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;

import com.googlecode.tesseract.android.TessBaseAPI;

public class MainActivity extends ActionBarActivity implements OnInitListener{
    public static final String PACKAGE_NAME = "com.example.minu.tericone";
    public static final String DATA_PATH = Environment
            .getExternalStorageDirectory().toString() + "/Tericone/";

    public static final String lang = "eng";

    private static final String TAG = "tericone";

    protected EditText minusTextView;
    protected RelativeLayout minusLayout;
    protected String _path;
    protected boolean _taken;
    public String recognizedText = "Please take a photo first";

    protected static final String PHOTO_TAKEN = "photo_taken";

    private TextToSpeech myTTS;

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

        myTTS = new TextToSpeech(this, this);

        minusTextView = (EditText) findViewById(R.id.minusTextView);
        minusLayout = (RelativeLayout) findViewById(R.id.minusLayout);
        minusLayout.setOnClickListener(new OcrButtonClickListener());
        minusLayout.setOnLongClickListener(new OcrButtonLongClickListener());

        _path = DATA_PATH + "/ocr.jpg";

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        SharedPreferences sharedPref = getSharedPreferences("TericoneModeAndDictionary", Context.MODE_PRIVATE);
        String mode = sharedPref.getString("mode", "none");
        boolean dictionary = sharedPref.getBoolean("dictionary", false);
        //For the first time
        if(mode.equals("none")){
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("mode", "Accuracy");
            editor.apply();
            mode = sharedPref.getString("mode", "none");
        }
        if(menu.findItem(R.id.accuracyRadio).getTitle().toString().equals(mode)){
            menu.findItem(R.id.accuracyRadio).setChecked(true);
        }
        else if(menu.findItem(R.id.swiftRadio).getTitle().toString().equals(mode)){
            menu.findItem(R.id.swiftRadio).setChecked(true);
        }
        menu.findItem(R.id.dictionaryRadio).setChecked(dictionary);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        SharedPreferences sharedPref = getSharedPreferences("TericoneModeAndDictionary", Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPref.edit();
        if (id == R.id.accuracyRadio || id == R.id.swiftRadio) {
            editor.putString("mode", item.getTitle().toString());
        }
        else{
            editor.putBoolean("dictionary", !item.isChecked());
        }
        editor.apply();

        //noinspection SimplifiableIfStatement
        if (id == R.id.accuracyRadio) {
            item.setChecked(true);
            return true;
        }

        if (id == R.id.swiftRadio) {
            item.setChecked(true);
            return true;
        }

        if (id == R.id.dictionaryRadio) {
            if(item.isChecked()) {
                item.setChecked(false);
            }
            else{
                item.setChecked(true);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    //Step 1: Preparation of TTS engine
    public void onInit(int initStatus) {
        //check for successful instantiation
        if (initStatus == TextToSpeech.SUCCESS) {
            if(myTTS.isLanguageAvailable(Locale.US)==TextToSpeech.LANG_AVAILABLE)
                myTTS.setLanguage(Locale.US);
        }
        else if (initStatus == TextToSpeech.ERROR) {
            Toast.makeText(this, "Sorry! Text To Speech failed...", Toast.LENGTH_LONG).show();
        }
    }

    //Step 2: OCR button click
    public class OcrButtonClickListener implements View.OnClickListener {
        public void onClick(View view) {
            Log.v(TAG, "Starting Camera app");
            startCameraActivity();
        }
    }

    public class OcrButtonLongClickListener implements View.OnLongClickListener {
        public boolean onLongClick(View view) {
            Log.v(TAG, "Starting Long Click app");
            speakTheText(recognizedText);
            return true;
        }
    }

    //Step 3: Starting camera
    protected void startCameraActivity() {
        File file = new File(_path);
        Uri outputFileUri = Uri.fromFile(file);

        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);

        startActivityForResult(intent, 1);
    }

    //Step 4: Override methods for camera activity in different cases
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.i(TAG, "resultCode: " + requestCode);

//        if (requestCode == MY_DATA_CHECK_CODE) {
//            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
//                myTTS = new TextToSpeech(this, this);
//                Log.i(TAG, "Came here");
//            }
//            else {
//                Intent installTTSIntent = new Intent();
//                installTTSIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
//                startActivity(installTTSIntent);
//                Log.i(TAG, "Came");
//            }
//        }
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

        SharedPreferences sharedPref = getSharedPreferences("TericoneModeAndDictionary", Context.MODE_PRIVATE);
        String mode = sharedPref.getString("mode", "none");
        boolean dictionary = sharedPref.getBoolean("dictionary", false);

        //resizing the image
        //bitmap = Resize(bitmap, ?, ?);

        Log.i(TAG, "Mode: "+ mode);
        if(mode.equals("Accuracy")) {
            //making the image grayscale
            bitmap = SetGrayscale(bitmap);

            //removing noise from image
            bitmap = RemoveNoise(bitmap);
        }

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

        //saving processed image
        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/Tericone");
        String fname = "Image.jpg";
        File file = new File (myDir, fname);
        if (file.exists ()) file.delete ();
        try {
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.v(TAG, "Before baseApi");

        //Creating TessBaseAPI object
        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.setDebug(true);
        baseApi.init(DATA_PATH, lang);
        baseApi.setImage(bitmap);

        //This variable will contain the text after processing
        recognizedText = baseApi.getUTF8Text();

        baseApi.end();

        Log.v(TAG, "OCRED TEXT: " + recognizedText);

        if ( lang.equalsIgnoreCase("eng") ) {
            recognizedText = recognizedText.replaceAll("[^a-zA-Z0-9.,']+", " ");
        }

        recognizedText = recognizedText.trim();

        //checking whether all words are valid
        if(dictionary == true) {
            Log.i(TAG, "Dictionary starts here");
            Log.i(TAG, "Initial words are" + recognizedText);
            String[] words = recognizedText.split(" ");
            Log.i(TAG, "All the words are" + words);
            for (int a = 0; a < words.length; a++) {
                if (check_for_word(words[a]) == false) {
                    Log.i(TAG, "Came here");
                    words[a] = "";
                }
            }
            String properString = "";
            for (int a = 0; a < words.length; a++) {
                if (words[a] != "") {
                    properString = properString + words[a] + " ";
                }
            }
            recognizedText = properString.trim();
            Log.i(TAG, "The resulting sentence is " + properString);
            Log.i(TAG, "Dictionary ends here");
        }

        //Setting the EditText field with the OCRed text
        if ( recognizedText.length() != 0 ) {
            minusTextView.setText(recognizedText);
        }
        speakTheText(recognizedText);

    }

    //Checking whether word is in dictionary
    public static boolean check_for_word(String oldWord) {
        String word = oldWord.replaceAll("[^a-zA-Z']+", "");
        Log.i(TAG, "The word is " + word);
        File sdcard = Environment.getExternalStorageDirectory();

        //Get the text file
        File file = new File(sdcard,"americanenglish.txt");

        //Read text from file
        StringBuilder text = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            while (text != null) {
                if (text.indexOf(word) != -1) {
                    return true;
                }
                else{
                    return false;
                }
            }
            br.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    //Functions for improving the image quality
    //1)Resize
    public Bitmap Resize(Bitmap bmp, int newWidth, int newHeight)
    {

        Bitmap temp = (Bitmap)bmp;

        Bitmap bmap = Bitmap.createScaledBitmap(temp, newWidth, newHeight, true);

        double nWidthFactor = (double)temp.getWidth() / (double)newWidth;
        double nHeightFactor = (double)temp.getHeight() / (double)newHeight;

        double fx, fy, nx, ny;
        int cx, cy, fr_x, fr_y;
        int color1,color2,color3,color4;
        byte nRed, nGreen, nBlue;

        byte bp1, bp2;

        for (int x = 0; x < bmap.getWidth(); ++x)
        {
            for (int y = 0; y < bmap.getHeight(); ++y)
            {

                fr_x = (int)Math.floor(x * nWidthFactor);
                fr_y = (int)Math.floor(y * nHeightFactor);
                cx = fr_x + 1;
                if (cx >= temp.getWidth()) cx = fr_x;
                cy = fr_y + 1;
                if (cy >= temp.getHeight()) cy = fr_y;
                fx = x * nWidthFactor - fr_x;
                fy = y * nHeightFactor - fr_y;
                nx = 1.0 - fx;
                ny = 1.0 - fy;

                color1 = temp.getPixel(fr_x, fr_y);
                color2 = temp.getPixel(cx, fr_y);
                color3 = temp.getPixel(fr_x, cy);
                color4 = temp.getPixel(cx, cy);

                // Blue
                bp1 = (byte)(nx * Color.blue(color1) + fx * Color.blue(color2));

                bp2 = (byte)(nx * Color.blue(color3) + fx * Color.blue(color4));

                nBlue = (byte)(ny * (double)(bp1) + fy * (double)(bp2));

                // Green
                bp1 = (byte)(nx * Color.green(color1) + fx * Color.green(color2));

                bp2 = (byte)(nx * Color.green(color3) + fx * Color.green(color4));

                nGreen = (byte)(ny * (double)(bp1) + fy * (double)(bp2));

                // Red
                bp1 = (byte)(nx * Color.red(color1) + fx * Color.red(color2));

                bp2 = (byte)(nx * Color.red(color3) + fx * Color.red(color4));

                nRed = (byte)(ny * (double)(bp1) + fy * (double)(bp2));

                bmap.setPixel(x, y, Color.argb(255, nRed, nGreen, nBlue));
            }
        }

        return bmap;

    }


    //2)SetGrayscale
    public Bitmap SetGrayscale(Bitmap img)
    {
        Log.i(TAG, "Started Grayscale");
        Bitmap temp = (Bitmap)img;
        Bitmap bmap = (Bitmap)temp.copy(temp.getConfig(), true);
        int c;
        for (int i = 0; i < bmap.getWidth(); i++)
        {
            for (int j = 0; j < bmap.getHeight(); j++)
            {
                c = bmap.getPixel(i, j);
                byte gray = (byte)(.299 * Color.red(c) + .587 * Color.green(c) + .114 * Color.blue(c));

                bmap.setPixel(i, j, Color.argb(255, gray, gray, gray));
            }
        }
        Log.i(TAG, "Did Grayscale");
        return (Bitmap)bmap.copy(bmap.getConfig(), true);

    }
    //3)RemoveNoise
    public Bitmap RemoveNoise(Bitmap bmap)
    {
        Log.i(TAG, "Started RemoveNoise");
        for (int x = 0; x < bmap.getWidth(); x++)
        {
            for (int y = 0; y < bmap.getHeight(); y++)
            {
                int pixel = bmap.getPixel(x, y);
                if (Color.red(pixel) < 162 && Color.green(pixel) < 162 && Color.blue(pixel) < 162)
                    bmap.setPixel(x, y, Color.BLACK);
            }
        }

        for (int x = 0; x < bmap.getWidth(); x++)
        {
            for (int y = 0; y < bmap.getHeight(); y++)
            {
                int pixel = bmap.getPixel(x, y);
                if (Color.red(pixel) > 162 && Color.green(pixel) > 162 && Color.blue(pixel) > 162)
                    bmap.setPixel(x, y, Color.WHITE);
            }
        }

        Log.i(TAG, "Removed Noise");

        return bmap;
    }

    //Step 6: Speak button click
    public void speakTheText(String ttsText){
        speakText(ttsText);
    }

    private void speakText(String speech) {
        myTTS.speak(speech, TextToSpeech.QUEUE_FLUSH, null);
    }

    //To stop speaking when activity is paused
    @Override
    protected void onPause() {
        myTTS.stop();
        super.onPause();
    }

}