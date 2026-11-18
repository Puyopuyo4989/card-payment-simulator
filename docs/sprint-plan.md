# スプリント計画（2026/11/16〜11/27）

## スプリントゴール
クレジットカードの支払シミュレーション（分割払い・リボ払い）とカード申込管理のMVPを、
品質基準（カバレッジ90%・TDD・hooks）を守った状態でAWS EC2にデプロイし、
11/27（金）17:00に上司へ説明する。

## 前提
- 稼働：1日8時間（11/16は午後のみ）
- **11/23（月・勤労感謝の日）は作業しない（11/17に決定）**。遅れは残業と翌日の巻き取りで取り戻し、それでも足りなければ機能を減らす
- 開発環境：GitHub Codespaces
- 技術スタック：Spring Boot＋React（TypeScript）、ビルドはMaven、リポジトリは1つ、ビルド成果物も1つ（jar）
- デプロイ先：AWS EC2（前回の課題のAWSアカウントを使用）。アプリとPostgreSQLをdocker composeで動かす
- デプロイ方法：GitHub Actionsでイメージを作成 → GHCRに保存 → OIDCとSSMでEC2に反映（SSHのポートを開けない、AWSの鍵をGitHubに保存しない）
- 説明資料：PowerPoint

## デイリー計画

| 日付 | 午前 | 午後 | 完了の条件 |
|---|---|---|---|
| 11/16（月）Day1 | — | この計画を確定する。リポジトリ・Codespaces・Claude Code・Superpowers・SpecKitを準備する。**mainブランチの保護設定**と**PreToolUseのhook**を入れる | PreToolUseのhookが、わざと頼んだ危険なコマンドを止める。mainへの直接pushが拒否される |
| 11/17（火）Day2 | constitution（v1.0.0）。3機能の仕様（specify・clarify） | ADR（0001〜0012）。分割払いのplan・tasks・analyze | constitution・3機能の仕様・ADR・分割払いの設計とタスクがmainに入っている |
| 11/18（水）Day3 | constitutionの改定（v1.1.0：個人情報もダミーだけ）。`.gitignore`に一時ファイル（`memo-*.md`・`*-questions.md`）を追加。devcontainer.json（Java 25・Node.js 24の固定、Claude CodeとSuperpowersの自動インストール）。分割払いのtasksのPhase 1（Mavenの土台、フロントを同梱した1つのjarで見出しを表示） | **ADR 0006**（カバレッジの計測ツールと除外対象。別のPR）→ カバレッジの閾値。CI（GitHub Actions）とRulesetのCI必須。**lefthook**（pre-commit・commit-msg）。**PostToolUseのhook** | 1つのjarで画面が開く。CIが通る。わざとカバレッジを下げるとCIが落ちる。各hookがわざとした違反を止める |
| 11/19（木）Day4 | PostgreSQL（compose）＋Flyway、Dockerfile | **EC2の用意とCD**（OIDC＋SSM、セキュリティグループでIP制限、予算アラート）。見出しの画面を自動デプロイする | mainにマージするとEC2に自動でデプロイされ、ブラウザから画面が見える |
| 11/20（金）Day5 | **Stopのhook**、**pre-push**、**自作スキル①（金額計算のレビュー）・②（PR前の確認）**。分割払い：tasksのPhase 2〜3（US1） | 分割払い：tasksのPhase 4〜6（US2・US3・仕上げ）。PRを出してレビューする | 分割払いがEC2上の画面から使える。カバレッジ90%以上 |
| 11/23（月・祝） | **作業しない** | **作業しない** | — |
| 11/24（火）Day6 | リボ払い：`/speckit-plan`→`tasks`→`analyze`。計算ロジックとAPI（TDD） | リボ払い：画面。PRを出してレビューする | リボ払いがEC2上の画面から使える |
| 11/25（水）Day7 | 申込管理：`/speckit-plan`→`tasks`→`analyze`。登録と状態遷移、履歴、DB（TDD） | 申込管理：画面。PRを出してレビューする | 全機能（Mustだけ）がEC2上の画面から使える |
| 11/26（木）Day8 | **EC2（本番）で全体の動作確認**。カバレッジの総点検、`/security-review`、カード情報と個人情報の手動チェック、取りこぼしの対応 | 説明資料（PowerPoint）のドラフトを作る。デモの手順書を作る | 品質基準をすべて満たしている。資料のドラフトがある |
| 11/27（金）Day9 | 資料を仕上げる。振り返り（KPT） | 〜15:00 リハーサル（EC2上のデモ込み）、〜16:30 最終修正、**17:00 説明** | 説明完了 |

