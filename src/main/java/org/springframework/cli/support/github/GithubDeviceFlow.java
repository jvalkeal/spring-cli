/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cli.support.github;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.UnicastSpec;
import reactor.util.retry.Retry;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Imlements methods for github auth flows.
 *
 * @author Janne Valkealahti
 */
public class GithubDeviceFlow {

	private static final Logger log = LoggerFactory.getLogger(GithubDeviceFlow.class);
	private static final ParameterizedTypeReference<Map<String, String>> RESPONSE_TYPE_REFERENCE = new ParameterizedTypeReference<Map<String, String>>() {
	};

	private String baseUrl;

	/**
	 * Constructs a new github device flow.
	 * @param baseUrl the base url
	 */
	public GithubDeviceFlow(String baseUrl) {
		Assert.hasText(baseUrl, "baseUrl must be set");
		this.baseUrl = baseUrl;
	}

	/**
	 * Starts a device flow. Makes a simple request to github api to request new device
	 * code which user can use to complete authentication process.
	 * @param webClientBuilder the web client builder
	 * @param clientId the client id
	 * @param scope the scopes
	 * @return Map of response values
	 */
	public Map<String, String> requestDeviceFlow(WebClient.Builder webClientBuilder, String clientId, String scope) {
		WebClient client = webClientBuilder.baseUrl(baseUrl).build();
		Mono<Map<String, String>> response = client.post()
			.uri(uriBuilder -> uriBuilder.path("login/device/code")
				.queryParam("client_id", clientId)
				.queryParam("scope", scope)
				.build())
			.accept(MediaType.APPLICATION_JSON)
			.retrieve()
			.bodyToMono(RESPONSE_TYPE_REFERENCE);
		Map<String, String> values = response.block();
		return values;
	}

	/**
	 * Waits user to enter code based on info api gave us from initial device flow
	 * request.
	 * @param clientId the client id
	 * @param deviceCode the device code
	 * @param timeout the timeout
	 * @param interval the interval
	 * @return a token
	 */
	public Optional<String> waitTokenFromDeviceFlow(WebClient.Builder webClientBuilder, String clientId,
			String deviceCode, int timeout, int interval) {
		WebClient client = webClientBuilder.baseUrl(baseUrl).build();
		Mono<String> accessToken = client.post()
			.uri(uriBuilder -> uriBuilder.path("login/oauth/access_token")
				.queryParam("client_id", clientId)
				.queryParam("device_code", deviceCode)
				.queryParam("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
				.build())
			.accept(MediaType.APPLICATION_JSON)
			.exchangeToMono(response -> {
				return response.bodyToMono(RESPONSE_TYPE_REFERENCE);
			})
			.flatMap(data -> {
				String token = data.get("access_token");
				if (StringUtils.hasText(token)) {
					return Mono.just(token);
				}
				else {
					return Mono.error(new NoAccessTokenException());
				}
			})
			.retryWhen(Retry.fixedDelay(timeout / interval, Duration.ofSeconds(interval))
				.filter(t -> t instanceof NoAccessTokenException))
			.onErrorResume(e -> Mono.empty());
		return accessToken.blockOptional();
	}

	/**
	 * Used in a reactor chain to retry when poll/retry request don't yet have a token.
	 */
	private static class NoAccessTokenException extends RuntimeException {

	}

	public record TokenRequestState(String deviceCode, String userCode, Integer expiresIn, Integer interval,
			String verificationUri, String token) {

		private static TokenRequestState of() {
			return new TokenRequestState(null, null, null, null, null, null);
		}

		private static TokenRequestState ofDeviceCode(TokenRequestState state, String deviceCode, String userCode,
				Integer expiresIn, Integer interval, String verificationUri) {
			return new TokenRequestState(deviceCode, userCode, expiresIn, interval, verificationUri, state.token());
		}

		private static TokenRequestState ofToken(TokenRequestState state, String token) {
			return new TokenRequestState(state.deviceCode(), state.userCode(), state.expiresIn(), state.interval(),
					state.verificationUri(), token);
		}
	}

	public Flux<TokenRequestState> requestDeviceFlowx(WebClient.Builder webClientBuilder, String clientId, String scope) {
		WebClient client = webClientBuilder.baseUrl(baseUrl).build();
		Many<TokenRequestState> sink = Sinks.many().unicast().onBackpressureBuffer();
		Mono<TokenRequestState> loginChain = Mono.just(TokenRequestState.of())
			.flatMap(state -> loginDeviceCodeRequest(client, clientId, scope, state, sink))
			.flatMap(state -> loginOauthAccessTokenRequest(client, clientId, state, sink))
			.doOnTerminate(() -> {
				sink.emitComplete(EmitFailureHandler.FAIL_FAST);
			});
		Flux<TokenRequestState> flux = sink.asFlux()
			.doFirst(() -> {
				loginChain.subscribe();
			});

		return flux;
	}

	private static Mono<TokenRequestState> loginDeviceCodeRequest(WebClient client, String clientId, String scope,
			TokenRequestState state, Many<TokenRequestState> sink) {
		return client.post()
			.uri(uriBuilder -> uriBuilder.path("login/device/code")
				.queryParam("client_id", clientId)
				.queryParam("scope", scope)
				.build())
			.accept(MediaType.APPLICATION_JSON)
			.retrieve()
			.bodyToMono(RESPONSE_TYPE_REFERENCE)
			.map(response -> {
				String deviceCode = response.get("device_code");
				String userCode = response.get("user_code");
				Integer expiresIn = Integer.parseInt(response.get("expires_in"));
				Integer interval = Integer.parseInt(response.get("interval"));
				String verificationUri = response.get("verification_uri");
				return TokenRequestState.ofDeviceCode(state, deviceCode, userCode, expiresIn, interval, verificationUri);
			})
			.doOnNext(trs -> {
				sink.emitNext(trs, EmitFailureHandler.FAIL_FAST);
			});
	}

	private static Mono<TokenRequestState> loginOauthAccessTokenRequest(WebClient client, String clientId,
			TokenRequestState state, Many<TokenRequestState> sink) {
		return client.post()
			.uri(uriBuilder -> uriBuilder.path("login/oauth/access_token")
				.queryParam("client_id", clientId)
				.queryParam("device_code", state.deviceCode())
				.queryParam("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
				.build())
			.accept(MediaType.APPLICATION_JSON)
			.exchangeToMono(response -> {
				return response.bodyToMono(RESPONSE_TYPE_REFERENCE);
			})
			.flatMap(data -> {
				String token = data.get("access_token");
				if (StringUtils.hasText(token)) {
					return Mono.just(TokenRequestState.ofToken(state, token));
				}
				else {
					sink.emitNext(state, EmitFailureHandler.FAIL_FAST);
					return Mono.error(new NoAccessTokenException());
				}
			})
			.retryWhen(Retry.fixedDelay(state.expiresIn() / state.interval(), Duration.ofSeconds(state.interval()))
				.filter(t -> t instanceof NoAccessTokenException))
			.onErrorResume(e -> Mono.empty())
			.doOnNext(trs -> {
				sink.emitNext(trs, EmitFailureHandler.FAIL_FAST);
			});
	}


}
