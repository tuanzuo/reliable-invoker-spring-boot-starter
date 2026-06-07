CREATE TABLE IF NOT EXISTS reliable_invocation_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    serial_no VARCHAR(64) NOT NULL UNIQUE,
    scene VARCHAR(64) NOT NULL,
    bean_name VARCHAR(128) NOT NULL,
    method_name VARCHAR(128) NOT NULL,
    params TEXT,
    status TINYINT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    retry_delay INT NOT NULL DEFAULT 5000,
    execute_time DATETIME,
    remark VARCHAR(512),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
