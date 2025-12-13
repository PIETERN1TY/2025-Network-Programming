/*
 * 클라이언트와 서버 간의 실시간 데이터 송수신을 담당하며,
 * InputStream과 OutputStream을 효율적으로 관리
 */
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class GameDataStreamManager {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private ObjectInputStream objectIn;
    private ObjectOutputStream objectOut;
     
    /**
     * 소켓을 받아서 입출력 스트림 초기화
     * @param socket 클라이언트 소켓
     * @throws IOException 스트림 생성 실패 시
     */
    public GameDataStreamManager(Socket socket) throws IOException {
        this.socket = socket;
        
        // 텍스트 기반 스트림 초기화
        this.writer = new PrintWriter(
            new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), 
            true  // auto-flush 활성화
        );
        this.reader = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), "UTF-8")
        );
    }
    
    /**
     * 객체 직렬화를 위한 스트림 초기화
     * @throws IOException 스트림 생성 실패 시
     */
    public void initializeObjectStreams() throws IOException {
        // 중요: ObjectOutputStream을 먼저 생성해야 함
        this.objectOut = new ObjectOutputStream(socket.getOutputStream());
        objectOut.flush();
        this.objectIn = new ObjectInputStream(socket.getInputStream());
    }
    
    /**
     * 문자열 메시지 전송
     * @param message 전송할 메시지
     */
    public void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
            writer.flush();
        }
    }
    
    /**
     * 문자열 메시지 수신 (타임아웃 없음)
     * @return 수신한 메시지
     * @throws IOException 수신 실패 시
     */
    public String receiveMessage() throws IOException {
        if (reader != null) {
            return reader.readLine();
        }
        return null;
    }
    
    /**
     * 문자열 메시지 수신 (타임아웃 설정)
     * @param timeoutMillis 타임아웃 시간 (밀리초)
     * @return 수신한 메시지 (타임아웃 시 null)
     * @throws IOException 수신 실패 시
     */
    public String receiveMessage(int timeoutMillis) throws IOException {
        if (reader != null) {
            try {
                // 소켓 타임아웃 설정
                int originalTimeout = socket.getSoTimeout();
                socket.setSoTimeout(timeoutMillis);
                
                String message = reader.readLine();
                
                // 원래 타임아웃으로 복원
                socket.setSoTimeout(originalTimeout);
                
                return message;
            } catch (SocketTimeoutException e) {
                System.out.println("메시지 수신 타임아웃 (" + timeoutMillis + "ms)");
                return null;
            }
        }
        return null;
    }
    
    /**
     * 객체 전송 (직렬화)
     * @param obj 전송할 객체 (Serializable 구현 필요)
     * @throws IOException 전송 실패 시
     */
    public void sendObject(Object obj) throws IOException {
        if (objectOut != null) {
            objectOut.writeObject(obj);
            objectOut.flush();
            objectOut.reset(); // 캐시 초기화
        }
    }
    
    /**
     * 객체 수신 (역직렬화)
     * @return 수신한 객체
     * @throws IOException 수신 실패 시
     * @throws ClassNotFoundException 클래스를 찾을 수 없을 때
     */
    public Object receiveObject() throws IOException, ClassNotFoundException {
        if (objectIn != null) {
            return objectIn.readObject();
        }
        return null;
    }
    
    /**
     * 바이트 배열 전송
     * @param data 전송할 데이터
     * @throws IOException 전송 실패 시
     */
    public void sendBytes(byte[] data) throws IOException {
        OutputStream out = socket.getOutputStream();
        // 먼저 데이터 길이 전송
        DataOutputStream dataOut = new DataOutputStream(out);
        dataOut.writeInt(data.length);
        // 데이터 전송
        out.write(data);
        out.flush();
    }
    
    /**
     * 바이트 배열 수신
     * @return 수신한 데이터
     * @throws IOException 수신 실패 시
     */
    public byte[] receiveBytes() throws IOException {
        InputStream in = socket.getInputStream();
        // 먼저 데이터 길이 수신
        DataInputStream dataIn = new DataInputStream(in);
        int length = dataIn.readInt();
        
        // 데이터 수신
        byte[] data = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int bytesRead = in.read(data, totalRead, length - totalRead);
            if (bytesRead == -1) {
                throw new IOException("연결이 끊어졌습니다.");
            }
            totalRead += bytesRead;
        }
        return data;
    }
    
    /**
     * 연결이 활성 상태인지 확인
     * @return 연결 상태
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
    
    /**
     * 소켓의 원격 주소 반환
     * @return IP 주소
     */
    public String getRemoteAddress() {
        if (socket != null) {
            return socket.getInetAddress().getHostAddress();
        }
        return "Unknown";
    }
    
    /**
     * 소켓의 포트 번호 반환
     * @return 포트 번호
     */
    public int getPort() {
        if (socket != null) {
            return socket.getPort();
        }
        return -1;
    }
    
    /**
     * 스트림과 소켓 종료
     * @throws IOException 종료 실패 시
     */
    public void close() throws IOException {
        // 스트림 종료
        if (reader != null) {
            reader.close();
        }
        if (writer != null) {
            writer.close();
        }
        if (objectIn != null) {
            objectIn.close();
        }
        if (objectOut != null) {
            objectOut.close();
        }
        
        // 소켓 종료
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        System.out.println("스트림 매니저가 종료되었습니다.");
    }
    
    // 테스트용 메인 메서드
    public static void main(String[] args) {
        System.out.println("=== GameDataStreamManager 테스트 ===");
        System.out.println("서버와 클라이언트를 별도로 실행하여 테스트하세요.");
        System.out.println("\n1. 먼저 서버 테스트를 실행:");
        System.out.println("   java GameDataStreamManager server");
        System.out.println("\n2. 그 다음 클라이언트 테스트를 실행:");
        System.out.println("   java GameDataStreamManager client");
        
        if (args.length == 0) {
            System.out.println("\n인자를 지정하지 않았습니다. 기본값으로 서버 모드를 실행합니다.");
            runServer();
        } else if (args[0].equals("server")) {
            runServer();
        } else if (args[0].equals("client")) {
            runClient();
        }
    }
    
    private static void runServer() {
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(8888)) {
            System.out.println("서버가 포트 8888에서 대기 중...");
            Socket clientSocket = serverSocket.accept();
            System.out.println("클라이언트 접속: " + clientSocket.getInetAddress());
            
            GameDataStreamManager manager = new GameDataStreamManager(clientSocket);
            
            // 메시지 송수신 테스트
            System.out.println("\n=== 문자열 메시지 테스트 ===");
            manager.sendMessage("서버에서 보내는 메시지입니다.");
            String received = manager.receiveMessage();
            System.out.println("클라이언트로부터 수신: " + received);
            
            // 타임아웃 테스트
            System.out.println("\n=== 타임아웃 테스트 (5초) ===");
            String timeoutMsg = manager.receiveMessage(5000);
            System.out.println("수신 결과: " + (timeoutMsg != null ? timeoutMsg : "타임아웃"));
            
            manager.close();
            System.out.println("\n서버 테스트 완료");
            
        } catch (IOException e) {
            System.err.println("서버 오류: " + e.getMessage());
        }
    }
    
    private static void runClient() {
        try {
            System.out.println("localhost:8888로 접속 시도...");
            Socket socket = new Socket("localhost", 8888);
            System.out.println("서버에 연결되었습니다.");
            
            GameDataStreamManager manager = new GameDataStreamManager(socket);
            
            // 메시지 송수신 테스트
            System.out.println("\n=== 문자열 메시지 테스트 ===");
            String received = manager.receiveMessage();
            System.out.println("서버로부터 수신: " + received);
            manager.sendMessage("클라이언트에서 보내는 응답입니다.");
            
            // 타임아웃 테스트를 위해 메시지를 보내지 않음
            System.out.println("\n타임아웃 테스트를 위해 5초 대기...");
            Thread.sleep(6000);
            
            manager.close();
            System.out.println("\n클라이언트 테스트 완료");
            
        } catch (IOException | InterruptedException e) {
            System.err.println("클라이언트 오류: " + e.getMessage());
        }
    }
}
