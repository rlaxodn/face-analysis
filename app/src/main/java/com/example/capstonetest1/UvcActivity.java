package com.example.capstonetest1;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.hardware.usb.UsbDevice;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.view.PreviewView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.jiangdg.usbcamera.UVCCameraHelper;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.widget.CameraViewInterface;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static android.speech.tts.TextToSpeech.ERROR;

public class UvcActivity extends AppCompatActivity implements CameraDialog.CameraDialogParent, CameraViewInterface.Callback{
    FaceDetector detector;
    ImageView faceImg;
    PreviewView previewView;
    ImageView face_preview;
    Interpreter tfLite,tfLite_emo;
    TextView reco_name,preview_info,reco_emotion,textAbove_preview;
    Button recognize,camera_switch, actions;
    ImageButton add_face;
    CameraSelector cameraSelector;
    boolean developerMode=false;
    float distance= 1.0f;
    boolean start=true,flipX=false;
    Context context=UvcActivity.this;

    int[] intValues,intValues_emo;
    int inputSize=112,inputSize_emo=64;  //Input size for model
    boolean isModelQuantized=false;
    float[][] embeedings;
    float IMAGE_MEAN = 128.0f;
    float IMAGE_STD = 128.0f;
    int OUTPUT_SIZE=192; //Output size of model
    private static int SELECT_PICTURE = 1;
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    private TextToSpeech tts;
    int previous_emotion,arg;

    String modelFile="mobile_face_net.tflite"; //model name
    String modelFile2="emotion_recog.tflite";

    List<String> name_list; //일정시간 보관될 이름 리스트
    List<Integer> emo_list = new ArrayList<Integer>();

    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;

    private boolean isRequest;
    private boolean isPreview;
    private Thread th1;
    private Handler handler;

    private HashMap<String, SimilarityClassifier.Recognition> registered = new HashMap<>(); //saved Faces
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setTitle("USB카메라 동작");

        registered=readFromSP(); //Load saved faces from memory when app starts
        setContentView(R.layout.activity_main);
        reco_name =findViewById(R.id.textView);
        reco_emotion=findViewById(R.id.textView2);
        camera_switch=findViewById(R.id.button5);
        camera_switch.setVisibility(View.GONE);

        name_list = new ArrayList<String>();

