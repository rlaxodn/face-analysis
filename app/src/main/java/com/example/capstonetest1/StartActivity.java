package com.example.capstonetest1;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class StartActivity extends AppCompatActivity {

    SharedPreferences sp;
    Gson gson;
    String contact_data;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);

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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mymenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch(item.getItemId()) {
            case(R.id.solution):
                Intent intent = new Intent(getApplicationContext(), ExplanationActivity.class);
                startActivity(intent);
                break;
        }
        return true;
    }
    public void mOnClick(View v){
        switch (v.getId()) {
            case R.id.Button_start:
                Toast.makeText(this,"인식 시작", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intent);
                break;
            case R.id.Button_manage:
                Intent intent2 = new Intent(getApplicationContext(), PeopleActivity.class);
                startActivity(intent2);
//                Toast.makeText(this,"등록관리창 열기", Toast.LENGTH_SHORT).show();
                break;
            case R.id.Button_uvc_start:
                Intent intent3 = new Intent(getApplicationContext(), UvcActivity.class);
                startActivity(intent3);
                break;
        }
    }
}