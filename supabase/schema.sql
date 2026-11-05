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
    source VARCHAR(40),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE public.feeding_logs ADD COLUMN IF NOT EXISTS source VARCHAR(40);

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

-- 7. CÀI ĐẶT MÁY CHO ĂN (DEVICE_SETTINGS) - chỉ 1 dòng id = 1
-- Trạng thái thời gian thực (cân, thùng chứa, mèo...) KHÔNG lưu DB mà giữ trong RAM của backend.
CREATE TABLE IF NOT EXISTS public.device_settings (
    id INTEGER PRIMARY KEY,
    target_gram INTEGER NOT NULL DEFAULT 30,
    auto_mode BOOLEAN NOT NULL DEFAULT FALSE,
    schedule_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    schedule_times VARCHAR(255) DEFAULT '07:00,12:00,18:00',
    feeder_pet_id UUID,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
-- Bơm nước là bơm tuần hoàn chạy liên tục (chỉ theo dõi mực nước), bỏ cài đặt "tự bơm khi mèo đến" của bản cũ
ALTER TABLE public.device_settings DROP COLUMN IF EXISTS pump_auto;
ALTER TABLE public.device_settings DROP COLUMN IF EXISTS pump_seconds;

-- Gỡ các bảng giả lập của phiên bản cũ (không còn dùng)
DROP TABLE IF EXISTS public.system_status;
DROP TABLE IF EXISTS public.system_food;
DROP TABLE IF EXISTS public.system_water;

-- Tùy chọn: Thiết lập Row Level Security (RLS) cho Supabase API
-- Lưu ý: Nếu truy cập qua Spring Boot Backend bằng service/postgres role thì RLS được bypass.
-- Nếu cho phép truy vấn trực tiếp từ Supabase JS/Flutter Client:
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.weight_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.feeding_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.litter_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_settings ENABLE ROW LEVEL SECURITY;

-- KHÔNG tạo policy đọc công khai: mọi truy cập đi qua backend Spring Boot (role postgres bỏ qua RLS),
-- nên bật RLS mà không có policy = chặn hoàn toàn truy cập trực tiếp bằng anon key của Supabase.
DROP POLICY IF EXISTS "Public Read Access Pets" ON public.pets;
DROP POLICY IF EXISTS "Public Read Access Alerts" ON public.alerts;