        // Camera step.1 initialize UVCCameraHelper
        mUVCCameraView = (CameraViewInterface) findViewById(R.id.uvcView);
        mUVCCameraView.setCallback(this);
        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_YUYV);
        mCameraHelper.initUSBMonitor(this, mUVCCameraView, listener);

        TextureView textureView = (TextureView) findViewById(R.id.uvcView);

        mCameraHelper.setOnPreviewFrameListener(new AbstractUVCCameraHandler.OnPreViewResultListener() {
            @Override
            public void onPreviewResult(byte[] nv21Yuv) {
            }
        });

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != ERROR) {
                    // 언어를 선택한다.
                    tts.setLanguage(Locale.KOREAN);
                }
            }
        });

        tts.setPitch(1.0f);         // 음성 톤은 기본 설정
        tts.setSpeechRate(1.0f);    // 읽는 속도를 기본 빠르기로 설정

        faceImg = findViewById(R.id.face);
        SharedPreferences sharedPref = getSharedPreferences("Distance",Context.MODE_PRIVATE);
        distance = sharedPref.getFloat("distance",1.00f);

        //Load model
        try {
            tfLite=new Interpreter(loadModelFile(UvcActivity.this,modelFile));
            tfLite_emo=new Interpreter(loadModelFile(UvcActivity.this,modelFile2));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Initialize Face Detector
        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build();
        detector = FaceDetection.getClient(highAccuracyOpts);


//        //뷰에서 bitmap 추출하여 얼굴검출 실행
        handler = new Handler(){
            @Override
            public void handleMessage(Message msg){
                Bitmap bitmap=textureView.getBitmap();
                int rot = (int)textureView.getRotation();
                InputImage image = null;
                image = InputImage.fromBitmap(bitmap,rot);
                //Process acquired image to detect faces
                Task<List<Face>> result =
                        detector.process(image)
                                .addOnSuccessListener(
                                        new OnSuccessListener<List<Face>>() {
                                            @Override
                                            public void onSuccess(List<Face> faces) {

                                                if(faces.size()!=0) {

                                                    Face face = faces.get(0); //Get first face from detected faces
//                                                    System.out.println(face);

                                                    //mediaImage to Bitmap
                                                    Bitmap frame_bmp = bitmap;

                                                    //Adjust orientation of Face
                                                    Bitmap frame_bmp1 = rotateBitmap(frame_bmp, rot, false, false);

                                                    //Get bounding box of face
                                                    RectF boundingBox = new RectF(face.getBoundingBox());

                                                    //Crop out bounding box from whole Bitmap(image)
                                                    Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);
                                                    Bitmap cropped_face2=cropped_face.copy(Bitmap.Config.ARGB_8888,true);
                                                    if(flipX)
                                                        cropped_face = rotateBitmap(cropped_face, 0, flipX, false);
                                                    cropped_face2 = rotateBitmap(cropped_face2, 0, flipX, false);

                                                    Bitmap mm = cropped_face2.copy(Bitmap.Config.ARGB_8888,true);
                                                    mm = getResizedBitmap(mm, 400, 400);
                                                    faceImg.setImageBitmap(mm);

                                                    //Scale the acquired Face to 112*112 which is required input for model
                                                    Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);
                                                    Bitmap scaled2 = getResizedBitmap(cropped_face2,64,64);

                                                    if(start)
                                                        recognizeImage(scaled); //Send scaled bitmap to create face embeddings.
                                                    recognizeEmotion(scaled2);
//                                                    System.out.println(boundingBox);

                                                }
                                                else
                                                {
                                                    reco_name.setText("No Face Detected!");
                                                }
                                            }
                                        })
                                .addOnFailureListener(
                                        new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                // Task failed with an exception
                                                // ...
                                            }
                                        })
                                .addOnCompleteListener(new OnCompleteListener<List<Face>>() {
                                    @Override
                                    public void onComplete(@NonNull Task<List<Face>> task) {
                                    }
                                });
            }
        };
        th1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    while(true){
                        //일정 시간에 한번씩 동작
                        Thread.sleep(2000);
                        if(isPreview && textureView != null)
                            handler.sendEmptyMessage(0);
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        th1.start();
    }

    private void changeImg(Bitmap bitmap){
        if(bitmap != null){
            faceImg.setImageBitmap(bitmap);
        }
    }
    public static Bitmap StringToBitmap(String encodedString) {
        try {
            byte[] encodeByte = Base64.decode(encodedString, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.length);
            return bitmap;
        } catch (Exception e) {
            e.getMessage();
            return null;
        }
    }

    //뒤로가기 버튼 눌렀을 때
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        th1.interrupt(); //스레드 종료
        Intent intent = new Intent(UvcActivity.this, StartActivity.class); //지금 액티비티에서 다른 액티비티로 이동하는 인텐트 설정
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);    //인텐트 플래그 설정
        startActivity(intent);  //인텐트 이동
        finish();   //현재 액티비티 종료
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return mCameraHelper.getUSBMonitor();
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
            showShortMsg("取消操作");
        }
    }

    @Override
    public void onSurfaceCreated(CameraViewInterface cameraViewInterface, Surface surface) {
        if (!isPreview && mCameraHelper.isCameraOpened()) {
//            mCameraHelper.startPreview(mUVCCameraView);
            isPreview = true;
            showShortMsg("onSurfaceCreated");
        }
    }

    @Override
    public void onSurfaceChanged(CameraViewInterface cameraViewInterface, Surface surface, int i, int i1) {

    }

    @Override
    public void onSurfaceDestroy(CameraViewInterface cameraViewInterface, Surface surface) {
        if (isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.stopPreview();
            isPreview = false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // step.2 register USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.registerUSB();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // step.3 unregister USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.unregisterUSB();
        }
    }

    private void showShortMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {

        @Override
        public void onAttachDev(UsbDevice device) {
            if (mCameraHelper == null || mCameraHelper.getUsbDeviceCount() == 0) {
                showShortMsg("check no usb camera");
                return;
            }
            // request open permission
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    mCameraHelper.requestPermission(0);
                }
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            // close camera
            if (isRequest) {
                isRequest = false;
                isPreview = false;
                mCameraHelper.closeCamera();
                showShortMsg(device.getDeviceName() + " is out");
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (!isConnected) {
                showShortMsg("fail to connect,please check resolution params");
                isPreview = false;
            } else {
                isPreview = true;
                showShortMsg("connecting");
                // initialize seekbar
                // need to wait UVCCamera initialize over
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Looper.prepare();
                        if(mCameraHelper != null && mCameraHelper.isCameraOpened()) {
//                            mSeekBrightness.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_BRIGHTNESS));
//                            mSeekContrast.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_CONTRAST));
                        }
                        Looper.loop();
                    }
                }).start();
            }
        }

        @Override
        public void onDisConnectDev(UsbDevice device) {
            isPreview=false;
            showShortMsg("disconnecting");
            try {
                th1.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    private void testHyperparameter()
    {

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Select Hyperparameter:");

        // add a checkbox list
        String[] names= {"Maximum Nearest Neighbour Distance"};

        builder.setItems(names, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                switch (which)
                {
                    case 0:
//                        Toast.makeText(context, "Clicked", Toast.LENGTH_SHORT).show();
                        hyperparameters();
                        break;
                }

            }

        });
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.setNegativeButton("Cancel", null);

        // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void addFace()
    {
        {

            start=false;
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Enter Name");

            // Set up the input
            final EditText input = new EditText(context);

            input.setInputType(InputType.TYPE_CLASS_TEXT );
            builder.setView(input);

            // Set up the buttons
            builder.setPositiveButton("ADD", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //Toast.makeText(context, input.getText().toString(), Toast.LENGTH_SHORT).show();

                    //Create and Initialize new object with Face embeddings and Name.
                    SimilarityClassifier.Recognition result = new SimilarityClassifier.Recognition(
                            "0", "", -1f);
                    result.setExtra(embeedings);

                    registered.put( input.getText().toString(),result);
                    start=true;

                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    start=true;
                    dialog.cancel();
                }
            });

            builder.show();
        }
    }


    private void hyperparameters()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Euclidean Distance");
        builder.setMessage("0.00 -> Perfect Match\n1.00 -> Default\nTurn On Developer Mode to find optimum value\n\nCurrent Value:");
        // Set up the input
        final EditText input = new EditText(context);

        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        builder.setView(input);
        SharedPreferences sharedPref = getSharedPreferences("Distance",Context.MODE_PRIVATE);
        distance = sharedPref.getFloat("distance",1.00f);
        input.setText(String.valueOf(distance));
        // Set up the buttons
        builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Toast.makeText(context, input.getText().toString(), Toast.LENGTH_SHORT).show();

                distance= Float.parseFloat(input.getText().toString());


                SharedPreferences sharedPref = getSharedPreferences("Distance",Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putFloat("distance", distance);
                editor.apply();

            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.cancel();
            }
        });

        builder.show();
    }


    private MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void recognizeEmotion(final Bitmap bitmap){
        ByteBuffer imgData2 = ByteBuffer.allocateDirect(1 * inputSize_emo * inputSize_emo * 1 * 4);

        imgData2.order(ByteOrder.nativeOrder());

        intValues_emo = new int[inputSize_emo * inputSize_emo];

        bitmap.getPixels(intValues_emo, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData2.rewind();

        for (int i = 0; i < inputSize_emo; ++i) {
            for (int j = 0; j < inputSize_emo; ++j) {
                int pixelValue = intValues_emo[i * inputSize_emo + j];
                int r = (pixelValue >> 16) & 0xFF;
                int g = (pixelValue >> 8) & 0xFF;
                int b = pixelValue & 0xFF;
                float gray = 0.299f*r+0.587f*g+0.114f*b;
                gray = gray / 255.0f;
                gray = gray - 0.5f;
                gray = gray * 2f;
                imgData2.putFloat(gray);
            }
        }
        float[][] result=new float[1][5];

        tfLite_emo.run(imgData2,result);
        final Pair<Integer,Float> max =argmax(result[0]);
        int emotion = max.first;
        float prob=max.second;
        if(emo_list.size()<6)
            emo_list.add(emotion);
        else {
            int[] emo_count=new int[5];
            for(int i=0;i<5;i++){
                emo_count[i]= Collections.frequency(emo_list, i);
            }
            arg=argmax(emo_count);
            emo_list.clear();
        }
        switch(emotion){
            case 0:
                reco_emotion.setText("화남");
                // 이전 표정이 화남이 아니라면 음성으로 알려줌
                if( previous_emotion != 0){
                    tts.speak("화남",TextToSpeech.QUEUE_FLUSH, null);
                    previous_emotion=0;
                }
                break;
            case 1:
                reco_emotion.setText("행복");
                if( previous_emotion != 1){
                    tts.speak("행복",TextToSpeech.QUEUE_FLUSH, null);
                    previous_emotion=1;
                }
                break;
            case 2:
                reco_emotion.setText("보통");
                if( previous_emotion != 2){
                    tts.speak("보통",TextToSpeech.QUEUE_FLUSH, null);
                    previous_emotion=2;
                }
                break;
            case 3:
                reco_emotion.setText("슬픔");
                if( previous_emotion != 3){
                    tts.speak("슬픔",TextToSpeech.QUEUE_FLUSH, null);
                    previous_emotion=3;
                }
                break;
            case 4:
                reco_emotion.setText("놀람");
                if( previous_emotion != 4){
                    tts.speak("놀람",TextToSpeech.QUEUE_FLUSH, null);
                    previous_emotion=4;
                }
                break;
        }
    }
    public int argmax(int[] a) {
        int re = Integer.MIN_VALUE;
        int arg = -1;
        for (int i = 0; i < a.length; i++) {
            if (a[i] > re) {
                re = a[i];
                arg = i;
            }
        }
        return arg;
    }
    public void recognizeImage(final Bitmap bitmap) {

        // set Face to Preview
//        face_preview.setImageBitmap(bitmap);

        //Create ByteBuffer to store normalized image

        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4);

        imgData.order(ByteOrder.nativeOrder());

        intValues = new int[inputSize * inputSize];

        //get pixel values from Bitmap to normalize
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();

        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }
        //imgData is input to our model
        Object[] inputArray = {imgData};

        Map<Integer, Object> outputMap = new HashMap<>();


        embeedings = new float[1][OUTPUT_SIZE]; //output of model will be stored in this variable

        outputMap.put(0, embeedings);

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap); //Run model

        float[] emb = embeedings[0];
        //저장된값과 비교
        float shortest_distance = 1;
        String shortest_name = "unkown";

        //Sharedpreferences 생성
        SharedPreferences sp = getSharedPreferences("shared",MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();

        //gson 생성
        Gson gson = new GsonBuilder().create();

        Collection<?> col =  sp.getAll().values();
        Iterator<?> it = col.iterator();

        while(it.hasNext())
        {
            String msg = (String)it.next();
            MyData myData = gson.fromJson(msg,MyData.class);
            float[] saved_embeeding = myData.getEmbedding();

            //distance 계산
            float current_distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - saved_embeeding[i];
                current_distance += diff*diff;
            }
            current_distance = (float) Math.sqrt(current_distance);

            if(current_distance<shortest_distance){
                shortest_distance=current_distance;
                shortest_name = myData.getName();
            }
        }

        if(shortest_distance<0.8)
        {
            //가장 가까운 사람 출력
            System.out.println(shortest_name+"distance="+shortest_distance);
            Log.d("가장가까운사람=",shortest_name);

            //record 리스트에 이름이 없다면 추가
            if( !name_list.contains(shortest_name) ) {
                record_name(shortest_name);
                // 음성으로 알려줌
                tts.speak(shortest_name,TextToSpeech.QUEUE_FLUSH, null);
            }
            reco_name.setText(shortest_name);
        }
        else {
            System.out.println("모르는 얼굴입니다");
            Log.d("모르는얼굴", "모르는얼굴이다");
            reco_name.setText("unKnown");
            // 음성으로 알려줌
            //tts.speak("모르는 얼굴입니다",TextToSpeech.QUEUE_FLUSH, null);
        }
