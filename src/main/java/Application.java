import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class Application {
    public static void main(String[] args) {
        for (int fileIndex = 0; fileIndex < 4; fileIndex++) {
            final String fileName = "test" + fileIndex + ".mov";
            try {
                grab(fileName);
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
        System.exit(0);
    }

    private static void grab(String fileName) throws IOException, JCodecException {
        System.out.println("-- reading " + fileName + " ----------------------------");
        final File file = new File("./" + fileName);
        final ByteBuffer byteBuffer = ByteBuffer.wrap(Files.readAllBytes(Path.of(".", fileName)));
        final ByteBufferSeekableByteChannel byteChannel =
                new ByteBufferSeekableByteChannel(byteBuffer, (int) file.length());
        FrameGrab.getFrameFromChannel(byteChannel, 0);
        System.out.println("SUCCESS");
    }
}
