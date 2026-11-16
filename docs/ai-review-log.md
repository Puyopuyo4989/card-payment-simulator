# AIレビューログ

AIの出力や手順を、4つの問いで疑って確かめた記録。

- **照合**：何と照らし合わせた？（仕様、ADR、自分の環境）
- **前提**：前提は合ってる？（プレースホルダー、バージョン、ブランチ）
- **代替**：他のやり方はなかった？
- **確認**：本当に効いてるか、どう確かめる？（壊して確かめる、実データで確かめる）

結果が「未」のものは、確認が終わったら追記する。

| # | 日付 | 対象 | 問い | 照合先 | 気づき | 確かめ方 → 結果 |
|---|---|---|---|---|---|---|
| 1 | 11/16 | PCのメモリ増設 | 前提 | NECの仕様書、エレコムのメモリ対応表 | スロットが空いているように見えたが、4GBが基板に直付けされていて増設はできない | 型番（PC-VK22TGGGS）とメモリの型番で仕様を確認 → 増設不可 → 開発環境をCodespacesに決定 |
| 2 | 11/16 | Claude Codeのログイン | 前提 | 自分の環境（Codespaces） | 認証後に戻る`localhost`はCodespaces側のもので、PCのブラウザからは届かない | コードを手で貼り付ける／Codespaces内から`curl`でコールバックURLを叩く → 認証できた |
| 3 | 11/16 | SpecKitの導入 | 前提 | 自分の環境（Codespaces） | PCにはuvが入っているが、Codespacesは別の環境なので入っていない | Codespacesにuvを入れてから`specify init` → 導入できた |
| 4 | 11/16 | SpecKitのファイルの場所（AIの説明） | 照合・前提 | 実物（`git status`、`ls`） | AIは`.claude/commands`と説明したが、実物は`.claude/skills`（バージョンによる違い）。説明になかった`checklist`・`converge`もあった | `ls -la .claude`で確認 → `.claude/skills`をコミット。AIの説明を鵜呑みにしないで、実物を見るのが大事 |
| 5 | 11/16 | Superpowersのインストール先 | 前提 | 環境の再現性 | `~/.claude`に入るので、リポジトリには残らない。Codespaceを作り直すと消える | Day3でdevcontainer.jsonを作るときに対応する → 結果：未 |
| 6 | 11/16 | mainブランチの保護 | 前提 | GitHubの料金プラン | 無料プランのPrivateリポジトリでは、ブランチ保護のルールが効かない | Publicに変更してRulesetを設定 → mainへ直接pushして拒否されるか確認 → 拒否された |
| 7 | 11/16 | Public化前の確認 | 照合 | `git log --stat` | コミット済みのファイルに、`.env`・APIキー・`settings.local.json`はなかった | 履歴を確認 → 問題なし。`.specify/init-options.json`と`integration.json`の中身の確認 → 問題なし。 |
| 8 | 11/16 | PreToolUseのhook（初版） | 代替 | 依頼内容 | 禁止リストにすり抜けがある（`git push origin +main`、`find -delete`、`eval`、`git clean -f`など） | 単体でJSONを流してすり抜けを確認 → テストを先に書いてred → 修正してgreen。完全な網羅はできないので、目的を「AIのうっかりミス防止」と定め、Ruleset・permissions.denyとの多層防御で補う |
| 8 | 11/16 | PreToolUseのhook（初版） | 代替 | 依頼内容 | 禁止リストにすり抜けがある（`git push origin +main`、`find -delete`、`eval`、`git clean -f`など） | 単体でJSONを流してすり抜けを確認 → テストを先に書いてred → 修正してgreen。完全な網羅はできないので、目的を「AIのうっかりミス防止」と定め、Ruleset・permissions.denyとの多層防御で補う |
| 9 | 11/16 | hookが効かなくなる条件 | 前提 | Claude Codeのhookの仕様 | hookが`deny`か終了コード2以外のエラーで終わると、操作がそのまま通る（例：`python3`が無い）。気づきにくい | `python3 --version`でPythonが入っていることを確認 → `/hooks`でhookが登録されていることを確認 → 本物のClaude Codeに`git push origin +main`を頼む → ブロックされた（hookが実際に効いていることを確認） |
| 10 | 11/16 | permissions.deny | 前提 | Claude Codeの設定の仕様 | denyはallowより優先されるので、「.env.*から.env.exampleだけ除く」は書けない（レビュー役の想定が外れていた） | AIの説明どおり名前を列挙する方式にした → .env.exampleがClaude Codeから読めるか確認 → 結果：未 |
| 11 | 11/16 | PreToolUseのhook（修正版） | 代替 | ふだんの開発操作 | 止めすぎがある（`rm -rf build`、`find -delete`、`echo .env >> .gitignore`など） | 止まっても別の手段（`./gradlew clean`、Editツール）で進められるので許容。Day3で邪魔なら許可リストを見直す |
| 12 | 11/16 | AIの完了報告「全17テストgreen」 | 照合 | redの表のケース数（31件） | 報告のテスト数と、表のケース数が合わない（subTestと推測） | 自分で`python3 -m unittest discover -s .claude/hooks/tests -v`を実行して件数と結果を確認 → 結果：未 |

## 理解したこと（メモ）
- **git hooksとGitHubのRulesetは別物**：git hooksは手元で動き、`--no-verify`で飛ばせる。Rulesetはサーバー側で動き、Bypass listが空なら飛ばせない。Claude Code hooks → git hooks → CI＋Rulesetの3層で、お互いの弱いところを補い合う（多層防御）
