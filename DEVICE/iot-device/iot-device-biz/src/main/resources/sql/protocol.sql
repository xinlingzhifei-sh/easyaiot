-- Protocol management table used by ProtocolMapper.xml.
CREATE TABLE IF NOT EXISTS public.protocol (
    id BIGSERIAL NOT NULL,
    app_id VARCHAR(255) NULL,
    product_identification VARCHAR(100) NULL,
    protocol_name VARCHAR(255) NULL,
    protocol_identification VARCHAR(255) NULL,
    protocol_version VARCHAR(100) NULL,
    protocol_type VARCHAR(100) NULL,
    protocol_voice VARCHAR(100) NULL,
    class_name VARCHAR(255) NULL,
    file_path VARCHAR(500) NULL,
    content TEXT NULL,
    status VARCHAR(10) DEFAULT '0' NULL,
    create_by VARCHAR(64) NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    update_by VARCHAR(64) NULL,
    update_time TIMESTAMP NULL,
    remark VARCHAR(500) NULL,
    tenant_id BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT protocol_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_protocol_product_identification
    ON public.protocol(product_identification);

CREATE INDEX IF NOT EXISTS idx_protocol_protocol_identification
    ON public.protocol(protocol_identification);

CREATE INDEX IF NOT EXISTS idx_protocol_tenant_id
    ON public.protocol(tenant_id);
