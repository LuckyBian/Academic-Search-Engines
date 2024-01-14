package hk.edu.hkbu.comp;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import org.json.JSONObject;

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

        //System.out.println(url);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        //System.out.println(responseCode);
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

                //System.out.println(response);
                String content = "Null";

            String json = String.valueOf(response);
            String keyToFind = "\"sm_api_content\":\"";
            int keyIndex = json.indexOf(keyToFind);

            if (keyIndex != -1) {
                // Find the start index of the actual content
                int start = keyIndex + keyToFind.length();

                // Find the end index of the actual content
                // It's important to start the search from the 'start' to avoid finding any quotes that belong to other keys.
                int end = json.indexOf("\"", start);

                if (end != -1) {
                    // Extract the content using the indices
                    content = json.substring(start, end);

                    // Replace any escaped characters, such as \" or \\
                    content = content.replace("\\\"", "\"");
                    content = content.replace("\\\\", "\\");

                    // Handle the [BREAK] token if necessary
                    content = content.replace("[BREAK]", "\n");

                    //System.out.println(content);
                } else {
                    System.out.println("End quote of value not found.");
                }
            } else {
                System.out.println("Key not found.");
            }

            return content;
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
