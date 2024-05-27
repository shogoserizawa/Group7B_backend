import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Base64.Encoder;

class SocketFree {

    private static ServerSocket sSck;// サーバー用のソケット
    private static Socket[] sck;// 受付用のソケット
    private static InputStream[] is;// 入力ストリーム
    private static InputStreamReader[] isr;// 入力ストリームを読み込む
    private static BufferedReader[] in;// バッファリングによるテキスト読み込み
    private static OutputStream[] os;// 出力ストリーム
    private static ClientThread user[];// 各クライアントのインスタンス
    private static int member;// 接続しているメンバーの数
    private static String name[];

    public static void main(String[] args) {
        int n = 0;
        int maxUser = 100;
        // 各フィールドの配列を用意
        sck = new Socket[maxUser];
        is = new InputStream[maxUser];
        isr = new InputStreamReader[maxUser];
        in = new BufferedReader[maxUser];
        os = new OutputStream[maxUser];
        user = new ClientThread[maxUser];
        name = new String[maxUser];

        try {
            sSck = new ServerSocket(60000);// サーバーソケットのインスタンスを作成(ポートは60000)
            System.out.println("サーバーに接続したよ！");
            while (true) {
                sck[n] = sSck.accept();// 接続待ち。来たらソケットに代入。
                System.out.println((n + 1) + "番目の参加者が接続しました！");

                // 必要な入出力ストリームを作成する
                is[n] = sck[n].getInputStream();// ソケットからの入力をバイト列として読み取る
                isr[n] = new InputStreamReader(is[n]);// 読み取ったバイト列を変換して文字列を読み込む
                in[n] = new BufferedReader(isr[n]);// 文字列をバッファリングして(まとめて)読み込む
                os[n] = sck[n].getOutputStream();// ソケットにバイト列を書き込む

                // クライアントへ接続許可を返す(ハンドシェイク)
                handShake(in[n], os[n]);

                // 各クライアントのスレッドを作成
                user[n] = new ClientThread(n, sck[n], is[n], isr[n], in[n], os[n]);
                user[n].start();

                member = n + 1;// 接続数の更新
                n++;// 次の接続者へ
            }
        } catch (Exception e) {
            System.err.println("エラーが発生しました: " + e);
        }
    }

    // クライアントへ接続許可を返すメソッド(ハンドシェイク)
    public static void handShake(BufferedReader in, OutputStream os) {
        String header = "";// ヘッダーの変数宣言
        String key = "";// ウェブソケットキーの変数宣言
        try {
            while (!(header = in.readLine()).equals("")) {// 入力ストリームから得たヘッダーを文字列に代入し、全行ループ。
                System.out.println(header);// 1行ごとにコンソールにヘッダーの内容を表示
                String[] spLine = header.split(":");// 1行を「:」で分割して配列に入れ込む
                if (spLine[0].equals("Sec-WebSocket-Key")) {// Sec-WebSocket-Keyの行
                    key = spLine[1].trim();// 空白をトリムし、ウェブソケットキーを入手
                }
            }
            key += "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";// キーに謎の文字列を追加する
            byte[] keyUtf8 = key.getBytes("UTF-8");// キーを「UTF-8」のバイト配列に変換する
            MessageDigest md = MessageDigest.getInstance("SHA-1");// 指定されたダイジェスト・アルゴリズムを実装するオブジェクトを返す
            byte[] keySha1 = md.digest(keyUtf8);// キー(UTF-8)を使用してダイジェスト計算を行う
            Encoder encoder = Base64.getEncoder();// Base64のエンコーダーを用意
            byte[] keyBase64 = encoder.encode(keySha1);// キー(SHA-1)をBase64でエンコード
            String keyNext = new String(keyBase64);// キー(Base64)をStringへ変換
            byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Sec-WebSocket-Accept: "
                    + keyNext
                    + "\r\n\r\n")
                    .getBytes("UTF-8");// HTTP レスポンスを作成
            os.write(response);// HTTP レスポンスを送信
        } catch (IOException e) {
            System.err.println("エラーが発生しました: " + e);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("エラーが発生しました: " + e);
        }
    }

    public static void sendAll(int number, byte[] sendHead, String line) {
        try {
            String modifiedLine = name[number] + "<%>" + line; // 送信者情報を追加
            byte[] modifiedLineBytes = modifiedLine.getBytes("UTF-8"); // バイト配列に変換

            for (int i = 0; i < member; i++) {
                if (sck[i].isClosed())
                    continue; // ソケットが閉じていたら無視

                if (i == number) {
                    continue;         //自分が送っていても無視
                } else {
                    byte[] modifiedSendHead = new byte[2];
                    modifiedSendHead[0] = sendHead[0];
                    modifiedSendHead[1] = (byte) modifiedLineBytes.length; // 文字列の長さをヘッダーに設定

                    os[i].write(modifiedSendHead); // ヘッダー出力
                    os[i].write(modifiedLineBytes); // 変更されたメッセージを出力
                    System.out.println((i + 1) + "番目に" + (number + 1) + "番目のメッセージを送りました！");
                }
            }
        } catch (IOException e) {
            System.err.println("エラーが発生しました: " + e);
        }
    }

    public static void AddUsername(int number, String line) {
        name[number] = line;
        System.out.println("add user : " + name[number]);
    }
}

