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
        panel.setBackground(new Color(34, 139, 34));
        
        JLabel waitLabel = new JLabel("매칭 대기 중...", SwingConstants.CENTER);
        waitLabel.setFont(new Font("맑은 고딕", Font.BOLD, 36));
        waitLabel.setForeground(Color.WHITE);
        
        panel.add(waitLabel, BorderLayout.CENTER);
        return panel;
    }
    
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
        
        switch(resultType) {
            case "HIT": gamePanel.advanceRunners("HIT"); break;
            case "HOMERUN": gamePanel.advanceRunners("HOMERUN"); break;
            case "WALK": gamePanel.advanceRunners("WALK"); break;
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
    
    public GamePanel(GameClient client) {
        this.client = client;
        setPreferredSize(new Dimension(800, 600));
        loadImages();
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
        
        // 배경
        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
        } else {
            g.setColor(new Color(34, 100, 34));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        
        // 캐릭터
        if (pitcherImage != null) g.drawImage(pitcherImage, 360, 250, 80, 100, this);
        if (batterImage != null) g.drawImage(batterImage, 360, 420, 80, 100, this);
        
        // UI 그리기
        drawScoreboard(g2);
        drawBaseDiamond(g2);
        drawBSOCount(g2);
        drawControlInfo(g2);
    }
    
    private void drawScoreboard(Graphics2D g) {
        int x = 20, y = 20, w = 200, h = 140;
        
        // 흰색 배경 박스
        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y, w, h, 15, 15);
        g.setColor(new Color(200, 200, 200));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(x, y, w, h, 15, 15);
        
        // 점수 결정 (타자=공격팀, 투수=수비팀)
        int attackScore, defenseScore;
        if (role.equals("BATTER")) {
            attackScore = myScore;
            defenseScore = oppScore;
        } else {
            attackScore = oppScore;
            defenseScore = myScore;
        }
        
        // 공격팀 (빨간색)
        g.setColor(new Color(220, 53, 69));
        g.fillRect(x + 5, y + 8, w - 10, 40);
        g.setColor(Color.WHITE);
        g.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        g.drawString("공격팀", x + 15, y + 35);
        g.setFont(new Font("맑은 고딕", Font.BOLD, 24));
        g.drawString(String.valueOf(attackScore), x + w - 40, y + 38);
        
        // 수비팀 (파란색)
        g.setColor(new Color(0, 123, 255));
        g.fillRect(x + 5, y + 53, w - 10, 40);
        g.setColor(Color.WHITE);
        g.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        g.drawString("수비팀", x + 15, y + 80);
        g.setFont(new Font("맑은 고딕", Font.BOLD, 24));
        g.drawString(String.valueOf(defenseScore), x + w - 40, y + 83);
        
        // 타이머
        g.setColor(Color.BLACK);
        g.setFont(new Font("맑은 고딕", Font.PLAIN, 16));
        g.drawString("⏱ 0:" + String.format("%02d", remainingSeconds), x + 15, y + 125);
    }
    
    private void drawBaseDiamond(Graphics2D g) {
        int cx = 400, cy = 75;
        
        // 흰색 배경 박스
        g.setColor(Color.WHITE);
        g.fillRoundRect(cx - 75, 15, 150, 110, 15, 15);
        g.setColor(new Color(200, 200, 200));
        g.drawRoundRect(cx - 75, 15, 150, 110, 15, 15);
        
        // 베이스 3개만 (홈 제외)
        drawBase(g, cx, cy - 25, runner2nd);      // 2루 (위)
        drawBase(g, cx - 35, cy + 5, runner3rd);  // 3루 (왼쪽)
        drawBase(g, cx + 35, cy + 5, runner1st);  // 1루 (오른쪽)
        
        // 이닝 (초▲ / 말▼)
        g.setColor(Color.BLACK);
        g.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        String inningText = inning + (isTopInning ? "▲" : "▼");
        g.drawString(inningText, cx - 15, cy + 55);
    }
    
    private void drawBase(Graphics2D g, int x, int y, boolean hasRunner) {
        int size = 24;
        int[] xp = {x, x + size/2, x, x - size/2};
        int[] yp = {y - size/2, y, y + size/2, y};
        
        g.setColor(hasRunner ? new Color(255, 140, 0) : new Color(200, 200, 200));
        g.fillPolygon(xp, yp, 4);
        g.setColor(new Color(100, 100, 100));
        g.drawPolygon(xp, yp, 4);
    }
    
    private void drawBSOCount(Graphics2D g) {
        int x = 560, y = 20, w = 140, h = 115;
        
        // 흰색 배경 박스
        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y, w, h, 15, 15);
        g.setColor(new Color(200, 200, 200));
        g.drawRoundRect(x, y, w, h, 15, 15);
        
        g.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        
        // Ball (초록색)
        g.setColor(Color.BLACK);
        g.drawString("B", x + 15, y + 30);
        for (int i = 0; i < 4; i++) {
            g.setColor(i < balls ? new Color(40, 167, 69) : new Color(200, 200, 200));
            g.fillOval(x + 40 + i * 22, y + 15, 16, 16);
        }
        
        // Strike (주황색)
        g.setColor(Color.BLACK);
        g.drawString("S", x + 15, y + 62);
        for (int i = 0; i < 3; i++) {
            g.setColor(i < strikes ? new Color(255, 165, 0) : new Color(200, 200, 200));
            g.fillOval(x + 40 + i * 22, y + 47, 16, 16);
        }
        
        // Out (빨간색)
        g.setColor(Color.BLACK);
        g.drawString("O", x + 15, y + 94);
        for (int i = 0; i < 3; i++) {
            g.setColor(i < outs ? new Color(220, 53, 69) : new Color(200, 200, 200));
            g.fillOval(x + 40 + i * 22, y + 79, 16, 16);
        }
    }
    
    private void drawControlInfo(Graphics2D g) {
        int y = getHeight() - 50;
        
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, y - 5, getWidth(), 55);
        
        g.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        g.setColor(Color.ORANGE);
        g.drawString("투수: A(포크) S(직구) D(커브) F(슬라이더)", 30, y + 15);
        g.setColor(Color.CYAN);
        g.drawString("타자: H(스윙)", 30, y + 35);
        
        // 현재 역할
        g.setColor(Color.WHITE);
        String roleText = role.equals("PITCHER") ? "[ 투수 ]" : role.equals("BATTER") ? "[ 타자 ]" : "";
        g.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        g.drawString(roleText, getWidth() - 100, y + 25);
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
}
