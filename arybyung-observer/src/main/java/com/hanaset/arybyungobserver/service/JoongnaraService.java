package com.hanaset.arybyungobserver.service;

import com.google.common.collect.Lists;
import com.hanaset.arybyungcommon.entity.ArticleEntity;
import com.hanaset.arybyungcommon.repository.ArticleRepository;
import com.hanaset.arybyungobserver.client.JoonggonaraParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JoongnaraService {

    private final JoonggonaraParser joonggonaraParser;
    private final ArticleRepository articleRepository;
    private final Long defaultArticleId = 691000000L;

    public JoongnaraService(JoonggonaraParser joonggonaraParser,
                            ArticleRepository articleRepository) {
        this.joonggonaraParser = joonggonaraParser;
        this.articleRepository = articleRepository;
    }

    private Long getTopArticleId() { // DB에서 저장된 가장 최근 게시글 번호 불러오기
        ArticleEntity articleEntity = articleRepository.findTopByOrderByArticleIdDesc();
        return articleEntity.getArticleId();
    }

    public void parsingArticle() throws Exception {

        Long topArticleId = getTopArticleId();
        Long recentArticleId = joonggonaraParser.getRecentArticleId();

        if (recentArticleId > topArticleId + 1500) {
            recentArticleId = topArticleId + 1500;
        }

        if (topArticleId.compareTo(recentArticleId) < 0) {

            for (Long i = topArticleId + 1; i <= recentArticleId; i++) {
                joonggonaraParser.getArticle(i);
            }
        }
    }
}