//
//        float distance_local = Float.MAX_VALUE;
//        String id = "0";
//        String label = "?";
//
//        //Compare new face with saved Faces.
//        if (registered.size() > 0) {
//
//            final List<Pair<String, Float>> nearest = findNearest(embeedings[0]);//Find 2 closest matching face
//
//            if (nearest.get(0) != null) {
//
//                final String name = nearest.get(0).first; //get name and distance of closest matching face
//                // label = name;
//                distance_local = nearest.get(0).second;
//                if (developerMode)
//                {
//                    if(distance_local<distance) //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
//                        reco_name.setText("Nearest: "+name +"\nDist: "+ String.format("%.3f",distance_local)+"\n2nd Nearest: "+nearest.get(1).first +"\nDist: "+ String.format("%.3f",nearest.get(1).second));
//                    else
//                        reco_name.setText("Unknown "+"\nDist: "+String.format("%.3f",distance_local)+"\nNearest: "+name +"\nDist: "+ String.format("%.3f",distance_local)+"\n2nd Nearest: "+nearest.get(1).first +"\nDist: "+ String.format("%.3f",nearest.get(1).second));
//
////                    System.out.println("nearest: " + name + " - distance: " + distance_local);
//                }
//                else
//                {
//                    if(distance_local<distance) //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
//                        reco_name.setText(name);
//                    else
//                        reco_name.setText("Unknown");
////                    System.out.println("nearest: " + name + " - distance: " + distance_local);
//                }
//
//
//
//            }
//        }
////여기까지 살리면됨

