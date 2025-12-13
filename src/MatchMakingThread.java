/*
 * ServerSocket이 accept()를 통해 접속한 클라이언트 소켓들을 관리하고,
 * 2인 매칭이 완료되면 게임 스레드를 시작하도록 관리하는 역할
 */
import java.net.*;
import java.awt.Image;
import java.io.*;
import java.util.*;

public class MatchMakingThread extends Thread {
    private ServerSocket serverSocket;
    private List<Socket> waitingClients;
    private int port;
    private boolean isRunning;
    
    
    public MatchMakingThread(int port) {
        this.port = port;
        this.waitingClients = Collections.synchronizedList(new ArrayList<>());
        this.isRunning = true;
    }
    
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("매칭 서버가 포트 " + port + "에서 시작되었습니다.");
            
            while (isRunning) {
                try {
                    // 클라이언트 접속 대기
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("새로운 클라이언트 접속: " + clientSocket.getInetAddress());
                    
                    // 대기 목록에 추가
                    synchronized(waitingClients) {
                        waitingClients.add(clientSocket);
                        System.out.println("현재 대기 중인 클라이언트 수: " + waitingClients.size());
                        
                        // 2명이 모이면 게임 시작
                        if (waitingClients.size() >= 2) {
                            Socket player1 = waitingClients.remove(0);
                            Socket player2 = waitingClients.remove(0);
                            
                            System.out.println("\n매칭 완료! 게임을 시작합니다.");
                            startGame(player1, player2);
                        }
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("클라이언트 접속 처리 중 오류: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("서버 소켓 생성 실패: " + e.getMessage());
        } finally {
            shutdown();
        }
    }
    
    /**
     * 매칭된 두 플레이어로 게임 스레드를 시작
     * @param player1 첫 번째 플레이어 소켓
     * @param player2 두 번째 플레이어 소켓
     */
    private void startGame(Socket player1, Socket player2) {
        // 랜덤으로 역할 배정 (투수/타자)
        Random random = new Random();
        boolean player1IsPitcher = random.nextBoolean();
        
        Socket pitcher = player1IsPitcher ? player1 : player2;
        Socket batter = player1IsPitcher ? player2 : player1;
        
        System.out.println("플레이어 1 역할: " + (player1IsPitcher ? "투수" : "타자"));
        System.out.println("플레이어 2 역할: " + (player1IsPitcher ? "타자" : "투수"));
        
        // 게임 스레드 생성 및 시작
        GameThread gameThread = new GameThread(pitcher, batter);
        gameThread.start();
    }
    
    /**
     * 서버 종료 메서드
     */
    public void shutdown() {
        isRunning = false;
        
        // 대기 중인 클라이언트 연결 종료
        synchronized(waitingClients) {
            for (Socket socket : waitingClients) {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("클라이언트 소켓 종료 실패: " + e.getMessage());
                }
            }
            waitingClients.clear();
        }
        
        // 서버 소켓 종료
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("서버 소켓이 종료되었습니다.");
            }
        } catch (IOException e) {
            System.err.println("서버 소켓 종료 실패: " + e.getMessage());
        }
    }
    
    /**
     * 대기 중인 클라이언트 수 반환
     * @return 대기 중인 클라이언트 수
     */
    public int getWaitingCount() {
        synchronized(waitingClients) {
            return waitingClients.size();
        }
    }
    
    // 테스트용 메인 메서드
    public static void main(String[] args) {
        final int PORT = 9999;
        
        MatchMakingThread server = new MatchMakingThread(PORT);
        server.start();
        
        // 서버 종료를 위한 간단한 명령 처리
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n서버를 종료하려면 'quit'를 입력하세요.");
        
        while (true) {
            String command = scanner.nextLine();
            if (command.equalsIgnoreCase("quit")) {
                System.out.println("서버를 종료합니다...");
                server.shutdown();
                break;
            } else if (command.equalsIgnoreCase("status")) {
                System.out.println("현재 대기 중인 클라이언트: " + server.getWaitingCount());
            }
        }
        
        scanner.close();
    }
}

/**
 * 매칭된 두 플레이어 간의 게임을 관리하는 스레드
 */
class GameThread extends Thread {
    private Socket pitcherSocket;
    private Socket batterSocket;
    private GameDataStreamManager pitcherStream;
    private GameDataStreamManager batterStream;
    private RandomSpeedGenerator speedGenerator;
    private JudgementProcessor judgementProcessor;
    
