package hk.edu.hkbu.comp;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class summary {

    private static final String API_KEY = "67B08E92E0";
    private static final String BASE_URL = "http://api.smmry.com";

    public static String getSummary(String articleUrl) throws Exception {
        URL url = new URL(BASE_URL + "?SM_API_KEY=" + API_KEY
                + "&SM_LENGTH=1"
                + "&SM_WITH_BREAK=false"
                + "&SM_URL=" + articleUrl);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response.toString());
            return rootNode.get("sm_api_content").asText();
        } else {
            throw new Exception("Failed to get summary. HTTP response code: " + responseCode);
        }
    }

    public static void main(String[] args) {
        try {
            String summary = getSummary("http://www.example.com/link-to-article");
            System.out.println(summary);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
