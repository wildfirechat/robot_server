package cn.wildfirechat.app.llm;

/**
 * 大模型调用相关的异常。
 */
public class LlmException extends Exception {
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