### 見積もりと余裕（11/17時点）
- 使える時間：7日（Day3〜Day9）× 8時間 ＝ 56時間
- 見積もりの合計：54時間（Day5とDay7は、見積もりの時点で8時間を超えている）
- 余裕は2時間。これまでの実績では1日あたり1〜1.5時間遅れているため、残業と翌日の巻き取りで吸収する

### 判断のタイミング
遅れの判断を先延ばしにしないため、次の時点で確認し、遅れていれば下の「削る順番」を発動するかを決める。
- **Day3（11/18）の終わり**：土台（1つのjarで画面、CI、カバレッジの閾値、lefthook）が終わっているか
- **Day5（11/20）の終わり**：分割払いが画面まで終わっているか
- **Day7（11/25）の終わり**：申込管理の画面が終わっているか

## Skills・Hooksの導入計画
考え方：
- AIに作業させる前に、ミスを止める仕組みを置く
- 自動チェックは、チェックする対象（コードやビルド）ができてから足す
- 1つ入れるたびに、わざと違反して止まるかを確かめる
- 止めすぎていないか（ふだんの開発操作まで止めていないか）も確かめる

| 日 | Claude Code hooks | git hooks・GitHub設定 | スキル |
|---|---|---|---|
| Day1 | PreToolUse：危険なコマンド（`rm -rf`、`git push --force`など）と`.env`の読み取りをブロック | mainブランチの保護設定（Day2以降はPRで進める） | Superpowers・SpecKitを導入（既製品） |
| Day3 | PostToolUse：編集後に自動でフォーマットと関連テスト。PreToolUseの許可リスト見直し（`rm -rf target`などが邪魔になった場合） | lefthook：pre-commit（フォーマット・lint・シークレット検知）、commit-msg（メッセージの形式チェック）。RulesetにCI必須を追加 | devcontainer.jsonでClaude CodeとSuperpowersを自動で入れる（環境の再現性） |
| Day5 | Stop：作業を終える前にテストとカバレッジを確認 | pre-push：テスト全件とカバレッジ90% | 自作①：金額計算のレビュー（ADR 0005が照合先）。自作②：PR前の確認（テスト・カバレッジ・`/speckit-analyze`・レビューをまとめて実行）。時間が足りなければ①はDay6へ |
| Day6〜 | 運用で困ったら追加・調整 | 同左 | 同左 |

※ 自作スキル③（カード情報のチェック）は、デプロイ作業を追加したため見送り。Day8に手動で確認する。

## ツールの使い分け
- **SpecKit：何を作るかを決める**（constitution → specify → clarify → plan → tasks → analyze）
- **Superpowers：どう作るかを守らせる**（test-driven-development・systematic-debugging・requesting-code-review・verification-before-completion）
- 実装では、tasks.mdのタスクを**Phase（ストーリー）単位**でSuperpowersのTDDで進め、人は各PhaseのCheckpointで確認する
- Superpowersのbrainstormingとwriting-plansは使わない（SpecKitと成果物が二重にならないようにするため）
- SpecKitは`.specify/feature.json`（gitの管理外）で作業中の機能を決める。機能を切り替えるとき、Codespaceを作り直したときは、自分で向け直す

