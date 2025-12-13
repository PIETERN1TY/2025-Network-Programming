/*
 * 게임 클라이언트 - 완전한 UI 구현
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
    private String role;
    
    private JPanel mainPanel;
    private CardLayout cardLayout;
    private GamePanel gamePanel;
    
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
            socket = new Socket(serverAddress, port);
            streamManager = new GameDataStreamManager(socket);
            
            initUI();
            showNicknameDialog();
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
        
        mainPanel.add(createWaitingPanel(), "WAITING");
        
        gamePanel = new GamePanel(this);
        mainPanel.add(gamePanel, "GAME");
        
        add(mainPanel);
        setVisible(true);
        
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                int keyCode = e.getKeyCode();
                
                if (waitingForInput && role != null) {
                    if (role.equals("PITCHER")) {
                        if (keyCode == KeyEvent.VK_A) { sendPitch('A'); return true; }
                        if (keyCode == KeyEvent.VK_S) { sendPitch('S'); return true; }
                        if (keyCode == KeyEvent.VK_D) { sendPitch('D'); return true; }
                        if (keyCode == KeyEvent.VK_F) { sendPitch('F'); return true; }
                    } else if (role.equals("BATTER")) {
                        if (keyCode == KeyEvent.VK_H) { sendSwing(); return true; }
                    }
                }
            }
            return false;
        });
    }
    
    private JPanel createWaitingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        panel.setBackground(Color.DARK_GRAY);
        
        JLabel waitLabel = new JLabel("매칭 대기 중...", SwingConstants.CENTER);
        waitLabel.setFont(new Font("맑은 고딕", Font.BOLD, 36));
        waitLabel.setForeground(Color.WHITE);
        
        panel.add(waitLabel, BorderLayout.CENTER);
        return panel;
    }
    //닉네임 설정 메세지 창 
    private void showNicknameDialog() {
        nickname = JOptionPane.showInputDialog(this, "닉네임을 입력하세요:", "닉네임 설정", JOptionPane.QUESTION_MESSAGE);
        
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = "Player" + System.currentTimeMillis() % 1000;
        }
        
        try {
            streamManager.sendMessage(GameProtocol.Builder.buildNickname(nickname));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void startMessageListener() {
        new Thread(() -> {
            try {
                while (true) {
                    String message = streamManager.receiveMessage();
                    if (message == null) break;
                    handleServerMessage(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void handleServerMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("[수신] " + message);
            
            if (message.equals(GameProtocol.ROLE_PITCHER)) {
                role = "PITCHER";
                gamePanel.setRole("PITCHER");
                return;
            }
            
            if (message.equals(GameProtocol.ROLE_BATTER)) {
                role = "BATTER";
                gamePanel.setRole("BATTER");
                return;
            }
            
            if (message.equals(GameProtocol.MATCH_START)) {
                cardLayout.show(mainPanel, "GAME");
                requestFocus();
                return;
            }
            
            if (message.equals(GameProtocol.ACTION_PITCH)) {
                if (role != null && role.equals("PITCHER")) {
                    waitingForInput = true;
                    isMyTurn = true;
                    gamePanel.startTimer(5);
                    gamePanel.startPitchAnimation();  // 투구 애니메이션
                }
                return;
            }
            
            if (message.equals(GameProtocol.SWITCH_SIDE)) {
                role = role.equals("PITCHER") ? "BATTER" : "PITCHER";
                gamePanel.setRole(role);
                strikes = 0;
                balls = 0;
                gamePanel.updateCount(strikes, balls, outs);
                gamePanel.clearBases();
                gamePanel.switchInningHalf(); // 초/말 전환
                return;
            }
            
            if (message.startsWith("PITCH_INFO:")) {
                String[] pitchData = GameProtocol.Parser.parsePitchInfo(message);
                
                if (role != null && role.equals("BATTER")) {
                    waitingForInput = true;
                    isMyTurn = true;
                    gamePanel.startTimer(3);
                    gamePanel.startPitchAnimation();  // 타자 시점에서도 투구 보이기
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
        
        // 결과 이펙트 표시
        gamePanel.showResult(resultType);
        
        switch(resultType) {
            case "HIT": 
                gamePanel.advanceRunners("HIT");
                gamePanel.startSwingAnimation();
                break;
            case "HOMERUN": 
                gamePanel.advanceRunners("HOMERUN");
                gamePanel.startSwingAnimation();
                break;
            case "WALK": 
                gamePanel.advanceRunners("WALK");
                break;
            case "OUT":
                gamePanel.startSwingAnimation();
                break;
            case "STRIKE":
            case "BALL":
            case "FOUL":
                // 스트라이크/볼/파울은 이펙트만 표시
                break;
        }
    }
    
    private void handleGameEnd(String message) {
        String[] parts = message.split(":");
        String result = parts[parts.length - 1];
        String endMsg = result.equals("WIN") ? "승리!" : result.equals("LOSE") ? "패배..." : "무승부";
        
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
        if (!waitingForInput || role == null || !role.equals("PITCHER")) return;
        
        try {
            streamManager.sendMessage(GameProtocol.Builder.buildPitch(pitchType));
            waitingForInput = false;
            isMyTurn = false;
            gamePanel.stopTimer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void sendSwing() {
        if (!waitingForInput || role == null || !role.equals("BATTER")) return;
        
        try {
            streamManager.sendMessage(GameProtocol.SWING_YES);
            waitingForInput = false;
            isMyTurn = false;
            gamePanel.stopTimer();
            gamePanel.startSwingAnimation();  // 스윙 애니메이션
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String serverAddress = JOptionPane.showInputDialog(null, "서버 주소:", "서버 연결", JOptionPane.QUESTION_MESSAGE);
            if (serverAddress == null || serverAddress.trim().isEmpty()) {
                serverAddress = "localhost";
            }
            new GameClient(serverAddress, 9999);
        });
    }
}

/**
 * 게임 화면 패널 - 완전한 UI
 */
