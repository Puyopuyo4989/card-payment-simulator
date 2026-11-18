# ADR（アーキテクチャ決定記録）

技術の選定と、その理由を記録する。constitution の「技術とデプロイの制約」により、技術の選定はADRに従う。

## 書き方

- 1つの決定につき1ファイル：`NNNN-タイトル.md`
- 見出し：状態（Accepted／Proposed）、背景、決定、検討した他の案、結果（良い影響・悪い影響）
- 読んでもわからない理由は推測で書かず、「TODO：理由を追記」とする
- constitution と矛盾する場合は、constitution を正とし、ADRを直す

## 一覧

| 番号 | タイトル | 状態 |
|---|---|---|
| [0001](0001-技術スタック.md) | 技術スタック：Spring Boot＋React（TypeScript） | Accepted |
| [0002](0002-ビルドツール.md) | ビルドツール：Maven | Accepted |
| [0003](0003-リポジトリと成果物.md) | リポジトリと成果物：リポジトリ1つ、フロントを同梱した1つのjar | Accepted |
| [0004](0004-開発環境.md) | 開発環境：GitHub Codespaces | Accepted |
| [0005](0005-金額計算と端数処理.md) | 金額計算と端数処理：BigDecimal、1円未満切り捨て | Accepted |
| [0006](0006-カバレッジ基準.md) | カバレッジ基準：行・分岐とも90% | Accepted |
| [0007](0007-ツールの使い分け.md) | ツールの使い分け：SpecKitとSuperpowers | Accepted |
| [0008](0008-品質の多層防御.md) | 品質の多層防御：Claude Code hooks、git hooks、CI＋Ruleset | Accepted |
| [0009](0009-デプロイ構成.md) | デプロイ構成：EC2上のdocker compose、GHCR、OIDC＋SSM | Accepted |
| [0010](0010-認証を作らない.md) | 認証を作らない | Accepted |
| [0011](0011-DB.md) | DB：PostgreSQL＋Flyway | Accepted |
| [0012](0012-フロントのビルドとテスト.md) | フロントのビルドとテスト：Vite＋Vitest | Accepted |

## TODO が残っているもの

なし。
