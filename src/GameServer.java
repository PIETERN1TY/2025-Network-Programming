/*
 * 게임 서버 메인 클래스
 * MatchMakingThread와 RecordManager를 통합하여 완전한 게임 서버 구현
 */
import java.io.IOException;
import java.net.*;
import java.util.*;

public class GameServer {
    private ServerSocket serverSocket;
    private List<ClientHandler> waitingClients;
    private RecordManager recordManager;
    private int port;
    private boolean isRunning;
    private int nextGameId = 1;
    
    public GameServer(int port) {
        this.port = port;
        this.waitingClients = Collections.synchronizedList(new ArrayList<>());
        this.recordManager = new RecordManager("game_records.dat");
        this.isRunning = true;
    }
    
    /**
     * 서버 시작
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║     Hit & Run 게임 서버 시작됨        ║");
            System.out.println("║     포트: " + port + "                        ║");
            System.out.println("╚════════════════════════════════════════╝");
            
            // 클라이언트 접속 대기
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("\n[연결] 새로운 클라이언트: " + clientSocket.getInetAddress());
                    
                    // 클라이언트 핸들러 생성
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    handler.start();
                    
                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("[오류] 클라이언트 접속 처리 실패: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[오류] 서버 소켓 생성 실패: " + e.getMessage());
        } finally {
            shutdown();
        }
    }
    
    /**
     * 매칭 대기열에 클라이언트 추가
     */
    public synchronized void addToWaitingList(ClientHandler client) {
        waitingClients.add(client);
        System.out.println("[매칭] " + client.getNickname() + " 대기열 추가 (현재: " + waitingClients.size() + "명)");
        
        // 2명이 모이면 매칭
        if (waitingClients.size() >= 2) {
            ClientHandler player1 = waitingClients.remove(0);
            ClientHandler player2 = waitingClients.remove(0);
            
            System.out.println("[매칭] 완료! " + player1.getNickname() + " vs " + player2.getNickname());
            startGame(player1, player2);
        }
    }
    
    /**
     * 대기열에서 클라이언트 제거
     */
    public synchronized void removeFromWaitingList(ClientHandler client) {
        waitingClients.remove(client);
        System.out.println("[매칭] " + client.getNickname() + " 대기열 제거");
    }
    
    /**
     * 게임 시작
     */
    private void startGame(ClientHandler player1, ClientHandler player2) {
        // 랜덤으로 역할 배정
        Random random = new Random();
        boolean player1IsPitcher = random.nextBoolean();
        
        ClientHandler pitcher = player1IsPitcher ? player1 : player2;
        ClientHandler batter = player1IsPitcher ? player2 : player1;
        
        int gameId = nextGameId++;
        
        System.out.println("[게임 " + gameId + "] 시작");
        System.out.println("  투수: " + pitcher.getNickname());
        System.out.println("  타자: " + batter.getNickname());
        
        // 게임 스레드 생성 및 시작
        EnhancedGameThread gameThread = new EnhancedGameThread(
            gameId, pitcher, batter, recordManager
        );
        gameThread.start();
    }
    
    /**
     * 서버 종료
     */
    public void shutdown() {
        isRunning = false;
        
        System.out.println("\n[종료] 서버를 종료합니다...");
        
        // 대기 중인 클라이언트 연결 종료
        synchronized(waitingClients) {
            for (ClientHandler handler : waitingClients) {
                handler.disconnect();
            }
            waitingClients.clear();
        }
        
        // 서버 소켓 종료
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[오류] 서버 소켓 종료 실패: " + e.getMessage());
        }
        
        // 전적 저장
        recordManager.saveRecords();
        
        System.out.println("[종료] 서버가 종료되었습니다.");
    }
    
    /**
     * RecordManager 반환
     */
    public RecordManager getRecordManager() {
        return recordManager;
    }
    
    // 메인 메서드
    public static void main(String[] args) {
        final int PORT = 9999;
        
        GameServer server = new GameServer(PORT);
        
        // 서버 시작 (별도 스레드)
        new Thread(() -> server.start()).start();
        
        // 명령어 처리
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n명령어:");
        System.out.println("  status - 서버 상태 확인");
        System.out.println("  records - 전적 조회");
        System.out.println("  quit - 서버 종료");
        
        while (true) {
            System.out.print("\n> ");
            String command = scanner.nextLine().trim().toLowerCase();
            
            switch(command) {
                case "quit":
                case "exit":
                    server.shutdown();
                    scanner.close();
                    System.exit(0);
                    break;
                    
                case "status":
                    System.out.println("대기 중인 플레이어: " + server.waitingClients.size());
                    System.out.println("등록된 플레이어: " + server.recordManager.getTotalPlayers());
                    break;
                    
                case "records":
                    server.recordManager.printAllRecords();
                    break;
                    
                default:
                    System.out.println("알 수 없는 명령어: " + command);
            }
        }
    }
}

