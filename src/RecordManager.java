/*
 * 사용자별 승/패 기록을 저장하고 조회하는 기능을 담당하는 모듈
 */
import java.io.*;
import java.util.*;

public class RecordManager {
    private String recordFilePath;
    private Map<String, PlayerRecord> recordMap;
     
    /**
     * 기본 생성자 - 기본 파일 경로 사용
     */
    public RecordManager() {
        this("game_records.dat");
    }
    
    /**
     * 파일 경로를 지정하는 생성자
     * @param filePath 전적 기록 파일 경로
     */
    public RecordManager(String filePath) {
        this.recordFilePath = filePath;
        this.recordMap = new HashMap<>();
        loadRecords();
    }
    
    /**
     * 파일에서 전적 기록 불러오기
     */
    private void loadRecords() {
        File file = new File(recordFilePath);
        
        if (!file.exists()) {
            System.out.println("전적 파일이 없습니다. 새로 생성됩니다.");
            return;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            recordMap = (Map<String, PlayerRecord>) ois.readObject();
            System.out.println("전적 기록을 불러왔습니다. (총 " + recordMap.size() + "명)");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("전적 불러오기 실패: " + e.getMessage());
            recordMap = new HashMap<>();
        }
    }
    
    /**
     * 전적 기록을 파일에 저장
     */
    public synchronized void saveRecords() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(recordFilePath))) {
            oos.writeObject(recordMap);
            System.out.println("전적 기록이 저장되었습니다.");
        } catch (IOException e) {
            System.err.println("전적 저장 실패: " + e.getMessage());
        }
    }
    
    /**
     * 플레이어 등록 (신규 플레이어)
     * @param nickname 닉네임
     * @return 등록 성공 여부
     */
    public synchronized boolean registerPlayer(String nickname) {
        if (recordMap.containsKey(nickname)) {
            System.out.println("이미 존재하는 닉네임입니다: " + nickname);
            return false;
        }
        
        recordMap.put(nickname, new PlayerRecord(nickname));
        saveRecords();
        System.out.println("신규 플레이어 등록: " + nickname);
        return true;
    }
    
    /**
     * 승리 기록 추가
     * @param nickname 플레이어 닉네임
     */
    public synchronized void addWin(String nickname) {
        PlayerRecord record = getOrCreateRecord(nickname);
        record.addWin();
        saveRecords();
        System.out.println(nickname + " 승리 기록 추가");
    }
    
    /**
     * 패배 기록 추가
     * @param nickname 플레이어 닉네임
     */
    public synchronized void addLoss(String nickname) {
        PlayerRecord record = getOrCreateRecord(nickname);
        record.addLoss();
        saveRecords();
        System.out.println(nickname + " 패배 기록 추가");
    }
    
    /**
     * 게임 결과 기록 (승자와 패자)
     * @param winner 승자 닉네임
     * @param loser 패자 닉네임
     */
    public synchronized void recordGameResult(String winner, String loser) {
        addWin(winner);
        addLoss(loser);
        System.out.println("경기 결과 기록: " + winner + " vs " + loser + " -> " + winner + " 승리");
    }
    
    /**
     * 플레이어 전적 조회
     * @param nickname 플레이어 닉네임
     * @return PlayerRecord 객체 (없으면 null)
     */
    public PlayerRecord getRecord(String nickname) {
        return recordMap.get(nickname);
    }
    
    /**
     * 플레이어 전적 조회 (없으면 생성)
     * @param nickname 플레이어 닉네임
     * @return PlayerRecord 객체
     */
    private PlayerRecord getOrCreateRecord(String nickname) {
        if (!recordMap.containsKey(nickname)) {
            recordMap.put(nickname, new PlayerRecord(nickname));
        }
        return recordMap.get(nickname);
    }
    
    /**
     * 승률 순위 목록 반환
     * @param limit 반환할 최대 개수 (0이면 전체)
     * @return 승률 순으로 정렬된 플레이어 목록
     */
    public List<PlayerRecord> getRankingByWinRate(int limit) {
        List<PlayerRecord> ranking = new ArrayList<>(recordMap.values());
        
        // 승률 기준 내림차순 정렬
        ranking.sort((p1, p2) -> {
            double rate1 = p1.getWinRate();
            double rate2 = p2.getWinRate();
            if (rate1 != rate2) {
                return Double.compare(rate2, rate1);
            }
            // 승률이 같으면 총 게임 수가 많은 순
            return Integer.compare(p2.getTotalGames(), p1.getTotalGames());
        });
        
        if (limit > 0 && limit < ranking.size()) {
            return ranking.subList(0, limit);
        }
        return ranking;
    }
    
    /**
     * 총 승리 수 순위 목록 반환
     * @param limit 반환할 최대 개수
     * @return 승리 수 순으로 정렬된 플레이어 목록
     */
    public List<PlayerRecord> getRankingByWins(int limit) {
        List<PlayerRecord> ranking = new ArrayList<>(recordMap.values());
        
        ranking.sort((p1, p2) -> {
            if (p1.getWins() != p2.getWins()) {
                return Integer.compare(p2.getWins(), p1.getWins());
            }
            return Double.compare(p2.getWinRate(), p1.getWinRate());
        });
        
        if (limit > 0 && limit < ranking.size()) {
            return ranking.subList(0, limit);
        }
        return ranking;
    }
    
    /**
     * 전체 플레이어 수 반환
     * @return 플레이어 수
     */
    public int getTotalPlayers() {
        return recordMap.size();
    }
    
    /**
     * 모든 전적 초기화
     */
    public synchronized void resetAllRecords() {
        recordMap.clear();
        saveRecords();
        System.out.println("모든 전적이 초기화되었습니다.");
    }
    
    /**
     * 특정 플레이어 전적 삭제
     * @param nickname 플레이어 닉네임
     * @return 삭제 성공 여부
     */
    public synchronized boolean deleteRecord(String nickname) {
        if (recordMap.remove(nickname) != null) {
            saveRecords();
            System.out.println(nickname + " 전적이 삭제되었습니다.");
            return true;
        }
        return false;
    }
    
    /**
     * 전적 정보를 문자열로 출력
     */
    public void printAllRecords() {
        System.out.println("\n=== 전체 전적 목록 ===");
        System.out.println("총 플레이어 수: " + recordMap.size());
        System.out.println("─".repeat(60));
        System.out.printf("%-20s %8s %8s %8s %10s\n", "닉네임", "승", "패", "총경기", "승률");
        System.out.println("─".repeat(60));
        
        List<PlayerRecord> ranking = getRankingByWinRate(0);
        for (PlayerRecord record : ranking) {
            System.out.printf("%-20s %8d %8d %8d %9.1f%%\n",
                record.getNickname(),
                record.getWins(),
                record.getLosses(),
                record.getTotalGames(),
                record.getWinRate() * 100
            );
        }
        System.out.println("─".repeat(60));
    }
    
    // 테스트용 메인 메서드
    public static void main(String[] args) {
        RecordManager manager = new RecordManager("test_records.dat");
        
        System.out.println("=== RecordManager 테스트 ===\n");
        
        // 플레이어 등록
        System.out.println("1. 플레이어 등록");
        manager.registerPlayer("이지원");
        manager.registerPlayer("홍길동");
        manager.registerPlayer("김철수");
        manager.registerPlayer("박영희");
        
        // 게임 결과 기록
        System.out.println("\n2. 게임 결과 기록");
        manager.recordGameResult("이지원", "홍길동");
        manager.recordGameResult("이지원", "김철수");
        manager.recordGameResult("홍길동", "박영희");
        manager.recordGameResult("김철수", "이지원");
        manager.recordGameResult("이지원", "박영희");
        manager.recordGameResult("홍길동", "김철수");
        
        // 전적 조회
        System.out.println("\n3. 개별 전적 조회");
        PlayerRecord record = manager.getRecord("이지원");
        if (record != null) {
            System.out.println(record);
        }
        
        // 전체 전적 출력
        System.out.println("\n4. 전체 전적 출력");
        manager.printAllRecords();
        
        // 랭킹 조회
        System.out.println("\n5. 승률 TOP 3");
        List<PlayerRecord> topPlayers = manager.getRankingByWinRate(3);
        for (int i = 0; i < topPlayers.size(); i++) {
            System.out.println((i+1) + "위: " + topPlayers.get(i));
        }
        
        System.out.println("\n테스트 완료!");
    }
}

/**
 * 개별 플레이어의 전적 정보를 담는 클래스
 */
class PlayerRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String nickname;
    private int wins;
    private int losses;
    private Date lastPlayDate;
    
    public PlayerRecord(String nickname) {
        this.nickname = nickname;
        this.wins = 0;
        this.losses = 0;
        this.lastPlayDate = new Date();
    }
    
    public void addWin() {
        wins++;
        lastPlayDate = new Date();
    }
    
    public void addLoss() {
        losses++;
        lastPlayDate = new Date();
    }
    
    public String getNickname() {
        return nickname;
    }
    
    public int getWins() {
        return wins;
    }
    
    public int getLosses() {
        return losses;
    }
    
    public int getTotalGames() {
        return wins + losses;
    }
    
    public double getWinRate() {
        int total = getTotalGames();
        return total == 0 ? 0.0 : (double) wins / total;
    }
    
    public Date getLastPlayDate() {
        return lastPlayDate;
    }
    
    @Override
    public String toString() {
        return String.format("%s - %d승 %d패 (승률: %.1f%%, 총 %d경기)",
            nickname, wins, losses, getWinRate() * 100, getTotalGames());
    }
}
