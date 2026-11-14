package cn.wildfirechat.app.rag;

import java.util.List;

/**
 * Embedding 客户端抽象，便于测试替换。
 */
public interface EmbeddingClient {
    float[] embedOne(String text) throws Exception;

    List<float[]> embedAll(List<String> texts) throws Exception;
}
