/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.chatgpt;

import io.github.sashirestela.openai.SimpleOpenAI;
import io.github.sashirestela.openai.common.ResponseFormat;
import io.github.sashirestela.openai.domain.chat.Chat;
import io.github.sashirestela.openai.domain.chat.ChatMessage;
import io.github.sashirestela.openai.domain.chat.ChatRequest;

import java.util.List;

public class TestChatGPT {

    void testChatGPT() {
        // Inizializza il client leggendo la chiave da OPENAI_API_KEY
        var openAI = SimpleOpenAI.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        var chatRequest = ChatRequest.builder()
                .model("gpt-4o-mini")
                .message(ChatMessage.SystemMessage
                        .of("You are a helpful math tutor. Guide the user through the solution step by step."))
                .message(ChatMessage.UserMessage.of("How can I solve 8x + 7 = -23"))
                .responseFormat(ResponseFormat.jsonSchema(ResponseFormat.JsonSchema.builder()
                        .name("MathReasoning")
                        .schemaClass(MathReasoning.class)
                        .build()))
                .build();
        var chatResponse = openAI.chatCompletions().createStream(chatRequest).join();
        chatResponse.filter(chatResp -> chatResp.getChoices().size() > 0 && chatResp.firstContent() != null)
                .map(Chat::firstContent)
                .forEach(System.out::print);
        System.out.println();
    }

    public static class MathReasoning {
        public List<Step> steps;
        public String finalAnswer;

        public static class Step {

            public String explanation;
            public String output;

        }
    }
}