/**
 * 클라이언트 핸들러 - 개별 클라이언트 연결 관리
 */
class ClientHandler extends Thread {
    private Socket socket;
    private GameDataStreamManager streamManager;
    private GameServer server;
    private String nickname;
    private boolean isConnected;
    
    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        this.isConnected = true;
        
        try {
            this.streamManager = new GameDataStreamManager(socket);
        } catch (IOException e) {
            System.err.println("[오류] 스트림 초기화 실패: " + e.getMessage());
            isConnected = false;
        }
    }
    
    @Override
    public void run() {
        try {
            // 닉네임 수신 대기
            String nicknameMsg = streamManager.receiveMessage();
            if (nicknameMsg != null && nicknameMsg.startsWith(GameProtocol.SET_NICKNAME)) {
                nickname = GameProtocol.Parser.getData(nicknameMsg);
                System.out.println("[접속] " + nickname + " 입장");
                
                // 플레이어 등록 (신규면 등록, 기존이면 무시)
                server.getRecordManager().registerPlayer(nickname);
                
                // 매칭 대기열에 추가
                server.addToWaitingList(this);
                sendMessage(GameProtocol.WAITING_MATCH);
                
                // 매칭될 때까지 대기 (스레드 유지)
                // 게임이 시작되면 EnhancedGameThread가 통신을 담당
                while (isConnected && !socket.isClosed()) {
                    Thread.sleep(1000);
                }
            }
            
        } catch (IOException e) {
            System.err.println("[오류] 클라이언트 핸들러 오류: " + e.getMessage());
        } catch (InterruptedException e) {
            // 정상적인 종료
        }
    }
    
    public void sendMessage(String message) {
        if (streamManager != null) {
            streamManager.sendMessage(message);
        }
    }
    
    public String receiveMessage(int timeout) throws IOException {
        return streamManager.receiveMessage(timeout);
    }
    
    public String receiveMessage() throws IOException {
        return streamManager.receiveMessage();
    }
    
    public String getNickname() {
        return nickname != null ? nickname : "Unknown";
    }
    
    public GameDataStreamManager getStreamManager() {
        return streamManager;
    }
    
    public void disconnect() {
        isConnected = false;
        server.removeFromWaitingList(this);
        
        try {
            if (streamManager != null) {
                streamManager.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("[오류] 연결 종료 실패: " + e.getMessage());
        }
        
        // 스레드 인터럽트
        this.interrupt();
    }
}

/**
 * 강화된 게임 스레드 - 실제 게임 로직 처리
 */
class EnhancedGameThread extends Thread {
    private int gameId;
    private ClientHandler pitcher;
    private ClientHandler batter;
    private RecordManager recordManager;
    private RandomSpeedGenerator speedGenerator;
    private JudgementProcessor judgementProcessor;
    
    private int strikes = 0;
    private int balls = 0;
    private int outs = 0;
    private int inning = 1;
    private int pitcherScore = 0;
    private int batterScore = 0;
    private boolean isTopInning = true; // true: 초, false: 말
    
    // 주자 정보
    private boolean runner1st = false;
    private boolean runner2nd = false;
    private boolean runner3rd = false;
    
    public EnhancedGameThread(int gameId, ClientHandler pitcher, ClientHandler batter, RecordManager recordManager) {
        this.gameId = gameId;
        this.pitcher = pitcher;
        this.batter = batter;
        this.recordManager = recordManager;
        this.speedGenerator = new RandomSpeedGenerator();
        this.judgementProcessor = new JudgementProcessor();
    }
    
