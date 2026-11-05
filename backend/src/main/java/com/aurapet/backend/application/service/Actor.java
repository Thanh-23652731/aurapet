package com.aurapet.backend.application.service;

import java.util.UUID;

/** Người dùng đang thao tác (rút gọn từ JWT) để kiểm tra quyền trong tầng service. */
public record Actor(UUID id, boolean admin) {
}
