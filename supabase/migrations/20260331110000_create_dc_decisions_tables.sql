-- Migration: crea tabelle DecisionKeeper e relative RLS policies
-- Tabelle: dc_decisions, dc_logs

-- ── dc_decisions ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dc_decisions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID,
    title TEXT NOT NULL,
    description TEXT,
    image TEXT,
    categories JSONB DEFAULT '[]',
    option1_text TEXT NOT NULL,
    option1_points INTEGER NOT NULL,
    option2_text TEXT NOT NULL,
    option2_points INTEGER NOT NULL,
    option3_text TEXT NOT NULL,
    option3_points INTEGER NOT NULL,
    reminder_enabled BOOLEAN DEFAULT false,
    reminder_frequency TEXT DEFAULT 'daily',
    reminder_frequency_details JSONB DEFAULT '{}',
    total_points INTEGER DEFAULT 0,
    status TEXT DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dc_decisions_user ON dc_decisions(user_id);
CREATE INDEX IF NOT EXISTS idx_dc_decisions_status ON dc_decisions(status);

ALTER TABLE dc_decisions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "dc_decisions_select" ON dc_decisions;
DROP POLICY IF EXISTS "dc_decisions_insert" ON dc_decisions;
DROP POLICY IF EXISTS "dc_decisions_update" ON dc_decisions;
DROP POLICY IF EXISTS "dc_decisions_delete" ON dc_decisions;

CREATE POLICY "dc_decisions_select" ON dc_decisions
    FOR SELECT USING (auth.uid() IS NOT NULL);

CREATE POLICY "dc_decisions_insert" ON dc_decisions
    FOR INSERT WITH CHECK (auth.uid() IS NOT NULL);

CREATE POLICY "dc_decisions_update" ON dc_decisions
    FOR UPDATE USING (auth.uid() IS NOT NULL);

CREATE POLICY "dc_decisions_delete" ON dc_decisions
    FOR DELETE USING (auth.uid() IS NOT NULL);

-- ── dc_logs ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dc_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    decision_id UUID REFERENCES dc_decisions(id) ON DELETE CASCADE,
    selected_option INTEGER NOT NULL,
    points_earned INTEGER NOT NULL,
    notes TEXT,
    logged_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dc_logs_decision ON dc_logs(decision_id);
CREATE INDEX IF NOT EXISTS idx_dc_logs_date ON dc_logs(logged_at);

ALTER TABLE dc_logs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "dc_logs_select" ON dc_logs;
DROP POLICY IF EXISTS "dc_logs_insert" ON dc_logs;
DROP POLICY IF EXISTS "dc_logs_delete" ON dc_logs;

CREATE POLICY "dc_logs_select" ON dc_logs
    FOR SELECT USING (auth.uid() IS NOT NULL);

CREATE POLICY "dc_logs_insert" ON dc_logs
    FOR INSERT WITH CHECK (auth.uid() IS NOT NULL);

CREATE POLICY "dc_logs_delete" ON dc_logs
    FOR DELETE USING (auth.uid() IS NOT NULL);

-- ── cm_apps: registra DecisionKeeper nel launcher ─────────────────────────────
INSERT INTO cm_apps (title, description, html_file, score_query, color, active, riservato)
VALUES (
    'DecisionKeeper',
    'Traccia decisioni personali con punteggi e focus mode',
    'decisions.html',
    'SELECT COUNT(*) FROM dc_logs',
    '#7B2FBE',
    true,
    true
)
ON CONFLICT (html_file) DO UPDATE SET
    title     = EXCLUDED.title,
    color     = EXCLUDED.color,
    riservato = EXCLUDED.riservato;
