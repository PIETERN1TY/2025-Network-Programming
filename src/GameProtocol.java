/*
 * 서버-클라이언트 간 통신 프로토콜 정의
 * 모든 메시지 형식을 상수로 관리하여 일관성 유지
 */
public class GameProtocol { 
    
    // ===== 연결 및 초기화 관련 =====
    public static final String CONNECT_REQUEST = "CONNECT";
    public static final String CONNECT_SUCCESS = "CONNECT:SUCCESS";
    public static final String CONNECT_FAIL = "CONNECT:FAIL";
    
    // ===== 닉네임 및 캐릭터 설정 =====
    public static final String SET_NICKNAME = "NICKNAME:";  // + nickname
    public static final String NICKNAME_OK = "NICKNAME:OK";
    public static final String NICKNAME_DUPLICATE = "NICKNAME:DUPLICATE";
    public static final String SET_CHARACTER = "CHARACTER:"; // + character_id
    
    // ===== 매칭 관련 =====
    public static final String WAITING_MATCH = "WAITING";
    public static final String MATCH_FOUND = "MATCH:FOUND";
    public static final String MATCH_START = "MATCH:START";
    public static final String MATCH_TIMEOUT = "MATCH:TIMEOUT";
    public static final String START_BUTTON = "START:READY";
    
    // ===== 역할 배정 =====
    public static final String ROLE_PITCHER = "ROLE:PITCHER";
    public static final String ROLE_BATTER = "ROLE:BATTER";
    
    // ===== 게임 진행 =====
    public static final String GAME_START = "GAME:START";
    public static final String INNING_START = "INNING:"; // + inning_number
    public static final String SWITCH_SIDE = "SWITCH:SIDE";
    
    // ===== 투구 관련 =====
    public static final String ACTION_PITCH = "ACTION:PITCH";
    public static final String PITCH_FORK = "PITCH:A";      // 포크
    public static final String PITCH_FASTBALL = "PITCH:S";  // 직구
    public static final String PITCH_CURVE = "PITCH:D";     // 커브
    public static final String PITCH_SLIDER = "PITCH:F";    // 슬라이더
    public static final String PITCH_TIMEOUT = "PITCH:TIMEOUT";
    
    // ===== 타격 관련 =====
    public static final String ACTION_BAT = "ACTION:BAT";
    public static final String SWING_YES = "SWING:YES";
    public static final String SWING_NO = "SWING:NO";
    public static final String BAT_TIMEOUT = "BAT:TIMEOUT";
    
    // ===== 투구 정보 전달 (투수 -> 서버 -> 타자) =====
    public static final String PITCH_INFO = "PITCH_INFO:"; // + type:speed
    
    // ===== 판정 결과 =====
    public static final String RESULT_STRIKE = "RESULT:STRIKE";
    public static final String RESULT_BALL = "RESULT:BALL";
    public static final String RESULT_FOUL = "RESULT:FOUL";
    public static final String RESULT_HIT = "RESULT:HIT";
    public static final String RESULT_HOMERUN = "RESULT:HOMERUN";
    public static final String RESULT_OUT = "RESULT:OUT";
    public static final String RESULT_WALK = "RESULT:WALK";
    public static final String RESULT_STRIKEOUT = "RESULT:STRIKEOUT";
    
    // ===== 게임 상태 =====
    public static final String COUNT_UPDATE = "COUNT:"; // + strikes:balls:outs
    public static final String SCORE_UPDATE = "SCORE:"; // + home_score:away_score
    
    // ===== 게임 종료 =====
    public static final String GAME_END = "GAME:END";
    public static final String WIN = "WIN";
    public static final String LOSE = "LOSE";
    public static final String DRAW = "DRAW";
    
    // ===== 에러 처리 =====
    public static final String ERROR = "ERROR:";
    public static final String DISCONNECT = "DISCONNECT";
    
    /**
     * 메시지 파싱 유틸리티
     */
    public static class Parser {
        
        /**
         * 메시지에서 접두사 추출
         * @param message 전체 메시지
         * @return 접두사 (콜론 앞부분)
         */
        public static String getPrefix(String message) {
            if (message == null) return "";
            int colonIndex = message.indexOf(':');
            return colonIndex > 0 ? message.substring(0, colonIndex + 1) : message;
        }
        
