import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatModel;

public class ChatGptExample {
    public static void main(String[] args) {
        // Il client legge la chiave da env: OPENAI_API_KEY
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addUserMessage("Ciao, mi puoi dare un comando per muovere il robot?")
                .model(ChatModel.GPT_4_1)  // Sostituisci con modello disponibile
                .build();

        ChatCompletion response = client.chat().completions().create(params);
        System.out.println("ChatGPT dice: " + response.choices().get(0).message().content());
    }
}
