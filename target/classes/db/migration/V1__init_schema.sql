CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  full_name VARCHAR(120) NOT NULL,
  role VARCHAR(20) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_users_email UNIQUE (email),
  CONSTRAINT ck_users_role CHECK (role IN ('USER','ADMIN'))
) ENGINE=InnoDB;

CREATE TABLE refresh_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);

CREATE TABLE accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  type VARCHAR(20) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  initial_balance DECIMAL(19,2) NOT NULL,
  current_balance DECIMAL(19,2) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT uk_accounts_user_name UNIQUE (user_id, name),
  CONSTRAINT ck_accounts_type CHECK (type IN ('SAVINGS','CURRENT','CASH_WALLET','DIGITAL_WALLET','CREDIT_CARD')),
  CONSTRAINT ck_accounts_initial CHECK (initial_balance >= 0)
) ENGINE=InnoDB;
CREATE INDEX idx_accounts_user ON accounts(user_id);

CREATE TABLE categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NULL,
  name VARCHAR(80) NOT NULL,
  type VARCHAR(10) NOT NULL,
  is_system BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT ck_categories_type CHECK (type IN ('INCOME','EXPENSE'))
) ENGINE=InnoDB;

CREATE TABLE transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  type VARCHAR(10) NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  description VARCHAR(255) NULL,
  notes VARCHAR(1000) NULL,
  txn_date DATE NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_txn_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_txn_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CONSTRAINT fk_txn_category FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT ck_txn_type CHECK (type IN ('INCOME','EXPENSE')),
  CONSTRAINT ck_txn_amount CHECK (amount > 0)
) ENGINE=InnoDB;
CREATE INDEX idx_txn_user_date ON transactions(user_id, txn_date);
CREATE INDEX idx_txn_account ON transactions(account_id);
CREATE INDEX idx_txn_category ON transactions(category_id);

CREATE TABLE transfers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  from_account_id BIGINT NOT NULL,
  to_account_id BIGINT NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  description VARCHAR(255) NULL,
  idempotency_key VARCHAR(64) NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_transfer_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_transfer_from FOREIGN KEY (from_account_id) REFERENCES accounts(id),
  CONSTRAINT fk_transfer_to FOREIGN KEY (to_account_id) REFERENCES accounts(id),
  CONSTRAINT uk_transfer_idem UNIQUE (user_id, idempotency_key),
  CONSTRAINT ck_transfer_amount CHECK (amount > 0),
  CONSTRAINT ck_transfer_diff CHECK (from_account_id <> to_account_id)
) ENGINE=InnoDB;
CREATE INDEX idx_transfer_user ON transfers(user_id, created_at);
