ALTER TABLE compute_node ADD COLUMN IF NOT EXISTS control_plane_id bigint;

CREATE INDEX IF NOT EXISTS idx_compute_node_control_plane_id
    ON compute_node(control_plane_id);

WITH platform AS (
    SELECT id
    FROM compute_node
    WHERE deleted = 0
      AND COALESCE((capabilities ->> 'platform')::boolean, false) IS TRUE
    ORDER BY id
    LIMIT 1
)
UPDATE compute_node
SET control_plane_id = CASE
    WHEN COALESCE((capabilities ->> 'platform')::boolean, false) IS TRUE THEN id
    ELSE COALESCE(control_plane_id, (SELECT id FROM platform))
END
WHERE deleted = 0
  AND control_plane_id IS NULL;

CREATE SEQUENCE IF NOT EXISTS control_plane_peer_id_seq;

CREATE TABLE IF NOT EXISTS control_plane_peer (
    id bigint PRIMARY KEY DEFAULT nextval('control_plane_peer_id_seq'),
    name varchar(128) NOT NULL,
    api_base_url varchar(512) NOT NULL,
    host varchar(128),
    peer_token varchar(128),
    status varchar(32) DEFAULT 'unknown',
    remote_platform_node_id bigint,
    last_sync_at timestamp without time zone,
    remark varchar(512),
    creator varchar(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64),
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    deleted smallint DEFAULT 0
);

ALTER SEQUENCE control_plane_peer_id_seq OWNED BY control_plane_peer.id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_control_plane_peer_api_base_url
    ON control_plane_peer(api_base_url)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_control_plane_peer_status
    ON control_plane_peer(status)
    WHERE deleted = 0;
