package com.aurapet.backend.infrastructure.mqtt;

import com.aurapet.backend.application.service.DeviceCommandPublisher;
import com.aurapet.backend.application.service.DeviceMessageEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kết nối duy nhất giữa backend và MQTT broker.
 *
 * Topic (PREFIX là chuỗi bí mật riêng của nhóm, ví dụ "aurapet/9f3c2a7d1b4e"):
 *   PREFIX/telemetry  ESP32 -> server  (2 giây/lần)
 *   PREFIX/log        ESP32 -> server  (sau mỗi lần nhả hạt)
 *   PREFIX/status     ESP32 -> server  ("online"/"offline", retained + Last Will)
 *   PREFIX/vision     Raspberry Pi -> server (phát hiện mèo bằng camera)
 *   PREFIX/water      ESP32 máy dọn cát -> server (mực nước bình bơm + trạng thái khay, 5 giây/lần)
 *   PREFIX/litter     ESP32 máy dọn cát -> server (mèo đi vệ sinh, dọn cát xong)
 *   PREFIX/command    server -> ESP32
 *
 * Trình duyệt và app KHÔNG kết nối broker trực tiếp nữa, nên mật khẩu broker không bị lộ.
 */
@Slf4j
@Component
public class MqttGateway implements DeviceCommandPublisher, MqttCallbackExtended {

    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    @Value("${aurapet.mqtt.enabled:true}")
    private boolean enabled;
    @Value("${aurapet.mqtt.host:broker.emqx.io}")
    private String host;
    @Value("${aurapet.mqtt.port:1883}")
    private int port;
    @Value("${aurapet.mqtt.tls:false}")
    private boolean tls;
    @Value("${aurapet.mqtt.username:}")
    private String username;
    @Value("${aurapet.mqtt.password:}")
    private String password;
    @Value("${aurapet.mqtt.topic-prefix:}")
    private String topicPrefix;
    @Value("${aurapet.mqtt.client-id:AuraPet_Server}")
    private String clientIdBase;

    private MqttAsyncClient client;
    private MqttConnectOptions options;
    private String prefix;

    public MqttGateway(ApplicationEventPublisher events, ObjectMapper mapper) {
        this.events = events;
        this.mapper = mapper;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("[MQTT] Đã tắt (aurapet.mqtt.enabled=false)");
            return;
        }
        prefix = topicPrefix == null ? "" : topicPrefix.trim();
        while (prefix.endsWith("/")) prefix = prefix.substring(0, prefix.length() - 1);
        if (prefix.isEmpty() || prefix.contains("#") || prefix.contains("+")) {
            log.error("[MQTT] Chưa cấu hình MQTT_TOPIC_PREFIX hợp lệ trong .env — sẽ KHÔNG điều khiển được phần cứng. "
                    + "Hãy đặt cùng giá trị với TOPIC_PREFIX trong firmware/esp32_aurapet/secrets.h");
            return;
        }
        String cleanHost = host.trim().replaceFirst("^(tcp|ssl|mqtt|mqtts|wss?)://", "").replaceAll("/.*$", "");
        String url = (tls ? "ssl://" : "tcp://") + cleanHost + ":" + port;
        try {
            String clientId = clientIdBase + "_" + UUID.randomUUID().toString().substring(0, 6);
            client = new MqttAsyncClient(url, clientId, new MemoryPersistence());
            client.setCallback(this);
            options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            options.setMaxReconnectDelay(15000);
            if (username != null && !username.isBlank()) {
                options.setUserName(username);
                options.setPassword(password == null ? new char[0] : password.toCharArray());
            }
            log.info("[MQTT] Kết nối {} (prefix {}/...)", url, maskPrefix());
            connect();
        } catch (MqttException e) {
            log.error("[MQTT] Không khởi tạo được client: {}", e.getMessage());
        }
    }

    private void connect() {
        if (client == null || client.isConnected() || !connecting.compareAndSet(false, true)) return;
        try {
            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    connecting.set(false);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    connecting.set(false);
                    log.warn("[MQTT] Kết nối thất bại: {} (thử lại sau 15 giây)", exception.getMessage());
                }
            });
        } catch (MqttException e) {
            connecting.set(false);
            log.warn("[MQTT] Lỗi kết nối: {}", e.getMessage());
        }
    }

    /** Paho chỉ tự kết nối lại sau khi đã từng kết nối thành công; lần đầu thất bại thì ta tự thử lại. */
    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void ensureConnected() {
        if (client != null && !client.isConnected()) {
            connect();
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("[MQTT] Đã kết nối broker{}", reconnect ? " (kết nối lại)" : "");
        try {
            client.subscribe(new String[]{prefix + "/telemetry", prefix + "/log", prefix + "/status", prefix + "/vision",
                            prefix + "/water", prefix + "/litter"},
                    new int[]{0, 1, 1, 0, 0, 0});
        } catch (MqttException e) {
            log.error("[MQTT] Lỗi subscribe: {}", e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("[MQTT] Mất kết nối broker: {}", cause == null ? "?" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            if (!topic.startsWith(prefix + "/")) return;
            String channel = topic.substring(prefix.length() + 1);
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            events.publishEvent(new DeviceMessageEvent(channel, payload));
        } catch (Exception e) {
            log.warn("[MQTT] Lỗi xử lý gói tin {}: {}", topic, e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // không cần xử lý
    }

    @Override
    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public boolean publishCommand(Map<String, Object> command) {
        if (!isConnected()) return false;
        try {
            MqttMessage msg = new MqttMessage(mapper.writeValueAsBytes(command));
            msg.setQos(1);
            msg.setRetained(false);
            client.publish(prefix + "/command", msg);
            log.info("[MQTT] -> command {}", command);
            return true;
        } catch (Exception e) {
            log.warn("[MQTT] Gửi lệnh lỗi: {}", e.getMessage());
            return false;
        }
    }

    private String maskPrefix() {
        int slash = prefix.lastIndexOf('/');
        String tail = prefix.substring(slash + 1);
        String head = slash >= 0 ? prefix.substring(0, slash + 1) : "";
        return head + (tail.length() > 3 ? tail.substring(0, 3) + "***" : "***");
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (client != null) {
                if (client.isConnected()) client.disconnect(2000).waitForCompletion(2000);
                client.close();
            }
        } catch (Exception ignored) {
            // đang tắt server
        }
    }
}
