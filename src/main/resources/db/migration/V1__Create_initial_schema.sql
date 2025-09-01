-- PDF Wrangler Initial Database Schema
-- Version: 1.0
-- Description: Creates tables for metadata storage and operation tracking

-- Table for storing uploaded file metadata
CREATE TABLE file_metadata (
    id BIGSERIAL PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_hash VARCHAR(64) NOT NULL, -- SHA-256 hash for duplicate detection
    upload_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    temp_file_path VARCHAR(500), -- Path to temporary file during processing
    status VARCHAR(50) NOT NULL DEFAULT 'UPLOADED', -- UPLOADED, PROCESSING, COMPLETED, FAILED, DELETED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table for tracking PDF operations
CREATE TABLE pdf_operations (
    id BIGSERIAL PRIMARY KEY,
    operation_type VARCHAR(50) NOT NULL, -- MERGE, SPLIT, CONVERT, WATERMARK, OCR, etc.
    operation_name VARCHAR(100) NOT NULL, -- Descriptive name of the operation
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    input_file_ids BIGINT[] NOT NULL, -- Array of file_metadata IDs as input
    output_file_ids BIGINT[], -- Array of file_metadata IDs as output (filled after completion)
    operation_parameters JSONB, -- JSON object storing operation-specific parameters
    progress_percentage INTEGER DEFAULT 0 CHECK (progress_percentage >= 0 AND progress_percentage <= 100),
    error_message TEXT, -- Error details if status is FAILED
    processing_time_ms BIGINT, -- Processing duration in milliseconds
    memory_usage_mb BIGINT, -- Peak memory usage during operation
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table for audit logging of security events
CREATE TABLE security_audit (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL, -- FILE_UPLOAD, VALIDATION_FAILED, MALICIOUS_CONTENT, PATH_TRAVERSAL, etc.
    severity VARCHAR(20) NOT NULL, -- INFO, WARNING, ERROR, CRITICAL
    source_ip VARCHAR(45), -- IPv4 or IPv6 address
    user_agent TEXT,
    filename VARCHAR(255),
    file_size BIGINT,
    content_type VARCHAR(100),
    threat_description TEXT, -- Description of security threat if applicable
    action_taken VARCHAR(100), -- Action taken in response to the event
    additional_data JSONB, -- Additional context data
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table for system statistics and monitoring
CREATE TABLE system_statistics (
    id BIGSERIAL PRIMARY KEY,
    metric_name VARCHAR(100) NOT NULL, -- TEMP_FILES_COUNT, TOTAL_OPERATIONS, DISK_USAGE, etc.
    metric_value DECIMAL(20,2) NOT NULL,
    metric_unit VARCHAR(20), -- MB, COUNT, PERCENT, MS, etc.
    category VARCHAR(50) NOT NULL, -- PERFORMANCE, STORAGE, OPERATIONS, SECURITY
    description TEXT,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table for temporary file tracking (backup for service layer)
CREATE TABLE temp_file_registry (
    id BIGSERIAL PRIMARY KEY,
    file_path VARCHAR(500) NOT NULL UNIQUE,
    purpose VARCHAR(100) NOT NULL, -- UPLOAD_TEMP, PROCESSING_TEMP, OUTPUT_TEMP
    associated_operation_id BIGINT REFERENCES pdf_operations(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    cleaned_up BOOLEAN NOT NULL DEFAULT FALSE
);

-- Indexes for performance optimization
CREATE INDEX idx_file_metadata_status ON file_metadata(status);
CREATE INDEX idx_file_metadata_upload_timestamp ON file_metadata(upload_timestamp);
CREATE INDEX idx_file_metadata_file_hash ON file_metadata(file_hash);

CREATE INDEX idx_pdf_operations_status ON pdf_operations(status);
CREATE INDEX idx_pdf_operations_operation_type ON pdf_operations(operation_type);
CREATE INDEX idx_pdf_operations_created_at ON pdf_operations(created_at);
CREATE INDEX idx_pdf_operations_input_files ON pdf_operations USING GIN(input_file_ids);

CREATE INDEX idx_security_audit_event_type ON security_audit(event_type);
CREATE INDEX idx_security_audit_severity ON security_audit(severity);
CREATE INDEX idx_security_audit_timestamp ON security_audit(timestamp);

CREATE INDEX idx_system_statistics_metric_name ON system_statistics(metric_name);
CREATE INDEX idx_system_statistics_recorded_at ON system_statistics(recorded_at);

CREATE INDEX idx_temp_file_registry_expires_at ON temp_file_registry(expires_at);
CREATE INDEX idx_temp_file_registry_cleaned_up ON temp_file_registry(cleaned_up);

-- Function to automatically update updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for automatic timestamp updates
CREATE TRIGGER update_file_metadata_updated_at 
    BEFORE UPDATE ON file_metadata 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_pdf_operations_updated_at 
    BEFORE UPDATE ON pdf_operations 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE file_metadata IS 'Stores metadata for all uploaded and generated files';
COMMENT ON TABLE pdf_operations IS 'Tracks all PDF processing operations and their status';
COMMENT ON TABLE security_audit IS 'Logs security events and potential threats';
COMMENT ON TABLE system_statistics IS 'Stores system performance and usage metrics';
COMMENT ON TABLE temp_file_registry IS 'Registry of temporary files for cleanup tracking';

COMMENT ON COLUMN file_metadata.file_hash IS 'SHA-256 hash for duplicate detection and integrity verification';
COMMENT ON COLUMN pdf_operations.operation_parameters IS 'JSON object containing operation-specific configuration';
COMMENT ON COLUMN pdf_operations.input_file_ids IS 'Array of input file metadata IDs';
COMMENT ON COLUMN pdf_operations.output_file_ids IS 'Array of output file metadata IDs';