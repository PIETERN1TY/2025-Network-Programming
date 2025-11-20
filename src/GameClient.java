/*
 * 게임 클라이언트 - PitcherDecisionHandler의 UI를 활용한 완전한 클라이언트
 */
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.Socket;

public class GameClient extends JFrame {
    private Socket socket;
    private GameDataStreamManager streamManager;
    private String nickname;
    private String role; // "PITCHER" or "BATTER"
    
    // UI 컴포넌트
    private JPanel mainPanel;
    private CardLayout cardLayout;
    private GamePanel gamePanel;
    
    // 게임 상태
    private int strikes = 0;
    private int balls = 0;
    private int outs = 0;
    private int myScore = 0;
    private int opponentScore = 0;
    private int currentInning = 1;
    private boolean isMyTurn = false;
    private boolean waitingForInput = false;
    
    public GameClient(String serverAddress, int port) {
        super("Hit & Run - 야구 게임");
        
        try {
            // 서버 연결
            socket = new Socket(serverAddress, port);
            streamManager = new GameDataStreamManager(socket);
            
            initUI();
            
            // 닉네임 입력
            showNicknameDialog();
            
            // 서버 메시지 수신 시작
            startMessageListener();
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "서버 연결 실패: " + e.getMessage(),
                "연결 오류", 
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    
    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        
        // 대기 화면
        mainPanel.add(createWaitingPanel(), "WAITING");
        
        // 게임 화면
        gamePanel = new GamePanel(this);
        mainPanel.add(gamePanel, "GAME");
        
        add(mainPanel);
        setVisible(true);
        
        // 전역 키 리스너 추가 (KeyEventDispatcher 사용)
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                char key = Character.toUpperCase(e.getKeyChar());
                
                if (waitingForInput && role != null) {
                    if (role.equals("PITCHER") && (key == 'A' || key == 'S' || key == 'D' || key == 'F')) {
                        sendPitch(key);
                        return true;
                    } else if (role.equals("BATTER") && key == 'H') {
                        sendSwing();
                        return true;
                    }
                }
            }
            return false;
        });
    }
    
    private JPanel createWaitingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(34, 139, 34));
        
        JLabel waitLabel = new JLabel("매칭 대기 중...", SwingConstants.CENTER);
        waitLabel.setFont(new Font("맑은 고딕", Font.BOLD, 36));
        waitLabel.setForeground(Color.WHITE);
        
        panel.add(waitLabel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void showNicknameDialog() {
        nickname = JOptionPane.showInputDialog(this, 
            "닉네임을 입력하세요:", 
            "닉네임 설정", 
            JOptionPane.QUESTION_MESSAGE);
        
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = "Player" + System.currentTimeMillis() % 1000;
        }
        
        try {
            streamManager.sendMessage(GameProtocol.Builder.buildNickname(nickname));
            gamePanel.addLog("닉네임: " + nickname);
        } catch (Exception e) {
            gamePanel.addLog("닉네임 전송 실패: " + e.getMessage());
        }
    }
    
    private void startMessageListener() {
        new Thread(() -> {
            try {
                while (true) {
                    String message = streamManager.receiveMessage();
                    if (message == null) {
                        gamePanel.addLog("서버 연결이 끊어졌습니다.");
                        break;
                    }
                    handleServerMessage(message);
                }
            } catch (IOException e) {
                gamePanel.addLog("통신 오류: " + e.getMessage());
            }
        }).start();
    }
    
    private void handleServerMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("[수신] " + message); // 디버깅용 로그
            
            // 전체 메시지로 먼저 비교
            if (message.equals(GameProtocol.ROLE_PITCHER)) {
                role = "PITCHER";
                gamePanel.setRole("투수 (수비)");
                gamePanel.addLog(">>> 당신은 투수입니다!");
                return;
            }
            
            if (message.equals(GameProtocol.ROLE_BATTER)) {
                role = "BATTER";
                gamePanel.setRole("타자 (공격)");
                gamePanel.addLog(">>> 당신은 타자입니다!");
                return;
            }
            
            if (message.equals(GameProtocol.MATCH_FOUND)) {
                gamePanel.addLog("상대를 찾았습니다! 게임을 시작합니다...");
                return;
            }
            
            if (message.equals(GameProtocol.MATCH_START)) {
                cardLayout.show(mainPanel, "GAME");
                gamePanel.addLog("\n=== 게임 시작! ===\n");
                requestFocus();
                return;
            }
            
            if (message.equals(GameProtocol.ACTION_PITCH)) {
                if (role != null && role.equals("PITCHER")) {
                    waitingForInput = true;
                    isMyTurn = true;
                    gamePanel.addLog("[5초 안에 구종 선택! A/S/D/F]");
                    gamePanel.startTimer(5);
                }
                return;
            }
            
            if (message.equals(GameProtocol.ACTION_BAT)) {
                // 타자에게 스윙 요청이 올 때
                return;
            }
            
            if (message.equals(GameProtocol.SWITCH_SIDE)) {
                gamePanel.addLog("\n>>> 공수 교대!\n");
                role = role.equals("PITCHER") ? "BATTER" : "PITCHER";
                gamePanel.setRole(role.equals("PITCHER") ? "투수 (수비)" : "타자 (공격)");
                strikes = 0;
                balls = 0;
                gamePanel.updateCount(strikes, balls, outs);
                return;
            }
            
            // 접두사로 시작하는 메시지 처리
            if (message.startsWith("PITCH_INFO:")) {
                String[] pitchData = GameProtocol.Parser.parsePitchInfo(message);
                char pitchType = pitchData[0].charAt(0);
                String pitchName = getPitchName(pitchType);
                gamePanel.addLog("투구: " + pitchName + " (" + pitchData[1] + "km/h)");
                
                if (role != null && role.equals("BATTER")) {
                    waitingForInput = true;
                    isMyTurn = true;
                    gamePanel.addLog("[3초 안에 스윙 결정! H키]");
                    gamePanel.startTimer(3);
                }
                return;
            }
            
            if (message.startsWith("RESULT:")) {
                handleResult(message);
                waitingForInput = false;
                gamePanel.stopTimer();
                return;
            }
            
            if (message.startsWith("COUNT:")) {
                int[] count = GameProtocol.Parser.parseCount(message);
                strikes = count[0];
                balls = count[1];
                outs = count[2];
                gamePanel.updateCount(strikes, balls, outs);
                return;
            }
            
            if (message.startsWith("SCORE:")) {
                int[] scores = GameProtocol.Parser.parseScore(message);
                myScore = scores[0];
                opponentScore = scores[1];
                gamePanel.updateScore(myScore, opponentScore, currentInning);
                return;
            }
            
            if (message.startsWith("INNING:")) {
                currentInning = Integer.parseInt(GameProtocol.Parser.getData(message));
                gamePanel.addLog("\n=== " + currentInning + "회 시작 ===");
                gamePanel.updateScore(myScore, opponentScore, currentInning);
                return;
            }
            
            if (message.startsWith("GAME:END")) {
                handleGameEnd(message);
                return;
            }
        });
    }
    
    private void handleResult(String message) {
        String data = GameProtocol.Parser.getData(message);
        String[] parts = data.split(":", 2);
        String resultType = parts[0];
        
        String resultMsg = "";
        
        switch(resultType) {
            case "STRIKE": resultMsg = "스트라이크!"; break;
            case "BALL": resultMsg = "볼!"; break;
            case "FOUL": resultMsg = "파울!"; break;
            case "HIT": resultMsg = "안타!"; break;
            case "HOMERUN": resultMsg = "홈런!!!"; break;
            case "OUT": resultMsg = "아웃!"; break;
            case "WALK": resultMsg = "볼넷!"; break;
            case "STRIKEOUT": resultMsg = "삼진 아웃!"; break;
        }
        
        gamePanel.addLog(">>> " + resultMsg);
    }
    
    private void handleGameEnd(String message) {
        String[] parts = message.split(":");
        String result = parts[parts.length - 1];
        
        String endMsg = result.equals("WIN") ? "승리!" : result.equals("LOSE") ? "패배..." : "무승부";
        
        gamePanel.addLog("\n=== 게임 종료 ===");
        gamePanel.addLog("최종 점수: " + myScore + " : " + opponentScore);
        gamePanel.addLog(endMsg);
        
        JOptionPane.showMessageDialog(this, 
            endMsg + "\n최종 점수: " + myScore + " : " + opponentScore,
            "게임 종료", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private String getPitchName(char type) {
        switch(type) {
            case 'A': return "포크";
            case 'S': return "직구";
            case 'D': return "커브";
            case 'F': return "슬라이더";
            default: return "알 수 없음";
        }
    }
    
    public void sendPitch(char pitchType) {
        if (!waitingForInput || !role.equals("PITCHER")) return;
        
        try {
            streamManager.sendMessage(GameProtocol.Builder.buildPitch(pitchType));
            waitingForInput = false;
            isMyTurn = false;
            gamePanel.disablePitchInput();
            gamePanel.stopTimer();
            gamePanel.addLog("→ " + getPitchName(pitchType) + " 투구!");
        } catch (Exception e) {
            gamePanel.addLog("투구 전송 실패: " + e.getMessage());
        }
    }
    
    public void sendSwing() {
        if (!waitingForInput || !role.equals("BATTER")) return;
        
        try {
            streamManager.sendMessage(GameProtocol.SWING_YES);
            waitingForInput = false;
            isMyTurn = false;
            gamePanel.disableBatInput();
            gamePanel.stopTimer();
            gamePanel.addLog("→ 스윙!");
        } catch (Exception e) {
            gamePanel.addLog("스윙 전송 실패: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String serverAddress = JOptionPane.showInputDialog(
                null, "서버 주소:", "서버 연결", JOptionPane.QUESTION_MESSAGE);
            
            if (serverAddress == null || serverAddress.trim().isEmpty()) {
                serverAddress = "localhost";
            }
            
            new GameClient(serverAddress, 9999);
        });
    }
}