//            final int numDetectionsOutput = 1;
//            final ArrayList<SimilarityClassifier.Recognition> recognitions = new ArrayList<>(numDetectionsOutput);
//            SimilarityClassifier.Recognition rec = new SimilarityClassifier.Recognition(
//                    id,
//                    label,
//                    distance);
//
//            recognitions.add( rec );

    }
//    public void register(String name, SimilarityClassifier.Recognition rec) {
//        registered.put(name, rec);
//    }


    //5분간 이름 기억후 제거
    private void record_name(String name){
        name_list.add(name);
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(300000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int index = name_list.indexOf(name);
                name_list.remove(index);
            }
        });
        th.start();
    }
    //Compare Faces by distance between face embeddings
    private List<Pair<String, Float>> findNearest(float[] emb) {
        List<Pair<String, Float>> neighbour_list = new ArrayList<Pair<String, Float>>();
        Pair<String, Float> ret = null; //to get closest match
        Pair<String, Float> prev_ret = null; //to get second closest match
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet())
        {

            final String name = entry.getKey();
            final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];

            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff*diff;
            }
            distance = (float) Math.sqrt(distance);
            if (ret == null || distance < ret.second) {
                prev_ret=ret;
                ret = new Pair<>(name, distance);
            }
        }
        if(prev_ret==null) prev_ret=ret;
        neighbour_list.add(ret);
        neighbour_list.add(prev_ret);

        return neighbour_list;

    }
    private Pair<Integer,Float> argmax(float[] array){
        int argmax=0;
        float max=array[0];
        for(int i=1;i<array.length;i++){
            float f= array[i];
            if(f>max){
                argmax=i;
                max=f;
            }
        }
        return new Pair<>(argmax,max);
    }
    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }
    private static Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        Bitmap resultBitmap = Bitmap.createBitmap((int) cropRectF.width(),
                (int) cropRectF.height(), Bitmap.Config.ARGB_8888);
        Canvas cavas = new Canvas(resultBitmap);

        // draw background
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        cavas.drawRect(
                new RectF(0, 0, cropRectF.width(), cropRectF.height()),
                paint);

        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);

        cavas.drawBitmap(source, matrix, paint);

        if (source != null && !source.isRecycled()) {
            source.recycle();
        }

        return resultBitmap;
    }

    private static Bitmap rotateBitmap(
            Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees);

        // Mirror the image along the X or Y axis.
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

    //IMPORTANT. If conversion not done ,the toBitmap conversion does not work on some devices.
    private static byte[] YUV_420_888toNV21(Image image) {

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width*height;
        int uvSize = width*height/4;

        byte[] nv21 = new byte[ySize + uvSize*2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int rowStride = image.getPlanes()[0].getRowStride();
        assert(image.getPlanes()[0].getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        }
        else {
            long yBufferPos = -rowStride; // not an actual position
            for (; pos<ySize; pos+=width) {
                yBufferPos += rowStride;
                yBuffer.position((int) yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        assert(rowStride == image.getPlanes()[1].getRowStride());
        assert(pixelStride == image.getPlanes()[1].getPixelStride());

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            byte savePixel = vBuffer.get(1);
            try {
                vBuffer.put(1, (byte)~savePixel);
                if (uBuffer.get(0) == (byte)~savePixel) {
                    vBuffer.put(1, savePixel);
                    vBuffer.position(0);
                    uBuffer.position(0);
                    vBuffer.get(nv21, ySize, 1);
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining());

                    return nv21; // shortcut
                }
            }
            catch (ReadOnlyBufferException ex) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel);
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant

        for (int row=0; row<height/2; row++) {
            for (int col=0; col<width/2; col++) {
                int vuPos = col*pixelStride + row*rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }

        return nv21;
    }

    private Bitmap toBitmap(Image image) {

        byte[] nv21=YUV_420_888toNV21(image);


        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        //System.out.println("bytes"+ Arrays.toString(imageBytes));

        //System.out.println("FORMAT"+image.getFormat());

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }


    //Load Faces from Shared Preferences.Json String to Recognition object
    private HashMap<String, SimilarityClassifier.Recognition> readFromSP(){
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        String defValue = new Gson().toJson(new HashMap<String, SimilarityClassifier.Recognition>());
        String json=sharedPreferences.getString("map",defValue);
        // System.out.println("Output json"+json.toString());
        TypeToken<HashMap<String,SimilarityClassifier.Recognition>> token = new TypeToken<HashMap<String,SimilarityClassifier.Recognition>>() {};
        HashMap<String,SimilarityClassifier.Recognition> retrievedMap=new Gson().fromJson(json,token.getType());
        // System.out.println("Output map"+retrievedMap.toString());

        //During type conversion and save/load procedure,format changes(eg float converted to double).
        //So embeddings need to be extracted from it in required format(eg.double to float).
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : retrievedMap.entrySet())
        {
            float[][] output=new float[1][OUTPUT_SIZE];
            ArrayList arrayList= (ArrayList) entry.getValue().getExtra();
            arrayList = (ArrayList) arrayList.get(0);
            for (int counter = 0; counter < arrayList.size(); counter++) {
                output[0][counter]= ((Double) arrayList.get(counter)).floatValue();
            }
            entry.getValue().setExtra(output);

            //System.out.println("Entry output "+entry.getKey()+" "+entry.getValue().getExtra() );

        }
//        System.out.println("OUTPUT"+ Arrays.deepToString(outut));
        Toast.makeText(context, "Recognitions Loaded", Toast.LENGTH_SHORT).show();
        return retrievedMap;
    }

    //Similar Analyzing Procedure
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();
                try {
                    InputImage impphoto=InputImage.fromBitmap(getBitmapFromUri(selectedImageUri),0);
                    detector.process(impphoto).addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                        @Override
                        public void onSuccess(List<Face> faces) {

                            if(faces.size()!=0) {
                                recognize.setText("Recognize");
                                add_face.setVisibility(View.VISIBLE);
                                reco_name.setVisibility(View.INVISIBLE);
                                face_preview.setVisibility(View.VISIBLE);
                                preview_info.setText("1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face.");
                                Face face = faces.get(0);
//                                System.out.println(face);

                                //write code to recreate bitmap from source
                                //Write code to show bitmap to canvas

                                Bitmap frame_bmp= null;
                                try {
                                    frame_bmp = getBitmapFromUri(selectedImageUri);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                Bitmap frame_bmp1 = rotateBitmap(frame_bmp, 0, flipX, false);

                                //face_preview.setImageBitmap(frame_bmp1);


                                RectF boundingBox = new RectF(face.getBoundingBox());


                                Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);

                                Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);
                                // face_preview.setImageBitmap(scaled);

                                recognizeImage(scaled);
                                addFace();
//                                System.out.println(boundingBox);
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            start=true;
                            Toast.makeText(context, "Failed to add", Toast.LENGTH_SHORT).show();
                        }
                    });
                    face_preview.setImageBitmap(getBitmapFromUri(selectedImageUri));
                } catch (IOException e) {
                    e.printStackTrace();
                }


            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // TTS 객체가 남아있다면 실행을 중지하고 메모리에서 제거한다.
        if(tts != null){
            tts.stop();
            tts.shutdown();
            tts = null;
        }
//        FileUtils.releaseFile();

        // step.4 release uvc camera resources
        if (mCameraHelper != null) {
            mCameraHelper.release();
        }
        th1.interrupt();
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor =
                getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }

}

