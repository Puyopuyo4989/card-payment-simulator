<!--
Sync Impact Report
[1.1.0 — 2026-11-18]
- Version change: 1.0.0 → 1.1.0（MINOR：原則IVの対象を個人情報まで広げたため）
- Modified principles:
  - IV. カード情報はダミーだけを使う（NON-NEGOTIABLE）
    → IV. カード情報と個人情報はダミーだけを使う（NON-NEGOTIABLE）
    - 追加：本物の個人情報（氏名・生年月日・電話番号・勤務先・年収・担当者名など）を
      扱わない（MUST NOT）。コード・テストデータ・ログ・ドキュメントに入れない
    - 追加：個人情報はダミーだけを使う（MUST）
    - 理由に追加：申込管理で個人情報の形をしたデータを扱うため
    - カード番号についての既存の内容は変更なし
- Added sections: none
- Removed sections: none
- Templates: not modified (plan/spec/tasks templates read this file at runtime)
- Follow-up TODOs:
  - 既存の仕様・計画・テストデータに本物の個人情報が入っていないか確認する
    （Governance「原則を変えたときは、影響する仕様・設計・ADRも直す」）

[1.0.0 — 2026-11-17]
- Version change: (template, unversioned) → 1.0.0
- Modified principles: none (initial adoption). Template had 5 principle slots; 9 adopted per user input.
  - I. TDD必須（NON-NEGOTIABLE）
  - II. カバレッジ90%以上（行・分岐）
  - III. 金額はBigDecimalで扱う（NON-NEGOTIABLE）
  - IV. カード情報はダミーだけを使う（NON-NEGOTIABLE）
  - V. 構成：APIでつなぎ、1リポジトリ・1jar
  - VI. 変更はPRを通す
  - VII. 秘密情報をコミットしない
  - VIII. シンプルさ：MVPの範囲外は作らない
  - IX. ドキュメントは日本語
- Added sections: 技術とデプロイの制約 / 開発ワークフローと人とAIの分担 / Governance
- Removed sections: none
- Pre-adoption revision (version kept at 1.0.0 per user):
  - 技術とデプロイの制約：具体的な技術の列挙を削除し「技術の選定はADRに従う」のみに
  - 原則IV：NON-NEGOTIABLE に変更
  - 原則II：テスト1,000件はバックエンドとフロントエンドの合計件数と明記
- Templates: not modified (plan/spec/tasks templates read this file at runtime)
- Follow-up TODOs:
  - カバレッジ計測から除外してよい対象をADRで定める（原則II）
  - 端数処理のルールを各機能の仕様に明記する（原則III）
-->

# card-payment-simulator Constitution

クレジットカードの支払シミュレーション（分割払い・リボ払い）とカード申込管理のMVP。
本書は、仕様・設計・実装・レビューのすべてが従う原則を定める。

## Core Principles

### I. TDD必須（NON-NEGOTIABLE）

- テストより先に本番コードを書いてはならない（MUST NOT）。
- 失敗するテストを書く（red）→ 通る最小のコードを書く（green）→ 整える（refactor）の順で進める。
- バグの修正も、そのバグを再現する失敗するテストを先に書いてから行う（MUST）。

理由：AIが書くコードの正しさを、人が読む前に機械的に確かめられる状態を保つため。

### II. カバレッジ90%以上（行・分岐）

- 行カバレッジと分岐カバレッジは、どちらも90%以上でなければならない（MUST）。
  バックエンド・フロントエンドのそれぞれに適用する。
- 90%未満を認めるのは、テストが1,000件以上（バックエンドとフロントエンドの合計件数）あり、
  かつ理由を記録した場合だけとする。
  理由はADRに記録する。
- 計測から除外してよい対象はADRで定める。ADRにない対象を除外してはならない（MUST NOT）。

理由：品質基準を数値で示し、ビルドとCIで自動的に確かめられるようにするため。

### III. 金額はBigDecimalで扱う（NON-NEGOTIABLE）

- 金額は`BigDecimal`で扱わなければならない（MUST）。金額に`double`や`float`を使ってはならない
  （MUST NOT）。
- 端数処理のルール（丸め方・桁数・どの段階で丸めるか・端数をどこに寄せるか）は、
  各機能の仕様（spec）に明記しなければならない（MUST）。実装は仕様に書かれたとおりに
  丸めを明示し、暗黙の丸めに頼らない。