class ClientThread extends Thread {
    // 各クライアントのフィールド
    private int myNumber;
    private Socket mySck;
    private InputStream myIs;
    private InputStreamReader myIsr;
    private BufferedReader myIn;
    private OutputStream myOs;
    private String firstThreeChars = "";
    private String remainingString = "";

    // コンストラクタでインスタンスのフィールドに各値を代入
    public ClientThread(int n, Socket sck, InputStream is, InputStreamReader isr, BufferedReader in, OutputStream os) {
        myNumber = n;
        mySck = sck;
        myIs = is;
        myIsr = isr;
        myIn = in;
        myOs = os;
    }

    // Threadクラスのメイン
    public void run() {
        try {
            echo(myIs, myOs, myNumber);
        } catch (Exception e) {
            System.err.println("エラーが発生しました: " + e);
        }
    }

    // ソケットへの入力を無限ループで監視する ※125文字まで(126文字以上はヘッダーのフレームが変化する)
    public void echo(InputStream is, OutputStream os, int myNumber) {
        try {
            while (true) {
                byte[] buff = new byte[1024]; // クライアントから送られたバイナリデータを入れる配列
                int lineData = is.read(buff); // データを読み込む lineDataは実際に送られてきたバイト数を表す

                if (lineData == 8) { // クライアントが接続を切断した場合
                    System.out.println("受信者 " + (myNumber + 1) + " が切断されました");
                    if (mySck != null && !mySck.isClosed()) {
                        mySck.close();
                    }
                    break;
                }

                for (int i = 0; i < lineData - 6; i++) {
                    buff[i + 6] = (byte) (buff[i % 4 + 2] ^ buff[i + 6]); // 7バイト目以降を3-6バイト目のキーを用いてデコード
                }
                String line = new String(buff, 6, lineData - 6, "UTF-8"); // デコードしたデータを文字列に変換

                byte[] sendHead = new byte[2]; // 送り返すヘッダーを用意
                sendHead[0] = buff[0]; // 1バイト目は同じもの
                sendHead[1] = (byte) line.getBytes("UTF-8").length; // 2バイト目は文字列の長さ

                if (line.length() >= 3)
                    firstThreeChars = line.substring(0, 3); // メッセージの最初の三文字を格納

                if (firstThreeChars.equals("<%>")) {
                    remainingString = line.substring(3);
                    SocketFree.AddUsername(myNumber, remainingString);
                } else
                    SocketFree.sendAll(myNumber, sendHead, line); // 各クライアントへの送信は元クラスのsendAllメソッドで実行

                if (line.equals("bye"))
                    break; // 「bye」が送られたなら受信終了
            }
        } catch (Exception e) {
            System.err.println("エラーが発生しました: " + e);
        }
    }
}
