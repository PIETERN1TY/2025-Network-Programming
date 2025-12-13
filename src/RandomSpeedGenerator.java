/*
 * 서버 측에서 특정 구종(포크, 직구, 커브, 슬라이더)의 범위에 따라
 구속을 랜덤으로 결정하는 로직을 구현
 */
import java.util.Random;

public class RandomSpeedGenerator {
    private Random random;
     
    // 구종별 구속 범위 (km/h)
    private static final int FORK_MIN = 110;
    private static final int FORK_MAX = 130;
    
    private static final int FASTBALL_MIN = 140;
    private static final int FASTBALL_MAX = 160;
    
    private static final int CURVE_MIN = 100;
    private static final int CURVE_MAX = 120;
    
    private static final int SLIDER_MIN = 120;
    private static final int SLIDER_MAX = 140;
    
    public RandomSpeedGenerator() {
        this.random = new Random();
    }
    
    /**
     * 구종에 따라 랜덤 구속을 생성
     * @param pitchType 구종 ('A': 포크, 'S': 직구, 'D': 커브, 'F': 슬라이더)
     * @return 생성된 구속 (km/h)
     */
    public int generateSpeed(char pitchType) {
        switch(pitchType) {
            case 'A': // 포크
                return generateRandomSpeed(FORK_MIN, FORK_MAX);
            case 'S': // 직구
                return generateRandomSpeed(FASTBALL_MIN, FASTBALL_MAX);
            case 'D': // 커브
                return generateRandomSpeed(CURVE_MIN, CURVE_MAX);
            case 'F': // 슬라이더
                return generateRandomSpeed(SLIDER_MIN, SLIDER_MAX);
            default:
                throw new IllegalArgumentException("잘못된 구종: " + pitchType);
        }
    }
    
    /**
     * 최소값과 최대값 사이의 랜덤 정수 생성
     * @param min 최소값
     * @param max 최대값
     * @return 랜덤 구속
     */
    private int generateRandomSpeed(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }
    
    /**
     * 구종 이름 반환
     * @param pitchType 구종 코드
     * @return 구종 이름
     */
    public String getPitchName(char pitchType) {
        switch(pitchType) {
            case 'A': return "포크";
            case 'S': return "직구";
            case 'D': return "커브";
            case 'F': return "슬라이더";
            default: return "알 수 없음";
        }
    }
    
    // 테스트용 메인 메서드
    public static void main(String[] args) {
        RandomSpeedGenerator generator = new RandomSpeedGenerator();
        
        System.out.println("=== 구종별 구속 테스트 ===");
        char[] pitchTypes = {'A', 'S', 'D', 'F'};
        
        for (char pitch : pitchTypes) {
            System.out.println("\n" + generator.getPitchName(pitch) + ":");
            for (int i = 0; i < 5; i++) {
                int speed = generator.generateSpeed(pitch);
                System.out.println("  시도 " + (i+1) + ": " + speed + " km/h");
            }
        }
    }
} 
