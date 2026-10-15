package com.aurapet.backend.infrastructure.firebase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class FirebaseAdminService {

    @Value("${aurapet.firebase.enabled:false}")
    private boolean firebaseEnabled;

    public void sendPushNotification(String fcmToken, String title, String body, Map<String, String> data) {
        if (!firebaseEnabled) {
            log.info("[Firebase Stub] Push notification simulated: title='{}', body='{}', token='{}'", title, body, fcmToken);
            return;
        }
        log.info("Sending FCM push notification to token: {}", fcmToken);
    }

    public boolean verifyIdToken(String idToken) {
        if (!firebaseEnabled) {
            log.info("[Firebase Stub] Verifying mock ID token: {}", idToken != null ? idToken.substring(0, Math.min(10, idToken.length())) + "..." : "null");
            return true;
        }
        return true;
    }
}
