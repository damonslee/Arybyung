package com.how.arybyungobserver.service.joonggonara;

import com.how.arybyungcommon.client.joonggonara.JoonggonaraApiClient;
import com.how.arybyungcommon.client.joonggonara.model.JoonggonaraArticleDetailResponse;
import com.how.arybyungcommon.client.joonggonara.model.JoonggonaraListResponse;
import com.how.arybyungcommon.properties.UrlProperties;
import com.how.arybyungcommon.service.FilteringWordService;
import com.how.muchcommon.entity.jpaentity.ArticleEntity;
import com.how.muchcommon.model.type.ArticleState;
import com.how.muchcommon.model.type.MarketName;
import com.how.muchcommon.repository.jparepository.ArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
@Service
public class JoonggonaraCrawlingService {

    private final JoonggonaraApiClient joonggonaraApiClient;
    private final ArticleRepository articleRepository;
    private final UrlProperties urlProperties;
    private final FilteringWordService filteringWordService;

    public JoonggonaraCrawlingService(JoonggonaraApiClient joonggonaraApiClient,
                                      ArticleRepository articleRepository,
                                      UrlProperties urlProperties,
                                      FilteringWordService filteringWordService) {
        this.joonggonaraApiClient = joonggonaraApiClient;
        this.articleRepository = articleRepository;
        this.urlProperties = urlProperties;
        this.filteringWordService = filteringWordService;
    }

    public Long getTopArticleId() {
        ArticleEntity articleEntity = articleRepository.findTopBySiteOrderByArticleIdDesc(MarketName.joonggonara.getName()).orElse(ArticleEntity.builder()
                .articleId(0L).build());
        return articleEntity.getArticleId();
    }

    public Long getRecentArticleId() {
        try {
            Response<JoonggonaraListResponse> response = joonggonaraApiClient.getArticleList().execute();
            if (response.isSuccessful()) {

                Long recentArticleId = response.body().getMessage().getResult().getArticleList().stream()
                        .map(article -> article.getArticleId())
                        .max(Long::compareTo)
                        .orElse(0L);

                return recentArticleId;
            } else {
                log.error("JoonggoNARA getRecentArtilceId Failed : {}", response.errorBody().byteStream().toString());
                return 0L;
            }
        } catch (IOException e) {
            log.error("JoonggoNARA getRecentArticleId IOException : {}", e.getMessage());
            return 0L;
        }
    }

    @Async(value = "joonggonaraTaskExecutor")
    public void separationGetArticles(Long start, Long unit) {
        for (Long articleId = start; articleId < start + unit; articleId++) {
            getArticle(articleId);
        }
    }

    public void getArticle(Long articleId) {
        try {
            Response<JoonggonaraArticleDetailResponse> response = joonggonaraApiClient.getArticleDetail(articleId).execute();
            if (response.isSuccessful()) {

                if (response.body().getArticle() == null) {
                    return;
                }

                String content = Jsoup.parse(response.body().getArticle().getContent()).getAllElements().text();
                if (filteringWordService.stringFilter(content)) {
                    return;
                }

                Instant i = Instant.ofEpochMilli(response.body().getArticle().getWriteDate());
                ZonedDateTime postDtime = ZonedDateTime.ofInstant(i, ZoneId.of("Asia/Seoul"));

                ArticleState state;
                switch (response.body().getSaleInfo().getSaleStatus()) {
                    case "SALE":
                    case "ESCROW":
                        state = ArticleState.S;
                        break;
                    case "SOLD_OUT":
                        state = ArticleState.C;
                        break;
                    default:
                        state = ArticleState.D;
                        break;
                }

                ArticleEntity articleEntity = ArticleEntity.builder()
                        .articleId(articleId)
                        .state(state)
                        .site(MarketName.joonggonara.getName())
                        .price(response.body().getSaleInfo().getPrice())
                        .url(urlProperties.getJoonggoArtlcleUrl() + articleId)
                        .subject(response.body().getArticle().getSubject())
                        .content(content)
                        .image(response.body().getSaleInfo().getImgUrl())
                        .postingDtime(postDtime)
                        .build();

                articleRepository.save(articleEntity);
            } else {
//                log.error("JoonggoNARA getArticle Failed articleID: {} => {}", articleId, response.errorBody().byteStream().toString());
            }

        } catch (IOException | NullPointerException | DataIntegrityViolationException e) {
            log.error("JoonggoNARA getArticle articleId : {} => Exception : {}", articleId, e.getMessage());
        }
    }
}
