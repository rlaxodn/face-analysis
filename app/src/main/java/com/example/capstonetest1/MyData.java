package com.example.capstonetest1;

public class MyData {
    private String image;
    private String name;
    private float[] embedding;

    public MyData(String name, String image, float[] embedding){
        this.name = name;
        this.image = image;
        this.embedding = embedding;
    }
    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
}