/**
 * 게임 화면 패널 - PitcherDecisionHandler 스타일 UI
 */
class GamePanel extends JPanel {
    private GameClient client;
    
    // 상태 레이블
    private JLabel roleLabel;
    private JLabel countLabel;
    private JLabel scoreLabel;
    private JLabel timerLabel;
    
    // 게임 로그
    private JTextArea logArea;
    
    // 타이머
    private Timer countdownTimer;
    private int remainingSeconds;
    
    public GamePanel(GameClient client) {
        this.client = client;
        setLayout(new BorderLayout(10, 10));
        setBackground(new Color(34, 139, 34));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        initComponents();
    }
    
    private void initComponents() {
        // 상단 정보 패널
        JPanel topPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        topPanel.setOpaque(false);
        
        roleLabel = createLabel("역할: 대기중", 24, Color.WHITE);
        countLabel = createLabel("S: 0  B: 0  O: 0", 20, Color.YELLOW);
        scoreLabel = createLabel("0 : 0 (1회)", 20, Color.CYAN);
        timerLabel = createLabel("", 18, Color.RED);
        
        topPanel.add(roleLabel);
        topPanel.add(countLabel);
        topPanel.add(scoreLabel);
        topPanel.add(timerLabel);
        
        // 중앙 로그 영역
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        logArea.setBackground(new Color(20, 20, 20));
        logArea.setForeground(Color.WHITE);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        // 하단 조작 안내
        JPanel bottomPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        bottomPanel.setOpaque(false);
        
        JLabel pitchLabel = createLabel("투수: A(포크) S(직구) D(커브) F(슬라이더)", 16, Color.ORANGE);
        JLabel batLabel = createLabel("타자: H(스윙)", 16, Color.CYAN);
        JLabel infoLabel = createLabel("※ 키보드로 입력하세요!", 14, Color.LIGHT_GRAY);
        
        bottomPanel.add(pitchLabel);
        bottomPanel.add(batLabel);
        bottomPanel.add(infoLabel);
        
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private JLabel createLabel(String text, int fontSize, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font("맑은 고딕", Font.BOLD, fontSize));
        label.setForeground(color);
        return label;
    }
    