class GamePanel extends JPanel {
    private GameClient client;
    
    // 이미지
    private Image backgroundImage;
    private Image pitcherImage;
    private Image batterImage;
    
    // 게임 상태
    private int balls = 0;
    private int strikes = 0;
    private int outs = 0;
    private int myScore = 0;
    private int oppScore = 0;
    private int inning = 1;
    private boolean isTopInning = true; // true: 초(▲), false: 말(▼)
    private String role = "";
    
    // 주자 상태
    private boolean runner1st = false;
    private boolean runner2nd = false;
    private boolean runner3rd = false;
    
    // 타이머
    private Timer countdownTimer;
    private int remainingSeconds = 0;
    
    // 애니메이션 관련
    private Timer animationTimer;
    private String currentAnimation = ""; // PITCH, SWING, HIT, HOMERUN, STRIKEOUT
    private int animationFrame = 0;
    private int maxAnimationFrames = 20;
    
    // 투구 애니메이션
    private int ballX = 360;
    private int ballY = 280;
    private boolean showBall = false;
    
    // 타격 이펙트
    private String lastResult = "";
    private int resultDisplayFrame = 0;
    private int maxResultFrames = 60;
    
    // 캐릭터 애니메이션 오프셋
    private int pitcherOffsetY = 0;
    private int batterOffsetX = 0;
    
    public GamePanel(GameClient client) {
        this.client = client;
        setPreferredSize(new Dimension(800, 600));
        loadImages();
        
        // 애니메이션 타이머 (60 FPS)
        animationTimer = new Timer(16, e -> {
            if (!currentAnimation.isEmpty()) {
                animationFrame++;
                updateAnimation();
                repaint();
                
                if (animationFrame >= maxAnimationFrames) {
                    stopAnimation();
                }
            }
            
            if (!lastResult.isEmpty()) {
                resultDisplayFrame++;
                if (resultDisplayFrame >= maxResultFrames) {
                    lastResult = "";
                    resultDisplayFrame = 0;
                }
                repaint();
            }
        });
        animationTimer.start();
    }
    
