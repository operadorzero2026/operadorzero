package br.com.operadorzero.shared.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.operadorzero.shared.web.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class SafeRasterImageProcessorTest {
    @Test
    void reprocessesAValidPng() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);

        var processed = SafeRasterImageProcessor.process(
            new MockMultipartFile("file", "photo.png", "image/png", output.toByteArray()));

        assertThat(processed.contentType()).isEqualTo("image/png");
        assertThat(processed.data()).isNotEmpty();
    }

    @Test
    void rejectsContentThatIsNotAnImage() {
        assertThatThrownBy(() -> SafeRasterImageProcessor.process(
            new MockMultipartFile("file", "photo.png", "image/png", "not-an-image".getBytes())))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo("INVALID_IMAGE"));
    }
}
