package cn.wildfirechat.app.rag;

/**
 * 内存索引中的向量条目，不保存正文。
 */
public class VectorEntry {
    private final long chunkId;
    private final long documentId;
    private final int chunkIndex;
    private final String title;
    private final float[] vector;

    public VectorEntry(long chunkId, long documentId, int chunkIndex, String title, float[] vector) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.title = title;
        this.vector = vector;
    }

    public long getChunkId() {
        return chunkId;
    }

    public long getDocumentId() {
        return documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getTitle() {
        return title;
    }

    public float[] getVector() {
        return vector;
    }
}