    private int strikes = 0;
    private int balls = 0;
    private int outs = 0;
    private int inning = 1;
    private int pitcherScore = 0;
    private int batterScore = 0;
    
    // 주자 정보
    private boolean runner1st = false;
    private boolean runner2nd = false;
    private boolean runner3rd = false;
    
    public GameThread(Socket pitcherSocket, Socket batterSocket) {
        this.pitcherSocket = pitcherSocket;
        this.batterSocket = batterSocket;
        this.speedGenerator = new RandomSpeedGenerator();
        this.judgementProcessor = new JudgementProcessor();
    }
    
    @Override
    public void run() {
        try {
            // 스트림 매니저 초기화
            pitcherStream = new GameDataStreamManager(pitcherSocket);
            batterStream = new GameDataStreamManager(batterSocket);
            
            // 역할 정보 전송
            pitcherStream.sendMessage("ROLE:PITCHER");
            batterStream.sendMessage("ROLE:BATTER");
            
            System.out.println("게임 시작!");
            
            // 게임 루프
            while (inning <= 9 && isGameActive()) {
                playInning();
                
                if (outs >= 3) {
                    // 공수 교대
                    swapRoles();
                    outs = 0;
                    inning++;
                    
                    if (inning > 9 || (inning == 9 && pitcherScore > batterScore)) {
                        break;
                    }
                }
            }
            
            // 게임 종료 처리
            endGame();
            
        } catch (IOException e) {
            System.err.println("게임 스레드 오류: " + e.getMessage());
        } finally {
            closeConnections();
        }
    }
    
    /**
     * 한 이닝 진행
     */
    private void playInning() throws IOException {
        while (outs < 3) {
            // 새로운 타석 시작 - 카운트 초기화
            strikes = 0;
            balls = 0;
            sendCountUpdate(); // 클라이언트에 0-0 카운트 전송
            
            // 한 타석 진행 (결과가 나올 때까지)
            boolean atBatFinished = false;
            while (!atBatFinished && outs < 3) {
                String result = playAtBat();
                
                // 타석 종료 조건 체크
                if (result.equals("HIT") || result.equals("HOMERUN") || 
                    result.equals("OUT")) {
                    atBatFinished = true;
                }
                
                // 삼진 체크
                if (strikes >= 3) {
                    outs++;
                    pitcherStream.sendMessage("RESULT:STRIKEOUT");
                    batterStream.sendMessage("RESULT:STRIKEOUT");
                    atBatFinished = true;
                }
                
                // 볼넷 체크
                if (balls >= 4) {
                    handleWalk();
                    atBatFinished = true;
                }
            }
        }
    }
    
    /**
     * 볼넷 처리
     */
    private void handleWalk() throws IOException {
        // 볼넷: 주자 진루 처리
        if (runner1st && runner2nd && runner3rd) {
            // 만루: 타자 출루, 3루 주자 득점
            batterScore++;
        } else if (runner1st && runner2nd) {
            // 1,2루: 3루로 밀림
            runner3rd = true;
        } else if (runner1st) {
            // 1루만: 2루로 밀림
            runner2nd = true;
        }
        // 타자는 1루 출루
        runner1st = true;
        
        batterStream.sendMessage("RESULT:WALK");
        pitcherStream.sendMessage("RESULT:WALK");
    }
    
    /**
     * 한 타석 진행 (투구 -> 타격 -> 판정)
     * @return 판정 결과
     */
    private String playAtBat() throws IOException {
        // 투수에게 투구 요청 (5초 제한)
        pitcherStream.sendMessage("ACTION:PITCH");
        String pitchData = pitcherStream.receiveMessage(5000);
        
        if (pitchData == null || !pitchData.startsWith("PITCH:")) {
            // 타임아웃 또는 잘못된 응답 - 볼 처리
            balls++;
            sendCountUpdate();
            return "BALL";
        }
        
        char pitchType = pitchData.charAt(6); // "PITCH:S" 형태
        int speed = speedGenerator.generateSpeed(pitchType);
        
        // 타자에게 투구 정보 전송 및 스윙 여부 요청 (3초 제한)
        batterStream.sendMessage("PITCH:" + pitchType + ":" + speed);
        String swingData = batterStream.receiveMessage(3000);
        
        boolean isSwing = swingData != null && swingData.equals("SWING:YES");
        
        // 판정
        String result = judgementProcessor.judge(pitchType, speed, isSwing);
        String message = judgementProcessor.getResultMessage(result, pitchType, speed);
        
        // 결과 전송
        pitcherStream.sendMessage("RESULT:" + result + ":" + message);
        batterStream.sendMessage("RESULT:" + result + ":" + message);
        
        // 카운트 업데이트
        updateCount(result);
        
        // 클라이언트에게 업데이트된 카운트 전송
        sendCountUpdate();
        
        return result;
    }
    
