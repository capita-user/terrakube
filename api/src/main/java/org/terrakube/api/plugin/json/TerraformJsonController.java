package org.terrakube.api.plugin.json;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/terraform")
public class TerraformJsonController {
    @Autowired
    private WebClient.Builder webClientBuilder; // Use Spring-managed WebClient.Builder

    TerraformJsonProperties terraformJsonProperties;

    @GetMapping(value= "/index.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createToken() {

        String releasesUrl = terraformJsonProperties.getReleasesUrl();
        String finalUrl = (releasesUrl != null && !releasesUrl.isEmpty()) ? releasesUrl : "https://releases.hashicorp.com/terraform/index.json";

        log.info("Fetching Terraform index from: {}", finalUrl);

        WebClient webClient = webClientBuilder
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                        .build())
                .baseUrl(finalUrl)
                .build();

        String terraformIndex;
        try {
            terraformIndex = webClient.get()
                    .uri(finalUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return ResponseEntity.ok(terraformIndex);
        } catch (Exception e) {
            log.error("Failed to fetch Terraform index from {}", finalUrl, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching Terraform index");
        }

    }
}