## 人とAIの分担
- **判断は人**：要件・基準・選定理由は、説明責任を持つ人が決める
- **生成はAI**：文書やコードの下書きはClaude Codeに任せる
- **権限が要る操作・元に戻せない操作は人**：GitHubの設定、mainの保護、ログイン、AWSの設定（IAM・EC2・予算アラート）など
- **検証は人が行い、記録に残す**：一言 → レビュー → 一行メモ
- Claude Codeが自分自身を縛るhookは、中身の確認と止まるかのテストを必ず人が行う
- Claude Codeは権限確認のモードをデフォルトのまま使い、全部自動で許可する設定は使わない
- git hooksが入るまで（Day3まで）は、コミットとpushを人が行う。仕組みでミスを止められるようになった分だけ、AIに任せる範囲を広げる
- **AIの「確かめました」は、同じAIによる自己確認として扱う**。大事な値は、別の方法（レビュー役の別計算、Excel、テスト）で確かめる
- **AIが頼んでいない作業を足したとき**は、次の3つで受け入れるかを判断する：頼んだことの目的に沿っているか／頼んだ作業の範囲の中か／報告されているか

## 毎日の決まりごと
- 始め：その日の完了条件を確認する
- 作業中：AIの回答を受け取る → 自分で一言「気になるところ」を書く → レビュー
- 一行メモ：1日の終わりに、レビュー役（Claude）がその日の気づきを`docs/ai-review-log.md`の追記分としてまとめる。自分は内容を確認して追記する（時間を優先するため、11/16に変更）
- 選定理由は、その場でADRに書く（資料の材料になる）
- 作業の区切り（PRのマージなど）で、Claude Codeの会話を`/clear`する
- 終わり：実績と翌日に持ち越すことを、下の「実績」に1行で残す。Codespaceを停止する。EC2を使った日は、使わない時間にインスタンスを停止する

## 実績
| 日付 | できたこと | 持ち越し・計画変更 |
|---|---|---|
| 11/16（月）Day1 | 計画確定。Codespaces・Claude Code・Superpowers・SpecKitを導入。リポジトリをPublic化し、Rulesetでmainを保護（直接pushの拒否を確認）。PreToolUseのhookをTDDで作成（すり抜け対策を追加、ユニットテストgreen、本物のClaude Codeでブロックを確認） | constitutionと3機能の仕様はDay2午前へ。SpecKitのplan／tasksは機能ごとに実装日の直前に行う方式に変更。EC2へのデプロイが必要と判明し、Day4午後にCDを追加 |
| 11/17（火）Day2 | constitution v1.0.0（ビルドツールはMavenに決定、技術選定はADRへ）。3機能の仕様（分割払い：初回寄せ、リボ払い：元利定額、申込管理：履歴＋否決理由＋担当者名、状態での絞り込み）。認証は作らないと決定。ADR 0001〜0012（0006のみProposed）。分割払いのplan・tasks・analyze（CRITICALを解消）。残業で完了 | 朝にCodespaceを二重に作成して約1.5時間の遅れ。スキル①はDay5へ、ADR 0006はDay3へ。constitution v1.1.0（個人情報）をDay3朝に追加。**11/23は作業しないと決定**し、判断のタイミングを設定 |

## 遅れたときに削る順番
1. 申込管理のShould（キーワード検索・ページ分け）
2. リボ払いの画面（APIだけ残す）
3. リボ払いの機能そのもの
4. 申込管理の画面を簡素にする（一覧と状態の変更だけ）

品質基準（カバレッジ・TDD・hooks）とデプロイは削らない。

## 説明資料の構成（案）
1. 課題とスプリントゴール
2. 要件とMVPの範囲
3. アーキテクチャと技術選定の理由（ADRから）
   - デプロイ構成（GitHub Actions → GHCR → OIDC＋SSM → EC2上のdocker compose）と、選んだ理由（RDSと比べた費用と手間、SSHと比べた安全性）
4. 品質を守る仕組み（TDD・カバレッジ・Claude Code hooks・git hooks・自作スキル）
   - 3層の防御を図1枚で示す：①Claude Code hooks（AIが操作した瞬間に止める）→ ②git hooks（コミット・push前に手元で止める。速いが`--no-verify`で飛ばせる）→ ③CI＋GitHub Ruleset（サーバー側で最後に止める。飛ばせない）
   - git hooksとRulesetの違い（動く場所・タイミング・すり抜けられるか）と、両方を置いて弱いところを補い合う理由（多層防御）を説明する
5. 人とAIの分担、AIの出力をどう検証したか（AIレビューログから）
6. デモ（EC2上で）
7. 結果（テスト件数・カバレッジ・計画と実績の差）
8. 振り返りと次のスプリントでやること（認証の導入など）
