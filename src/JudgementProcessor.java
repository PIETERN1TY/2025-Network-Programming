/*
 * 서버에서 동작하며, 투구 정보 (구종, 구속)와 타격 정보 (스윙 여부)를 입력받아
 * 스트라이크, 볼, 안타 등의 최종 결과를 산출하는 가장 핵심적인 판정 메서드
 */
import java.util.Random;

public class JudgementProcessor {
    
    // 판정 결과 상수
    public static final String STRIKE = "STRIKE";
    public static final String BALL = "BALL";
    public static final String FOUL = "FOUL";
    public static final String HIT = "HIT";
    public static final String HOMERUN = "HOMERUN";
    public static final String OUT = "OUT";
    
    private Random random;
    
    public JudgementProcessor() {
        this.random = new Random();
    }
    
    /**
     * 투구와 타격 정보를 바탕으로 결과 판정
     * @param pitchType 구종 ('A', 'S', 'D', 'F')
     * @param speed 구속 (km/h)
     * @param isSwing 타자가 스윙했는지 여부
     * @return 판정 결과 (STRIKE, BALL, FOUL, HIT, HOMERUN, OUT)
     */
    public String judge(char pitchType, int speed, boolean isSwing) {
        // 스트라이크 존 확률 (구종별로 다르게 설정)
        double strikeZoneProbability = getStrikeZoneProbability(pitchType);
        boolean isStrikeZone = random.nextDouble() < strikeZoneProbability;
        
        // 타자가 스윙하지 않은 경우
        if (!isSwing) {
            return isStrikeZone ? STRIKE : BALL;
        }
        
        // 타자가 스윙한 경우
        if (isStrikeZone) {
            // 스트라이크 존에서 스윙 - 타격 판정
            return judgeHit(pitchType, speed);
        } else {
            // 볼 존에서 스윙 - 높은 확률로 헛스윙 or 파울
            double missChance = 0.7; // 70% 헛스윙
            if (random.nextDouble() < missChance) {
                return STRIKE; // 헛스윙 (스트라이크 카운트)
            } else {
                return FOUL;
            }
        }
    }
    
    /**
     * 구종별 스트라이크 존 진입 확률 반환
     * @param pitchType 구종
     * @return 스트라이크 존 확률
     */
    private double getStrikeZoneProbability(char pitchType) {
        switch(pitchType) {
            case 'S': return 0.75; // 직구: 75% 스트라이크
            case 'F': return 0.65; // 슬라이더: 65%
            case 'D': return 0.55; // 커브: 55%
            case 'A': return 0.50; // 포크: 50%
            default: return 0.60;
        }
    }
    
    /**
     * 타격 결과 판정 (스트라이크 존에서 스윙한 경우)
     * @param pitchType 구종
     * @param speed 구속
     * @return HIT, HOMERUN, FOUL, OUT 중 하나
     */
    private String judgeHit(char pitchType, int speed) {
        // 구속에 따른 타격 난이도 계산 (빠를수록 어려움)
        double hitDifficulty = calculateHitDifficulty(pitchType, speed);
        
        double rand = random.nextDouble();
        
        // 타격 성공 확률
        double hitChance = 0.35 * (1 - hitDifficulty);
        double homerunChance = 0.08 * (1 - hitDifficulty);
        double foulChance = 0.40;
        
        if (rand < homerunChance) {
            return HOMERUN;
        } else if (rand < homerunChance + hitChance) {
            return HIT;
        } else if (rand < homerunChance + hitChance + foulChance) {
            return FOUL;
        } else {
            return OUT; // 친 공이 야수에게 잡힘
        }
    }
    
    /**
     * 구종과 구속에 따른 타격 난이도 계산
     * @param pitchType 구종
     * @param speed 구속
     * @return 0.0 ~ 1.0 사이의 난이도 (높을수록 어려움)
     */
    private double calculateHitDifficulty(char pitchType, int speed) {
        double baseDifficulty = 0.0;
        
        // 구종별 기본 난이도
        switch(pitchType) {
            case 'S': baseDifficulty = 0.3; break; // 직구는 상대적으로 쉬움
            case 'F': baseDifficulty = 0.5; break;
            case 'D': baseDifficulty = 0.6; break;
            case 'A': baseDifficulty = 0.7; break; // 포크가 가장 어려움
        }
        
        // 구속에 따른 추가 난이도 (140km/h 기준으로 정규화)
        double speedFactor = (speed - 120.0) / 40.0; // 0.0 ~ 1.0
        speedFactor = Math.max(0, Math.min(1, speedFactor));
        
        // 최종 난이도 계산
        return baseDifficulty * 0.6 + speedFactor * 0.4;
    }
    
    /**
     * 판정 결과에 따른 상세 메시지 반환
     * @param result 판정 결과
     * @param pitchType 구종
     * @param speed 구속
     * @return 상세 메시지
     */
    public String getResultMessage(String result, char pitchType, int speed) {
        RandomSpeedGenerator gen = new RandomSpeedGenerator();
        String pitchName = gen.getPitchName(pitchType);
        
        switch(result) {
            case STRIKE:
                return pitchName + " " + speed + "km/h - 스트라이크!";
            case BALL:
                return pitchName + " " + speed + "km/h - 볼!";
            case FOUL:
                return pitchName + " " + speed + "km/h - 파울!";
            case HIT:
                return pitchName + " " + speed + "km/h - 안타!";
            case HOMERUN:
                return pitchName + " " + speed + "km/h - 홈런!!!";
            case OUT:
                return pitchName + " " + speed + "km/h - 아웃!";
            default:
                return "알 수 없는 결과";
        }
    }
    
    // 테스트용 메인 메서드
    public static void main(String[] args) {
        JudgementProcessor processor = new JudgementProcessor();
        RandomSpeedGenerator speedGen = new RandomSpeedGenerator();
        
        System.out.println("=== 투구 판정 시뮬레이션 ===\n");
        
        char[] pitchTypes = {'A', 'S', 'D', 'F'};
        boolean[] swingOptions = {true, false};
        
        for (char pitch : pitchTypes) {
            int speed = speedGen.generateSpeed(pitch);
            System.out.println("[" + speedGen.getPitchName(pitch) + " " + speed + "km/h]");
            
            for (boolean swing : swingOptions) {
                String swingText = swing ? "스윙함" : "스윙 안함";
                String result = processor.judge(pitch, speed, swing);
                System.out.println("  " + swingText + " -> " + result);
            }
            System.out.println();
        }
        
        // 연속 타격 시뮬레이션
        System.out.println("\n=== 10번 타석 시뮬레이션 (모두 스윙) ===");
        for (int i = 1; i <= 10; i++) {
            char pitch = pitchTypes[i % 4];
            int speed = speedGen.generateSpeed(pitch);
            String result = processor.judge(pitch, speed, true);
            System.out.println(i + "번째: " + processor.getResultMessage(result, pitch, speed));
        }
    }
}