package org.terrakube.api.plugin.json;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.TimeUnit;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/tofu")
public class TofuJsonController {
    @Autowired
    private WebClient.Builder webClientBuilder; // Use Spring-managed WebClient.Builder

    private static final String TOFU_REDIS_KEY = "tofuReleasesResponse";
    TofuJsonProperties tofuJsonProperties;
    RedisTemplate redisTemplate;

    @GetMapping(value= "/index.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTofuReleases() {
        if(redisTemplate.hasKey(TOFU_REDIS_KEY)) {
            log.info("Getting tofu releases from redis....");
            String tofuRedis = (String) redisTemplate.opsForValue().get(TOFU_REDIS_KEY);
            return new ResponseEntity<>(tofuRedis, HttpStatus.OK);
        } else {
            String releasesUrl = tofuJsonProperties.getReleasesUrl();
            String tofuUrl =  (releasesUrl != null && !releasesUrl.isEmpty()) ? releasesUrl : "https://api.github.com/repos/opentofu/opentofu/releases";

            log.info("Fetching Tofu index from: {}", tofuUrl);

            WebClient webClient = webClientBuilder
                    .exchangeStrategies(ExchangeStrategies.builder()
                            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                            .build())
                    .baseUrl(tofuUrl)
                    .build();

            try {
                String tofuIndex = webClient.get()
                        .uri(tofuUrl)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                log.warn("Saving tofu releases to redis...");
                redisTemplate.opsForValue().set(TOFU_REDIS_KEY, tofuIndex);
                redisTemplate.expire(TOFU_REDIS_KEY, 30, TimeUnit.MINUTES);

                return ResponseEntity.ok(tofuIndex);
            } catch (Exception e) {
                log.error("Failed to fetch Terraform index from {}", tofuUrl, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching Tofu index");
            }
        }

    }
}