    public void setRole(String role) {
        roleLabel.setText("역할: " + role);
        roleLabel.setForeground(role.contains("투수") ? Color.ORANGE : Color.CYAN);
    }
    
    public void updateCount(int s, int b, int o) {
        countLabel.setText(String.format("S: %d  B: %d  O: %d", s, b, o));
    }
    
    public void updateScore(int my, int opp, int inning) {
        scoreLabel.setText(String.format("%d : %d (%d회)", my, opp, inning));
    }
    
    public void addLog(String text) {
        logArea.append(text + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
    
    public void enablePitchInput() {
        boolean pitchInputEnabled = true;
        boolean batInputEnabled = false;
    }
    
    public void disablePitchInput() {
        boolean pitchInputEnabled = false;
    }
    
    public void enableBatInput() {
        boolean batInputEnabled = true;
        boolean pitchInputEnabled = false;
    }
    
    public void disableBatInput() {
        boolean batInputEnabled = false;
    }
    
    public void disableAllInput() {
        boolean pitchInputEnabled = false;
        boolean batInputEnabled = false;
    }
    
    public void startTimer(int seconds) {
        remainingSeconds = seconds;
        updateTimerLabel();
        
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        
        countdownTimer = new Timer(1000, e -> {
            remainingSeconds--;
            updateTimerLabel();
            
            if (remainingSeconds <= 0) {
                stopTimer();
                timerLabel.setText("시간 초과!");
            }
        });
        countdownTimer.start();
    }
    
    public void stopTimer() {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        timerLabel.setText("");
    }
    
    private void updateTimerLabel() {
        timerLabel.setText("⏱ " + remainingSeconds + "초");
    }
    
    public void keyPressed(KeyEvent e) {
        char key = Character.toUpperCase(e.getKeyChar());
        
        boolean pitchInputEnabled = false;
		boolean batInputEnabled = false;
		if (pitchInputEnabled) {
            if (key == 'A' || key == 'S' || key == 'D' || key == 'F') {
                client.sendPitch(key);
            }
        } else if (batInputEnabled) {
            if (key == 'H') {
                client.sendSwing();
            }
        }
    }
    
    public void keyReleased(KeyEvent e) {}
    
    public void keyTyped(KeyEvent e) {}
}