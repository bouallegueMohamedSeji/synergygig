package tn.esprit.synergygig.entities;

public class NewsArticles {

    private String title;
    private String description;
    private String imageUrl;
    private String publishedAt;
    private String url;

    public NewsArticles(String title,
                       String description,
                       String imageUrl,
                       String publishedAt,
                       String url) {
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
        this.publishedAt = publishedAt;
        this.url = url;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public String getPublishedAt() { return publishedAt; }
    public String getUrl() { return url; }
}
