package com.medassist.ai.provider;

import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.NotFoundException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.UnprocessableEntityException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Adapter for OpenAI, Azure OpenAI and OpenAI-compatible chat models through Spring AI. Uses low temperature and
 * JSON response formats; never retries internally (retries are handled by the resilience decorator).
 */
public class SpringAiClinicalProvider implements ClinicalAiProvider {

    private final ChatModel chatModel;
    private final ProviderDescriptor descriptor;
    private final AiProperties.ResponseFormatMode responseFormat;
    private final boolean useMaxCompletionTokens;

    public SpringAiClinicalProvider(ChatModel chatModel, ProviderDescriptor descriptor,
                                    AiProperties.ResponseFormatMode responseFormat, boolean useMaxCompletionTokens) {
        this.chatModel = chatModel;
        this.descriptor = descriptor;
        this.responseFormat = responseFormat;
        this.useMaxCompletionTokens = useMaxCompletionTokens;
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ClinicalReasoningResponse reason(ClinicalReasoningRequest request) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        options.temperature(request.temperature());
        if (useMaxCompletionTokens) {
            options.maxCompletionTokens(request.maxOutputTokens());
        } else {
            options.maxTokens(request.maxOutputTokens());
        }
        switch (responseFormat) {
            case JSON_OBJECT -> options.responseFormat(OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT).build());
            case JSON_SCHEMA -> options.responseFormat(OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                    .jsonSchema(request.responseJsonSchema()).strict(false).build());
            case TEXT -> {
                // plain text; the structured-output parser still enforces the JSON contract
            }
        }
        Prompt prompt = new Prompt(List.of(new SystemMessage(request.systemPrompt()),
                new UserMessage(request.userPrompt())), options.build());
        long start = System.nanoTime();
        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (RuntimeException e) {
            throw translate(e);
        }
        Duration latency = Duration.ofNanos(System.nanoTime() - start);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null
                || response.getResult().getOutput().getText().isBlank()) {
            throw new AiProviderException(AiProviderException.Category.EMPTY_RESPONSE, "Provider returned no content");
        }
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        String reportedModel = response.getMetadata() == null ? null : response.getMetadata().getModel();
        return new ClinicalReasoningResponse(descriptor.provider(), descriptor.model(), reportedModel,
                response.getResult().getOutput().getText(), usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(), latency);
    }

    static AiProviderException translate(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof RateLimitException) {
                return new AiProviderException(AiProviderException.Category.RATE_LIMITED, "Provider rate limit reached", error);
            }
            if (t instanceof UnauthorizedException || t instanceof PermissionDeniedException) {
                return new AiProviderException(AiProviderException.Category.AUTHENTICATION,
                        "Provider rejected the credentials", error);
            }
            if (t instanceof BadRequestException || t instanceof UnprocessableEntityException || t instanceof NotFoundException) {
                return new AiProviderException(AiProviderException.Category.BAD_REQUEST,
                        "Provider rejected the request (check model/deployment configuration)", error);
            }
            if (t instanceof InternalServerException) {
                return new AiProviderException(AiProviderException.Category.UNAVAILABLE, "Provider server error", error);
            }
            if (t instanceof InterruptedIOException) {
                return new AiProviderException(AiProviderException.Category.TIMEOUT, "Provider timed out", error);
            }
            if (t instanceof ConnectException || t instanceof OpenAIIoException) {
                return new AiProviderException(AiProviderException.Category.UNAVAILABLE, "Provider unreachable", error);
            }
        }
        return new AiProviderException(AiProviderException.Category.UNAVAILABLE,
                "Provider call failed (" + error.getClass().getSimpleName() + ")", error);
    }
}
