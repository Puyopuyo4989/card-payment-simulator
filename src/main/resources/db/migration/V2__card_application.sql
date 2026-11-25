-- 申込管理の表（specs/003-card-application/data-model.md 1）。
-- 申込と状態の変更履歴を、2つの表に分ける（research.md R2）。
-- 値の制約（CHECK・長さ）は、コードの誤りで変な値が入るのを止める最後の守り。
-- 入力のチェックはアプリが正で、状態の遷移（どこからどこへ）は DB に書かない（research.md R3）。
-- このファイルは、main に入ってデプロイしたら書き換えない。直すときは V3 を足す（ADR 0011）。

-- 申込番号の連番。'A' ＋ 8桁（例：A00000001）にする（research.md R7）
CREATE SEQUENCE card_application_number_seq;

-- 申込。登録したあとは、状態・更新日時・version だけが変わる（spec FR-006）。カード番号の列は作らない（spec FR-021）
CREATE TABLE card_application (
    application_number varchar(9) PRIMARY KEY
        DEFAULT ('A' || lpad(nextval('card_application_number_seq')::text, 8, '0')),
    name_kanji         varchar(50)   NOT NULL,
    name_kana          varchar(50)   NOT NULL,
    birth_date         date          NOT NULL,
    -- 年収（万円）。金額なので numeric で持つ（constitution 原則III、research.md R5）
    annual_income      numeric(4, 0) NOT NULL CHECK (annual_income BETWEEN 0 AND 9999),
    employer_name      varchar(100)  NOT NULL,
    phone_number       varchar(11)   NOT NULL CHECK (phone_number ~ '^0[0-9]{9,10}$'),
    status             varchar(20)   NOT NULL
        CHECK (status IN ('APPLIED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED')),
    applied_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    -- 楽観ロック。状態を変えるたびに1増える（research.md R4）
    version            integer       NOT NULL DEFAULT 0
);

-- 連番を申込番号の列に結び付ける。テストの TRUNCATE … RESTART IDENTITY で1に戻すため（research.md R6）
ALTER SEQUENCE card_application_number_seq OWNED BY card_application.application_number;

-- 状態の変更履歴。足すだけで、直さない・消さない（spec FR-018）
CREATE TABLE card_application_status_history (
    id                 bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_number varchar(9)    NOT NULL REFERENCES card_application (application_number),
    changed_at         timestamptz   NOT NULL,
    from_status        varchar(20)   NOT NULL
        CHECK (from_status IN ('APPLIED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED')),
    to_status          varchar(20)   NOT NULL
        CHECK (to_status IN ('APPLIED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED')),
    -- 担当者名は自己申告（ADR 0010、spec FR-016）
    staff_name         varchar(50)   NOT NULL,
    -- 否決の理由。否決の行だけにあり、ほかの行は NULL（spec FR-017）
    rejection_reason   varchar(500),
    CHECK ((to_status = 'REJECTED') = (rejection_reason IS NOT NULL))
);

-- 詳細の画面で、申込ごとの履歴を読むための索引
CREATE INDEX card_application_status_history_application_number_idx
    ON card_application_status_history (application_number);
