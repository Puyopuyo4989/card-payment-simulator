---
name: money-calc-review
description: "金額計算のレビュー（自作スキル①）。constitution 原則III と ADR 0005 に照らして、バックエンドの金額の計算と、フロントが金額を計算していないことを確かめ、結果をファイル名と行番号つきの一覧で報告する。見つけた問題は直さない。金額の計算に関わるコード（src/main/java、frontend/src）を書いたり変えたりしたとき、スキル②（pre-pr-check）から呼ばれたとき、または「金額計算のレビュー」を頼まれたときに使う。"
user-invocable: true
disable-model-invocation: false
---

# 金額計算のレビュー（自作スキル①）

照合先は次の2つ。判断に迷ったら、ここに書いてあることを正とする。

- `.specify/memory/constitution.md` の **原則III（金額はBigDecimalで扱う）**
- [ADR 0005 金額計算と端数処理](../../../docs/adr/0005-金額計算と端数処理.md)。丸める箇所は、ADR 0005 が指す各機能の spec（分割払い：`specs/001-installment-simulation/spec.md` FR-006・FR-007・FR-011、リボ払い：`specs/002-revolving-simulation/spec.md` FR-006〜FR-008・FR-012）

## 守ること

- **見つけた問題を、その場で直さない。** 一覧にして報告し、直すかどうかは人が決める。
- 確認の 1〜6 を**すべて**行う。1つで問題が見つかっても、残りを続ける。
- grep の結果は、**出た行をすべて自分で読んで**判定する。grep に出なかったことだけで「問題なし」と書かない（2・3・5 は grep のあとでコードを読む）。
- 金額でない使い方に見えても、原則III の禁止に当たる書き方（`double`・`float` など）は「違反」か「要確認」として一覧に載せ、勝手に除外しない。
- 結果は、実際に実行したコマンドの出力と、読んだコードにもとづいて書く。

## 対象

- バックエンド：`src/main/java/`（テストは対象外）
- フロント：`frontend/src/` の `*.ts`・`*.tsx`（`*.test.ts(x)` と `setupTests.ts` は対象外）
- スキル②から呼ばれたときも、変わったファイルだけでなく、上の対象の全体を確かめる

## 1. 金額に `double`・`float`・`Math.pow` を使っていない

```bash
grep -rnE '\b(double|float|Double|Float)\b|Math\.pow|\bdoubleValue\(|\bfloatValue\(' src/main/java
```

- 何も出さなければ通過。
- 出た行はすべて一覧に載せる。金額に使っていれば「違反」、金額かどうかはっきりしなければ「要確認」。

## 2. `new BigDecimal(double)` を使っていない（文字列か整数から作る）

```bash
grep -rnE 'new BigDecimal\(|BigDecimal\.valueOf\(' src/main/java
```

- 出た行ごとに、引数の型を読んで確かめる。
  - 文字列（`"12000"`、入力の文字列）か整数（`int`・`long`・整数のリテラル）なら通過。
  - `double`・`float`、小数のリテラル（`0.15` など）、型のわからない式なら「違反」。`BigDecimal.valueOf(double)` も同じく「違反」。

## 3. 丸めは `RoundingMode.DOWN` で、spec に書いた箇所だけ。割り算は桁数と丸め方を必ず指定している

```bash
grep -rnE '\.divide\(|\.setScale\(|RoundingMode\.|MathContext|\.round\(|\.divideToIntegralValue\(|\.toBigInteger\(|\.(intValue|longValue)\(\)' src/main/java
```

出た行ごとに、次を確かめる。

