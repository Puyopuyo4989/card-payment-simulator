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
| 5 | 11/16 | Superpowersのインストール先 | 前提 | 環境の再現性 | `~/.claude`に入るので、リポジトリには残らない。Codespaceを作り直すと消える | 11/17に実際に消えた（#13）。11/18にdevcontainerの`post-create.sh`で自動で入れるようにし、作り直した後に`claude plugin list`で有効なことを確認 → 解決 |
| 6 | 11/16 | mainブランチの保護 | 前提 | GitHubの料金プラン | 無料プランのPrivateリポジトリでは、ブランチ保護のルールが効かない | Publicに変更してRulesetを設定 → mainへ直接pushして拒否されるか確認 → 拒否された |
| 7 | 11/16 | Public化前の確認 | 照合 | `git log --stat` | コミット済みのファイルに、`.env`・APIキー・`settings.local.json`はなかった | 履歴を確認 → 問題なし。`.specify/init-options.json`と`integration.json`の中身の確認 → 問題なし。 |
| 8 | 11/16 | PreToolUseのhook（初版） | 代替 | 依頼内容 | 禁止リストにすり抜けがある（`git push origin +main`、`find -delete`、`eval`、`git clean -f`など） | 単体でJSONを流してすり抜けを確認 → テストを先に書いてred → 修正してgreen。完全な網羅はできないので、目的を「AIのうっかりミス防止」と定め、Ruleset・permissions.denyとの多層防御で補う |
| 9 | 11/16 | hookが効かなくなる条件 | 前提 | Claude Codeのhookの仕様 | hookが`deny`か終了コード2以外のエラーで終わると、操作がそのまま通る（例：`python3`が無い）。気づきにくい | `python3 --version`でPythonが入っていることを確認 → `/hooks`でhookが登録されていることを確認 → 本物のClaude Codeに`git push origin +main`を頼む → ブロックされた（hookが実際に効いていることを確認） |
| 10 | 11/16 | permissions.deny | 前提 | Claude Codeの設定の仕様 | denyはallowより優先されるので、「.env.*から.env.exampleだけ除く」は書けない（レビュー役の想定が外れていた） | AIの説明どおり名前を列挙する方式にした → Claude Codeにダミーの.env.exampleを読ませる → 読めた（止めすぎていないことを確認） |
| 11 | 11/16 | PreToolUseのhook（修正版） | 代替 | ふだんの開発操作 | 止めすぎがある（`rm -rf build`、`find -delete`、`echo .env >> .gitignore`など） | 止まっても別の手段（`./gradlew clean`、Editツール）で進められるので許容。Day3で邪魔なら許可リストを見直す。※11/17：ビルドツールはMavenに変更（ADR 0002）。Mavenでは`./mvnw clean`、出力先は`target` |
| 12 | 11/16 | AIの完了報告「全17テストgreen」 | 照合 | redの表のケース数（31件） | 報告のテスト数と、表のケース数が合わない（subTestと推測） | 11/17に自分でpython3 -m unittest discover -s .claude/hooks/tests -vを実行 → Ran 17 tests・OK（1つのテストで複数のケースを回すsubTestのため、31ケースと矛盾しない） |
| 13 | 11/17 | Codespaceの起動 | 前提 | github.com/codespaces の一覧 | 「Create codespace」を押して新しいcodespaceを作ってしまい、Claude CodeとSuperpowersが入っていない環境で作業していた | 昨日のcodespaceに戻り、新しいほうを削除。devcontainerでツールを自動で入れる優先度を上げた（Day3） |
| 14 | 11/17 | Pythonのキャッシュファイル | 照合 | git status | __pycache__/*.pycが前日のPRでコミットされていた（レビューでも見落とし） | .gitignoreに追加し、git rm -r --cachedで管理から外した |
| 15 | 11/17 | constitution（初版） | 照合・前提 | 会話で決めた内容 | 決めていないビルドツール（Gradle）が、AI経由で決定事項として書かれていた。技術選定もconstitutionに入っていた | Mavenに決定（前回の経験、金融系での採用）。技術選定はADRへ移し、ツール名（JaCoCo・Vitest）も削除 |
| 16 | 11/17 | 分割払いの仕様（clarify） | 前提 | 端数処理の質問 | 端数を寄せる回の前に、手数料の計算方式が決まっていなかった | 方式（総額を先に決めて回数で割り、初回寄せ。返済表の最終回の手数料で調整）を決めて仕様に明記 |
| 17 | 11/17 | 分割払いの検算 | 照合 | 別の計算方法 | 検算したのが仕様を作ったAI自身だった（自己採点）。早見表の数値は記憶で、出典なし | レビュー役が別に計算して一致を確認。早見表の数値は採用しない。ExcelのPMTでも確かめられる |
| 18 | 11/17 | リボ払いの仕様 | 照合 | 依頼の範囲 | 頼んでいない作業（Sync Impact Reportの削除）が混ざっていた。原因はClaude Codeのメモリにあった指示 | このPRから外した。/memoryでメモリの中身を確認し、不要な指示がないことを確かめた |
| 19 | 11/17 | リボ払いの受け入れ条件の数値 | 照合 | 別の計算方法 | AIの自己確認だけだった | レビュー役が別に計算して6ケースすべて一致。ExcelのNPERで回数も確かめられる |
| 20 | 11/17 | 申込管理の仕様 | 前提 | AIが置いた前提 | Shouldの定義、年齢・年収の範囲、変更履歴に担当者を残さないことを、AIが決めていた。constitution原則IVの引用が、個人情報まで範囲を広げていた | Shouldに着手の順番を足した。担当者名（自己申告）を履歴に残すことにした。原則IVの改定（v1.1.0）をDay3に予定 |
| 21 | 11/17 | ADR | 照合 | 渡した資料 | AIは資料にない理由を推測で書かずTODOにした（良い動き）。資料の食い違い（Gradle）も見つけた。一方、Mavenの「悪い影響：なし」は検討が浅い | TODOは会話で決めた理由で埋めた。Mavenの悪い影響を書き足した |
| 22 | 11/17 | 決めたつもりの技術 | 前提 | 会話での決定 | PostgreSQL・Flyway・Vite・Vitestは計画に書いただけで、決めていなかった | 理由を確認して決定し、ADR 0011・0012をAcceptedにした |
| 23 | 11/17 | 分割払いのplan | 前提 | .specify/feature.json | feature.jsonが申込管理（003）を指していて、最初は003のフォルダに計画ができた。「ブランチ名で決まる」というレビュー役の説明も誤りだった | 003に影響がないことをgit diffで確認。feature.jsonはgitの管理外で、機能ごとに自分で向け直す必要があると分かった |
| 24 | 11/17 | Spring Bootのバージョン | 前提 | 公式のリリース予定 | 4.2が11/19（スプリント中）にリリース予定 | 4.1系に固定し、スプリント中は乗り換えない |
| 25 | 11/17 | API契約の金額 | 代替 | Jacksonの動き | BigDecimalがJSONで1E+5のような指数表記になるおそれがある。AIは頼んでいない「本文の文字列で確かめる」を足した | テストに追加。AIの追加は目的に沿い、範囲内で、報告もあったので受け入れた |
| 26 | 11/17 | tasks.md | 照合 | 別の計算方法 | 割り切れない年率（14.9%）の値のテストがなかった | 仕様の定義どおりの計算と、AIの変形した式の計算が一致することを確かめ、値のテストに追加。上限・下限の3条件も一致 |
| 27 | 11/17 | analyzeの指摘への対応 | 確認 | git diff --word-diff・grep | AIがtasks.mdを丸ごと書き直し、全ファイルで一括置換していた | 差分を単語単位で確認し、足したテスト（14.9%・1E+5・ポップアップ禁止・ADR 0006）が残っていることをgrepで確かめた |
| 28 | 11/18 | devcontainer | 代替 | 依頼の範囲 | AIが頼んでいないPythonとGitHub CLIを足した。Pythonが無いとPreToolUseのhookが黙って効かなくなる（#9）ことをAIが自分で見つけていた | 目的に沿い・範囲内・報告あり、の3つを満たすので受け入れた |
| 29 | 11/18 | devcontainerのJava | 前提 | CIと本番のDockerイメージ | 開発環境のJavaはMicrosoft Build、CIと本番はTemurinになる見込みで、ディストリビューションがそろっていなかった。確認で「temurin」をgrepしたが、設定値は略称の「tem」で引っかからなかった（確かめ方の前提のずれ） | `jdkDistro: tem`に変更。作り直した後の`java -version`で「Temurin」を確認 |
| 30 | 11/18 | mainの上でのadd | 確認 | `git log` | addだけしてコミットしたと思い込んでいた | `git switch -c`で作業ブランチに移り、mainは`git branch -f main origin/main`で戻した。lefthookでmainへの直接コミットを止める根拠になった |
| 31 | 11/18 | Phase 1（Mavenの土台） | 代替・照合 | 依頼の範囲、`pom.xml` | AIが動かすのに必要な依存（plugin-reactなど）を足した。npm auditで開発用の依存にhighが7件。ビルド中に`apache.snapshots`への接続がタイムアウトしたが、`pom.xml`に取得元の設定はなかった | 依存の追加は受け入れた。auditとsnapshotsは既知の課題として記録（本番のjarには入らない／何もダウンロードされていない） |
| 32 | 11/18 | 画面の確認 | 前提 | 自分の環境 | Codespacesで動くアプリはPCの`localhost`では開けない | PORTSタブの転送（Private）で開いて見出しを確認 |
| 33 | 11/18 | push時のエラー | 前提 | `.git/hooks` | 前のCodespaceが入れたGit LFSのhookが`.git/hooks`に残り、LFSの無い新しいコンテナでpushが止まった | LFSを使っていないことを確認し、LFSのhookだけを別の場所に移した |
| 34 | 11/18 | ADR 0006の除外対象 | 照合 | `pom.xml`・`vite.config.ts` | `vite.config.ts`は除外リストではなく「計測の範囲（`src/`）の外」という仕組みで対象外になっていた | ADRに「`src/`の外は計測の範囲外」を明記 |
| 35 | 11/18 | CIのactionのハッシュ | 照合 | GitHub本体（`gh api`） | AIが書いたハッシュが本物かを、AIに聞かずに一次情報で確かめた。公開直後の版を避けるルールがなかった | ハッシュは完全一致。「公開から7日以上の版を使う」をADR 0008に追加し、setup-javaをv6.2.0に変更。checkoutの公開日も確認 |
| 36 | 11/18 | CI＋Ruleset | 確認 | PRの画面 | — | わざとテストを壊すコミットでCIが赤になり、マージできないことを確認。`git revert`で戻し、履歴に証拠を残した |
| 37 | 11/18 | commit-msgの`feature:` | 照合 | `git log` | AIは「過去のコミットはすべて`feature:`」と書いたが、実際は`feature:`4件・`feat:`1件。レビュー役も「`feat:`を使ってきた」と思い込んでいた | 両方の主張を`git log`で確かめた。今後は標準の`feat:`にそろえ、`feature:`は通さない |
| 38 | 11/18 | `git reset --hard` | 確認 | `git status` | レビュー役が`--hard`を使う手順を出し、コミット前の変更（pom.xmlなど）が消えた | 新しく作ったファイルは残っていた。消えた変更はClaude Codeに再適用させて回復。以後、試す前にコミットする、`--hard`の手順は出さない |
| 39 | 11/18 | lefthookが動いていない | 確認 | わざと違反するテスト | `feature:`のコミットが通った。AIがテスト中に`.git/hooks`のhookを消しており、`post-create.sh`の`lefthook install`は作り直したときにしか動かないため、hookが入っていなかった | lefthookとgitleaksを入れて`lefthook install`。hookが無い間の2つのコミットはgitleaksで後から確認（漏えいなし） |
| 40 | 11/18 | mainへの直接コミットの確認 | 前提 | どのブランチに何があるか | `lefthook.yml`はまだfeatureブランチにしかなく、mainでは設定が見つからず素通りした | マージしてから確認する順番に変えた |
| 41 | 11/18 | 空のコミット | 確認 | `lefthook run --verbose` | `--allow-empty`ではコミットするファイルが無いため、pre-commitのチェックごと飛ばされ、mainに空のコミットが通った | mainのチェックをcommit-msgのhookでも動かすように修正し、止まることを確認 |
| 42 | 11/18 | `reset --soft HEAD~2` | 確認 | `git status` | 戻しすぎて、手元がGitHubより1つ前になった | pushしてあったGitHub側を正として`git reset --soft origin/feature/lefthook`で戻した |
| 43 | 11/18 | PostToolUseのタイムアウト | 照合 | `settings.json`とスクリプト | 全体240秒は長すぎた。60秒に縮めると、スクリプト内の1コマンド100秒と食い違うことをAIが見つけ、勝手に直さずに相談してきた | 1コマンド25秒にし、環境変数で短くできるようにしてタイムアウトのテストを追加。子プロセスが残る問題は既知の限界としてREADMEに記載 |
| 44 | 11/18 | `--no-verify`のブロック | 照合・代替 | メモの表、本物のClaude Code | 読み違えて「止まっていない」と思ったが、スクリプト単体と本物のClaude Codeで止まることを確認。`LEFTHOOK=0`・`core.hooksPath`・省略形も回避手段だった | 3つともブロック対象に追加。`LEFTHOOK_*`は許可リスト方式。残る回避手段（`GIT_CONFIG_COUNT`など）は追わず、既知の限界として受け入れた |
| 45 | 11/18 | 一時ファイルの無視 | 照合 | `.gitignore` | AIのメモが大文字の`MEMO-`で始まり、小文字の`memo-*.md`の設定では無視されなかった | `.gitignore`に`MEMO-*.md`を追加 |

## 理解したこと（メモ）
- **git hooksとGitHubのRulesetは別物**：git hooksは手元で動き、`--no-verify`で飛ばせる。Rulesetはサーバー側で動き、Bypass listが空なら飛ばせない。Claude Code hooks → git hooks → CI＋Rulesetの3層で、お互いの弱いところを補い合う（多層防御）
- **「設定を入れた」と「動いている」は別物**：lefthookは設定があってもhook本体が入っていなかった。わざと違反して止まるのを見るまで「入れた」とは言わない
- **試す前にコミットしてpushする**：コミットしておけば、試しの操作で壊しても戻せる。pushしておけば、手元で間違えてもGitHubを正として戻せる
- **禁止リストより許可リスト**：止める対象を並べるより、許すものだけを決めるほうが、新しい抜け道にも強い（LEFTHOOK_*の環境変数）
- **AIもレビュー役も間違える**：どちらの主張も、実物（git log、git status、一次情報）で確かめる