        /**
         * 메시지에서 데이터 부분 추출
         * @param message 전체 메시지
         * @return 데이터 (콜론 뒷부분)
         */
        public static String getData(String message) {
            if (message == null) return "";
            int colonIndex = message.indexOf(':');
            return colonIndex >= 0 && colonIndex < message.length() - 1 
                ? message.substring(colonIndex + 1) 
                : "";
        }
        
        /**
         * 구종 문자 추출 (A, S, D, F)
         * @param pitchMessage PITCH:X 형태의 메시지
         * @return 구종 문자
         */
        public static char getPitchType(String pitchMessage) {
            String data = getData(pitchMessage);
            return data.length() > 0 ? data.charAt(0) : ' ';
        }
        
        /**
         * 투구 정보 파싱 (구종:구속)
         * @param pitchInfo PITCH_INFO:X:speed 형태
         * @return [구종, 구속] 배열
         */
        public static String[] parsePitchInfo(String pitchInfo) {
            String data = getData(pitchInfo);
            return data.split(":");
        }
        
        /**
         * 카운트 정보 파싱
         * @param countMsg COUNT:S:B:O 형태
         * @return [strikes, balls, outs] 배열
         */
        public static int[] parseCount(String countMsg) {
            String data = getData(countMsg);
            String[] parts = data.split(":");
            return new int[] {
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            };
        }
        
        /**
         * 점수 정보 파싱
         * @param scoreMsg SCORE:home:away 형태
         * @return [home_score, away_score] 배열
         */
        public static int[] parseScore(String scoreMsg) {
            String data = getData(scoreMsg);
            String[] parts = data.split(":");
            return new int[] {
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1])
            };
        }
    }
    
    /**
     * 메시지 생성 유틸리티
     */
    public static class Builder {
        
        public static String buildNickname(String nickname) {
            return SET_NICKNAME + nickname;
        }
        
        public static String buildCharacter(int characterId) {
            return SET_CHARACTER + characterId;
        }
        
        public static String buildPitch(char pitchType) {
            return "PITCH:" + pitchType;
        }
        
        public static String buildPitchInfo(char pitchType, int speed) {
            return PITCH_INFO + pitchType + ":" + speed;
        }
        
        public static String buildCount(int strikes, int balls, int outs) {
            return COUNT_UPDATE + strikes + ":" + balls + ":" + outs;
        }
        
        public static String buildScore(int homeScore, int awayScore) {
            return SCORE_UPDATE + homeScore + ":" + awayScore;
        }
        
        public static String buildInning(int inning) {
            return INNING_START + inning;
        }
        
        public static String buildResult(String resultType, String message) {
            return "RESULT:" + resultType + ":" + message;
        }
        
        public static String buildError(String errorMessage) {
            return ERROR + errorMessage;
        }
    }
    
    // 테스트용 메인 메서드
    public static void main(String[] args) {
        System.out.println("=== GameProtocol 테스트 ===\n");
        
        // 메시지 생성 테스트
        System.out.println("1. 메시지 생성 테스트");
        System.out.println(Builder.buildNickname("이지원"));
        System.out.println(Builder.buildPitch('S'));
        System.out.println(Builder.buildPitchInfo('S', 150));
        System.out.println(Builder.buildCount(2, 1, 0));
        System.out.println(Builder.buildScore(3, 2));
        
        // 메시지 파싱 테스트
        System.out.println("\n2. 메시지 파싱 테스트");
        String pitchMsg = "PITCH:S";
        System.out.println("원본: " + pitchMsg);
        System.out.println("구종: " + Parser.getPitchType(pitchMsg));
        
        String pitchInfo = "PITCH_INFO:S:150";
        System.out.println("\n원본: " + pitchInfo);
        String[] pitchData = Parser.parsePitchInfo(pitchInfo);
        System.out.println("구종: " + pitchData[0] + ", 구속: " + pitchData[1]);
        
        String countMsg = "COUNT:2:1:0";
        System.out.println("\n원본: " + countMsg);
        int[] count = Parser.parseCount(countMsg);
        System.out.println("스트라이크: " + count[0] + ", 볼: " + count[1] + ", 아웃: " + count[2]);
    }
}
