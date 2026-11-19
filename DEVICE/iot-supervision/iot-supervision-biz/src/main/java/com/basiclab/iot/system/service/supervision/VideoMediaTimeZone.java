package com.basiclab.iot.system.service.supervision;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** Converts VIDEO wall-clock values without changing DEVICE storage or API timestamps. */
@Component
class VideoMediaTimeZone {

    private final ZoneId deviceZone;
    private final ZoneId videoZone;

    @Autowired
    VideoMediaTimeZone(@Value("${yfeieye.video.record-time-zone:}") String videoZone) {
        this(ZoneId.systemDefault(), videoZone);
    }

    VideoMediaTimeZone(ZoneId deviceZone, String videoZone) {
        this.deviceZone = deviceZone;
        this.videoZone = videoZone == null || videoZone.isBlank() ? deviceZone : ZoneId.of(videoZone);
    }

    LocalDateTime toVideo(LocalDateTime time) {
        return time.atZone(deviceZone).withZoneSameInstant(videoZone).toLocalDateTime();
    }

    String formatVideo(LocalDateTime time) {
        return time == null ? null : toVideo(time).toString();
    }

    LocalDateTime fromVideo(LocalDateTime time) {
        return time.atZone(videoZone).withZoneSameInstant(deviceZone).toLocalDateTime();
    }

    LocalDateTime parseVideo(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String iso = value.strip().replace(' ', 'T');
        try {
            return fromVideo(LocalDateTime.parse(iso));
        } catch (DateTimeParseException ignored) {
            try {
                Instant instant = OffsetDateTime.parse(iso).toInstant();
                return LocalDateTime.ofInstant(instant, deviceZone);
            } catch (DateTimeParseException invalid) {
                return null;
            }
        }
    }
}
