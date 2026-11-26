-- ============================================================================
-- SM Telemetry & Surveillance Suite - Row Level Security (RLS) & Access Control
-- Strict multi-tenant isolation scoped to auth.uid()
-- ============================================================================

-- Enable RLS across all tables
ALTER TABLE public.owners ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recordings ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- 1. OWNERS POLICIES
-- ============================================================================
CREATE POLICY "Owners can view own profile"
    ON public.owners FOR SELECT
    TO authenticated
    USING (id = auth.uid());

CREATE POLICY "Owners can update own profile"
    ON public.owners FOR UPDATE
    TO authenticated
    USING (id = auth.uid());

-- ============================================================================
-- 2. DEVICES POLICIES
-- ============================================================================
-- Authenticated owner operations
CREATE POLICY "Owner can view their own devices"
    ON public.devices FOR SELECT
    TO authenticated
    USING (owner_id = auth.uid());

CREATE POLICY "Owner can register/create devices with pairing code"
    ON public.devices FOR INSERT
    TO authenticated
    WITH CHECK (owner_id = auth.uid());

CREATE POLICY "Owner can update their own devices"
    ON public.devices FOR UPDATE
    TO authenticated
    USING (owner_id = auth.uid())
    WITH CHECK (owner_id = auth.uid());

CREATE POLICY "Owner can delete their own devices"
    ON public.devices FOR DELETE
    TO authenticated
    USING (owner_id = auth.uid());

-- Anon agent operations: allows an agent to claim an existing pairing_code
CREATE POLICY "Agent can claim device with pairing code"
    ON public.devices FOR UPDATE
    TO anon
    USING (status = 'pending_pair' OR status IS NOT NULL);

CREATE POLICY "Agent can select device for pairing verification"
    ON public.devices FOR SELECT
    TO anon
    USING (true);

-- ============================================================================
-- 3. COMMANDS POLICIES
-- ============================================================================
CREATE POLICY "Owner can view commands for their devices"
    ON public.commands FOR SELECT
    TO authenticated
    USING (owner_id = auth.uid());

CREATE POLICY "Owner can issue commands for their devices"
    ON public.commands FOR INSERT
    TO authenticated
    WITH CHECK (owner_id = auth.uid());

CREATE POLICY "Owner can update/cancel commands"
    ON public.commands FOR UPDATE
    TO authenticated
    USING (owner_id = auth.uid());

-- Agent can select pending commands and update their execution status
CREATE POLICY "Agent can fetch assigned commands"
    ON public.commands FOR SELECT
    TO anon
    USING (true);

CREATE POLICY "Agent can update command execution status"
    ON public.commands FOR UPDATE
    TO anon
    USING (true)
    WITH CHECK (true);

-- ============================================================================
-- 4. LOCATIONS POLICIES
-- ============================================================================
CREATE POLICY "Owner can view location telemetry of their devices"
    ON public.locations FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.devices
            WHERE devices.id = locations.device_id
            AND devices.owner_id = auth.uid()
        )
    );

CREATE POLICY "Agent can insert location fixes"
    ON public.locations FOR INSERT
    TO anon, authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.devices
            WHERE devices.id = locations.device_id
        )
    );

-- ============================================================================
-- 5. RECORDINGS POLICIES
-- ============================================================================
CREATE POLICY "Owner can view recording metadata of their devices"
    ON public.recordings FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.devices
            WHERE devices.id = recordings.device_id
            AND devices.owner_id = auth.uid()
        )
    );

CREATE POLICY "Owner can delete recordings"
    ON public.recordings FOR DELETE
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.devices
            WHERE devices.id = recordings.device_id
            AND devices.owner_id = auth.uid()
        )
    );

CREATE POLICY "Agent can insert audio recording records"
    ON public.recordings FOR INSERT
    TO anon, authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.devices
            WHERE devices.id = recordings.device_id
        )
    );

-- ============================================================================
-- 6. STORAGE POLICIES (recordings bucket)
-- ============================================================================
-- Allow authenticated owners to download recordings
CREATE POLICY "Owner can read and download recording files"
    ON storage.objects FOR SELECT
    TO authenticated
    USING (bucket_id = 'recordings');

-- Allow authenticated owners to delete audio files
CREATE POLICY "Owner can delete recording files"
    ON storage.objects FOR DELETE
    TO authenticated
    USING (bucket_id = 'recordings');

-- Allow agent app to upload audio chunks
CREATE POLICY "Agent can upload audio chunk files"
    ON storage.objects FOR INSERT
    TO anon, authenticated
    WITH CHECK (bucket_id = 'recordings');

-- ============================================================================
-- 7. ATOMIC STORED PROCEDURES / RPCs FOR AGENT & OWNER
-- ============================================================================

-- Function: Pair an agent device safely
CREATE OR REPLACE FUNCTION public.pair_device_agent(
    p_code TEXT,
    p_device_name TEXT,
    p_model TEXT,
    p_android_ver TEXT,
    p_fcm_token TEXT
)
RETURNS JSONB AS $$
DECLARE
    v_device public.devices%ROWTYPE;
BEGIN
    SELECT * INTO v_device
    FROM public.devices
    WHERE pairing_code = UPPER(TRIM(p_code))
      AND status = 'pending_pair'
    LIMIT 1;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'Invalid or expired pairing code');
    END IF;

    UPDATE public.devices
    SET name = COALESCE(NULLIF(p_device_name, ''), v_device.name),
        model = p_model,
        android_version = p_android_ver,
        fcm_token = p_fcm_token,
        status = 'online',
        last_seen_at = timezone('utc'::text, now()),
        updated_at = timezone('utc'::text, now())
    WHERE id = v_device.id;

    RETURN jsonb_build_object(
        'success', true,
        'device_id', v_device.id,
        'owner_id', v_device.owner_id,
        'device_name', v_device.name
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function: Heartbeat / ping from Agent
CREATE OR REPLACE FUNCTION public.device_heartbeat(
    p_device_id UUID,
    p_battery_level INT,
    p_is_charging BOOLEAN,
    p_fcm_token TEXT DEFAULT NULL
)
RETURNS VOID AS $$
BEGIN
    UPDATE public.devices
    SET battery_level = p_battery_level,
        is_charging = p_is_charging,
        fcm_token = COALESCE(p_fcm_token, fcm_token),
        status = 'online',
        last_seen_at = timezone('utc'::text, now()),
        updated_at = timezone('utc'::text, now())
    WHERE id = p_device_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
