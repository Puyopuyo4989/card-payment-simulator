# 本番用のイメージ（ADR 0009）。
# jar は CI で1回だけビルドし、テストに通った jar（target/*.jar）をコピーするだけにする（Build once）。
# ここではビルドしない。先に ./mvnw package で jar を作っておく。

# 版は固定する。公開から7日以上たった版を使う（ADR 0008）。タグは付け替えられるのでダイジェストでも固定する
FROM eclipse-temurin:25.0.5_8-jre-noble@sha256:3c6d7b40c2e78d4a8f4fe00af8165c706b34faf672dc21ff0924e583559ec6c2

# root ではない利用者で動かす
RUN groupadd --system app && useradd --system --gid app --no-create-home app

WORKDIR /app
# jar は root の持ち物で読み取りだけにし、動いているアプリが書き換えられないようにする
COPY --chmod=444 target/*.jar app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
