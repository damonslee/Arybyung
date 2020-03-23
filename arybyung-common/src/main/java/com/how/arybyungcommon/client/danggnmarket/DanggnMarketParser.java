package com.how.arybyungcommon.client.danggnmarket;

import com.how.arybyungcommon.properties.UrlProperties;
import com.how.arybyungcommon.service.FilteringWordService;
import com.how.muchcommon.entity.jpaentity.ArticleEntity;
import com.how.muchcommon.model.type.ArticleState;
import com.how.muchcommon.model.type.MarketName;
import com.how.muchcommon.repository.jparepository.ArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class DanggnMarketParser {

    private final ArticleRepository articleRepository;
    private final FilteringWordService filteringWordService;
    private final UrlProperties urlProperties;

    public DanggnMarketParser(ArticleRepository articleRepository,
                              FilteringWordService filteringWordService,
                              UrlProperties urlProperties) {
        this.articleRepository = articleRepository;
        this.filteringWordService = filteringWordService;
        this.urlProperties = urlProperties;
    }

    public Long getRecentArticleId() throws IOException {
        Document document = Jsoup.connect(urlProperties.getDanggnArticleUrl())
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36")
                .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,imgwebp,*/*;q=0.8")
                .header(HttpHeaders.ACCEPT_CHARSET, "utf-8")
                .get();

        List<Element> elements = document.select("[data-event-label]");
        Long maxArticleId = elements.stream().map(element -> Long.parseLong(element.attr("data-event-label"))).max(Long::compareTo).orElse(0L);

        return maxArticleId;
    }


    public Boolean validationArticle(Long articleId) {
        String url = urlProperties.getDanggnArticleUrl() + "/articles/" + articleId;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try {

            Document document = Jsoup.connect(url)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36")
                    .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,imgwebp,*/*;q=0.8")
                    .header(HttpHeaders.ACCEPT_CHARSET, "utf-8")
                    .get();

            Element subjectElement = document.getElementById("article-title");

            return subjectElement == null ? false : true;
        } catch (IOException e) {
            log.error("validation Danggn {} => IOException : {}", articleId, e.getMessage());
        }

        return false;
    }

    @Async("danggnMarketTaskExecutor")
    public void separationGetArticles(Long start, Long unit) {
        for(Long articleId = start ; articleId < start + unit ; articleId++) {
            getArticle(articleId);
        }
    }

    public void getArticle(Long articleId) {

        String url = urlProperties.getDanggnArticleUrl() + "/articles/" + articleId;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try {

            Document document = Jsoup.connect(url)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36")
                    .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,imgwebp,*/*;q=0.8")
                    .header(HttpHeaders.ACCEPT_CHARSET, "utf-8")
                    .get();

            Element subjectElement = document.getElementById("article-title");
            if(filteringWordService.stringFilter(subjectElement.text())){
                return;
            }

            Element priceElement = document.getElementById("article-price") == null ? document.getElementById("article-price-nanum") : document.getElementById("article-price");
            String stringPrice = priceElement.text().replaceAll("[^0-9]", "");

            ArticleState state = priceElement.text().equals("무료나눔") ? ArticleState.F : ArticleState.S;
            Long price = Long.parseLong(stringPrice.equals("") ? "0" : stringPrice);

            Element imageElement = document.selectFirst("[class=image-wrap]").child(0);
            Element regionElement = document.getElementById("region-name");
            Element dateElement = document.selectFirst("[datatype=xsd:date]");
            Element contentElement = document.getElementById("article-detail");

            String content = contentElement.text() + "\n" + regionElement.text();

            if(content.length() > 2000 || filteringWordService.stringFilter(content)) {
                return;
            }

            ArticleEntity articleEntity = ArticleEntity.builder()
                    .articleId(articleId)
                    .url(url)
                    .subject(subjectElement.text())
                    .price(price)
                    .state(state)
                    .image(imageElement.attr("data-lazy"))
                    .site(MarketName.danggn.getName())
                    .postingDtime(LocalDate.parse(dateElement.attr("content"), formatter).atStartOfDay(ZoneId.of("Asia/Seoul")).minusYears(2))
                    .content(content)
                    .build();

            articleRepository.save(articleEntity);
        } catch (HttpStatusException e) {
//            log.error("{} : Not found", url);
        } catch (NullPointerException e) {
//            log.error("{} : Secret Article", url);
        } catch (IOException e) {
//            log.error("{} : Connect Time", url);
        }
    }
}
