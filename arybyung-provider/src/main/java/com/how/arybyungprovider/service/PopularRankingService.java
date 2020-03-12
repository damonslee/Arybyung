package com.how.arybyungprovider.service;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.how.arybyungprovider.constant.ProviderApiErrorCode;
import com.how.arybyungprovider.exception.ProviderResponseException;
import com.how.muchcommon.entity.jpaentity.PopularRankEntity;
import com.how.muchcommon.entity.jpaentity.PopularRankId;
import com.how.muchcommon.model.popular.PopularRankItem;
import com.how.muchcommon.model.popular.PopularRankResponse;
import com.how.muchcommon.repository.jparepository.PopularRankRepository;
import com.how.muchcommon.utils.DateTimeHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PopularRankingService {

    private final RedisTemplate redisTemplate;
    private final PopularRankRepository popularRankRepository;
    private final String RANKING_NAME = "popular_chart";
    private List<PopularRankItem> prevRankedItems = new ArrayList<>();

    private Gson gson = new Gson();

    public PopularRankResponse getPopularChart() {
        return gson.fromJson((String) redisTemplate.opsForValue().get(RANKING_NAME), PopularRankResponse.class);
    }

    public PopularRankResponse updatePopularChart() {
        ZonedDateTime updateDateTime = DateTimeHelper.currentTime("Asia/Seoul");
        ZonedDateTime startDateTime = DateTimeHelper.currentTime("Asia/Seoul").minusDays(1);

        PopularRankResponse prevRank = getPopularChart();

        if (prevRank != null) {
            prevRankedItems = prevRank.getList();
        }

        // TODO 다음엔 queryDSL 사용해보자
        // 최근 24시간 동안의 top 10
        List<PopularRankEntity> currentPopularRanking = popularRankRepository.findAllByPopularRank(startDateTime, updateDateTime);

        if (currentPopularRanking.size() < 1) {
            throw new ProviderResponseException(ProviderApiErrorCode.DATA_NOT_FOUND, "인기차트 데이터 없음.");
        }

        List<PopularRankItem> popularRankItems = Lists.newArrayList();

        for (int i = 0; i < currentPopularRanking.size(); i++) {
            String keyword = currentPopularRanking.get(i).getId().getKeyword();

            popularRankItems.add(
                    PopularRankItem.builder()
                            .keyword(keyword)
                            .state(calcRankState(keyword, i))
                            .count(currentPopularRanking.get(i).getCount())
                            .build()
            );
        }

        PopularRankResponse res = PopularRankResponse.builder()
                .updateTime(updateDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .list(popularRankItems)
                .build();

        // redis로 현재 검색 저장
        redisTemplate.opsForValue().set(RANKING_NAME, gson.toJson(res));
        return res;
    }

    public void countingPopularRank(ZonedDateTime searchDate, String keyword) {
        PopularRankId id = PopularRankId.builder()
                .keyword(keyword)
                .searchDate(searchDate)
                .build();

        PopularRankEntity entity = popularRankRepository.findById(id)
                .orElse(PopularRankEntity.builder()
                        .id(id)
                        .build());

        entity.addCount();

        popularRankRepository.save(entity);

    }

    private int calcRankState(String keyword, int currentRanked) {

        if (prevRankedItems != null) {
            for (int i = 0; i < prevRankedItems.size(); i++) {
                if (prevRankedItems.get(i).getKeyword().equals(keyword)) {
                    return i - currentRanked;
                }
            }
        }

        return 66;
    }
}