    private void loadImages() {
        String[] paths = {"images/", "../images/", "/Users/jiwonlee/eclipse-workspace/BaseballGame/images/"};
        
        for (String path : paths) {
            java.io.File f = new java.io.File(path + "background.jpg");
            if (f.exists()) {
                backgroundImage = new ImageIcon(path + "background.jpg").getImage();
                pitcherImage = new ImageIcon(path + "pitcher.png").getImage();
                batterImage = new ImageIcon(path + "batter.png").getImage();
                
                System.out.println("이미지 로드 완료: " + path);
                return;
            }
        }
        System.err.println("이미지를 찾을 수 없습니다.");
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        // 배경
        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
            // 어두운 오버레이로 중계 느낌
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRect(0, 0, getWidth(), getHeight());
        } else {
            // 기본 진한 그린 배경
            g2.setColor(new Color(20, 60, 20));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
        
        // 캐릭터 (애니메이션 오프셋 적용)
        if (pitcherImage != null) {
            g.drawImage(pitcherImage, 360, 250 + pitcherOffsetY, 80, 100, this);
        }
        if (batterImage != null) {
            g.drawImage(batterImage, 360 + batterOffsetX, 420, 80, 100, this);
        }
        
        // 투구 공 그리기
        if (showBall) {
            drawBall(g2);
        }
        
        // 결과 이펙트 그리기
        if (!lastResult.isEmpty()) {
            drawResultEffect(g2);
        }
        
        // UI 그리기
        drawScoreboard(g2);
        drawBaseDiamond(g2);
        drawBSOCount(g2);
        drawControlInfo(g2);
    }
    
