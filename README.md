# これはなに？

- 楽天証券がログイン時に本人確認と称してフリーダイヤル認証なるものを導入したので電話をかけるアプリを作った
- 曰く `ふだんと異なる環境や端末からのアクセスの可能性` `はじめてアクセスしたパソコン・スマホである` とのことだがいつものブラウザで毎回聞かれる

# 技術的なポイント

- WebSocketのコネクションを貼ってサーバーからメッセージを受けたら電話をかけるようにした
  - WebSocketのサーバー実装 [https://github.com/m-m-n/ws-push](https://github.com/m-m-n/ws-push)
- アプリがフォアグラウンドにいないと失敗するのでスリープしないようにした