    /**
     * 카운트 및 점수 업데이트
     */
    private void updateCount(String result) {
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
                // 안타: 주자들을 한 베이스씩 진루 (역순 처리)
                boolean new3rd = false;
                boolean new2nd = false;
                boolean new1st = true; // 타자는 1루 출루
                
                // 3루 주자 -> 홈 (득점!)
                if (runner3rd) {
                    batterScore++;
                }
                
                // 2루 주자 -> 3루 진루
                if (runner2nd) {
                    new3rd = true;
                }
                
                // 1루 주자 -> 2루 진루
                if (runner1st) {
                    new2nd = true;
                }
                
                // 새로운 주자 상황 업데이트
                runner1st = new1st;
                runner2nd = new2nd;
                runner3rd = new3rd;
                
                // strikes, balls 초기화는 playInning에서 처리
                break;
            case JudgementProcessor.HOMERUN:
                // 홈런: 현재 주자 수 + 타자(1점) = 득점
                int runsScored = 1; // 타자
                if (runner1st) runsScored++;
                if (runner2nd) runsScored++;
                if (runner3rd) runsScored++;
                
                System.out.println("[홈런 득점] 1루:" + runner1st + " 2루:" + runner2nd + " 3루:" + runner3rd + " → " + runsScored + "점");
                
                batterScore += runsScored;
                
                // 모든 주자 클리어
                runner1st = false;
                runner2nd = false;
                runner3rd = false;
                
                // strikes, balls 초기화는 playInning에서 처리
                break;
            case JudgementProcessor.OUT:
                outs++;
                // strikes, balls 초기화는 playInning에서 처리
                break;
        }
    }
    
    /**
     * 클라이언트에게 현재 카운트 전송
     */
    private void sendCountUpdate() {
        String countMsg = GameProtocol.Builder.buildCount(strikes, balls, outs);
        pitcherStream.sendMessage(countMsg);
        batterStream.sendMessage(countMsg);
    }
    
    /**
     * 공수 교대
     */
    private void swapRoles() throws IOException {
        Socket temp = pitcherSocket;
        pitcherSocket = batterSocket;
        batterSocket = temp;
        
        GameDataStreamManager tempStream = pitcherStream;
        pitcherStream = batterStream;
        batterStream = tempStream;
        
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
        
        pitcherStream.sendMessage("ROLE:PITCHER");
        batterStream.sendMessage("ROLE:BATTER");
        pitcherStream.sendMessage("SWITCH:SIDE");
        batterStream.sendMessage("SWITCH:SIDE");
        
        // 초기화된 카운트 전송
        sendCountUpdate();
    }
    
    /**
     * 게임이 활성 상태인지 확인
     */
    private boolean isGameActive() {
        return !pitcherSocket.isClosed() && !batterSocket.isClosed();
    } 
    
    /**
     * 게임 종료 처리
     */
    private void endGame() throws IOException {
        String winner = pitcherScore > batterScore ? "PITCHER" : "BATTER";
        pitcherStream.sendMessage("GAME_END:SCORE:" + pitcherScore + ":" + batterScore + ":WINNER:" + winner);
        batterStream.sendMessage("GAME_END:SCORE:" + batterScore + ":" + pitcherScore + ":WINNER:" + winner);
        
        System.out.println("게임 종료 - 최종 점수: " + pitcherScore + " : " + batterScore);
    }
    
    /**
     * 연결 종료
     */
    private void closeConnections() {
        try {
            if (pitcherStream != null) pitcherStream.close();
            if (batterStream != null) batterStream.close();
            if (pitcherSocket != null) pitcherSocket.close();
            if (batterSocket != null) batterSocket.close();
        } catch (IOException e) {
            System.err.println("연결 종료 중 오류: " + e.getMessage());
        }
    }
}
