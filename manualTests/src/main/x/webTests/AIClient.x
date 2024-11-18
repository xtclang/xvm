/**
 * The command example:
 *
 *    xec -L build/xtc/main - o build/xtc/main AIClient.x "question 1" "question 2" ...
 */
module AIClient {
    package json import json.xtclang.org;
    package web  import web.xtclang.org;

    import json.*;

    import web.HttpClient;
    import web.RequestOut;
    import web.ResponseIn;
    import web.Uri;

    static String ANTHROPIC_API_URI =
        "https://api.anthropic.com/v1/messages";
//        "http://localhost/e/anthropic";

    @Inject Console console;

    void run(String[] args=[]) {
        HttpClient client = new HttpClient();

        if (args.size == 0) {
            console.print("What's your question?");
            return;
        }
        askClaude(client, getClaudeToken(), args);
    }

    void askClaude(HttpClient client, String apiKey, String[] questions) {
        JsonArray content = json.newArray();
        for (String question : questions) {
            content += ["role"="user", "content"=question];
        }

        JsonObject messageOut = [
            "model"      = "claude-3-5-sonnet-20241022",
            "max_tokens" = 1024,
            "messages"   = content,
        ];

        RequestOut request = client.createRequest(POST, new Uri(ANTHROPIC_API_URI), messageOut, Json);
        request.header["x-api-key"        ] = apiKey;
        request.header["anthropic-version"] = "2023-06-01";

        ResponseIn response = client.send(request);

        if (JsonObject messageIn := response.to(JsonObject)) {
            assert Doc text := JsonPointer.from("content/0/text").get(messageIn);
            console.print(text);
        } else {
            console.print(response);
        }
    }

    String getClaudeToken() {
        @Inject Directory homeDir;
        if (File credentialsFile := homeDir.findFile(".anthropic-key")) {
            for (String tokenString : credentialsFile.contents.unpackUtf8().split('\n')) {
                if (tokenString.empty) {
                    continue;
                }
                return tokenString;
            }
        }
        return console.readLine("Enter API key: ");
    }
}