    @Override
    public void run() {
        try {
            System.out.println("[게임 " + gameId + "] 시작 준비");
            
            // 매칭 완료 알림
            pitcher.sendMessage(GameProtocol.MATCH_FOUND);
            batter.sendMessage(GameProtocol.MATCH_FOUND);
            
            Thread.sleep(500);
            
            // 게임 시작 알림 (화면 전환)
            pitcher.sendMessage(GameProtocol.MATCH_START);
            batter.sendMessage(GameProtocol.MATCH_START);
            
            Thread.sleep(500);
            
            // 역할 통보
            pitcher.sendMessage(GameProtocol.ROLE_PITCHER);
            batter.sendMessage(GameProtocol.ROLE_BATTER);
            
            System.out.println("[게임 " + gameId + "] 역할 배정 완료");
            System.out.println("  - 투수: " + pitcher.getNickname());
            System.out.println("  - 타자: " + batter.getNickname());
            
            Thread.sleep(1000);
            
            // 초기 점수 전송
            updateScore();
            
            // 게임 루프
            while (inning <= 9) {
                pitcher.sendMessage(GameProtocol.Builder.buildInning(inning));
                batter.sendMessage(GameProtocol.Builder.buildInning(inning));
                
                System.out.println("[게임 " + gameId + "] " + inning + "회 " + (isTopInning ? "초" : "말") + " 시작");
                
                playInning();
                
                if (outs >= 3) {
                    if (!isTopInning) {
                        // 회 종료 - 다음 이닝으로
                        inning++;
                        isTopInning = true;
                        
                        if (inning > 9) {
                            break;
                        }
                    } else {
                        // 공수 교대 (초 → 말)
                        isTopInning = false;
                    }
                    
                    swapRoles(); // 여기서 outs, strikes, balls, 주자 초기화
                }
            }
            
            // 게임 종료
            endGame();
            
        } catch (Exception e) {
            System.err.println("[게임 " + gameId + "] 오류: " + e.getMessage());
            e.printStackTrace();
        } finally {
            pitcher.disconnect();
            batter.disconnect();
        }
    }
    
    /**
     * 한 이닝 진행
     */
    private void playInning() throws IOException, InterruptedException {
        while (outs < 3) {
            // 새 타석 시작
            strikes = 0;
            balls = 0;
            updateCount();
            
            // 타석 진행
            boolean atBatFinished = false;
            while (!atBatFinished && outs < 3) {
                String result = playAtBat();
                
                // 타석 종료 조건: 안타, 홈런, 아웃, 삼진, 볼넷
                if (result.equals("HIT") || result.equals("HOMERUN") || result.equals("OUT")) {
                    atBatFinished = true;
                }
                
                // 삼진 체크
                if (strikes >= 3) {
                    outs++;
                    sendToAll("RESULT:STRIKEOUT:삼진 아웃!");
                    updateCount();
                    atBatFinished = true;
                }
                
                // 볼넷 체크
                if (balls >= 4) {
                    handleWalk();
                    sendToAll("RESULT:WALK:볼넷!");
                    updateCount();
                    updateScore();
                    atBatFinished = true;
                }
            }
        }
    }
    
    /**
     * 볼넷 처리
     */
    private void handleWalk() {
        if (runner1st && runner2nd && runner3rd) {
            // 만루: 3루 주자 득점
            batterScore++;
        } else if (runner1st && runner2nd) {
            // 1,2루: 3루로 밀림
            runner3rd = true;
        } else if (runner1st) {
            // 1루만: 2루로 밀림
            runner2nd = true;
        }
        // 타자 1루 출루
        runner1st = true;
    }
    
    /**
     * 한 타석 진행
     */
    private String playAtBat() throws IOException, InterruptedException {
        // 투수에게 투구 요청
        pitcher.sendMessage(GameProtocol.ACTION_PITCH);
        
        // 5초 대기 (타임아웃)
        String pitchData = null;
        try {
            pitchData = pitcher.receiveMessage(5500); // 여유있게 5.5초
        } catch (Exception e) {
            System.out.println("[게임 " + gameId + "] 투구 타임아웃");
        }
        
        if (pitchData == null || !pitchData.startsWith("PITCH:")) {
            balls++;
            sendToAll("RESULT:BALL:투구 시간 초과 - 볼!");
            updateCount();
            System.out.println("[게임 " + gameId + "] 볼 판정 (타임아웃)");
            return "BALL";
        }
        
        System.out.println("[게임 " + gameId + "] 투구 수신: " + pitchData);
        
        char pitchType = GameProtocol.Parser.getPitchType(pitchData);
        int speed = speedGenerator.generateSpeed(pitchType);
        
        System.out.println("[게임 " + gameId + "] 구종: " + pitchType + ", 구속: " + speed);
        
        // 타자에게 투구 정보 전송
        String pitchInfo = GameProtocol.Builder.buildPitchInfo(pitchType, speed);
        batter.sendMessage(pitchInfo);
        pitcher.sendMessage(pitchInfo);
        
        Thread.sleep(500);
        
        // 타자 스윙 대기
        batter.sendMessage(GameProtocol.ACTION_BAT);
        String swingData = null;
        try {
            swingData = batter.receiveMessage(3500); // 여유있게 3.5초
        } catch (Exception e) {
            System.out.println("[게임 " + gameId + "] 타격 타임아웃");
        }
        
        System.out.println("[게임 " + gameId + "] 타격 수신: " + swingData);
        
        boolean isSwing = swingData != null && swingData.equals(GameProtocol.SWING_YES);
        
        System.out.println("[게임 " + gameId + "] 스윙: " + isSwing);
        
        // 판정
        String result = judgementProcessor.judge(pitchType, speed, isSwing);
        String message = judgementProcessor.getResultMessage(result, pitchType, speed);
        
        System.out.println("[게임 " + gameId + "] 판정: " + result + " - " + message);
        
        sendToAll(GameProtocol.Builder.buildResult(result, message));
        
        // 카운트 및 점수 업데이트
        updateGameState(result);
        
        return result;
    }
    
