package com.example.capstonetest1;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class PeopleAdapter extends BaseAdapter {
    Context mContext = null;

    ArrayList<MyData> sample;

    public PeopleAdapter(Context context, ArrayList<MyData> data) {
        mContext = context;
        sample = data;
    }


    @Override
    public int getCount() {
        return sample.size();
    }

    @Override
    public Object getItem(int i) {
        return sample.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {

        if(view == null){
            LayoutInflater inflater = LayoutInflater.from(mContext);
            view = inflater.inflate(R.layout.row, viewGroup, false);
        }

        ImageView imageView_image = view.findViewById(R.id.imageView_image);
        TextView textView_name = view.findViewById(R.id.textView_name);

        imageView_image.setImageBitmap(StringToBitmap(sample.get(i).getImage()));
        textView_name.setText(sample.get(i).getName());

        return view;
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
}