    private void drawScoreboard(Graphics2D g) {
        int x = 20, y = 15, w = 250, h = 110;
        
        // 점수 결정 (타자=공격팀, 투수=수비팀)
        int attackScore, defenseScore;
        if (role.equals("BATTER")) {
            attackScore = myScore;
            defenseScore = oppScore;
        } else {
            attackScore = oppScore;
            defenseScore = myScore;
        }
        
        // 메인 스코어보드 배경 (진한 네이비)
        g.setColor(new Color(15, 32, 60));
        g.fillRoundRect(x, y, w, h, 12, 12);
        
        // 금색 테두리
        g.setColor(new Color(218, 165, 32));
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(x, y, w, h, 12, 12);
        
        // 이닝 표시 (상단 중앙)
        g.setColor(new Color(218, 165, 32));
        g.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        String inningText = inning + (isTopInning ? "회초" : "회말");
        FontMetrics fm = g.getFontMetrics();
        int inningWidth = fm.stringWidth(inningText);
        g.drawString(inningText, x + (w - inningWidth) / 2, y + 25);
        
        // 팀 스코어 레이아웃
        int scoreY = y + 50;
        int scoreSpacing = 30;
        
        // AWAY (공격팀)
        g.setColor(new Color(220, 53, 69));
        g.fillRoundRect(x + 10, scoreY, 90, 22, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        g.drawString("AWAY", x + 20, scoreY + 16);
        
        // 공격팀 점수
        g.setColor(Color.WHITE);
        g.setFont(new Font("Impact", Font.BOLD, 32));
        g.drawString(String.valueOf(attackScore), x + 115, scoreY + 20);
        
        // HOME (수비팀)
        scoreY += scoreSpacing;
        g.setColor(new Color(0, 102, 204));
        g.fillRoundRect(x + 10, scoreY, 90, 22, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        g.drawString("HOME", x + 20, scoreY + 16);
        
        // 수비팀 점수
        g.setColor(Color.WHITE);
        g.setFont(new Font("Impact", Font.BOLD, 32));
        g.drawString(String.valueOf(defenseScore), x + 115, scoreY + 20);
    }
    
    private void drawBaseDiamond(Graphics2D g) {
        int cx = 400, cy = 55, size = 80;
        
        // 베이스 다이아몬드 배경
        g.setColor(new Color(15, 32, 60));
        g.fillRoundRect(cx - size/2 - 10, cy - size/2 - 10, size + 20, size + 30, 12, 12);
        
        // 금색 테두리
        g.setColor(new Color(218, 165, 32));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(cx - size/2 - 10, cy - size/2 - 10, size + 20, size + 30, 12, 12);
        
        // 다이아몬드 연결선 (흐린 흰색)
        g.setColor(new Color(255, 255, 255, 60));
        g.setStroke(new BasicStroke(1.5f));
        int[] xLine = {cx, cx + 25, cx, cx - 25, cx};
        int[] yLine = {cy - 20, cy, cy + 20, cy, cy - 20};
        g.drawPolyline(xLine, yLine, 5);
        
        // 베이스 3개
        drawBase(g, cx, cy - 20, runner2nd);      // 2루 (위)
        drawBase(g, cx - 25, cy, runner3rd);      // 3루 (왼쪽)
        drawBase(g, cx + 25, cy, runner1st);      // 1루 (오른쪽)
        
        // 홈 플레이트 (작은 오각형)
        int[] xHome = {cx - 6, cx + 6, cx + 6, cx, cx - 6};
        int[] yHome = {cy + 20, cy + 20, cy + 26, cy + 29, cy + 26};
        g.setColor(new Color(240, 240, 240));
        g.fillPolygon(xHome, yHome, 5);
        g.setColor(new Color(100, 100, 100));
        g.drawPolygon(xHome, yHome, 5);
    }
    
    private void drawBase(Graphics2D g, int x, int y, boolean hasRunner) {
        int size = 16;
        int[] xp = {x, x + size/2, x, x - size/2};
        int[] yp = {y - size/2, y, y + size/2, y};
        
        // 베이스 색상 (주자 있으면 밝은 주황색, 없으면 흰색)
        if (hasRunner) {
            g.setColor(new Color(255, 152, 0));
            g.fillPolygon(xp, yp, 4);
            g.setColor(new Color(230, 130, 0));
        } else {
            g.setColor(new Color(240, 240, 240));
            g.fillPolygon(xp, yp, 4);
            g.setColor(new Color(180, 180, 180));
        }
        g.setStroke(new BasicStroke(2));
        g.drawPolygon(xp, yp, 4);
    }
    
    private void drawBSOCount(Graphics2D g) {
        int x = 550, y = 15, w = 230, h = 110;
        
        // 메인 배경 (진한 네이비)
        g.setColor(new Color(15, 32, 60));
        g.fillRoundRect(x, y, w, h, 12, 12);
        
        // 금색 테두리
        g.setColor(new Color(218, 165, 32));
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(x, y, w, h, 12, 12);
        
        int labelX = x + 20;
        int circleStartX = x + 80;
        int circleSize = 20;
        int circleSpacing = 30;
        
        g.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        
        // Ball (3개)
        g.setColor(new Color(100, 200, 100));
        g.drawString("B", labelX, y + 35);
        for (int i = 0; i < 3; i++) {
            if (i < balls) {
                g.setColor(new Color(76, 175, 80));
                g.fillOval(circleStartX + i * circleSpacing, y + 20, circleSize, circleSize);
                g.setColor(new Color(56, 142, 60));
            } else {
                g.setColor(new Color(40, 40, 40));
                g.fillOval(circleStartX + i * circleSpacing, y + 20, circleSize, circleSize);
                g.setColor(new Color(60, 60, 60));
            }
            g.setStroke(new BasicStroke(2));
            g.drawOval(circleStartX + i * circleSpacing, y + 20, circleSize, circleSize);
        }
        
        // Strike (2개)
        g.setColor(new Color(255, 193, 7));
        g.drawString("S", labelX, y + 68);
        for (int i = 0; i < 2; i++) {
            if (i < strikes) {
                g.setColor(new Color(255, 193, 7));
                g.fillOval(circleStartX + i * circleSpacing, y + 53, circleSize, circleSize);
                g.setColor(new Color(245, 127, 23));
            } else {
                g.setColor(new Color(40, 40, 40));
                g.fillOval(circleStartX + i * circleSpacing, y + 53, circleSize, circleSize);
                g.setColor(new Color(60, 60, 60));
            }
            g.setStroke(new BasicStroke(2));
            g.drawOval(circleStartX + i * circleSpacing, y + 53, circleSize, circleSize);
        }
        
        // Out (2개)
        g.setColor(new Color(244, 67, 54));
        g.drawString("O", labelX, y + 101);
        for (int i = 0; i < 2; i++) {
            if (i < outs) {
                g.setColor(new Color(244, 67, 54));
                g.fillOval(circleStartX + i * circleSpacing, y + 86, circleSize, circleSize);
                g.setColor(new Color(198, 40, 40));
            } else {
                g.setColor(new Color(40, 40, 40));
                g.fillOval(circleStartX + i * circleSpacing, y + 86, circleSize, circleSize);
                g.setColor(new Color(60, 60, 60));
            }
            g.setStroke(new BasicStroke(2));
            g.drawOval(circleStartX + i * circleSpacing, y + 86, circleSize, circleSize);
        }
        
        // 타이머 (우측 상단)
        if (remainingSeconds > 0) {
            g.setColor(remainingSeconds <= 2 ? new Color(244, 67, 54) : new Color(218, 165, 32));
            g.setFont(new Font("Impact", Font.BOLD, 28));
            g.drawString("0:" + String.format("%02d", remainingSeconds), x + w - 65, y + 35);
        }
    }
    
    private void drawControlInfo(Graphics2D g) {
        int y = getHeight() - 60;
        
        // 진한 그라데이션 배경
        GradientPaint gradient = new GradientPaint(
            0, y, new Color(15, 32, 60, 240),
            0, getHeight(), new Color(10, 20, 40, 250)
        );
        g.setPaint(gradient);
        g.fillRect(0, y, getWidth(), 60);
        
        // 상단 금색 라인
        g.setColor(new Color(218, 165, 32));
        g.setStroke(new BasicStroke(2));
        g.drawLine(0, y, getWidth(), y);
        
        // 조작키 안내
        g.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        
        // 투수 조작 (주황색)
        g.setColor(new Color(255, 193, 7));
        g.drawString("투수", 30, y + 22);
        g.setColor(new Color(230, 230, 230));
        g.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        g.drawString("A(포크) S(직구) D(커브) F(슬라이더)", 80, y + 22);
        
        // 타자 조작 (하늘색)
        g.setColor(new Color(33, 150, 243));
        g.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        g.drawString("타자", 30, y + 45);
        g.setColor(new Color(230, 230, 230));
        g.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        g.drawString("H(스윙)", 80, y + 45);
        
        // 현재 역할 표시 (우측)
        if (!role.isEmpty()) {
            String roleText = role.equals("PITCHER") ? "투수" : "타자";
            Color roleColor = role.equals("PITCHER") ? new Color(255, 193, 7) : new Color(33, 150, 243);
            
            g.setFont(new Font("맑은 고딕", Font.BOLD, 20));
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth("▶ " + roleText);
            
            // 배경 박스
            int boxX = getWidth() - textWidth - 50;
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(boxX - 10, y + 15, textWidth + 30, 32, 8, 8);
            
            // 테두리
            g.setColor(roleColor);
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(boxX - 10, y + 15, textWidth + 30, 32, 8, 8);
            
            // 텍스트
            g.setColor(roleColor);
            g.drawString("▶", boxX, y + 38);
            g.setColor(Color.WHITE);
            g.drawString(roleText, boxX + 20, y + 38);
        }
    }
    
    // 업데이트 메서드들
    public void setRole(String r) {
        this.role = r;
        repaint();
    }
    
    public void updateCount(int s, int b, int o) {
        this.strikes = s;
        this.balls = b;
        this.outs = o;
        repaint();
    }
    
    public void updateScore(int my, int opp, int inn) {
        this.myScore = my;
        this.oppScore = opp;
        this.inning = inn;
        repaint();
    }
    
    public void advanceRunners(String result) {
        switch(result) {
            case "HIT":
                if (runner3rd) runner3rd = false;
                if (runner2nd) { runner3rd = true; runner2nd = false; }
                if (runner1st) { runner2nd = true; }
                runner1st = true;
                break;
            case "HOMERUN":
                runner1st = runner2nd = runner3rd = false;
                break;
            case "WALK":
                if (runner1st && runner2nd) runner3rd = true;
                if (runner1st) runner2nd = true;
                runner1st = true;
                break;
        }
        repaint();
    }
    
    public void clearBases() {
        runner1st = runner2nd = runner3rd = false;
        repaint();
    }
     
    public void switchInningHalf() {
        if (isTopInning) {
            // 초 → 말
            isTopInning = false;
        } else {
            // 말 → 다음 이닝 초
            isTopInning = true;
            inning++;
        }
        repaint();
    }
    
    public void startTimer(int sec) {
        remainingSeconds = sec;
        if (countdownTimer != null) countdownTimer.stop();
        
        countdownTimer = new Timer(1000, e -> {
            remainingSeconds--;
            repaint();
            if (remainingSeconds <= 0) stopTimer();
        });
        countdownTimer.start();
        repaint();
    } 
    
    public void stopTimer() {
        if (countdownTimer != null) countdownTimer.stop();
        remainingSeconds = 0;
        repaint();
    } 
    
    public void addLog(String msg) {
        System.out.println("[로그] " + msg);
    }
    
    // ===== 애니메이션 메서드 =====
    
    /**
     * 투구 애니메이션 시작
     */
    public void startPitchAnimation() {
        currentAnimation = "PITCH";
        animationFrame = 0;
        ballX = 360;
        ballY = 280;
        showBall = true;
        pitcherOffsetY = 0;
    }
    
    /**
     * 타격 애니메이션 시작
     */
    public void startSwingAnimation() {
        currentAnimation = "SWING";
        animationFrame = 0;
        batterOffsetX = 0;
    }
    
    /**
     * 애니메이션 업데이트
     */
    private void updateAnimation() {
        float progress = (float) animationFrame / maxAnimationFrames;
        
        switch (currentAnimation) {
            case "PITCH":
                // 투수 모션 (위로 살짝 올라갔다 내려옴)
                if (progress < 0.3f) {
                    pitcherOffsetY = (int)(-15 * Math.sin(progress * Math.PI / 0.3));
                } else {
                    pitcherOffsetY = 0;
                }
                
                // 공이 투수 -> 타자로 이동
                ballY = 280 + (int)((450 - 280) * progress);
                
                // 공이 타자에게 도달하면 숨김
                if (progress > 0.95f) {
                    showBall = false;
                }
                break;
                
            case "SWING":
                // 타자 스윙 모션 (좌우로 휘두르기)
                if (progress < 0.5f) {
                    batterOffsetX = (int)(-30 * Math.sin(progress * Math.PI / 0.5));
                } else {
                    batterOffsetX = (int)(30 * Math.sin((progress - 0.5) * Math.PI / 0.5));
                }
                break;
        }
    }
    
    /**
     * 애니메이션 중지
     */
    private void stopAnimation() {
        currentAnimation = "";
        animationFrame = 0;
        showBall = false;
        pitcherOffsetY = 0;
        batterOffsetX = 0;
        repaint();
    }
    
    /**
     * 공 그리기
     */
    private void drawBall(Graphics2D g) {
        // 공 그림자
        g.setColor(new Color(0, 0, 0, 100));
        g.fillOval(ballX + 38, ballY + 12, 14, 8);
        
        // 야구공
        g.setColor(Color.WHITE);
        g.fillOval(ballX + 35, ballY + 5, 18, 18);
        
        // 봉합선
        g.setColor(new Color(200, 50, 50));
        g.setStroke(new BasicStroke(2));
        g.drawArc(ballX + 37, ballY + 7, 7, 14, 90, 180);
        g.drawArc(ballX + 44, ballY + 7, 7, 14, 270, 180);
    }
    
    /**
     * 결과 이펙트 그리기
     */
    private void drawResultEffect(Graphics2D g) {
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2 - 50;
        
        float alpha = 1.0f - ((float) resultDisplayFrame / maxResultFrames);
        if (alpha < 0) alpha = 0;
        
        String displayText = "";
        Color effectColor = Color.WHITE;
        int fontSize = 60;
        
        switch (lastResult) {
            case "HIT":
                displayText = "안타!";
                effectColor = new Color(76, 175, 80);
                break;
            case "HOMERUN":
                displayText = "홈런!!!";
                effectColor = new Color(255, 193, 7);
                fontSize = 80;
                // 폭죽 효과
                drawFireworks(g, centerX, centerY, resultDisplayFrame);
                break;
            case "OUT":
                displayText = "아웃!";
                effectColor = new Color(244, 67, 54);
                break;
            case "STRIKEOUT":
                displayText = "삼진!";
                effectColor = new Color(244, 67, 54);
                fontSize = 70;
                break;
            case "STRIKE":
                displayText = "스트라이크!";
                effectColor = new Color(255, 152, 0);
                fontSize = 50;
                break;
            case "BALL":
                displayText = "볼!";
                effectColor = new Color(76, 175, 80);
                fontSize = 50;
                break;
            case "FOUL":
                displayText = "파울!";
                effectColor = new Color(156, 39, 176);
                fontSize = 50;
                break;
            case "WALK":
                displayText = "볼넷!";
                effectColor = new Color(33, 150, 243);
                break;
        }
        
        if (!displayText.isEmpty()) {
            // 텍스트 크기 애니메이션 (처음에 커졌다가 작아짐)
            float scale = 1.0f + (1.0f - alpha) * 0.3f;
            if (resultDisplayFrame < 10) {
                scale = 0.5f + (resultDisplayFrame / 10.0f) * 0.8f;
            }
            
            Font font = new Font("맑은 고딕", Font.BOLD, (int)(fontSize * scale));
            g.setFont(font);
            
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(displayText);
            
            // 텍스트 그림자
            g.setColor(new Color(0, 0, 0, (int)(200 * alpha)));
            g.drawString(displayText, centerX - textWidth/2 + 3, centerY + 3);
            
            // 메인 텍스트
            g.setColor(new Color(effectColor.getRed(), effectColor.getGreen(), 
                                  effectColor.getBlue(), (int)(255 * alpha)));
            g.drawString(displayText, centerX - textWidth/2, centerY);
            
            // 외곽선
            g.setStroke(new BasicStroke(3));
            g.setColor(new Color(255, 255, 255, (int)(150 * alpha)));
            g.drawString(displayText, centerX - textWidth/2, centerY);
        }
    }
    
    /**
     * 홈런 폭죽 효과
     */
    private void drawFireworks(Graphics2D g, int centerX, int centerY, int frame) {
        if (frame > 15) {
            for (int i = 0; i < 12; i++) {
                double angle = (i * Math.PI * 2) / 12;
                int distance = (frame - 15) * 4;
                int x = centerX + (int)(Math.cos(angle) * distance);
                int y = centerY + (int)(Math.sin(angle) * distance);
                
                float alpha = 1.0f - ((float)(frame - 15) / 45.0f);
                if (alpha < 0) alpha = 0;
                 
                Color[] colors = {
                    new Color(255, 193, 7),
                    new Color(244, 67, 54),
                    new Color(76, 175, 80),
                    new Color(33, 150, 243)
                };
                
                Color c = colors[i % colors.length];
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(255 * alpha)));
                g.fillOval(x - 5, y - 5, 10, 10);
            }
        }
    } 
    
    /**
     * 결과 표시 시작
     */
    public void showResult(String result) {
        lastResult = result;
        resultDisplayFrame = 0;
    }
}