理由：浮動小数点の誤差や丸めの不一致は、利用者に見せる金額の誤りに直結し、後から気づきにくい。

### IV. カード情報と個人情報はダミーだけを使う（NON-NEGOTIABLE）

- 本物のカード番号を扱ってはならない（MUST NOT）。コード・テストデータ・ログ・ドキュメントの
  どこにも入れない。
- カード番号はダミーだけを使う（MUST）。
- 画面にカード番号を表示するときは、マスクしなければならない（MUST）。
- 本物の個人情報（氏名・生年月日・電話番号・勤務先・年収・担当者名など）を扱ってはならない
  （MUST NOT）。コード・テストデータ・ログ・ドキュメントのどこにも入れない。
- 個人情報はダミーだけを使う（MUST）。

理由：シミュレーターであっても、カード情報を扱う画面は情報漏えいの入口になりうる。
また、申込管理で個人情報の形をしたデータを扱うため、個人情報も対象に含める。
リポジトリはPublicなので、一度コミットした情報は回収できない。

### V. 構成：APIでつなぎ、1リポジトリ・1jar

- フロントエンドとバックエンドは、APIだけでやり取りしなければならない（MUST）。
- リポジトリは1つ、ビルド成果物も1つ（jar）とする（MUST）。

理由：境界をAPIに限ることで、それぞれを独立してテストでき、配布とデプロイは1つにまとまる。

### VI. 変更はPRを通す

- mainへの変更は、必ずPRを通さなければならない（MUST）。mainへの直接pushはしない。
- PRを出す前に、ローカルでレビューと確認（テスト・カバレッジなど）を行わなければならない（MUST）。

理由：ローカルとサーバー側の両方で確かめることで、片方をすり抜けたミスをもう片方で止める。

### VII. 秘密情報をコミットしない

- APIキー・パスワード・認証情報などの秘密情報をコミットしてはならない（MUST NOT）。

理由：リポジトリはPublicなので、一度コミットした秘密情報は漏えいしたものとして扱うしかない。

### VIII. シンプルさ：MVPの範囲外は作らない

- MVPの範囲外のものは作らない（MUST NOT）。
- 範囲は、各機能の仕様（spec）で定める。仕様にないものは範囲外とする。

理由：限られた期間で、説明できる品質のMVPを確実に届けるため。

### IX. ドキュメントは日本語

- ドキュメント（仕様・設計・ADR・READMEなど）は日本語で書く（MUST）。

理由：説明する相手とレビューする人が、そのまま読めるようにするため。

## 技術とデプロイの制約

- 技術の選定はADRに従う。

## 開発ワークフローと人とAIの分担

- **ツールの使い分け**：SpecKitで「何を作るか」を決め（constitution → specify → clarify →
  plan → tasks → analyze）、Superpowersで「どう作るか」を守らせる（TDD・デバッグ・レビュー・
  完了前の検証）。
- **判断は人、生成はAI**：要件・基準・選定理由は人が決める。文書やコードの下書きはAIに任せる。
- **権限が要る操作・元に戻せない操作は人が行う**：GitHubの設定、ログイン、AWSの設定など。
- **検証は人が行い、記録に残す**：AIの出力の確認結果は`docs/ai-review-log.md`に残す。

## Governance

- 本書は、プロジェクトの他のすべての慣習・手順より優先する。仕様・設計・ADRと矛盾する場合は、
  本書を正とし、矛盾した側を直す。
- **改定の手順**：`/speckit-constitution`で改定案を作り、PRで人がレビューして承認する。
  原則を変えたときは、影響する仕様・設計・ADRも直す。
- **バージョン**：セマンティックバージョニングに従う。
  - MAJOR：原則の削除や、互換性のない再定義
  - MINOR：原則・セクションの追加、指針の大きな拡充
  - PATCH：言い回しの修正、誤字、意味を変えない明確化
- **遵守の確認**：`/speckit-plan`のConstitution Checkと`/speckit-analyze`で、各機能が本書に
  従っているかを確かめる。PRのレビューでも確認する。

**Version**: 1.1.0 | **Ratified**: 2026-11-17 | **Last Amended**: 2026-11-18
