package br.com.operadorzero.media;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.operadorzero.media.MediaDtos.Crop;
import br.com.operadorzero.media.MediaPlans.Rendition;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MediaRulesTest {
    @Test
    void detectsTheRealTypeFromMagicBytesNotTheExtension() {
        assertThat(FileSniffer.sniff(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).get()
            .extracting(FileSniffer.Detected::type).isEqualTo("image/jpeg");
        assertThat(FileSniffer.sniff(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})).get()
            .extracting(FileSniffer.Detected::type).isEqualTo("image/png");
        assertThat(FileSniffer.sniff(box("ftypheic"))).get().extracting(FileSniffer.Detected::kind).isEqualTo("IMAGE");
        assertThat(FileSniffer.sniff(box("ftypisom"))).get().extracting(FileSniffer.Detected::type).isEqualTo("video/mp4");
        assertThat(FileSniffer.sniff(box("ftypqt  "))).get().extracting(FileSniffer.Detected::type).isEqualTo("video/quicktime");
        // Um script ou executavel renomeado para .jpg nao passa.
        assertThat(FileSniffer.sniff("#!/bin/sh\nrm -rf /".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(FileSniffer.sniff(new byte[] {'M', 'Z', (byte) 0x90, 0})).isEmpty();
    }

    @Test
    void cropWindowIsNineBySixteenInsideTheFrameAndEven() {
        var full = MediaPlans.cropWindow(1920, 1080, new Crop(new BigDecimal("0.5"), new BigDecimal("0.5"), BigDecimal.ONE));
        assertThat(full.height()).isEqualTo(1080);
        assertThat(full.width()).isEqualTo(606);
        assertThat(full.x()).isEqualTo(656);
        // Centro fora do quadro e encostado na borda, nunca alem dela.
        var corner = MediaPlans.cropWindow(1920, 1080, new Crop(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("2")));
        assertThat(corner.x() + corner.width()).isLessThanOrEqualTo(1920);
        assertThat(corner.y() + corner.height()).isLessThanOrEqualTo(1080);
        assertThat(corner.width() % 2).isZero();
        assertThat(MediaPlans.cropWindow(1080, 1920, null)).isNull();
    }

    @Test
    void ladderNeverUpscalesAndAlwaysHasOneRendition() {
        assertThat(MediaPlans.ladder(1080, 1920)).extracting(Rendition::shortSide).containsExactly(360, 720, 1080);
        assertThat(MediaPlans.ladder(720, 1280)).extracting(Rendition::shortSide).containsExactly(360, 720);
        assertThat(MediaPlans.ladder(240, 426)).hasSize(1).first().satisfies(r -> assertThat(r.width()).isEqualTo(240));
        assertThat(MediaPlans.ladder(1920, 1080).getLast()).satisfies(r -> {
            assertThat(r.height()).isEqualTo(1080);
            assertThat(r.width()).isEqualTo(1920);
        });
    }

    @Test
    void everyOutputDropsMetadata() {
        Path in = Path.of("in.mov");
        Path out = Path.of("out");
        var ladder = MediaPlans.ladder(1080, 1920);
        List<List<String>> commands = List.of(
            MediaPlans.hlsArgs(in, null, ladder, true, null),
            MediaPlans.fallbackMp4Args(in, out.resolve("f.mp4"), null, ladder.getFirst(), true, null),
            MediaPlans.posterArgs(in, out.resolve("p.webp"), null, null, new MediaPlans.Size(720, 1280), 0.5),
            MediaPlans.imageArgs(in, out.resolve("i.webp"), 1080),
            MediaPlans.placeholderArgs(in, out.resolve("b.webp")));
        for (List<String> args : commands) {
            int index = args.indexOf("-map_metadata");
            assertThat(index).as(args.toString()).isPositive();
            assertThat(args.get(index + 1)).isEqualTo("-1");
        }
        List<String> hls = commands.getFirst();
        assertThat(hls).contains("-var_stream_map", "v:0,a:0 v:1,a:1 v:2,a:2", "master.m3u8");
        assertThat(MediaPlans.hlsArgs(in, null, ladder, false, null)).contains("v:0 v:1 v:2").doesNotContain("0:a:0");
    }

    @Test
    void unknownVideoFiltersAreRejected() {
        assertThat(VideoFilters.isKnown(null)).isTrue();
        assertThat(VideoFilters.isKnown("")).isTrue();
        assertThat(VideoFilters.isKnown("drawtext=text=oi")).isFalse();
    }

    private static byte[] box(String typeAndBrand) {
        byte[] bytes = new byte[16];
        bytes[3] = 0x18;
        System.arraycopy(typeAndBrand.getBytes(StandardCharsets.ISO_8859_1), 0, bytes, 4, 8);
        return bytes;
    }
}
