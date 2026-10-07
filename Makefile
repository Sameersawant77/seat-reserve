.PHONY: build test run burst docker-up

build:
	./gradlew bootJar

test:
	./gradlew test

run:
	./gradlew bootRun

docker-up:
	docker compose up --build

burst:
	bash burst.sh $(or $(BASE_URL),http://localhost:8080) $(or $(CONCURRENCY),200) $(or $(HOT_USERS),500) $(or $(MAX_PARALLEL),32)
