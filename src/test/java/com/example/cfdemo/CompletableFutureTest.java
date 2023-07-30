package com.example.cfdemo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
class CompletableFutureTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldGetContributors() {
        GithubUser user = GithubUser.of("pivotal");
        user = getRepositoryList(user);
        System.out.println(user);

        CompletableFuture[] contributorFeatures = user.getRepositories().stream()
                .map(this::contributorRequestBuild)
                .collect(Collectors.toList())
                .toArray(new CompletableFuture[0]);


        CompletableFuture<Void> finalContributors = CompletableFuture.allOf(contributorFeatures);
        finalContributors.join();

        System.out.println(user);
    }

    @SneakyThrows
    private CompletableFuture<Void> contributorRequestBuild(Repository repository) {
//        HttpRequest requestContributors = HttpRequest.newBuilder()
//                .uri(new URI("https://api.github.com/repos/" + repository.getFullName() + "/contributors"))
//                .version(HttpClient.Version.HTTP_2)
//                .GET()
//                .build();
//        return HttpClient.newBuilder()
//                .build()
//                .sendAsync(requestContributors, HttpResponse.BodyHandlers.ofString())
//                .thenApply(resp -> this.buildTreeNode(resp.body()))
//                .exceptionally(ex -> {log.error("", ex); return mapper.createArrayNode();})
//                .thenApply(tree -> tree.findValuesAsText ("login"))
//                .thenApply(contribList -> contribList.stream().map(Contributor::of).collect(Collectors.toList()))
//                .thenAccept(contributors -> repository.provideContributors(contributors));

        return CompletableFuture.supplyAsync(() -> this.buildContributorsResponse(repository.getFullName()))
                .thenApply(resp -> {
                    try {
                        log.info("Read contrib response for {} in thread {}", repository.getFullName(),
                                Thread.currentThread().getName());
                        return mapper.readTree(resp);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }

                })
                .exceptionally(ex -> {log.error("", ex); return mapper.createArrayNode();})
                .thenApply(tree -> tree.findValuesAsText ("login"))
                .thenApply(contribList -> contribList.stream().map(Contributor::of).collect(Collectors.toList()))
                .thenAccept(contributors -> repository.provideContributors(contributors));
    }

    @SneakyThrows
    private GithubUser getRepositoryList(GithubUser user) {
//        HttpRequest requestUserRepos = HttpRequest.newBuilder()
//                .uri(new URI(String.format("https://api.github.com/users/%s/repos", user.getName())))
//                .version(HttpClient.Version.HTTP_2)
//                .GET()
//                .build();
//        CompletableFuture<Void> responseRepos = HttpClient.newBuilder()
//                .build()
//                .sendAsync(requestUserRepos, HttpResponse.BodyHandlers.ofString())
//                .thenApply(resp -> {
//                    try {
//                        return mapper.readTree(resp.body());
//                    } catch (JsonProcessingException e) {
//                        throw new IllegalStateException(e);
//                    }
//
//                })
//                .exceptionally(ex -> {log.error("", ex); return mapper.createArrayNode();})
//                .thenApply(tree -> tree.findValuesAsText ("full_name"))
//                .thenApply(repoNames -> repoNames.stream().map(Repository::of).collect(Collectors.toList()))
//                .thenAccept(repoList -> user.provideRepositories(repoList));

        CompletableFuture<Void> responseRepos = CompletableFuture
                .supplyAsync(() -> this.buildRepositoriesResponse())
                .thenApply(resp -> {
                    try {
                        log.info("Read repos in thread {}", Thread.currentThread().getName());
                        return mapper.readTree(resp);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }

                })
                .exceptionally(ex -> {log.error("", ex); return mapper.createArrayNode();})
                .thenApply(tree -> tree.findValuesAsText ("full_name"))
                .thenApply(repoNames -> repoNames.stream().map(Repository::of).collect(Collectors.toList()))
                .thenAccept(repoList -> user.provideRepositories(repoList))
                .thenAccept(nothing -> log.info("Repos are read in thread {}", Thread.currentThread().getName()));

        responseRepos.join();
        return user;
    }

    @Value(staticConstructor = "of")
    @Getter
    static class Contributor {
        private String name;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    static class Repository {
        @Getter
        private final String fullName;
        private List<Contributor> contributors = new ArrayList<>();

        public static Repository of(String fullName) {
            Preconditions.checkArgument(StringUtils.isNotBlank(fullName), "Full name is required");
            return new Repository(fullName);
        }

        public void provideContributors(List<Contributor> contributors) {
            this.contributors.addAll(contributors);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    static class GithubUser {
        @Getter
        private final String name;
        private List<Repository> repositories = new ArrayList<>();

        public static GithubUser of(String name) {
            Preconditions.checkArgument(StringUtils.isNotBlank(name), "Name is required");
            return new GithubUser(name);
        }

        public void provideRepositories(List<Repository> repositories) {
            this.repositories.addAll(repositories);
        }

        public List<Repository> getRepositories() {
            return Collections.unmodifiableList(repositories);
        }
    }

    @SneakyThrows
    private String buildContributorsResponse(String repoName) {
        log.info("Build contrib response for {} in thread {}", repoName,
                Thread.currentThread().getName());
        TimeUnit.SECONDS.sleep(5);
        StringBuilder stringBuilder = new StringBuilder("[");
        int consumerNumber = RandomUtils.nextInt(1, 31);
        for (int i = 0; i < consumerNumber; i++) {
            stringBuilder.append("{\"login\":\"")
                    .append(RandomStringUtils.randomAlphabetic(9))
                    .append("\"},");
        }
        stringBuilder.append("{\"login\":\"")
                .append(RandomStringUtils.randomAlphabetic(9))
                .append("\"}]");
        return stringBuilder.toString();
    }

    private String buildRepositoriesResponse() {
        log.info("Build repo response in thread {}", Thread.currentThread().getName());
        StringBuilder stringBuilder = new StringBuilder("[");
        int consumerNumber = 10;//RandomUtils.nextInt(1, 31);
        for (int i = 0; i < consumerNumber; i++) {
            stringBuilder.append("{\"full_name\":\"")
                    .append(RandomStringUtils.randomAlphabetic(9))
                    .append("\"},");
        }
        stringBuilder.append("{\"full_name\":\"")
                .append(RandomStringUtils.randomAlphabetic(9))
                .append("\"}]");
        return stringBuilder.toString();
    }
}