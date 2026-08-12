BEGIN;

DO $$
BEGIN
  IF to_regclass('public.nvr') IS NULL THEN
    RAISE EXCEPTION 'required VIDEO nvr table does not exist';
  END IF;
END
$$;

ALTER TABLE public.nvr
  ADD COLUMN IF NOT EXISTS rtsp_template TEXT;

ALTER TABLE public.nvr
  ADD COLUMN IF NOT EXISTS rtsp_port SMALLINT;

COMMENT ON COLUMN public.nvr.rtsp_template IS '自定义 RTSP 路径模板';
COMMENT ON COLUMN public.nvr.rtsp_port IS 'RTSP 端口，默认 554';

COMMIT;
