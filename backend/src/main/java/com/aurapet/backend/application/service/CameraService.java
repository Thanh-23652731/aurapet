package com.aurapet.backend.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Giữ khung hình MỚI NHẤT từ camera Raspberry Pi trong RAM để web xem trực tiếp.
 * Không lưu xuống ổ đĩa hay CSDL: tắt máy chủ là không còn hình nào.
 */
@Slf4j
@Service
public class CameraService {

    /** Không có khung hình mới trong khoảng này thì coi camera mất tín hiệu. */
    static final Duration ONLINE_TIMEOUT = Duration.ofSeconds(5);

    private final DeviceService deviceService;
    private final Object lock = new Object();

    private byte[] jpeg;
    private long seq;
    private Instant frameAt;
    private boolean catPresent;
    private double confidence;
    private final Deque<Long> recent = new ArrayDeque<>(); // thời điểm các khung gần đây (tính FPS)

    public CameraService(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    public record Frame(byte[] jpeg, long seq, Instant at, boolean catPresent, double confidence, double fps, boolean online) {
    }

    public void putFrame(byte[] data, boolean cat, double conf) {
        synchronized (lock) {
            boolean first = jpeg == null;
            jpeg = data;
            seq++;
            frameAt = Instant.now();
            catPresent = cat;
            confidence = conf;
            long now = System.currentTimeMillis();
            recent.addLast(now);
            while (!recent.isEmpty() && now - recent.peekFirst() > 3000) recent.removeFirst();
            lock.notifyAll();
            if (first) log.info("[Camera] Đã nhận khung hình đầu tiên ({} KB)", data.length / 1024);
        }
        deviceService.markCameraSeen();
    }

    /**
     * Trả về khung hình có số thứ tự lớn hơn {@code after}. Nếu chưa có, chờ tối đa {@code waitMs}
     * (long-polling: web nhận hình ngay khi Pi gửi lên, không phải hỏi liên tục).
     * Không có khung mới -> Frame với jpeg = null.
     */
    public Frame await(long after, long waitMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Math.max(0, Math.min(waitMs, 5000));
        synchronized (lock) {
            while (seq <= after || jpeg == null) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) return snapshot(false);
                lock.wait(left);
            }
            return snapshot(true);
        }
    }

    public Frame status() {
        synchronized (lock) {
            return snapshot(false);
        }
    }

    private Frame snapshot(boolean withImage) {
        boolean online = frameAt != null && Duration.between(frameAt, Instant.now()).compareTo(ONLINE_TIMEOUT) < 0;
        double fps = 0;
        if (online && recent.size() > 1) {
            fps = (recent.size() - 1) * 1000.0 / Math.max(1, recent.peekLast() - recent.peekFirst());
        }
        return new Frame(withImage ? jpeg : null, seq, frameAt, online && catPresent, confidence, fps, online);
    }
}
