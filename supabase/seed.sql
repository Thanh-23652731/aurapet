-- ===================================================================
-- AuraPet AIoT Smart Pet Care - Supabase Seed Data
-- ===================================================================

-- 1. TÀI KHOẢN MẪU (Mật khẩu đã mã hóa BCrypt chuẩn Spring Security)
-- Admin: admin@aurapet.com / Admin@123
-- User:  user@aurapet.com  / User@123
INSERT INTO public.users (id, name, email, password, role, created_at, updated_at)
VALUES 
    ('11111111-1111-1111-1111-111111111111', 'Alex Johnson (Admin)', 'admin@aurapet.com', '$2a$10$wT2B1tT84oVlYQhH0jG14eFhUfE5qN6V5fM3x4p7z9s8r1y3w2q1a', 'ROLE_ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('22222222-2222-2222-2222-222222222222', 'Sarah Davis', 'user@aurapet.com', '$2a$10$wT2B1tT84oVlYQhH0jG14eFhUfE5qN6V5fM3x4p7z9s8r1y3w2q1a', 'ROLE_USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

-- 2. DANH SÁCH THÚ CƯNG (LUNA, MILO, OLIVER)
INSERT INTO public.pets (id, owner_id, name, breed, age, gender, neutered, weight, image_url, created_at, updated_at)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'Luna', 'British Shorthair', 2, 'FEMALE', true, 4.25, 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?auto=format&fit=crop&w=600&q=80', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '11111111-1111-1111-1111-111111111111', 'Milo', 'Scottish Fold', 1, 'MALE', true, 3.80, 'https://images.unsplash.com/photo-1573865526739-10659fec78a5?auto=format&fit=crop&w=600&q=80', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', '22222222-2222-2222-2222-222222222222', 'Oliver', 'Maine Coon', 3, 'MALE', true, 7.65, 'https://images.unsplash.com/photo-1533738363-b7f9aef128ce?auto=format&fit=crop&w=600&q=80', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 3. THEO DÕI CÂN NẶNG
INSERT INTO public.weight_records (pet_id, weight, recorded_at)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 4.10, CURRENT_TIMESTAMP - INTERVAL '35 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 4.13, CURRENT_TIMESTAMP - INTERVAL '28 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 4.16, CURRENT_TIMESTAMP - INTERVAL '21 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 4.19, CURRENT_TIMESTAMP - INTERVAL '14 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 4.22, CURRENT_TIMESTAMP - INTERVAL '7 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 4.25, CURRENT_TIMESTAMP),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 3.60, CURRENT_TIMESTAMP - INTERVAL '35 days'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 3.64, CURRENT_TIMESTAMP - INTERVAL '28 days'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 3.68, CURRENT_TIMESTAMP - INTERVAL '21 days'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 3.72, CURRENT_TIMESTAMP - INTERVAL '14 days'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 3.76, CURRENT_TIMESTAMP - INTERVAL '7 days'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 3.80, CURRENT_TIMESTAMP);

-- 4. NHẬT KÝ CHO ĂN (FEEDING LOGS)
INSERT INTO public.feeding_logs (pet_id, food_gram, timestamp)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 45, CURRENT_TIMESTAMP - INTERVAL '4 hours'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 50, CURRENT_TIMESTAMP - INTERVAL '10 hours'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 40, CURRENT_TIMESTAMP - INTERVAL '4 hours');

-- 5. TRẠNG THÁI HỆ THỐNG PHẦN CỨNG
INSERT INTO public.system_status (id, feeder, water_pump, litter_cleaner, esp32, raspberry_pi, food_level_percent, water_level_percent, litter_waste_percent, last_updated)
VALUES ('00000000-0000-0000-0000-000000000001', false, false, false, 'ONLINE', 'ONLINE', 85, 92, 18, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET last_updated = CURRENT_TIMESTAMP;

-- 6. THIẾT BỊ IoT CHO ĂN (SYSTEM_FOOD)
INSERT INTO public.system_food (id, device_id, auto_mode, btn_led, led_green, led_red, machines, percel, calories, food_level_percent, scale_weight_gram, cat_detected, schedule_times, last_dispensed, updated_at)
VALUES ('system_food_default', 'AuraFeeder_ESP32_01', false, false, false, false, false, 10, 50, 85, 0, false, '07:00, 12:00, 18:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET updated_at = CURRENT_TIMESTAMP;

-- 7. THIẾT BỊ IoT BƠM NƯỚC (SYSTEM_WATER)
INSERT INTO public.system_water (id, device_id, auto_mode, btn_led, led_green, led_red, machines, percel, water_level_percent, cat_detected, last_pumped, updated_at)
VALUES ('system_water_default', 'AuraWater_ESP32_01', false, false, false, false, false, 50, 90, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET updated_at = CURRENT_TIMESTAMP;

-- 8. CẢNH BÁO MẪU
INSERT INTO public.alerts (id, type, level, message, created_at)
VALUES
    (gen_random_uuid(), 'FOOD_LOW', 'INFO', 'Lượng hạt trong khay dự trữ còn 85%, đủ dùng cho 5 ngày tới.', CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'WATER_STATUS', 'INFO', 'Hệ thống lọc tuần hoàn nước hoạt động ổn định.', CURRENT_TIMESTAMP);
