package cn.wildfirechat.app.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class VectorMathTest {
    @Test
    public void parsesAndFormatsPgVectorLiteral() {
        assertArrayEquals(new float[]{0.5f, -1.25f}, VectorMath.fromString("[0.5,-1.25]"), 0.0001f);
        assertEquals("[0.5,-1.25]", VectorMath.toVectorLiteral(new float[]{0.5f, -1.25f}));
    }
}