    /**
     * 게임 상태 업데이트
     */
    private void updateGameState(String result) {
        switch(result) {
            case JudgementProcessor.STRIKE:
                strikes++;
                break;
            case JudgementProcessor.BALL:
                balls++;
                break;
            case JudgementProcessor.FOUL:
                if (strikes < 2) strikes++;
                break;
            case JudgementProcessor.HIT:
                // 안타: 주자 한 베이스씩 진루
                boolean new3rd = false;
                boolean new2nd = false;
                boolean new1st = true; // 타자 1루 출루
                
                // 3루 주자 -> 홈 (득점!)
                if (runner3rd) {
                    batterScore++;
                }
                
                // 2루 주자 -> 3루
                if (runner2nd) {
                    new3rd = true;
                }
                
                // 1루 주자 -> 2루
                if (runner1st) {
                    new2nd = true;
                }
                
                runner1st = new1st;
                runner2nd = new2nd;
                runner3rd = new3rd;
                break;
            case JudgementProcessor.HOMERUN:
                // 홈런: 타자 + 모든 주자 득점
                int runsScored = 1; // 타자
                if (runner1st) runsScored++;
                if (runner2nd) runsScored++;
                if (runner3rd) runsScored++;
                
                System.out.println("[게임 " + gameId + "] 홈런! " + runsScored + "점 득점");
                
                batterScore += runsScored;
                
                // 모든 베이스 클리어
                runner1st = false;
                runner2nd = false;
                runner3rd = false;
                break;
            case JudgementProcessor.OUT:
                outs++;
                break;
        }
        
        updateCount();
        updateScore();
    }
    
    /**
     * 카운트 업데이트 전송
     */
    private void updateCount() {
        String countMsg = GameProtocol.Builder.buildCount(strikes, balls, outs);
        sendToAll(countMsg);
    }
    
    /**
     * 점수 업데이트 전송
     */
    private void updateScore() {
        pitcher.sendMessage(GameProtocol.Builder.buildScore(pitcherScore, batterScore));
        batter.sendMessage(GameProtocol.Builder.buildScore(batterScore, pitcherScore));
    }
    
    /**
     * 공수 교대
     */
    private void swapRoles() {
        ClientHandler temp = pitcher;
        pitcher = batter;
        batter = temp;
        
        int tempScore = pitcherScore;
        pitcherScore = batterScore;
        batterScore = tempScore;
        
        // 주자 초기화
        runner1st = false;
        runner2nd = false;
        runner3rd = false;
        
        // 카운트 완전 초기화
        outs = 0;
        strikes = 0;
        balls = 0;
        
        pitcher.sendMessage(GameProtocol.SWITCH_SIDE);
        batter.sendMessage(GameProtocol.SWITCH_SIDE);
        
        updateCount();
        updateScore();
    }
    
    /**
     * 게임 종료 처리
     */
    private void endGame() {
        System.out.println("[게임 " + gameId + "] 종료 - " + 
            pitcher.getNickname() + " " + pitcherScore + " : " + 
            batterScore + " " + batter.getNickname());
        
        String winner, loser;
        if (pitcherScore > batterScore) {
            winner = pitcher.getNickname();
            loser = batter.getNickname();
            pitcher.sendMessage(GameProtocol.GAME_END + ":WIN");
            batter.sendMessage(GameProtocol.GAME_END + ":LOSE");
        } else if (batterScore > pitcherScore) {
            winner = batter.getNickname();
            loser = pitcher.getNickname();
            batter.sendMessage(GameProtocol.GAME_END + ":WIN");
            pitcher.sendMessage(GameProtocol.GAME_END + ":LOSE");
        } else {
            pitcher.sendMessage(GameProtocol.GAME_END + ":DRAW");
            batter.sendMessage(GameProtocol.GAME_END + ":DRAW");
            return;
        } 
        
        // 전적 기록
        recordManager.recordGameResult(winner, loser);
    }
    
    /**
     * 양쪽 클라이언트에 메시지 전송
     */
    private void sendToAll(String message) {
        pitcher.sendMessage(message);
        batter.sendMessage(message);
    }
}
