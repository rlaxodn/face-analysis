package com.example.capstonetest1;

import android.app.Activity;
import android.app.Dialog;
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
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.google.mlkit.vision.common.InputImage.fromBitmap;

public class PeopleActivity extends AppCompatActivity {

    String contact_data;
    ImageView imageView_img;
    EditText editText_name;
    Dialog peopleDialog;
    Gson gson;
    SharedPreferences sp;
    SharedPreferences.Editor editor;
    int[] intValues;
    float[][] embeedings;
    int OUTPUT_SIZE=192; //Output size of model
    int inputSize=112;  //Input size for model
    String modelFile="mobile_face_net.tflite"; //model name
    float IMAGE_MEAN = 128.0f;
    float IMAGE_STD = 128.0f;
    boolean isModelQuantized=false;
    Interpreter tfLite;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.acrivity_list);

        ArrayList<MyData> data_list = new ArrayList<MyData>();
        GridView listView = findViewById(R.id.listView);

        //Load model
        try {
            tfLite=new Interpreter(loadModelFile(PeopleActivity.this,modelFile));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Sharedpreferences 생성
        sp = getSharedPreferences("shared",MODE_PRIVATE);
        editor = sp.edit();

        //gson 생성
        gson = new GsonBuilder().create();

        Collection<?> col =  sp.getAll().values();
        Iterator<?> it = col.iterator();

        while(it.hasNext())
        {
            String msg = (String)it.next();
            MyData myData = gson.fromJson(msg,MyData.class);
            data_list.add(myData);
        }

        //어뎁터를 생성하여 데이터를 연결
        PeopleAdapter dataAdapter = new PeopleAdapter(this, data_list);
        listView.setAdapter(dataAdapter);

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                editor.remove(data_list.get(i).getName()).commit();
                Intent intent = getIntent();
                finish();
                startActivity(intent);
                return false;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_people, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        peopleDialog = new Dialog(PeopleActivity.this);
        peopleDialog.setContentView(R.layout.dialog_people);
        peopleDialog.setTitle("이미지 등록");
        imageView_img = peopleDialog.findViewById(R.id.ImageView_image);
        editText_name = peopleDialog.findViewById(R.id.editText_name);
        peopleDialog.show();
        return true;
    }
    
    public void getImg(View view){

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setData(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, 0);
    }

    public void save(View view){

        //Initialize Face Detector
        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build();
        FaceDetector detector = FaceDetection.getClient(highAccuracyOpts);

        //다이얼로그의 데이터 가져옴
        contact_data = "";
        String name = editText_name.getText().toString();
        Bitmap image_bit = ((BitmapDrawable)imageView_img.getDrawable()).getBitmap();


        //이미지에서 얼굴만 검출
        InputImage input_image = fromBitmap(image_bit, 0);

        //이미지 bitmap -> String
        String image = BitmapToString(image_bit);

        Task<List<Face>> result =
                detector.process(input_image)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<Face>>() {
                                    @Override
                                    public void onSuccess(List<Face> faces) {
                                        // 탐지가 성공할 경우
                                        Face face = faces.get(0); //Get first face from detected faces

                                        //Get bounding box of face
                                        RectF boundingBox = new RectF(face.getBoundingBox());

                                        //Crop out bounding box from whole Bitmap(image)
                                        Bitmap cropped_face = getCropBitmapByCPU(image_bit, boundingBox);

                                        //얼굴인식 모델에 넣도록 이미지 크기 조절
                                        Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);

                                        //bitmap -> 임베딩값 구하기
                                        float[] emb = getEmbeedings(scaled);

                                        //이름, 이미지 저장
                                        MyData myData = new MyData(name, image, emb);
                                        //gson.toJson을 이용해서 클래스를 String으로 변환해줍니다.
                                        // 첫번째 인자에는 실제로 변경이 되는 클래스를 넣어주고
                                        // 두번째 인자에는 클래스의 형식을 넣어준다고 생각하면 됩니다.
                                        contact_data = gson.toJson(myData,MyData.class);
                                        //SharedPreferences에 String으로 변환된 클래스를 저장해줍니다.

                                        editor.putString(name,contact_data);
                                        editor.commit();

                                        peopleDialog.cancel();
                                        Intent intent = getIntent();
                                        finish();
                                        startActivity(intent);
                                    }
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) { // 탐지가 실패할 경우
                                        Log.e("faces: ", "실패!");
                                        peopleDialog.dismiss();
                                        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());

                                        builder.setTitle("등록실패").setMessage("얼굴이 검출되지 않습니다");
                                        builder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                            @Override public void onClick(DialogInterface dialogInterface, int i) {
                                                finish();
                                            }
                                        });

                                        AlertDialog alertDialog = builder.create();
                                        alertDialog.show();

                                    }
                                });



