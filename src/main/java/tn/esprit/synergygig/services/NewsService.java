package tn.esprit.synergygig.services;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import tn.esprit.synergygig.entities.NewsArticles;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class NewsService {

    private static final String API_KEY = "";

    private String getTechNews() {

        try {

            String urlStr =
                    "https://newsapi.org/v2/top-headlines?category=business&language=en&apiKey=" + API_KEY;

            URL url = new URL(urlStr);
            HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");

            BufferedReader br =
                    new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            br.close();

            return response.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<NewsArticles> getTop5Articles()
    {

        List<NewsArticles> articlesList = new ArrayList<>();

        try {

            String json = getTechNews();
            if (json == null) return articlesList;

            JSONObject obj = new JSONObject(json);
            JSONArray articles = obj.getJSONArray("articles");

            for (int i = 0; i < Math.min(5, articles.length()); i++) {

                JSONObject article = articles.getJSONObject(i);

                String title = article.optString("title", "");
                String description = article.optString("description", "");
                String imageUrl = article.optString("urlToImage", "");
                String date = article.optString("publishedAt", "");
                String url = article.optString("url", "");

                articlesList.add(
                        new NewsArticles(
                                title,
                                description,
                                imageUrl,
                                date,
                                url
                        )
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return articlesList;
    }


}
