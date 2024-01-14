package hk.edu.hkbu.comp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public class gpt {
    public static String ask(String apiKey, String question) {
        HttpClient client = HttpClient.newHttpClient();
        String requestBody = new JSONObject()
                .put("model", "ft:gpt-3.5-turbo-1106:personal::8fXIoqcb")
                .put("messages", new JSONObject[]
                        {
                                new JSONObject().put("role", "system").put("content", "You are a chatbot that provides academic terms for everyday words."),
                                new JSONObject().put("role", "user").put("content", question)
                        })
                .toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            JSONObject jsonResponse = new JSONObject(response.body());

            // 获取 'choices' 数组的第一个元素，然后获取 'message' 对象
            JSONObject messageObject = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message");

            // 从 'message' 对象中提取 'content'
            if (messageObject.has("content")) {
                return messageObject.getString("content");
            } else {
                // 如果 'content' 字段不存在
                return "No content field in response";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        String apiKey = "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";
        String question = "Can you provide academic terms for 'hope'?";
        System.out.println(ask(apiKey, question));
    }
}

