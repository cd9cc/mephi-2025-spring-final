package ru.mephi.hotelbooking.booking.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import ru.mephi.hotelbooking.booking.service.TokenService;
import ru.mephi.hotelbooking.common.trace.TracePropagationInterceptor;

@Configuration
@EnableConfigurationProperties({HotelClientProperties.class, BookingProperties.class})
public class HotelClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient hotelRestClient(@LoadBalanced RestClient.Builder builder, HotelClientProperties properties,
                                      TokenService tokenService) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        return builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .requestInterceptor(new TracePropagationInterceptor())
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenService.issueServiceToken());
                    return execution.execute(request, body);
                })
                .build();
    }
}
