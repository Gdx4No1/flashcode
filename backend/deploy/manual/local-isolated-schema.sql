-- Fresh local database only. No teacher data, accounts, DROP, USE or ALTER.
CREATE TABLE app_user (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 nick_name VARCHAR(64), phone_number VARCHAR(64), open_id VARCHAR(64), avatar VARCHAR(255),
 UNIQUE KEY uk_phone(phone_number), UNIQUE KEY uk_open_id(open_id)
) ENGINE=InnoDB AUTO_INCREMENT=10000001;
CREATE TABLE app (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 app_name VARCHAR(255), app_desc VARCHAR(1000), app_type TINYINT,
 app_doc MEDIUMTEXT, app_screenshot VARCHAR(2048), preview_url VARCHAR(2048),
 deploy_status TINYINT NOT NULL DEFAULT 0, app_url VARCHAR(2048),
 create_time DATETIME, update_time DATETIME, deploy_time DATETIME,
 KEY idx_apps_user(user_id)
) ENGINE=InnoDB AUTO_INCREMENT=10000001;
CREATE TABLE chat_history (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, app_id BIGINT NOT NULL,
 msg_role TINYINT NOT NULL, content MEDIUMTEXT NOT NULL, chat_time DATETIME NOT NULL,
 KEY idx_chat_history_app_id_id(app_id,id)
) ENGINE=InnoDB;
CREATE TABLE sys_user (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 nick_name VARCHAR(64) NOT NULL, phone_number VARCHAR(64) NOT NULL,
 password VARCHAR(255) NOT NULL, identity VARCHAR(16) NOT NULL,
 remark VARCHAR(50), status VARCHAR(10) NOT NULL, UNIQUE KEY uk_phone(phone_number)
) ENGINE=InnoDB AUTO_INCREMENT=10000001;
CREATE TABLE sys_argument (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64),
 config_key VARCHAR(64), value VARCHAR(1024), remark VARCHAR(64),
 UNIQUE KEY idx_config_key(config_key)
) ENGINE=InnoDB;
CREATE TABLE sys_dictionary_type (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 type_key VARCHAR(64), value VARCHAR(64), remark VARCHAR(64), status TINYINT DEFAULT 1,
 UNIQUE KEY idx_type_key(type_key)
) ENGINE=InnoDB;
CREATE TABLE sys_dictionary_data (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 type_key VARCHAR(64), data_key VARCHAR(64), value VARCHAR(64), remark VARCHAR(64),
 sort INT DEFAULT 1, status TINYINT DEFAULT 1, KEY idx_type_key(type_key),
 UNIQUE KEY ui(type_key,data_key)
) ENGINE=InnoDB;
CREATE TABLE sys_region (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(16), parent_id BIGINT, parent_code VARCHAR(16), name VARCHAR(64),
 full_name VARCHAR(255), pinyin VARCHAR(255), level TINYINT,
 longitude DECIMAL(12,7), latitude DECIMAL(12,7)
) ENGINE=InnoDB;
