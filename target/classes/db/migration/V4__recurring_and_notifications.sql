CREATE TABLE recurring_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  type VARCHAR(10) NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  description VARCHAR(255) NULL,
  frequency VARCHAR(10) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NULL,
  next_execution_date DATE NOT NULL,
  anchor_day INT NOT NULL,
  status VARCHAR(12) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_rec_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_rec_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CONSTRAINT fk_rec_category FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT ck_rec_type CHECK (type IN ('INCOME','EXPENSE')),
  CONSTRAINT ck_rec_freq CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY')),
  CONSTRAINT ck_rec_status CHECK (status IN ('ACTIVE','PAUSED','CANCELLED','ENDED')),
  CONSTRAINT ck_rec_amount CHECK (amount > 0),
  CONSTRAINT ck_rec_dates CHECK (end_date IS NULL OR end_date >= start_date)
) ENGINE=InnoDB;
CREATE INDEX idx_rec_due ON recurring_transactions(status, next_execution_date);
CREATE INDEX idx_rec_user ON recurring_transactions(user_id);

-- One row per (rule, scheduled date). The unique key makes execution idempotent even across several app instances.
CREATE TABLE recurring_execution_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  recurring_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  scheduled_date DATE NOT NULL,
  status VARCHAR(10) NOT NULL,
  transaction_id BIGINT NULL,
  message VARCHAR(255) NULL,
  executed_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_exec_rec FOREIGN KEY (recurring_id) REFERENCES recurring_transactions(id) ON DELETE CASCADE,
  CONSTRAINT fk_exec_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT uk_exec_rec_date UNIQUE (recurring_id, scheduled_date),
  CONSTRAINT ck_exec_status CHECK (status IN ('SUCCESS','FAILED'))
) ENGINE=InnoDB;

CREATE TABLE notifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type VARCHAR(30) NOT NULL,
  title VARCHAR(120) NOT NULL,
  message VARCHAR(500) NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL,
  dedupe_key VARCHAR(120) NULL,
  CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT uk_notif_dedupe UNIQUE (user_id, dedupe_key)
) ENGINE=InnoDB;
CREATE INDEX idx_notif_user ON notifications(user_id, is_read, created_at);
