CREATE TABLE budgets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  budget_month DATE NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_budget_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_budget_category FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT uk_budget_user_cat_month UNIQUE (user_id, category_id, budget_month),
  CONSTRAINT ck_budget_amount CHECK (amount > 0)
) ENGINE=InnoDB;
CREATE INDEX idx_budget_user_month ON budgets(user_id, budget_month);

CREATE TABLE savings_goals (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  target_amount DECIMAL(19,2) NOT NULL,
  saved_amount DECIMAL(19,2) NOT NULL DEFAULT 0,
  target_date DATE NULL,
  status VARCHAR(12) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  completed_at DATETIME(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT ck_goal_status CHECK (status IN ('ACTIVE','COMPLETED')),
  CONSTRAINT ck_goal_target CHECK (target_amount > 0),
  CONSTRAINT ck_goal_saved CHECK (saved_amount >= 0 AND saved_amount <= target_amount)
) ENGINE=InnoDB;
CREATE INDEX idx_goal_user ON savings_goals(user_id, status);

CREATE TABLE savings_contributions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  goal_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  contribution_date DATE NOT NULL,
  note VARCHAR(255) NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_contrib_goal FOREIGN KEY (goal_id) REFERENCES savings_goals(id) ON DELETE CASCADE,
  CONSTRAINT fk_contrib_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT ck_contrib_amount CHECK (amount > 0)
) ENGINE=InnoDB;
CREATE INDEX idx_contrib_goal ON savings_contributions(goal_id);
