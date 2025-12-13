/*
 * 투수 클라이언트에서 5초 제한 시간 내에 A, S, D, F 키 입력을 처리하고 
 * 서버로 구종 데이터를 직렬화하여 전송하는 역할
 */

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

public class PitcherDecisionHandler extends JPanel implements ActionListener, KeyListener {
    private Timer timer; // 투수의 구종 결정 제한 타이머 
    private Image pitcherImage; // 투수 이미지
    private Image batterImage; // 타자 이미지 
    private Image backgroundImage; // 배경 이미지 
    private int pitcherX, pitcherY; // 투수 위치 좌표
    private int batterX, batterY; // 타자 위치 좌표 
    private final int SPEED = 5; // 플레이어의 이동 속도
    private boolean[] pKeys; // 투수 키 입력 상태를 추적하는 불리언 배열
    private boolean[] bKeys; // 타자 키 입력 상태를 추적하는 불리언 배

    
    public PitcherDecisionHandler() {
    	pitcherImage = new ImageIcon("images/pitcher.png").getImage();
    	batterImage = new ImageIcon("images/batter.png").getImage();
    	backgroundImage = new ImageIcon("images/background.jpg").getImage();
    	 
    	pKeys = new boolean[256];
    	bKeys = new boolean[256];
    	
    	// 초기 투수 위치 설정 
    	pitcherX = 360;
    	pitcherY = 360;
    	
    	// 초기 타자 위치 설정 
    	batterX = 100;
    	batterY = 180;
    	
    	// 투수 제구 타이머 설정
    	timer = new Timer(5000, this);
    	timer.start();
    	
    	addKeyListener(this);
    	setFocusable(true);
    	setPreferredSize(new Dimension(720, 500));
    }
    
    // 컴포넌트를 그릴 때 호출되는 메소드
    public void paintComponent(Graphics g) {
        super.paintComponent(g); // 상위 클래스의 paintComponent 호출
        
        // 배경 이미지를 그립니다.
        g.drawImage(backgroundImage, 0, 0, this);
        // 투수 이미지 그리기 
        g.drawImage(pitcherImage, pitcherX, pitcherY, this);
        // 타자 이미지 그리기 
        g.drawImage(batterImage, batterX, batterY, this);
    }
    
    // ActionListener 인터페이스를 구현한 메소드, 타이머 이벤트가 발생할 때마다 호출
    public void actionPerformed(ActionEvent e) {
        updatePlayerPosition(); // 플레이어 위치를 업데이트
        updateBackground(); // 배경을 업데이트
        repaint(); // 패널을 다시 그림 (paintComponent 호출)
    }
   
	private void updateBackground() {	}
    // 플레이어의 위치를 업데이트하는 메소드

	private void updatePlayerPosition() {
		// 투수 위치 키이벤트 
		 if (pKeys[KeyEvent.VK_LEFT]) {
	            pitcherX -= SPEED; // 왼쪽 키가 눌리면 왼쪽으로 이동
	        }
	        if (pKeys[KeyEvent.VK_RIGHT]) {
	            pitcherX += SPEED; // 오른쪽 키가 눌리면 오른쪽으로 이동
	        }
	        if (pKeys[KeyEvent.VK_UP]) {
	            pitcherY -= SPEED; // 위쪽 키가 눌리면 위로 이동
	        }
	        if (pKeys[KeyEvent.VK_DOWN]) {
	            pitcherY += SPEED; // 아래쪽 키가 눌리면 아래로 이동
	        }
	        
	        // 타자 위치 키이벤트 
			if (bKeys[KeyEvent.VK_LEFT]) {
				pitcherX -= SPEED; // 왼쪽 키가 눌리면 왼쪽으로 이동
				}
			if (bKeys[KeyEvent.VK_RIGHT]) {
				pitcherX += SPEED; // 오른쪽 키가 눌리면 오른쪽으로 이동
		        }
			if (bKeys[KeyEvent.VK_UP]) {
				pitcherY -= SPEED; // 위쪽 키가 눌리면 위로 이동
		        }
			if (bKeys[KeyEvent.VK_DOWN]) {
				pitcherY += SPEED; // 아래쪽 키가 눌리면 아래로 이동
		        }
	        
	        // 플레이어가 창의 경계를 넘지 않도록 위치를 조정
	        pitcherX = Math.max(pitcherX, 0);
	        pitcherX = Math.min(pitcherX, getWidth() - pitcherImage.getWidth(null));
	        pitcherY = Math.max(pitcherY, 0);
	        pitcherY = Math.min(pitcherY, getHeight() - pitcherImage.getHeight(null));
		
	}
	
    // KeyListener 인터페이스를 구현한 메소드, 키가 눌렸을 때 호출
	@Override
	public void keyPressed(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

    // KeyListener 인터페이스를 구현한 메소드, 키에서 손을 떼었을 때 호출 
	@Override
	public void keyReleased(KeyEvent e) {
		// TODO Auto-generated method stub
        pKeys[e.getKeyCode()] = false; // 해당 키에서 손을 떼었다면 배열에 false를 설정
        bKeys[e.getKeyCode()] = false; // 해당 키에서 손을 떼었다면 배열에 false를 설정
	}

	
    // KeyListener 인터페이스의 메소드, 키 타이핑 이벤트를 처리, 여기서는 구현x
	@Override
	public void keyTyped(KeyEvent e) {	}

	
	public static void main(String[] args) {
		
	}

}