- `divide` は `divide(除数, 桁数, RoundingMode.DOWN)` の形か。`divide(除数)` だけ、`divide(除数, RoundingMode.X)`（桁数なし）、`MathContext` を渡す形は「違反」。
- 丸め方が `RoundingMode.DOWN` か。`HALF_UP`・`FLOOR`・`UNNECESSARY` などほかの丸め方は「違反」。
- その丸めが、照合先の spec の FR に書かれた箇所に当たるか。コードを読み、どの FR のどの項目（例：リボ払い FR-007 の「各回の手数料」）に当たるかを一覧の「内容」に書く。spec に書いていない箇所での丸めは「違反」、当たる FR を決めきれなければ「要確認」。
- `intValue()`・`longValue()`・`toBigInteger()` は、端数を黙って捨てる（暗黙の丸め）。金額に使っていれば「違反」。`intValueExact()` のように端数があると例外にするものは通過。

## 4. `stripTrailingZeros` を使っていない

```bash
grep -rn 'stripTrailingZeros' src/main/java
```

- 何も出さなければ通過。出た行はすべて「違反」（金額が `1E+5` のような形で返るのを防ぐ）。

## 5. 金額の比較に `equals` ではなく `compareTo` を使っている

```bash
grep -rnE '\.equals\(|Objects\.equals\(|\.hashCode\(' src/main/java
```

- 出た行ごとに、比べているものの型を読む。`BigDecimal` どうしを `equals`・`Objects.equals` で比べていれば「違反」（`equals` は桁数の違いで別物と判定する。`1` と `1.0` が等しくならない）。
- `BigDecimal` を `compareTo` で比べている箇所（`compareTo(…) == 0`・`>= 0` など）、`signum()` は通過。
- 金額の `BigDecimal` を持つ `record` を、本番のコードで `equals` で比べていたり、`Set`・`Map` のキーにしていたりしたら「要確認」（`record` の `equals` は中の `BigDecimal.equals` を使う）。

## 6. フロントで金額を計算していない（表示だけ）

```bash
grep -rnE '[^=!<>/*]\s*[-+*/%]\s*[^=>/*]|Math\.|toFixed\(|parseFloat\(|parseInt\(|Number\(|reduce\(' frontend/src --include='*.ts' --include='*.tsx' | grep -vE '\.test\.tsx?:|setupTests\.ts:'
```

- grep は目安なので、**対象のファイルを全部読む**（数百行なので読める）。
- API の結果の金額（`totalFee`・`totalPayment`・`payment`・`fee`・`principal`・`balance` など）に、`+`・`-`・`*`・`/`・`%`・`Math.*`・`reduce` での合計・`toFixed` などをしていれば「違反」。
- 入力された文字列を数に変えて送っていたら「違反」（入力は文字列のまま API に送る）。
- 金額を `formatYen` などで表示用の文字列にするだけなら通過。

## 7. 結果を一覧で報告する

次の形で報告する。見つけたものは、1行に1件、ファイル名と行番号つきで書く。

### 確認ごとの結果

「結果」は「通過」「違反」「要確認」のどれかにする。

| # | 確認 | 結果 | 件数 |
|---|---|---|---|
| 1 | `double`・`float`・`Math.pow` | | |
| 2 | `new BigDecimal(double)` | | |
| 3 | 丸め（`RoundingMode.DOWN`・spec の箇所だけ・割り算の桁数と丸め方） | | |
| 4 | `stripTrailingZeros` | | |
| 5 | 金額の比較（`compareTo`） | | |
| 6 | フロントで金額を計算していない | | |

### 見つけたもの

| 判定 | 場所 | 確認 | 内容 |
|---|---|---|---|
| 違反 | `src/main/java/…/Foo.java:42` | 1 | 何をしているか、照合先（原則III・ADR 0005・spec の FR）のどれに反するか |

- 「違反」と「要確認」をすべて載せる。0件なら「見つけたものはなし」と書く。
- 3 で通過した丸めの箇所も、どの FR に当たるかを `ファイル:行` つきで別に並べる（丸めが spec の箇所だけであることを、人が確かめられるように）。

最後に「見つけた問題は直していません。判断をお願いします。」と書いて終える。
