import io.humble.video.*;
import io.humble.video.awt.MediaPictureConverter;
import io.humble.video.awt.MediaPictureConverterFactory;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
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
               // jcodecGrab(fileName);
                humbleGrab(Path.of(".", fileName));
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
        System.exit(0);
    }

    private static void jcodecGrab(String fileName) throws IOException, JCodecException {
        System.out.println("-- reading " + fileName + " ----------------------------");
        final File file = new File("./" + fileName);
        final ByteBuffer byteBuffer = ByteBuffer.wrap(Files.readAllBytes(Path.of(".", fileName)));
        final ByteBufferSeekableByteChannel byteChannel =
                new ByteBufferSeekableByteChannel(byteBuffer, (int) file.length());
        FrameGrab.getFrameFromChannel(byteChannel, 0);
        System.out.println("SUCCESS");
    }

    private static Path humbleGrab(final Path videoFile)

            throws InterruptedException, IOException {

        final Demuxer demuxer = Demuxer.make();
        demuxer.open(videoFile.toString(), null, false, true, null, null);

        int streamIndex = -1;
        Decoder videoDecoder = null;
        String rotate = null;
        final int numStreams = demuxer.getNumStreams();
        for (int i = 0; i < numStreams; ++i) {
            final DemuxerStream stream = demuxer.getStream(i);
            final KeyValueBag metaData = stream.getMetaData();
            final Decoder decoder = stream.getDecoder();
            if (decoder != null
                    && decoder.getCodecType() == MediaDescriptor.Type.MEDIA_VIDEO) {
                videoDecoder = decoder;
                streamIndex = i;
                rotate = metaData.getValue("rotate", KeyValueBag.Flags.KVB_NONE);
                break;
            }
        }

        if (videoDecoder == null) {
            throw new IOException("Not a valid video file");
        }
        videoDecoder.open(null, null);

        final MediaPicture picture = MediaPicture.make(videoDecoder.getWidth(),
                videoDecoder.getHeight(), videoDecoder.getPixelFormat());

        final MediaPictureConverter converter = MediaPictureConverterFactory
                .createConverter(MediaPictureConverterFactory.HUMBLE_BGR_24, picture);

        final MediaPacket packet = MediaPacket.make();
        BufferedImage image = null;
        MUX : while (demuxer.read(packet) >= 0) {
            if (packet.getStreamIndex() != streamIndex) {
                continue;
            }
            int offset = 0;
            int bytesRead = 0;
            videoDecoder.decodeVideo(picture, packet, offset);
            do {
                bytesRead += videoDecoder.decode(picture, packet, offset);
                if (picture.isComplete()) {
                    image = converter.toImage(null, picture);
                    break MUX;
                }
                offset += bytesRead;

            } while (offset < packet.getSize());
        }
        if (image == null) {
            throw new IOException("Unable to find a complete video frame");
        }
        if (rotate != null) {
            final AffineTransform transform = new AffineTransform();
            transform.translate(0.5 * image.getHeight(), 0.5 * image.getWidth());
            transform.rotate(Math.toRadians(Double.parseDouble(rotate)));
            transform.translate(-0.5 * image.getWidth(), -0.5 * image.getHeight());
            final AffineTransformOp op = new AffineTransformOp(transform,
                    AffineTransformOp.TYPE_BILINEAR);
            image = op.filter(image, null);
        }

        final Path target = videoFile.getParent()
                .resolve(videoFile.getFileName() + ".thumb.jpg");

        final double mul;
        if (image.getWidth() > image.getHeight()) {
            mul = 216 / (double) image.getWidth();
        } else {
            mul = 216 / (double) image.getHeight();
        }

        final int newW = (int) (image.getWidth() * mul);
        final int newH = (int) (image.getHeight() * mul);
        final Image thumbnailImage = image.getScaledInstance(newW, newH,
                Image.SCALE_SMOOTH);
        image = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_BGR);

        final Graphics2D g2d = image.createGraphics();
        g2d.drawImage(thumbnailImage, 0, 0, null);
        g2d.dispose();

        ImageIO.write(image, "jpeg", target.toFile());
        return target.toAbsolutePath(); }
}
