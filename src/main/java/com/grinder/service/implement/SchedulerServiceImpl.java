package com.grinder.service.implement;

import com.grinder.domain.entity.Cafe;
import com.grinder.domain.entity.Feed;
import com.grinder.domain.entity.Member;
import com.grinder.domain.enums.ContentType;
import com.grinder.repository.*;
import com.grinder.service.AlanQuestionService;
import com.grinder.service.AnalysisTagService;
import com.grinder.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerServiceImpl implements SchedulerService {
    private final CafeRepository cafeRepository;
    private final MemberRepository memberRepository;
    private final AnalysisTagService analysisTagService;
    private final AlanQuestionService alanQuestionService;
    private final FeedRepository feedRepository;
    private final HeartRepository heartRepository;
    private final CommentRepository commentRepository;
    private final JobLauncher jobLauncher;
    private final Job logJob;

    private static final List<String> logList = new ArrayList<>();

    //매일 0시에 전체 카페의 1/7만 평균 별점 업데이트 진행
    @Override
    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Seoul")
    public void CalAverage() {
        executeWithRetry(this::performCalAverageTask, "CalAverage");
    }

    private void performCalAverageTask() {
        LocalDate today = LocalDate.now();
        int dayOfWeek = today.getDayOfWeek().getValue();
        updateAverageGradeForCafes(dayOfWeek);
        updateTagListForMembers(dayOfWeek);
        int dayOfMonth = today.getDayOfMonth();
        recommendCafeForMembers(dayOfMonth);
        updateRanks(dayOfMonth);
    }

    @Override
    public void updateAverageGradeForCafes(int dayOfWeek) {
        // 전체 카페 수 조회
        long totalCafes = cafeRepository.count();
        long batchSize = totalCafes / 7;  // 한 번에 처리할 카페 수
        long offset = (dayOfWeek - 1) * batchSize;  // 주중의 날에 따라 오프셋 조정

        // 해당 요일에 처리할 카페 리스트 가져오기
        List<Cafe> cafes = cafeRepository.findCafesForAverageCalculation(batchSize, offset);
        cafes.forEach(this::calculateAndSetAverageGrade);
    }
    @Override
    public void calculateAndSetAverageGrade(Cafe cafe) {
        Double average = cafeRepository.findAverageGradeByCafeId(cafe.getCafeId());
        cafe.setAverageGrade(average.intValue());
        cafeRepository.save(cafe);
    }
    @Override
    public void updateTagListForMembers(int dayOfWeek) {
        long totalMembers = memberRepository.count();
        long batchSize = totalMembers / 7;
        long offset = (dayOfWeek - 1) * batchSize;

        List<Member> members = memberRepository.findMembersForTagUpdate(batchSize, offset);
        members.forEach(member -> analysisTagService.updateTagList(member.getEmail()));
    }

    @Override
    @Scheduled(cron = "0 0 1 * * ?", zone = "Asia/Seoul")
    public void recommendAlan() {
        executeWithRetry(this::performRecommendAlanTask, "recommendAlan");
    }

    private void performRecommendAlanTask() {
        LocalDate today = LocalDate.now();
        int dayOfMonth = today.getDayOfMonth();
        recommendCafeForMembers(dayOfMonth);
        updateRanks(dayOfMonth);
    }

    @Override
    public void recommendCafeForMembers(int dayOfMonth) {
        long totalMembers = memberRepository.count();
        long batchSize = totalMembers / 30;  // 전체 회원을 30으로 나누어 하루 처리할 회원 수 계산
        long offset = (dayOfMonth - 1) * batchSize;  // 일자에 따른 오프셋 계산

        List<Member> members = memberRepository.findMembersForTagUpdate(batchSize, offset);
        for (Member member : members) {
            alanQuestionService.recommendCafe(member.getEmail());
        }
    }

    @Override
    public void updateRanks(int dayOfMonth) {
        long totalMembers = feedRepository.count();
        long batchSize = totalMembers / 30;  // 전체 회원을 30으로 나누어 하루 처리할 회원 수 계산
        long offset = (dayOfMonth - 1) * batchSize;  // 일자에 따른 오프셋 계산

        List<Feed> feeds = feedRepository.findFeedsForRankUpdate(batchSize, offset);
        for (Feed feed : feeds) {
            Long rank = heartRepository.countByContentTypeAndContentId(ContentType.FEED, feed.getFeedId()) / 10;
            rank += commentRepository.countByFeed(feed) / 5;
            feedRepository.updateFeedRank(feed.getFeedId(), rank.intValue());
        }
    }
    @Override
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Seoul")
    public void updateRank() {
        executeWithRetry(this::performUpdateRankTask, "updateRank");
    }

    private void performUpdateRankTask() {
        LocalDate today = LocalDate.now();
        int dayOfMonth = today.getDayOfMonth();
        updateRanks(dayOfMonth);
    }

    @Override
    public void executeWithRetry(Runnable task, String taskName) {
        int MAX_RETRIES = 3;
        int retryCount = 0;
        while (retryCount < MAX_RETRIES) {
            try {
                task.run();
                logList.add(taskName + ": 성공");
                return;
            } catch (Exception e) {
                retryCount++;
                log.error(taskName + ": 재시도 " + retryCount + " 실패", e);
                if (retryCount >= MAX_RETRIES) {
                    logList.add(taskName + ": 실패, 재시도 " + MAX_RETRIES + " 번째 - " + e.getMessage());
                }
            }
        }
    }

    @Override
    public List<String> getLogList() {
        return new ArrayList<>(logList);
    }
    @Override
    public void clearLogList() {
        logList.clear();
    }

    @Override
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Seoul")
    public void runLogJob() {
        try {
            jobLauncher.run(logJob, new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
