package cn.wildfirechat.app.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocumentChunkerTest {
    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    public void chunksLongTextWithOverlap() {
        String text = "第一段内容。".repeat(200);
        List<DocumentChunker.Chunk> chunks = chunker.chunk(text, 300, 100);

        assertTrue(chunks.size() > 1);
        for (DocumentChunker.Chunk chunk : chunks) {
            assertTrue(chunk.getText().length() <= 300);
            assertFalse(chunk.getText().isEmpty());
        }
        assertEquals(0, chunks.get(0).getIndex());
        assertEquals(1, chunks.get(1).getIndex());
    }

    @Test
    public void emptyContentProducesNoChunks() {
        assertTrue(chunker.chunk("", 600, 100).isEmpty());
        assertTrue(chunker.chunk(null, 600, 100).isEmpty());
    }
}
