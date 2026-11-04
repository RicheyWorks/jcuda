/* JCuda - Java bindings for CUDA - http://www.jcuda.org */
package jcuda.test;

import static org.junit.Assert.*;
import java.nio.*;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import jcuda.Pointer;

/** Heap pointer construction only: no native memory transfer or CUDA loading. */
@RunWith(Parameterized.class)
public class PointerHeapBufferTest {
    @Parameterized.Parameters(name="{0}/{1}")
    public static Collection<Object[]> cases() {
        Collection<Object[]> cases = new ArrayList<Object[]>();
        for (String type : new String[]{"byte","char","short","int","long","float","double"})
            for (String layout : new String[]{"wrap-window","slice","nested-slice","duplicate","empty-end"})
                cases.add(new Object[]{type,layout});
        return cases;
    }
    private final String type, layout;
    public PointerHeapBufferTest(String type, String layout) { this.type=type; this.layout=layout; }

    private static class Offset extends Pointer {
        Offset(Pointer pointer) { super(pointer); }
        long value() { return getByteOffset(); }
    }

    private Buffer wrap() {
        if ("byte".equals(type)) return ByteBuffer.wrap(new byte[40],5,22);
        if ("char".equals(type)) return CharBuffer.wrap(new char[40],5,22);
        if ("short".equals(type)) return ShortBuffer.wrap(new short[40],5,22);
        if ("int".equals(type)) return IntBuffer.wrap(new int[40],5,22);
        if ("long".equals(type)) return LongBuffer.wrap(new long[40],5,22);
        if ("float".equals(type)) return FloatBuffer.wrap(new float[40],5,22);
        return DoubleBuffer.wrap(new double[40],5,22);
    }
    private int elementBytes() {
        if ("byte".equals(type)) return 1;
        if ("char".equals(type) || "short".equals(type)) return 2;
        if ("int".equals(type) || "float".equals(type)) return 4;
        return 8;
    }
    private static Buffer slice(Buffer b) {
        if (b instanceof ByteBuffer) return ((ByteBuffer)b).slice();
        if (b instanceof CharBuffer) return ((CharBuffer)b).slice();
        if (b instanceof ShortBuffer) return ((ShortBuffer)b).slice();
        if (b instanceof IntBuffer) return ((IntBuffer)b).slice();
        if (b instanceof LongBuffer) return ((LongBuffer)b).slice();
        if (b instanceof FloatBuffer) return ((FloatBuffer)b).slice();
        return ((DoubleBuffer)b).slice();
    }
    private static Buffer duplicate(Buffer b) {
        if (b instanceof ByteBuffer) return ((ByteBuffer)b).duplicate();
        if (b instanceof CharBuffer) return ((CharBuffer)b).duplicate();
        if (b instanceof ShortBuffer) return ((ShortBuffer)b).duplicate();
        if (b instanceof IntBuffer) return ((IntBuffer)b).duplicate();
        if (b instanceof LongBuffer) return ((LongBuffer)b).duplicate();
        if (b instanceof FloatBuffer) return ((FloatBuffer)b).duplicate();
        return ((DoubleBuffer)b).duplicate();
    }

    @Test
    public void preservesPhysicalIndexAndBufferState() {
        Buffer root=wrap(), buffer=root;
        int physicalIndex, mark=4, position=7;
        if ("wrap-window".equals(layout) || "duplicate".equals(layout)) {
            if ("duplicate".equals(layout)) buffer=duplicate(root);
            mark=5; position=9; physicalIndex=9;
        } else if ("empty-end".equals(layout)) {
            root.limit(40); root.position(40); buffer=slice(root);
            mark=0; position=0; physicalIndex=40;
        } else {
            root.position(3); root.limit(34); buffer=slice(root);
            physicalIndex=10; // root offset3 + final logical position7
            if ("nested-slice".equals(layout)) {
                buffer.position(5); buffer.limit(25); buffer=slice(buffer);
                physicalIndex=15; // root3 + first slice5 + final position7
            }
            buffer.limit(12);
        }
        buffer.position(mark); buffer.mark(); buffer.position(position);
        int rootPosition=root.position(), rootLimit=root.limit(), limit=buffer.limit();
        Object array=buffer.array();
        Pointer pointer=Pointer.toBuffer(buffer);
        long expected=(long)physicalIndex*elementBytes();
        assertEquals(expected,new Offset(pointer).value());
        assertEquals(expected+10,new Offset(pointer.withByteOffset(3).withByteOffset(7)).value());
        assertEquals(expected,new Offset(pointer).value());
        assertSame(array,buffer.array());
        assertEquals(position,buffer.position()); assertEquals(limit,buffer.limit());
        assertEquals(rootPosition,root.position()); assertEquals(rootLimit,root.limit());
        buffer.reset(); assertEquals(mark,buffer.position());
    }
}
