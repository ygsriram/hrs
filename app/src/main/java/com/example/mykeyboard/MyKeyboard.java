package com.example.mykeyboard;


import android.content.res.AssetFileDescriptor;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;

import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;


import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;


public class MyKeyboard extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {
    private static final String TAG = "TextClassificationDemo";
    private static final String START = "<START>";
    private static final String PAD = "<PAD>";
    private static final String UNKNOWN = "<UNKNOWN>";

    private static final int SENTENCE_LEN = 100;

    Interpreter tflite;
    private static final String SIMPLE_SPACE_OR_PUNCTUATION = " |\\,|\\.|\\!|\\?|\n";
    private KeyboardView kv;
    private Keyboard keyboard;

    private boolean caps = false;
    private double CONFIDENCE = 0.98;

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        switch(primaryCode){
            case 32:
                String text = ic.getTextBeforeCursor(100, 0 ).toString();
                Log.d("text", "onKey: " +text );
                float prediction= 0;
                try {
                    prediction = doInference(text);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                //Toast.makeText(this, "onCreate: "+prediction, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "onCreate: "+prediction);


                if(prediction>=CONFIDENCE){
                    Toast.makeText(this, "please try to avoid profanity / hateful texts", Toast.LENGTH_SHORT).show();
                    ic.deleteSurroundingText(text.length(), 0);
                }
                else {
                    char code = (char)primaryCode;
                    if(Character.isLetter(code) && caps){
                        code = Character.toUpperCase(code);
                    }
                    ic.commitText(String.valueOf(code),1);
                }
                break;
            case Keyboard.KEYCODE_DELETE :
                ic.deleteSurroundingText(1, 0);
                break;
            case Keyboard.KEYCODE_SHIFT:
                caps = !caps;
                keyboard.setShifted(caps);
                kv.invalidateAllKeys();
                break;
            case Keyboard.KEYCODE_DONE:
                String text_done = ic.getTextBeforeCursor(100, 0 ).toString();
                Log.d("text", "onKey: " +text_done );
                float prediction_done= 0;
                try {
                    prediction_done = doInference(text_done);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                //Toast.makeText(this, "onCreate: "+prediction_done, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "onCreate: "+prediction_done);


                if(prediction_done>=CONFIDENCE){
                    Toast.makeText(this, "please try to avoid profanity / hateful texts", Toast.LENGTH_SHORT).show();
                    ic.deleteSurroundingText(text_done.length(), 0);
                }
                else {
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                }
                break;
            default:
                char code = (char)primaryCode;
                if(Character.isLetter(code) && caps){
                    code = Character.toUpperCase(code);
                }
                ic.commitText(String.valueOf(code),1);
        }
    }

    @Override
    public void onPress(int primaryCode) {
    }

    @Override
    public void onRelease(int primaryCode) {
    }

    @Override
    public void onText(CharSequence text) {
    }

    @Override
    public void swipeDown() {
    }

    @Override
    public void swipeLeft() {
    }

    @Override
    public void swipeRight() {
    }

    @Override
    public void swipeUp() {
    }

    @Override
    public View onCreateInputView() {
        kv = (KeyboardView)getLayoutInflater().inflate(R.layout.keyboard, null);
        Log.d("keyboard", "onCreateInputView: "+ kv.getKeyboard());

        try {
            tflite = new Interpreter(loadModelFile());
        }catch (Exception ex){
            ex.printStackTrace();
        }


        keyboard = new Keyboard(this, R.xml.qwerty);
        kv.setKeyboard(keyboard);
        kv.setOnKeyboardActionListener(this);
        return kv;
    }


    public String loadJSONFromAsset() {
        String json = null;
        try {
            InputStream is = getAssets().open("word_dict.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor=this.getAssets().openFd("model_keras.tflite");
        FileInputStream inputStream=new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();
        long startOffset=fileDescriptor.getStartOffset();
        long declareLength=fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startOffset,declareLength);
    }


    /** Pre-prosessing: tokenize and map the input words into a float array. */
    float[][] tokenizeInputTextJson(String text) throws JSONException {
        float[] tmp = new float[SENTENCE_LEN];
        List<String> array = Arrays.asList(text.split(SIMPLE_SPACE_OR_PUNCTUATION));
        System.out.println(array.toString());
        JSONObject dic = new JSONObject(loadJSONFromAsset());
        int index = 0;
        // Prepend <START> if it is in vocabulary file.
        if (dic.has(START)) {
            tmp[index++] = (int) dic.get(START);
        }

        for (String word : array) {
            if (index >= SENTENCE_LEN) {
                break;
            }
            tmp[index++] = dic.has(word) ? (int) dic.get(word) : (int) dic.get(UNKNOWN);
            System.out.println(tmp[index-1]);
        }
        // Padding and wrapping.
        Arrays.fill(tmp, index, SENTENCE_LEN - 1, (int) 0);
        float[][] ans = {tmp};
        return ans;
    }
    private float doInference(String inputString) throws JSONException {
        float[][] inputVal = tokenizeInputTextJson(inputString);
        Log.d(TAG, "doInference: " + inputVal[0].length);
        //inputVal[0]=Integer.valueOf(inputString);
        float[][] output=new float[1][1];
        tflite.run(inputVal,output);
        float inferredValue=output[0][0];
        return  inferredValue;
    }

}