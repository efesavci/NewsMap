package com.example.newsmapdumen;

public class Article {
    public final String title;
    public final String source;
    public final String url;
    public final long timestampEpochMillis;

    public Article(String title, String source, String url, long timestampEpochMillis) {
        this.title = title;
        this.source = source;
        this.url = url;
        this.timestampEpochMillis = timestampEpochMillis;
    }
}