//        //얼굴 bitmap -> String
//        String image = BitmapToString(image_bit);
//
//        //이름, 이미지 저장
//        MyData myData = new MyData(name, image);
//        //gson.toJson을 이용해서 클래스를 String으로 변환해줍니다.
//        // 첫번째 인자에는 실제로 변경이 되는 클래스를 넣어주고
//        // 두번째 인자에는 클래스의 형식을 넣어준다고 생각하면 됩니다.
//        contact_data = gson.toJson(myData,MyData.class);
//        //SharedPreferences에 String으로 변환된 클래스를 저장해줍니다.
//
//        editor.putString(name,contact_data);
//        editor.commit();
//
//        peopleDialog.cancel();
//        Intent intent = getIntent();
//        finish();
//        startActivity(intent);
//        Toast.makeText(this,"저장 완료", Toast.LENGTH_SHORT).show();
    }
    public void cancel(View view){
        peopleDialog.cancel();
    }

    public float[] getEmbeedings(Bitmap bitmap){
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

        return embeedings[0];

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==0)
        {
            if(resultCode==RESULT_OK)
            {
                try{
                    InputStream in = getContentResolver().openInputStream(data.getData());

                    Bitmap img = BitmapFactory.decodeStream(in);
                    in.close();
                    int height = img.getHeight();
                    int width = img.getWidth();

                    Display display = getWindowManager().getDefaultDisplay();  // in Activity
                    /* getActivity().getWindowManager().getDefaultDisplay() */ // in Fragment
                    Point size = new Point();
                    display.getRealSize(size); // or getSize(size)
                    int display_width = size.x/2;

                    img = img.createScaledBitmap(img,display_width, (height/width) * display_width,true);
                    imageView_img.setImageBitmap(img);

                }catch (Exception e)
                {
                }
            }
            else if(resultCode == RESULT_CANCELED)
            {
                Toast.makeText(this,"사진 선택 취소", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static Bitmap StringToBitmap(String encodedString) {
        try {
            byte[] encodeByte = Base64.decode(encodedString, Base64.DEFAULT);// String 화 된 이미지를  base64방식으로 인코딩하여 byte배열을 만듬
            Bitmap bitmap = BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.length);//byte배열을 bitmapfactory 메소드를 이용하여 비트맵으로 바꿔준다.
            return bitmap;//만들어진 bitmap을 return
        } catch (Exception e) {
            e.getMessage();
            return null;
        }
    }

    public static String BitmapToString(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); //바이트 배열을 차례대로 읽어 들이기위한 ByteArrayOutputStream클래스 선언
        bitmap.compress(Bitmap.CompressFormat.PNG, 70, baos);//bitmap을 압축 (숫자 70은 70%로 압축한다는 뜻)
        byte[] bytes = baos.toByteArray();//해당 bitmap을 byte배열로 바꿔준다.
        String temp = Base64.encodeToString(bytes, Base64.DEFAULT);//Base 64 방식으로byte 배열을 String으로 변환
        return temp;//String을 retrurn
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

}

