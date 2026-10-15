-- ===================================================================
-- AuraPet AIoT Smart Pet Care - Supabase PostgreSQL Schema
-- Chạy script này trong Supabase Dashboard -> SQL Editor
-- ===================================================================

-- Kích hoạt tiện ích mở rộng UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. BẢNG NGƯỜI DÙNG (USERS)
CREATE TABLE IF NOT EXISTS public.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON public.users(email);

-- 2. BẢNG THÚ CƯNG (PETS)
CREATE TABLE IF NOT EXISTS public.pets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    breed VARCHAR(255) NOT NULL,
    age INTEGER NOT NULL DEFAULT 1,
    gender VARCHAR(20) NOT NULL DEFAULT 'MALE',
    neutered BOOLEAN NOT NULL DEFAULT FALSE,
    weight DOUBLE PRECISION NOT NULL DEFAULT 4.0,
    image_url VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pets_owner_id ON public.pets(owner_id);

-- 3. BẢNG LỊCH SỬ CÂN NẶNG (WEIGHT_RECORDS)
CREATE TABLE IF NOT EXISTS public.weight_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pet_id UUID NOT NULL REFERENCES public.pets(id) ON DELETE CASCADE,
    weight DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_weight_records_pet_id ON public.weight_records(pet_id);
CREATE INDEX IF NOT EXISTS idx_weight_records_recorded_at ON public.weight_records(recorded_at DESC);

-- 4. BẢNG NHẬT KÝ CHO ĂN (FEEDING_LOGS)
CREATE TABLE IF NOT EXISTS public.feeding_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pet_id UUID NOT NULL REFERENCES public.pets(id) ON DELETE CASCADE,
    food_gram INTEGER NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_feeding_logs_pet_id ON public.feeding_logs(pet_id);
CREATE INDEX IF NOT EXISTS idx_feeding_logs_timestamp ON public.feeding_logs(timestamp DESC);

-- 5. BẢNG NHẬT KÝ KHAY VỆ SINH & AI DIAGNOSTICS (LITTER_LOGS)
CREATE TABLE IF NOT EXISTS public.litter_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pet_id UUID NOT NULL REFERENCES public.pets(id) ON DELETE CASCADE,
    result VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    image_url VARCHAR(1024),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_litter_logs_pet_id ON public.litter_logs(pet_id);

-- 6. BẢNG CẢNH BÁO HỆ THỐNG (ALERTS)
CREATE TABLE IF NOT EXISTS public.alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(50) NOT NULL,
    level VARCHAR(50) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alerts_created_at ON public.alerts(created_at DESC);

-- 7. BẢNG TRẠNG THÁI HỆ THỐNG PHẦN CỨNG (SYSTEM_STATUS)
CREATE TABLE IF NOT EXISTS public.system_status (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feeder BOOLEAN NOT NULL DEFAULT FALSE,
    water_pump BOOLEAN NOT NULL DEFAULT FALSE,
    litter_cleaner BOOLEAN NOT NULL DEFAULT FALSE,
    esp32 VARCHAR(50) NOT NULL DEFAULT 'ONLINE',
    raspberry_pi VARCHAR(50) NOT NULL DEFAULT 'ONLINE',
    food_level_percent INTEGER DEFAULT 80,
    water_level_percent INTEGER DEFAULT 75,
    litter_waste_percent INTEGER DEFAULT 20,
    last_updated TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 8. BẢNG ĐIỀU KHIỂN MÁY CHO ĂN IoT (SYSTEM_FOOD)
CREATE TABLE IF NOT EXISTS public.system_food (
    id VARCHAR(36) PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL UNIQUE,
    auto_mode BOOLEAN NOT NULL DEFAULT FALSE,
    btn_led BOOLEAN NOT NULL DEFAULT FALSE,
    led_green BOOLEAN NOT NULL DEFAULT FALSE,
    led_red BOOLEAN NOT NULL DEFAULT FALSE,
    machines BOOLEAN NOT NULL DEFAULT FALSE,
    percel INTEGER NOT NULL DEFAULT 10,
    calories INTEGER NOT NULL DEFAULT 50,
    food_level_percent INTEGER NOT NULL DEFAULT 85,
    scale_weight_gram INTEGER DEFAULT 0,
    cat_detected BOOLEAN DEFAULT FALSE,
    schedule_times VARCHAR(255) DEFAULT '07:00, 12:00, 18:00',
    last_dispensed TIMESTAMPTZ,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 9. BẢNG ĐIỀU KHIỂN MÁY BƠM NƯỚC TỰ ĐỘNG IoT (SYSTEM_WATER)
CREATE TABLE IF NOT EXISTS public.system_water (
    id VARCHAR(36) PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL UNIQUE,
    auto_mode BOOLEAN NOT NULL DEFAULT FALSE,
    btn_led BOOLEAN NOT NULL DEFAULT FALSE,
    led_green BOOLEAN NOT NULL DEFAULT FALSE,
    led_red BOOLEAN NOT NULL DEFAULT FALSE,
    machines BOOLEAN NOT NULL DEFAULT FALSE,
    percel INTEGER NOT NULL DEFAULT 50,
    water_level_percent INTEGER NOT NULL DEFAULT 90,
    cat_detected BOOLEAN DEFAULT FALSE,
    last_pumped TIMESTAMPTZ,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Tùy chọn: Thiết lập Row Level Security (RLS) cho Supabase API
-- Lưu ý: Nếu truy cập qua Spring Boot Backend bằng service/postgres role thì RLS được bypass.
-- Nếu cho phép truy vấn trực tiếp từ Supabase JS/Flutter Client:
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.weight_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.feeding_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.litter_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.system_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.system_food ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.system_water ENABLE ROW LEVEL SECURITY;

-- Chính sách đọc công khai dữ liệu hệ thống cơ bản cho Client
CREATE POLICY "Public Read Access Pets" ON public.pets FOR SELECT USING (true);
CREATE POLICY "Public Read Access SystemStatus" ON public.system_status FOR SELECT USING (true);
CREATE POLICY "Public Read Access SystemFood" ON public.system_food FOR SELECT USING (true);
CREATE POLICY "Public Read Access SystemWater" ON public.system_water FOR SELECT USING (true);
CREATE POLICY "Public Read Access Alerts" ON public.alerts FOR SELECT USING (true);
